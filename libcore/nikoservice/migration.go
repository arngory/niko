package nikoservice

import (
	"context"

	"libcore/nikopb"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func (s *server) ExportUserStore(ctx context.Context, _ *nikopb.Empty) (*nikopb.UserStoreSnapshot, error) {
	if !s.userMode {
		return nil, status.Error(codes.FailedPrecondition, "ExportUserStore is only available in user mode")
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	raw, err := u.store.ExportSnapshot()
	if err != nil {
		return nil, storeError(err)
	}
	return &nikopb.UserStoreSnapshot{Json: raw}, nil
}

func (s *server) ImportUserStore(ctx context.Context, req *nikopb.UserStoreSnapshot) (*nikopb.ImportUserStoreResult, error) {
	if s.userMode {
		return nil, status.Error(codes.FailedPrecondition, "ImportUserStore is only available on the system service")
	}
	if req == nil || len(req.Json) == 0 {
		return nil, status.Error(codes.InvalidArgument, "empty store snapshot")
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	defer func() {
		s.syncLogRedactions()
		s.retainDeviceKeys()
	}()
	stats, err := u.store.ImportSnapshot(req.Json)
	if err != nil {
		return nil, storeError(err)
	}
	return &nikopb.ImportUserStoreResult{
		Groups:   int32(stats.Groups),
		Profiles: int32(stats.Profiles),
		Rules:    int32(stats.Rules),
		Nodes:    int32(stats.Nodes),
	}, nil
}
