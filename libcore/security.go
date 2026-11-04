package libcore

import (
	"context"
	"os"
	"path/filepath"
	"strings"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service/filemanager"
)

// boxSecurityPolicy refuses every feature the core guards with adapter.CheckSecurityFeature: each of
// them starts a process or changes the system (Tor executable_path, OpenConnect wrapper_path, Tailscale
// ssh_server, NTP write_to_system). A config reaches the core from subscriptions, shared links and
// custom JSON, so nothing in it may run a binary.
type boxSecurityPolicy struct{}

func (boxSecurityPolicy) CheckFeature(_ context.Context, feature string) error {
	return E.New(feature, " is not available in Niko: a config can come from an untrusted link")
}

// checkConfigSurface refuses the options that run a program outside the policy above: network
// namespaces start a holder process, and set_system_proxy runs `settings` (or a `rish` found next to
// the route assets).
func checkConfigSurface(options *option.Options) error {
	if len(options.NetworkNamespaces) > 0 {
		return E.New("network_namespaces is not available in Niko")
	}
	for _, in := range options.Inbounds {
		if o, ok := in.Options.(*option.HTTPMixedInboundOptions); ok && o.SetSystemProxy {
			return E.New("inbound[", in.Tag, "]: set_system_proxy is not available in Niko")
		}
	}
	return nil
}

var (
	_ adapter.SecurityPolicy = boxSecurityPolicy{}
	_ filemanager.Manager    = (*boxFileManager)(nil)
)

// boxFileManager confines what the core writes (log.output, the cache file, the Clash UI download and its zip
// entries, ACME data) to its working directory and the Clash UI folder. A config could otherwise write next to the
// route assets (a ca.pem trusted from the next start) or over the app's own files. Reads are not limited.
type boxFileManager struct {
	workDir  string
	writable []string // resolved roots
}

func newBoxFileManager() *boxFileManager {
	workDir, _ := os.Getwd()
	m := &boxFileManager{workDir: workDir}
	roots := []string{workDir}
	if internalAssetsPath != "" {
		roots = append(roots, filepath.Join(internalAssetsPath, "yacd"))
	}
	for _, root := range roots {
		if root == "" {
			continue
		}
		if resolved, err := resolvePath(root); err == nil {
			m.writable = append(m.writable, resolved)
		}
	}
	return m
}

// resolvePath is path absolute with the symlinks of its longest existing prefix resolved (/data/user/0 is a link).
func resolvePath(path string) (string, error) {
	path, err := filepath.Abs(path)
	if err != nil {
		return "", err
	}
	existing := path
	for {
		if _, err = os.Lstat(existing); err == nil {
			break
		} else if !os.IsNotExist(err) {
			return "", err
		}
		parent := filepath.Dir(existing)
		if parent == existing {
			return path, nil
		}
		existing = parent
	}
	resolved, err := filepath.EvalSymlinks(existing)
	if err != nil {
		return "", err
	}
	rest, err := filepath.Rel(existing, path)
	if err != nil {
		return "", err
	}
	return filepath.Join(resolved, rest), nil
}

func (m *boxFileManager) checkWrite(name string) (string, error) {
	resolved, err := resolvePath(name)
	if err != nil {
		return "", err
	}
	for _, root := range m.writable {
		if resolved == root || strings.HasPrefix(resolved, root+string(filepath.Separator)) {
			return name, nil
		}
	}
	return "", E.New("writing ", name, " is not available in Niko: only the core's own folders are")
}

func (m *boxFileManager) BasePath(name string) string { return name }
func (m *boxFileManager) TempPath() string            { return m.workDir }
func (m *boxFileManager) Chown(string) error          { return nil }

func (m *boxFileManager) OpenFile(name string, flag int, perm os.FileMode) (*os.File, error) {
	if flag&(os.O_WRONLY|os.O_RDWR|os.O_CREATE|os.O_APPEND|os.O_TRUNC) != 0 {
		if _, err := m.checkWrite(name); err != nil {
			return nil, err
		}
	}
	return os.OpenFile(name, flag, perm)
}

func (m *boxFileManager) Create(name string) (*os.File, error) {
	if _, err := m.checkWrite(name); err != nil {
		return nil, err
	}
	return os.Create(name)
}

func (m *boxFileManager) CreateTemp(pattern string) (*os.File, error) {
	return os.CreateTemp(m.workDir, pattern)
}

func (m *boxFileManager) Mkdir(path string, perm os.FileMode) error {
	if _, err := m.checkWrite(path); err != nil {
		return err
	}
	return os.Mkdir(path, perm)
}

func (m *boxFileManager) MkdirAll(path string, perm os.FileMode) error {
	if _, err := m.checkWrite(path); err != nil {
		return err
	}
	return os.MkdirAll(path, perm)
}

func (m *boxFileManager) Remove(path string) error {
	if _, err := m.checkWrite(path); err != nil {
		return err
	}
	return os.Remove(path)
}

func (m *boxFileManager) RemoveAll(path string) error {
	if _, err := m.checkWrite(path); err != nil {
		return err
	}
	return os.RemoveAll(path)
}

func (m *boxFileManager) Rename(oldPath string, newPath string) error {
	if _, err := m.checkWrite(oldPath); err != nil {
		return err
	}
	if _, err := m.checkWrite(newPath); err != nil {
		return err
	}
	return os.Rename(oldPath, newPath)
}
