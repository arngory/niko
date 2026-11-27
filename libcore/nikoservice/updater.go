package nikoservice

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"libcore/nikopb"
	"libcore/nikoray"
	"libcore/nikoupdater"
	"libcore/raysub"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const updateStreamChunkBytes = 256 << 10 // 256 KiB verified chunks

func (s *server) effectiveUpdaterConfig() nikoupdater.Config {
	if s.updaterCfg != nil {
		cfg := *s.updaterCfg
		if cfg.Transport == nil {
			cfg.Transport = s.bypassTransport()
		}
		if cfg.UserAgent == "" {
			cfg.UserAgent = "Niko/" + Version
		}
		return cfg
	}
	return nikoupdater.Config{
		UserAgent: "Niko/" + Version,
		Transport: s.bypassTransport(),
	}
}

func updaterError(err error) error {
	switch {
	case errors.Is(err, nikoupdater.ErrBadSignature),
		errors.Is(err, nikoupdater.ErrOtherRelease),
		errors.Is(err, nikoupdater.ErrBadHash),
		errors.Is(err, nikoupdater.ErrNoSignature):
		return status.Error(codes.PermissionDenied, err.Error())
	case errors.Is(err, nikoupdater.ErrInvalidTag),
		errors.Is(err, nikoupdater.ErrBadURL):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.Is(err, nikoupdater.ErrNoAsset):
		return status.Error(codes.NotFound, err.Error())
	case errors.Is(err, context.Canceled):
		return status.Error(codes.Canceled, "update canceled")
	case errors.Is(err, context.DeadlineExceeded):
		return status.Error(codes.DeadlineExceeded, "update request timed out")
	default:
		return status.Error(codes.Unavailable, err.Error())
	}
}

// newestMirrorRelease finds a non-refused node holding the newest stable desktop release (app-update-v1).
func (s *server) newestMirrorRelease(u *user, preferredID, current string) (*nikoray.Node, *nikoray.AppRelease) {
	candidates := func() []*nikoray.Node {
		var list []*nikoray.Node
		if preferredID != "" {
			if n, err := u.store.Node(preferredID); err == nil && n.Subscription != nil && n.RefreshAt != nikoray.Stopped {
				list = append(list, n)
			}
		}
		for _, kept := range u.store.Nodes() {
			if kept.Unreadable || kept.Node.Subscription == nil || kept.Node.RefreshAt == nikoray.Stopped {
				continue
			}
			if preferredID != "" && kept.Node.ID() == preferredID {
				continue
			}
			list = append(list, kept.Node)
		}
		return list
	}()

	cur := strings.TrimPrefix(strings.TrimSpace(current), "v")
	var bestNode *nikoray.Node
	var bestRel *nikoray.AppRelease
	for _, n := range candidates {
		for i := range n.AppReleases {
			rel := n.AppReleases[i]
			if rel.Prerelease {
				continue
			}
			if cur != "" && cur != "dev" && nikoupdater.CompareVersions(cur, rel.Tag) >= 0 {
				continue
			}
			if bestRel == nil || nikoupdater.CompareVersions(bestRel.Tag, rel.Tag) < 0 {
				copyRel := rel
				bestNode = n
				bestRel = &copyRel
			}
		}
	}
	return bestNode, bestRel
}

// nodeHoldingTag finds a non-refused node whose app-update-v1 list includes tag.
func (s *server) nodeHoldingTag(u *user, preferredID, tag string) *nikoray.Node {
	check := func(n *nikoray.Node) bool {
		if n == nil || n.Subscription == nil || n.RefreshAt == nikoray.Stopped {
			return false
		}
		for _, r := range n.AppReleases {
			if r.Tag == tag {
				return true
			}
		}
		return false
	}
	if preferredID != "" {
		if n, err := u.store.Node(preferredID); err == nil && check(n) {
			return n
		}
	}
	for _, kept := range u.store.Nodes() {
		if !kept.Unreadable && check(kept.Node) {
			return kept.Node
		}
	}
	return nil
}

func (s *server) mirrorFetchFunc(node *nikoray.Node, tag string) (nikoupdater.MirrorFetchFunc, error) {
	spa, err := spaOptions(node.SPAJSON())
	if err != nil {
		return nil, err
	}
	control, lookup := s.bypass()
	baseReq := raysub.Request{
		Host:     node.Host,
		Port:     node.Subscription.Port,
		Pin:      node.Subscription.Pin,
		DeviceID: node.DeviceID,
		NodeID:   node.ID(),
		Ed25519:  node.Subscription.Ed25519,
		MLDSA65:  node.Subscription.MLDSA65,
		SPA:      spa,
		Control:  control,
		Lookup:   lookup,
	}
	if name := node.ReachName(); name != "" {
		baseReq.Host, baseReq.Addrs = name, node.KnownAddrs()
	}
	return func(ctx context.Context, name, outPath, etag string) error {
		return raysub.AppFile(ctx, baseReq, "niko-desktop", tag, name, outPath, etag)
	}, nil
}

// CheckUpdate checks GitHub Releases (with raycontrol node app-mirror fallback) for a verified release.
func (s *server) CheckUpdate(ctx context.Context, req *nikopb.CheckUpdateRequest) (*nikopb.UpdateInfo, error) {
	if s.canStart != nil {
		if err := s.canStart(ctx); err != nil {
			return nil, err
		}
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	current := strings.TrimSpace(req.GetCurrentVersion())
	if current == "" {
		current = Version
	}
	cfg := s.effectiveUpdaterConfig()
	info, ghErr := nikoupdater.CheckGitHub(ctx, cfg, current, req.GetPreferredFormat())
	if ghErr == nil {
		return &nikopb.UpdateInfo{
			CurrentVersion:  info.CurrentVersion,
			LatestVersion:   info.LatestVersion,
			Tag:             info.Tag,
			UpdateAvailable: info.UpdateAvailable,
			AssetName:       info.AssetName,
			AssetSha256:     info.AssetSHA256,
			ReleaseUrl:      info.ReleaseURL,
			Notes:           info.Notes,
			Source:          info.Source,
			Verified:        info.Verified,
		}, nil
	}
	// Never bypass cryptographic signature failures by falling back to another source silently.
	if errors.Is(ghErr, nikoupdater.ErrBadSignature) || errors.Is(ghErr, nikoupdater.ErrOtherRelease) {
		return nil, updaterError(ghErr)
	}
	// Fallback: check whether a raycontrol node advertises a newer stable desktop release.
	if _, rel := s.newestMirrorRelease(u, req.GetNodeId(), current); rel != nil {
		return &nikopb.UpdateInfo{
			CurrentVersion:  current,
			LatestVersion:   strings.TrimPrefix(rel.Tag, "v"),
			Tag:             rel.Tag,
			UpdateAvailable: true,
			Source:          "mirror",
			Verified:        false, // Verified cryptographically when DownloadUpdate fetches signed SHA256SUMS.
		}, nil
	}
	return nil, updaterError(ghErr)
}

func checkDowngrade(current, tag string, allowDowngrade bool) error {
	if allowDowngrade {
		return nil
	}
	cur := strings.TrimPrefix(strings.TrimSpace(current), "v")
	if cur == "" || cur == "dev" {
		return nil
	}
	if nikoupdater.CompareVersions(cur, tag) >= 0 {
		return status.Errorf(codes.FailedPrecondition, "refusing downgrade or reinstall: target %s is not newer than %s", tag, current)
	}
	return nil
}

// DownloadUpdate downloads and cryptographically verifies the release archive inside the daemon's
// private staging directory, then streams the verified bytes to the unprivileged caller and removes
// the temporary staged copy.
func (s *server) DownloadUpdate(req *nikopb.DownloadUpdateRequest, stream grpc.ServerStreamingServer[nikopb.DownloadUpdateEvent]) error {
	ctx := stream.Context()
	if s.canStart != nil {
		if err := s.canStart(ctx); err != nil {
			return err
		}
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return err
	}
	if !s.updateMu.TryLock() {
		return status.Error(codes.Aborted, "an update download is already in progress")
	}
	defer s.updateMu.Unlock()

	current := strings.TrimSpace(req.GetCurrentVersion())
	if current == "" {
		current = Version
	}
	tag := strings.TrimSpace(req.GetTag())
	if tag != "" {
		if err := checkDowngrade(current, tag, req.GetAllowDowngrade()); err != nil {
			return err
		}
	}

	cfg := s.effectiveUpdaterConfig()
	destDir := filepath.Join(u.store.Dir(), "updates")

	dl, ghErr := nikoupdater.DownloadGitHub(ctx, cfg, tag, req.GetPreferredFormat(), destDir)
	if ghErr != nil {
		if errors.Is(ghErr, nikoupdater.ErrBadSignature) ||
			errors.Is(ghErr, nikoupdater.ErrOtherRelease) ||
			errors.Is(ghErr, nikoupdater.ErrBadHash) {
			return updaterError(ghErr)
		}
		// Fallback to raycontrol node app-mirror when GitHub is unreachable.
		var mirrorNode *nikoray.Node
		mirrorTag := tag
		if mirrorTag != "" {
			mirrorNode = s.nodeHoldingTag(u, req.GetNodeId(), mirrorTag)
		} else if n, rel := s.newestMirrorRelease(u, req.GetNodeId(), current); n != nil && rel != nil {
			mirrorNode, mirrorTag = n, rel.Tag
		}
		if mirrorNode == nil || mirrorTag == "" {
			return updaterError(ghErr)
		}
		if err := checkDowngrade(current, mirrorTag, req.GetAllowDowngrade()); err != nil {
			return err
		}
		fetchFn, ferr := s.mirrorFetchFunc(mirrorNode, mirrorTag)
		if ferr != nil {
			return updaterError(fmt.Errorf("mirror setup failed: %w (github: %v)", ferr, ghErr))
		}
		var mErr error
		dl, mErr = nikoupdater.DownloadFromMirror(ctx, cfg, mirrorTag, req.GetPreferredFormat(), destDir, fetchFn)
		if mErr != nil {
			return updaterError(mErr)
		}
	}

	// Enforce downgrade check when tag was resolved dynamically from /releases/latest.
	if err := checkDowngrade(current, dl.Tag, req.GetAllowDowngrade()); err != nil {
		_ = os.Remove(dl.FilePath)
		return err
	}

	// Stream the verified file to the caller, then clean up the staged copy so StateDirectory does not bloat.
	defer os.Remove(dl.FilePath)
	f, err := os.Open(dl.FilePath)
	if err != nil {
		return status.Error(codes.Internal, "open verified update: "+err.Error())
	}
	defer f.Close()

	buf := make([]byte, updateStreamChunkBytes)
	for {
		n, readErr := f.Read(buf)
		if n > 0 {
			if err := stream.Send(&nikopb.DownloadUpdateEvent{
				Tag:       dl.Tag,
				AssetName: dl.AssetName,
				Sha256:    dl.SHA256,
				Size:      dl.Size,
				Source:    dl.Source,
				Verified:  dl.Verified,
				Chunk:     buf[:n],
			}); err != nil {
				return err
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return status.Error(codes.Internal, "read verified update: "+readErr.Error())
		}
	}
	return stream.Send(&nikopb.DownloadUpdateEvent{
		Tag:       dl.Tag,
		AssetName: dl.AssetName,
		Sha256:    dl.SHA256,
		Size:      dl.Size,
		Source:    dl.Source,
		Verified:  dl.Verified,
		Done:      true,
	})
}
