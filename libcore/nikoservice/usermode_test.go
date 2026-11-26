package nikoservice

import (
	"context"
	"strings"
	"testing"

	"libcore/nikopb"

	"github.com/sagernet/sing-box/experimental/boxdd"

	"google.golang.org/grpc/codes"
)

func TestUserModeCapabilitiesAndConnect(t *testing.T) {
	var started []string
	s := storeServer(t.TempDir())
	s.userMode = true
	s.start = func(_ context.Context, config string, _ *boxdd.SystemProxy) error {
		started = append(started, config)
		return nil
	}
	client := serve(t, s)
	ctx := context.Background()

	info, err := client.GetInfo(ctx, &nikopb.GetInfoRequest{})
	if err != nil {
		t.Fatal(err)
	}
	if info.Api != API || info.Privileged || info.Tun {
		t.Fatalf("unexpected user-mode info: %+v", info)
	}

	if ids := runImport(t, client, socksLink, 0).Ids; len(ids) != 1 {
		t.Fatalf("imported %v", ids)
	}
	if _, err := client.Select(ctx, &nikopb.ProfileId{Id: 1}); err != nil {
		t.Fatal(err)
	}

	// Proxy mode succeeds in user-mode and strips auto_detect_interface.
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{Mode: nikopb.ConnectRequest_PROXY.Enum()}); err != nil {
		t.Fatalf("Connect(PROXY) in user-mode: %v", err)
	}
	cfg := parseConfig(t, started[len(started)-1])
	if _, hasAutoDetect := cfg.Route["auto_detect_interface"]; hasAutoDetect {
		t.Fatalf("user-mode config must not set auto_detect_interface: %v", cfg.Route)
	}
	coreReads(t, started[len(started)-1])

	// Explicit VPN mode refused with FAILED_PRECONDITION + SERVICE_REQUIRED.
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{Mode: nikopb.ConnectRequest_VPN.Enum()}); code(err) != codes.FailedPrecondition || !strings.Contains(err.Error(), "SERVICE_REQUIRED") {
		t.Fatalf("expected SERVICE_REQUIRED for explicit VPN mode, got %v", err)
	}

	// Settings-driven VPN mode also refused with SERVICE_REQUIRED.
	if _, err := client.UpdateSettings(ctx, &nikopb.SettingsUpdate{
		Settings: &nikopb.Settings{ServiceMode: "vpn"},
		Fields:   []string{"service_mode"},
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{}); code(err) != codes.FailedPrecondition || !strings.Contains(err.Error(), "SERVICE_REQUIRED") {
		t.Fatalf("expected SERVICE_REQUIRED for settings VPN mode, got %v", err)
	}
}

func TestUserStoreMigrationRPCs(t *testing.T) {
	ctx := context.Background()

	userSrv := storeServer(t.TempDir())
	userSrv.userMode = true
	userClient := serve(t, userSrv)

	svcSrv := storeServer(t.TempDir())
	svcSrv.userMode = false
	svcClient := serve(t, svcSrv)

	svcInfo, err := svcClient.GetInfo(ctx, &nikopb.GetInfoRequest{})
	if err != nil {
		t.Fatal(err)
	}
	if !svcInfo.Privileged || !svcInfo.Tun || svcInfo.Api != API {
		t.Fatalf("unexpected service info: %+v", svcInfo)
	}

	// Seed user-mode store.
	ids := runImport(t, userClient, socksLink+"\n"+naiveLink, 0).Ids
	if len(ids) != 2 {
		t.Fatalf("imported %v", ids)
	}
	if _, err := userClient.Select(ctx, &nikopb.ProfileId{Id: ids[1]}); err != nil {
		t.Fatal(err)
	}
	if _, err := userClient.SaveRule(ctx, &nikopb.Rule{
		Name:     "Direct local",
		Enabled:  true,
		Domains:  "local.test",
		Outbound: -1,
	}); err != nil {
		t.Fatal(err)
	}

	// ExportUserStore refused on service, ImportUserStore refused on user-mode.
	if _, err := svcClient.ExportUserStore(ctx, &nikopb.Empty{}); code(err) != codes.FailedPrecondition {
		t.Fatalf("expected FailedPrecondition for ExportUserStore on service, got %v", err)
	}
	if _, err := userClient.ImportUserStore(ctx, &nikopb.UserStoreSnapshot{Json: []byte(`{}`)}); code(err) != codes.FailedPrecondition {
		t.Fatalf("expected FailedPrecondition for ImportUserStore on user-mode, got %v", err)
	}

	// Export from user-mode and import into service.
	snap, err := userClient.ExportUserStore(ctx, &nikopb.Empty{})
	if err != nil {
		t.Fatalf("ExportUserStore: %v", err)
	}
	if len(snap.Json) == 0 {
		t.Fatalf("empty snapshot")
	}

	res, err := svcClient.ImportUserStore(ctx, snap)
	if err != nil {
		t.Fatalf("ImportUserStore: %v", err)
	}
	if res.Profiles != 2 || res.Rules != 1 {
		t.Fatalf("unexpected import result: %+v", res)
	}

	profs, err := svcClient.ListProfiles(ctx, &nikopb.ListProfilesRequest{})
	if err != nil || len(profs.Profiles) != 2 {
		t.Fatalf("service profiles: %+v %v", profs, err)
	}
	sel, err := svcClient.GetSelected(ctx, &nikopb.Empty{})
	if err != nil || sel.Id == 0 {
		t.Fatalf("service selected: %+v %v", sel, err)
	}
	rules, err := svcClient.ListRules(ctx, &nikopb.Empty{})
	if err != nil || len(rules.Rules) != 1 || rules.Rules[0].Name != "Direct local" {
		t.Fatalf("service rules: %+v %v", rules, err)
	}
}
