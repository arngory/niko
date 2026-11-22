package nikoservice

import (
	"context"

	"libcore/nikoconf"
	"libcore/nikopb"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/reflect/protoreflect"
)

// The computer's settings (slice 13's part for a computer, done before the backups): nikostore keeps them in
// settings.json by the app's names, which are the JSON names of NikoService's Settings; Connect builds with them.

func (s *server) GetSettings(ctx context.Context, _ *nikopb.Empty) (*nikopb.Settings, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	settings, err := u.store.Settings()
	if err != nil {
		return nil, storeError(err)
	}
	return settingsProto(settings), nil
}

func (s *server) UpdateSettings(ctx context.Context, request *nikopb.SettingsUpdate) (*nikopb.Settings, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	names, err := settingNames(request.Fields)
	if err != nil {
		return nil, err
	}
	settings, err := u.store.UpdateSettings(settingsFrom(request.Settings), names)
	if err != nil {
		return nil, storeError(err)
	}
	return settingsProto(settings), nil
}

// settingNames are the app's names of Settings' fields named as the .proto names them: their JSON names.
func settingNames(fields []string) ([]string, error) {
	descriptor := (&nikopb.Settings{}).ProtoReflect().Descriptor().Fields()
	names := make([]string, 0, len(fields))
	for _, field := range fields {
		fd := descriptor.ByName(protoreflect.Name(field))
		if fd == nil {
			return nil, status.Errorf(codes.InvalidArgument, "no setting %q", field)
		}
		names = append(names, fd.JSONName())
	}
	return names, nil
}

// settingsProto is a computer's settings as NikoService gives them.
func settingsProto(s nikoconf.Settings) *nikopb.Settings {
	return &nikopb.Settings{
		ServiceMode:             s.ServiceMode,
		SystemProxy:             s.SystemProxy != nil && *s.SystemProxy,
		TunImplementation:       int32(s.TunImplementation),
		Mtu:                     int32(s.MTU),
		Ipv6Mode:                int32(s.IPv6Mode),
		GlobalAllowInsecure:     s.GlobalAllowInsecure,
		RemoteDns:               s.RemoteDNS,
		DirectDns:               s.DirectDNS,
		EnableFakeDns:           s.EnableFakeDNS,
		DomainStrategyForRemote: deref(s.DomainStrategyForRemote),
		DomainStrategyForDirect: deref(s.DomainStrategyForDirect),
		DomainStrategyForServer: deref(s.DomainStrategyForServer),
		DnsOptimisticCache:      s.DNSOptimisticCache,
		BypassLanInCore:         s.BypassLanInCore,
		MixedPort:               int32(s.MixedPort),
		MixedUsername:           s.MixedUsername,
		MixedPassword:           s.Secrets.MixedPassword,
		AllowAccess:             s.AllowAccess,
		TrafficSniffing:         int32(s.TrafficSniffing),
		ResolveDestination:      s.ResolveDestination,
		RefuseQuicOverStreams:   s.RefuseQuicOverStreams,
		IcmpNoLocalReply:        s.IcmpNoLocalReply,
		LogLevel:                int32(s.LogLevel),
		ProgramsMode:            int32(s.ProgramsMode),
		Programs:                s.Programs,
	}
}

// settingsFrom are a request's values as the store takes them: an empty domain strategy is none.
func settingsFrom(p *nikopb.Settings) nikoconf.Settings {
	if p == nil {
		p = &nikopb.Settings{}
	}
	systemProxy := p.SystemProxy
	return nikoconf.Settings{
		ServiceMode:             p.ServiceMode,
		SystemProxy:             &systemProxy,
		TunImplementation:       int(p.TunImplementation),
		MTU:                     int(p.Mtu),
		IPv6Mode:                int(p.Ipv6Mode),
		GlobalAllowInsecure:     p.GlobalAllowInsecure,
		RemoteDNS:               p.RemoteDns,
		DirectDNS:               p.DirectDns,
		EnableFakeDNS:           p.EnableFakeDns,
		DomainStrategyForRemote: orNil(p.DomainStrategyForRemote),
		DomainStrategyForDirect: orNil(p.DomainStrategyForDirect),
		DomainStrategyForServer: orNil(p.DomainStrategyForServer),
		DNSOptimisticCache:      p.DnsOptimisticCache,
		BypassLanInCore:         p.BypassLanInCore,
		MixedPort:               int(p.MixedPort),
		MixedUsername:           p.MixedUsername,
		Secrets:                 nikoconf.Secrets{MixedPassword: p.MixedPassword},
		AllowAccess:             p.AllowAccess,
		TrafficSniffing:         int(p.TrafficSniffing),
		ResolveDestination:      p.ResolveDestination,
		RefuseQuicOverStreams:   p.RefuseQuicOverStreams,
		IcmpNoLocalReply:        p.IcmpNoLocalReply,
		LogLevel:                int(p.LogLevel),
		ProgramsMode:            int(p.ProgramsMode),
		Programs:                p.Programs,
	}
}

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func orNil(s string) *string {
	if s == "" {
		return nil
	}
	return &s
}
