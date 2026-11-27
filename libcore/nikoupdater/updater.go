// Package nikoupdater verifies and downloads Niko Desktop releases from GitHub Releases or a raycontrol
// node app-mirror (app-update-v1). Nothing fetched from the network is trusted until SHA256SUMS is verified
// against SHA256SUMS.minisig under the embedded Ed25519 minisign key (niko-update.pub) with trusted comment
// "niko <tag>", and the downloaded asset matches its listed SHA-256 hash.
package nikoupdater

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"time"

	"libcore/minisign"
)

// DefaultPublicKey matches niko/niko-update.pub (key ID D5D2D2E799D74D8D).
const DefaultPublicKey = "untrusted comment: minisign public key D5D2D2E799D74D8D\n" +
	"RWSNTdeZ59LS1bdwyf4iRzgIdSmDfFWw9yWiy6Qy/eAfBNj5gLnEwmRl\n"

const (
	DefaultAPIBase = "https://api.github.com"
	DefaultRepo    = "arngory/niko-desktop"
	SumsFile       = "SHA256SUMS"
	SigFile        = "SHA256SUMS.minisig"

	maxSmallBytes = 64 << 10  // 64 KiB for SHA256SUMS / SHA256SUMS.minisig / release JSON
	maxAssetBytes = 256 << 20 // 256 MiB cap for a release archive
)

var (
	ErrNoSignature  = errors.New("updater: release is missing SHA256SUMS or SHA256SUMS.minisig")
	ErrBadSignature = errors.New("updater: minisign signature verification failed")
	ErrOtherRelease = errors.New("updater: signature trusted comment does not match release tag")
	ErrNoAsset      = errors.New("updater: no matching release asset for this platform")
	ErrBadHash      = errors.New("updater: downloaded file SHA-256 does not match signed SHA256SUMS")
	ErrTooLarge     = errors.New("updater: release file exceeds maximum allowed size")
	ErrBadURL       = errors.New("updater: disallowed download URL")
	ErrInvalidTag   = errors.New("updater: invalid release tag")
)

var (
	sumLinePattern = regexp.MustCompile(`^([0-9a-fA-F]{64}) [ *]([^/\\]+)$`)
	validTag       = regexp.MustCompile(`^v[0-9A-Za-z][0-9A-Za-z.+-]{0,63}$`)
	validAssetName = regexp.MustCompile(`^[A-Za-z0-9._-]{1,128}$`)
)

// Config controls where releases are fetched from and which minisign public key verifies them.
type Config struct {
	PublicKey    string            // defaults to DefaultPublicKey
	APIBase      string            // defaults to DefaultAPIBase ("https://api.github.com")
	Repo         string            // defaults to DefaultRepo ("arngory/niko-desktop")
	UserAgent    string            // defaults to "Niko-Updater"
	GOOS         string            // defaults to runtime.GOOS
	GOARCH       string            // defaults to runtime.GOARCH
	Transport    http.RoundTripper // optional transport (e.g. bypassTransport or httptest)
	AllowHTTP    bool              // test-only: allow plain http:// against local httptest mirror
	AllowedHosts []string          // optional additional allowed download hosts (used by tests)
}

func (c Config) publicKey() (minisign.PublicKey, error) {
	raw := strings.TrimSpace(c.PublicKey)
	if raw == "" {
		raw = DefaultPublicKey
	}
	return minisign.ParsePublicKey(raw)
}

func (c Config) apiBase() string {
	if s := strings.TrimRight(strings.TrimSpace(c.APIBase), "/"); s != "" {
		return s
	}
	return DefaultAPIBase
}

func (c Config) repo() string {
	if s := strings.Trim(strings.TrimSpace(c.Repo), "/"); s != "" {
		return s
	}
	return DefaultRepo
}

func (c Config) goos() string {
	if c.GOOS != "" {
		return c.GOOS
	}
	return runtime.GOOS
}

func (c Config) goarch() string {
	if c.GOARCH != "" {
		return c.GOARCH
	}
	return runtime.GOARCH
}

func (c Config) userAgent() string {
	if c.UserAgent != "" {
		return c.UserAgent
	}
	return "Niko-Updater"
}

// Info describes a verified release check result.
type Info struct {
	CurrentVersion  string
	LatestVersion   string
	Tag             string
	UpdateAvailable bool
	AssetName       string
	AssetSHA256     string
	ReleaseURL      string
	Notes           string
	Source          string
	Verified        bool
}

// Downloaded describes a verified release artifact written on disk.
type Downloaded struct {
	Tag       string
	AssetName string
	FilePath  string
	SHA256    string
	Size      int64
	Source    string
	Verified  bool
}

// TrustedComment returns the exact minisign trusted comment expected for a desktop release tag.
// Distinct from Android's "niko <tag>" so Android and Desktop signatures cannot cross-verify.
func TrustedComment(tag string) string {
	return "niko-desktop " + tag
}

// ParseSHA256SUMS parses standard sha256sum lines into a filename -> lowercase hex hash map.
func ParseSHA256SUMS(text string) map[string]string {
	out := map[string]string{}
	for _, line := range strings.Split(text, "\n") {
		line = strings.TrimSuffix(line, "\r")
		m := sumLinePattern.FindStringSubmatch(line)
		if m == nil {
			continue
		}
		name := m[2]
		if !validAssetName.MatchString(name) || strings.Trim(name, ".") == "" {
			continue
		}
		out[name] = strings.ToLower(m[1])
	}
	return out
}

// VerifySums verifies SHA256SUMS against SHA256SUMS.minisig under key and ensures the trusted comment matches tag.
func VerifySums(key minisign.PublicKey, tag string, sums []byte, sig string) (map[string]string, error) {
	if !validTag.MatchString(tag) {
		return nil, fmt.Errorf("%w: %q", ErrInvalidTag, tag)
	}
	trusted, err := minisign.Verify(key, sig, sums)
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrBadSignature, err)
	}
	if trusted != TrustedComment(tag) {
		return nil, fmt.Errorf("%w: signed for %q, want %q", ErrOtherRelease, trusted, TrustedComment(tag))
	}
	parsed := ParseSHA256SUMS(string(sums))
	if len(parsed) == 0 {
		return nil, fmt.Errorf("%w: %s lists no valid files", ErrNoAsset, SumsFile)
	}
	return parsed, nil
}

// SelectDesktopAsset deterministically selects the exact versioned platform archive from verified SHA256SUMS.
// preferredFormat may be "appimage", "tar.gz", "deb", "rpm", "zip", or "exe" (empty uses default platform order).
func SelectDesktopAsset(sums map[string]string, tag, goos, goarch, preferredFormat string) (name, hash string, ok bool) {
	ver := strings.TrimPrefix(strings.TrimSpace(tag), "v")
	if ver == "" {
		return "", "", false
	}
	archAliases := []string{goarch}
	switch goarch {
	case "amd64":
		archAliases = []string{"amd64", "x64", "x86_64"}
	case "arm64":
		archAliases = []string{"arm64", "aarch64"}
	}
	var exts []string
	switch goos {
	case "linux":
		exts = []string{".tar.gz", ".AppImage", ".deb", ".rpm"}
		switch strings.ToLower(strings.TrimSpace(preferredFormat)) {
		case "appimage", ".appimage":
			exts = []string{".AppImage", ".tar.gz", ".deb", ".rpm"}
		case "deb", ".deb":
			exts = []string{".deb", ".tar.gz", ".AppImage", ".rpm"}
		case "rpm", ".rpm":
			exts = []string{".rpm", ".tar.gz", ".AppImage", ".deb"}
		}
	case "windows":
		exts = []string{".zip", ".exe"}
		if f := strings.ToLower(strings.TrimSpace(preferredFormat)); f == "exe" || f == ".exe" {
			exts = []string{".exe", ".zip"}
		}
	default:
		return "", "", false
	}
	for _, ext := range exts {
		for _, arch := range archAliases {
			for _, prefix := range []string{"niko-", "Niko-"} {
				candidate := fmt.Sprintf("%s%s-%s-%s%s", prefix, ver, goos, arch, ext)
				if sum, exists := sums[candidate]; exists {
					return candidate, sum, true
				}
			}
		}
	}
	return "", "", false
}

// CompareVersions compares two semver versions (with optional leading 'v') per SemVer 2.0.0 §11.
// Returns -1 if a < b, 0 if equal, +1 if a > b.
func CompareVersions(a, b string) int {
	pa, preA := splitSemver(a)
	pb, preB := splitSemver(b)
	for i := 0; i < 3; i++ {
		if pa[i] < pb[i] {
			return -1
		}
		if pa[i] > pb[i] {
			return 1
		}
	}
	// Release > prerelease (e.g. 0.2.0 > 0.2.0-rc.1).
	if preA == "" && preB == "" {
		return 0
	}
	if preA == "" {
		return 1
	}
	if preB == "" {
		return -1
	}
	partsA := strings.Split(preA, ".")
	partsB := strings.Split(preB, ".")
	for i := 0; i < len(partsA) && i < len(partsB); i++ {
		ia, errA := strconv.Atoi(partsA[i])
		ib, errB := strconv.Atoi(partsB[i])
		numA := errA == nil && partsA[i] != "" && !strings.HasPrefix(partsA[i], "-")
		numB := errB == nil && partsB[i] != "" && !strings.HasPrefix(partsB[i], "-")
		switch {
		case numA && numB:
			if ia < ib {
				return -1
			}
			if ia > ib {
				return 1
			}
		case numA && !numB:
			return -1
		case !numA && numB:
			return 1
		default:
			if partsA[i] < partsB[i] {
				return -1
			}
			if partsA[i] > partsB[i] {
				return 1
			}
		}
	}
	if len(partsA) < len(partsB) {
		return -1
	}
	if len(partsA) > len(partsB) {
		return 1
	}
	return 0
}

func splitSemver(v string) ([3]int, string) {
	v = strings.TrimSpace(strings.TrimPrefix(v, "v"))
	if i := strings.IndexByte(v, '+'); i >= 0 {
		v = v[:i]
	}
	var pre string
	if i := strings.IndexByte(v, '-'); i >= 0 {
		pre = v[i+1:]
		v = v[:i]
	}
	parts := strings.Split(v, ".")
	var nums [3]int
	for i := 0; i < len(parts) && i < 3; i++ {
		n, err := strconv.Atoi(parts[i])
		if err == nil && n >= 0 {
			nums[i] = n
		}
	}
	return nums, pre
}

type ghRelease struct {
	TagName string    `json:"tag_name"`
	HTMLURL string    `json:"html_url"`
	Body    string    `json:"body"`
	Assets  []ghAsset `json:"assets"`
}

type ghAsset struct {
	Name               string `json:"name"`
	BrowserDownloadURL string `json:"browser_download_url"`
}

func (c Config) isAllowedURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || u.Host == "" {
		return false
	}
	switch u.Scheme {
	case "https":
	case "http":
		if !c.AllowHTTP {
			return false
		}
	default:
		return false
	}
	host := strings.ToLower(u.Hostname())
	switch host {
	case "api.github.com", "github.com", "objects.githubusercontent.com",
		"release-assets.githubusercontent.com", "github-releases.githubusercontent.com":
		return true
	}
	if apiU, err := url.Parse(c.apiBase()); err == nil && strings.EqualFold(apiU.Hostname(), host) {
		return true
	}
	for _, h := range c.AllowedHosts {
		if strings.EqualFold(h, host) {
			return true
		}
	}
	return false
}

func (c Config) httpClient(timeout time.Duration) *http.Client {
	return &http.Client{
		Timeout:   timeout,
		Transport: c.Transport,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 5 {
				return errors.New("updater: too many redirects")
			}
			if !c.isAllowedURL(req.URL.String()) {
				return fmt.Errorf("%w: redirect to %s", ErrBadURL, req.URL.Host)
			}
			return nil
		},
	}
}

func (c Config) fetchSmall(ctx context.Context, client *http.Client, rawURL string) ([]byte, error) {
	if !c.isAllowedURL(rawURL) {
		return nil, fmt.Errorf("%w: %s", ErrBadURL, rawURL)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", c.userAgent())
	req.Header.Set("Accept", "application/vnd.github+json")
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("updater: HTTP %d from %s", resp.StatusCode, req.URL.Host)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, maxSmallBytes+1))
	if err != nil {
		return nil, err
	}
	if len(data) > maxSmallBytes {
		return nil, ErrTooLarge
	}
	return data, nil
}

func (c Config) fetchRelease(ctx context.Context, client *http.Client, tag string) (*ghRelease, map[string]string, error) {
	endpoint := c.apiBase() + "/repos/" + c.repo() + "/releases/latest"
	if tag != "" {
		if !validTag.MatchString(tag) {
			return nil, nil, fmt.Errorf("%w: %q", ErrInvalidTag, tag)
		}
		endpoint = c.apiBase() + "/repos/" + c.repo() + "/releases/tags/" + url.PathEscape(tag)
	}
	raw, err := c.fetchSmall(ctx, client, endpoint)
	if err != nil {
		return nil, nil, err
	}
	var rel ghRelease
	if err := json.Unmarshal(raw, &rel); err != nil {
		return nil, nil, fmt.Errorf("updater: invalid release JSON: %w", err)
	}
	if !validTag.MatchString(rel.TagName) {
		return nil, nil, fmt.Errorf("%w: %q", ErrInvalidTag, rel.TagName)
	}
	assets := map[string]string{}
	for _, a := range rel.Assets {
		if validAssetName.MatchString(a.Name) && c.isAllowedURL(a.BrowserDownloadURL) {
			assets[a.Name] = a.BrowserDownloadURL
		}
	}
	return &rel, assets, nil
}

// CheckGitHub fetches the latest release metadata from GitHub (or configured APIBase), verifies
// SHA256SUMS + SHA256SUMS.minisig under Config.PublicKey, and returns verified UpdateInfo.
func CheckGitHub(ctx context.Context, cfg Config, currentVersion, preferredFormat string) (*Info, error) {
	key, err := cfg.publicKey()
	if err != nil {
		return nil, err
	}
	// Bound the entire 3-request GitHub check stage so mirror fallback still fits inside the caller deadline.
	ghCtx, cancel := context.WithTimeout(ctx, 25*time.Second)
	defer cancel()
	client := cfg.httpClient(25 * time.Second)
	rel, assets, err := cfg.fetchRelease(ghCtx, client, "")
	if err != nil {
		return nil, err
	}
	sumsURL, hasSums := assets[SumsFile]
	sigURL, hasSig := assets[SigFile]
	if !hasSums || !hasSig {
		return nil, ErrNoSignature
	}
	sumsRaw, err := cfg.fetchSmall(ghCtx, client, sumsURL)
	if err != nil {
		return nil, err
	}
	sigRaw, err := cfg.fetchSmall(ghCtx, client, sigURL)
	if err != nil {
		return nil, err
	}
	listed, err := VerifySums(key, rel.TagName, sumsRaw, string(sigRaw))
	if err != nil {
		return nil, err
	}
	assetName, assetHash, ok := SelectDesktopAsset(listed, rel.TagName, cfg.goos(), cfg.goarch(), preferredFormat)
	if !ok {
		return nil, fmt.Errorf("%w (%s/%s)", ErrNoAsset, cfg.goos(), cfg.goarch())
	}
	latest := strings.TrimPrefix(rel.TagName, "v")
	cur := strings.TrimPrefix(strings.TrimSpace(currentVersion), "v")
	avail := cur == "" || cur == "dev" || CompareVersions(cur, latest) < 0
	notes := rel.Body
	if len(notes) > 2048 {
		notes = notes[:2048] + "…"
	}
	return &Info{
		CurrentVersion:  currentVersion,
		LatestVersion:   latest,
		Tag:             rel.TagName,
		UpdateAvailable: avail,
		AssetName:       assetName,
		AssetSHA256:     assetHash,
		ReleaseURL:      rel.HTMLURL,
		Notes:           notes,
		Source:          "github",
		Verified:        true,
	}, nil
}

// DownloadGitHub downloads the platform asset for tag (or latest if empty) into destDir, verifying both
// the minisign signature on SHA256SUMS and the SHA-256 hash of the downloaded archive before exposing it.
func DownloadGitHub(ctx context.Context, cfg Config, tag, preferredFormat, destDir string) (*Downloaded, error) {
	key, err := cfg.publicKey()
	if err != nil {
		return nil, err
	}
	metaCtx, cancel := context.WithTimeout(ctx, 25*time.Second)
	defer cancel()
	smallClient := cfg.httpClient(25 * time.Second)
	rel, assets, err := cfg.fetchRelease(metaCtx, smallClient, tag)
	if err != nil {
		return nil, err
	}
	sumsURL, hasSums := assets[SumsFile]
	sigURL, hasSig := assets[SigFile]
	if !hasSums || !hasSig {
		return nil, ErrNoSignature
	}
	sumsRaw, err := cfg.fetchSmall(metaCtx, smallClient, sumsURL)
	if err != nil {
		return nil, err
	}
	sigRaw, err := cfg.fetchSmall(metaCtx, smallClient, sigURL)
	if err != nil {
		return nil, err
	}
	listed, err := VerifySums(key, rel.TagName, sumsRaw, string(sigRaw))
	if err != nil {
		return nil, err
	}
	assetName, expectedHash, ok := SelectDesktopAsset(listed, rel.TagName, cfg.goos(), cfg.goarch(), preferredFormat)
	if !ok {
		return nil, fmt.Errorf("%w (%s/%s)", ErrNoAsset, cfg.goos(), cfg.goarch())
	}
	assetURL, ok := assets[assetName]
	if !ok {
		return nil, fmt.Errorf("%w: %s missing from release assets", ErrNoAsset, assetName)
	}
	if err := os.MkdirAll(destDir, 0o700); err != nil {
		return nil, err
	}
	finalPath := filepath.Join(destDir, assetName)
	if gotHash, size, err := fileSHA256(finalPath); err == nil && gotHash == expectedHash {
		pruneOldUpdates(destDir, assetName)
		return &Downloaded{
			Tag:       rel.TagName,
			AssetName: assetName,
			FilePath:  finalPath,
			SHA256:    expectedHash,
			Size:      size,
			Source:    "github",
			Verified:  true,
		}, nil
	}
	partPath := finalPath + ".part"
	dlClient := cfg.httpClient(15 * time.Minute)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, assetURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", cfg.userAgent())
	resp, err := dlClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("updater: HTTP %d downloading %s", resp.StatusCode, assetName)
	}
	f, err := os.OpenFile(partPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o600)
	if err != nil {
		return nil, err
	}
	hasher := sha256.New()
	written, copyErr := io.Copy(io.MultiWriter(f, hasher), io.LimitReader(resp.Body, maxAssetBytes+1))
	closeErr := f.Close()
	if copyErr != nil {
		_ = os.Remove(partPath)
		return nil, copyErr
	}
	if closeErr != nil {
		_ = os.Remove(partPath)
		return nil, closeErr
	}
	if written > maxAssetBytes {
		_ = os.Remove(partPath)
		return nil, ErrTooLarge
	}
	gotHash := hex.EncodeToString(hasher.Sum(nil))
	if gotHash != expectedHash {
		_ = os.Remove(partPath)
		return nil, fmt.Errorf("%w: got %s, want %s", ErrBadHash, gotHash[:16], expectedHash[:16])
	}
	if err := os.Rename(partPath, finalPath); err != nil {
		_ = os.Remove(partPath)
		return nil, err
	}
	pruneOldUpdates(destDir, assetName)
	return &Downloaded{
		Tag:       rel.TagName,
		AssetName: assetName,
		FilePath:  finalPath,
		SHA256:    expectedHash,
		Size:      written,
		Source:    "github",
		Verified:  true,
	}, nil
}

// MirrorFetchFunc writes a release file named `name` to `outPath`, optionally resuming using `etag`.
type MirrorFetchFunc func(ctx context.Context, name, outPath, etag string) error

// DownloadFromMirror verifies SHA256SUMS + SHA256SUMS.minisig fetched via a raycontrol node app-mirror
// (app-update-v1) and downloads/verifies the platform archive. The mirror is completely untrusted.
func DownloadFromMirror(ctx context.Context, cfg Config, tag, preferredFormat, destDir string, fetch MirrorFetchFunc) (*Downloaded, error) {
	if !validTag.MatchString(tag) {
		return nil, fmt.Errorf("%w: %q", ErrInvalidTag, tag)
	}
	key, err := cfg.publicKey()
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(destDir, 0o700); err != nil {
		return nil, err
	}
	sumsPath := filepath.Join(destDir, tag+"."+SumsFile)
	sigPath := filepath.Join(destDir, tag+"."+SigFile)
	defer func() {
		_ = os.Remove(sumsPath)
		_ = os.Remove(sigPath)
	}()
	if err := fetch(ctx, SumsFile, sumsPath, ""); err != nil {
		return nil, err
	}
	if err := fetch(ctx, SigFile, sigPath, ""); err != nil {
		return nil, err
	}
	sumsRaw, err := readBoundedFile(sumsPath, maxSmallBytes)
	if err != nil {
		return nil, err
	}
	sigRaw, err := readBoundedFile(sigPath, maxSmallBytes)
	if err != nil {
		return nil, err
	}
	listed, err := VerifySums(key, tag, sumsRaw, string(sigRaw))
	if err != nil {
		return nil, err
	}
	assetName, expectedHash, ok := SelectDesktopAsset(listed, tag, cfg.goos(), cfg.goarch(), preferredFormat)
	if !ok {
		return nil, fmt.Errorf("%w (%s/%s)", ErrNoAsset, cfg.goos(), cfg.goarch())
	}
	finalPath := filepath.Join(destDir, assetName)
	if gotHash, size, err := fileSHA256(finalPath); err == nil && gotHash == expectedHash {
		pruneOldUpdates(destDir, assetName)
		return &Downloaded{
			Tag:       tag,
			AssetName: assetName,
			FilePath:  finalPath,
			SHA256:    expectedHash,
			Size:      size,
			Source:    "mirror",
			Verified:  true,
		}, nil
	}
	partPath := finalPath + ".part"
	if err := fetch(ctx, assetName, partPath, `"`+expectedHash+`"`); err != nil {
		return nil, err
	}
	gotHash, size, err := fileSHA256(partPath)
	if err != nil {
		_ = os.Remove(partPath)
		return nil, err
	}
	if size > maxAssetBytes {
		_ = os.Remove(partPath)
		return nil, ErrTooLarge
	}
	if gotHash != expectedHash {
		_ = os.Remove(partPath)
		return nil, fmt.Errorf("%w: got %s, want %s", ErrBadHash, gotHash[:16], expectedHash[:16])
	}
	if err := os.Rename(partPath, finalPath); err != nil {
		_ = os.Remove(partPath)
		return nil, err
	}
	pruneOldUpdates(destDir, assetName)
	return &Downloaded{
		Tag:       tag,
		AssetName: assetName,
		FilePath:  finalPath,
		SHA256:    expectedHash,
		Size:      size,
		Source:    "mirror",
		Verified:  true,
	}, nil
}

func pruneOldUpdates(dir, keep string) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return
	}
	for _, e := range entries {
		if e.Name() != keep {
			_ = os.RemoveAll(filepath.Join(dir, e.Name()))
		}
	}
}

func readBoundedFile(path string, limit int64) ([]byte, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	data, err := io.ReadAll(io.LimitReader(f, limit+1))
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > limit {
		return nil, ErrTooLarge
	}
	return data, nil
}

func fileSHA256(path string) (string, int64, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", 0, err
	}
	defer f.Close()
	h := sha256.New()
	n, err := io.Copy(h, f)
	if err != nil {
		return "", 0, err
	}
	return hex.EncodeToString(h.Sum(nil)), n, nil
}
