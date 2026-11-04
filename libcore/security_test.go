package libcore

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// TestConfigSurface: a config that would start a process is refused before the core starts.
func TestConfigSurface(t *testing.T) {
	for _, c := range []struct{ name, config, want string }{
		{"tor", `{"outbounds":[{"type":"tor","tag":"t","executable_path":"/system/bin/sh"}]}`, "tor"},
		{"netns", `{"network_namespaces":[{"type":"unshare","tag":"n"}],"outbounds":[{"type":"direct","tag":"d"}]}`, "network_namespaces"},
		{"system proxy", `{"inbounds":[{"type":"mixed","tag":"m","listen":"127.0.0.1","set_system_proxy":true}]}`, "set_system_proxy"},
	} {
		t.Run(c.name, func(t *testing.T) {
			b, err := NewSingBoxInstance(c.config, nil)
			if err == nil {
				b.Close()
				t.Fatal("accepted")
			}
			if !strings.Contains(err.Error(), c.want) {
				t.Fatalf("error %q does not name %q", err, c.want)
			}
		})
	}
	b, err := NewSingBoxInstance(`{"inbounds":[{"type":"mixed","tag":"m","listen":"127.0.0.1"}],"outbounds":[{"type":"direct","tag":"d"}]}`, nil)
	if err != nil {
		t.Fatalf("plain config refused: %v", err)
	}
	b.Close()
}

func TestSecurityPolicyRefuses(t *testing.T) {
	if err := (boxSecurityPolicy{}).CheckFeature(nil, "Tor outbound"); err == nil {
		t.Fatal("feature allowed")
	}
}

// TestFileWritesConfined: the core writes only into its working directory and the Clash UI folder, never next
// to the route assets or elsewhere in the app's data (log.output, cache_file, a Clash UI zip with ../ entries).
func TestFileWritesConfined(t *testing.T) {
	work, outside := t.TempDir(), t.TempDir()
	t.Chdir(work)
	saved := internalAssetsPath
	internalAssetsPath = t.TempDir() + "/"
	defer func() { internalAssetsPath = saved }()
	m := newBoxFileManager()

	refused := map[string]string{
		"outside":      filepath.Join(outside, "ca.pem"),
		"route assets": filepath.Join(internalAssetsPath, "route", "ca.pem"),
		"up and out":   "../" + filepath.Base(outside) + "/x.log",
		"ui zip slip":  filepath.Join(internalAssetsPath, "yacd", "../route/ca.pem"),
		"assets root":  filepath.Join(internalAssetsPath, "x"),
	}
	if err := os.Symlink(outside, filepath.Join(work, "link")); err != nil {
		t.Fatal(err)
	}
	refused["symlink out"] = "link/x.log"
	for name, path := range refused {
		if f, err := m.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644); err == nil {
			f.Close()
			t.Errorf("%s: opened for writing", name)
		}
		if err := m.MkdirAll(filepath.Join(path, "d"), 0o755); err == nil {
			t.Errorf("%s: directory made", name)
		}
	}
	if err := m.Rename("cache.db", filepath.Join(outside, "cache.db")); err == nil {
		t.Error("renamed out of the working directory")
	}
	for _, path := range []string{"core.log", filepath.Join(work, "cache.db"), filepath.Join(internalAssetsPath, "yacd", "index.html")} {
		if err := m.MkdirAll(filepath.Dir(path), 0o755); err != nil {
			t.Fatalf("%s: %v", path, err)
		}
		f, err := m.OpenFile(path, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
		if err != nil {
			t.Fatalf("%s: %v", path, err)
		}
		f.Close()
	}
	// reads are not limited
	os.WriteFile(filepath.Join(outside, "r"), []byte("x"), 0o644)
	if f, err := m.OpenFile(filepath.Join(outside, "r"), os.O_RDONLY, 0); err != nil {
		t.Fatalf("read: %v", err)
	} else {
		f.Close()
	}
}
