// This is a generated file - do not edit.
//
// Generated from niko/v1/niko_service.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:async' as $async;
import 'dart:core' as $core;

import 'package:grpc/service_api.dart' as $grpc;
import 'package:protobuf/protobuf.dart' as $pb;

import 'niko_service.pb.dart' as $0;

export 'niko_service.pb.dart';

@$pb.GrpcServiceName('niko.v1.NikoService')
class NikoServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  NikoServiceClient(super.channel, {super.options, super.interceptors});

  /// What this daemon is. Any caller the daemon verified.
  $grpc.ResponseFuture<$0.Info> getInfo(
    $0.GetInfoRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getInfo, request, options: options);
  }

  /// Which rule of the running core a connection would match and where it would go; no connection is made. The
  /// daemon's owner only: it reads the owner's core. FAILED_PRECONDITION when no core runs.
  $grpc.ResponseFuture<$0.RouteTestResult> routeTest(
    $0.RouteTestRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$routeTest, request, options: options);
  }

  // method descriptors

  static final _$getInfo = $grpc.ClientMethod<$0.GetInfoRequest, $0.Info>(
      '/niko.v1.NikoService/GetInfo',
      ($0.GetInfoRequest value) => value.writeToBuffer(),
      $0.Info.fromBuffer);
  static final _$routeTest =
      $grpc.ClientMethod<$0.RouteTestRequest, $0.RouteTestResult>(
          '/niko.v1.NikoService/RouteTest',
          ($0.RouteTestRequest value) => value.writeToBuffer(),
          $0.RouteTestResult.fromBuffer);
}

@$pb.GrpcServiceName('niko.v1.NikoService')
abstract class NikoServiceBase extends $grpc.Service {
  $core.String get $name => 'niko.v1.NikoService';

  NikoServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.GetInfoRequest, $0.Info>(
        'GetInfo',
        getInfo_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.GetInfoRequest.fromBuffer(value),
        ($0.Info value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.RouteTestRequest, $0.RouteTestResult>(
        'RouteTest',
        routeTest_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.RouteTestRequest.fromBuffer(value),
        ($0.RouteTestResult value) => value.writeToBuffer()));
  }

  $async.Future<$0.Info> getInfo_Pre($grpc.ServiceCall $call,
      $async.Future<$0.GetInfoRequest> $request) async {
    return getInfo($call, await $request);
  }

  $async.Future<$0.Info> getInfo(
      $grpc.ServiceCall call, $0.GetInfoRequest request);

  $async.Future<$0.RouteTestResult> routeTest_Pre($grpc.ServiceCall $call,
      $async.Future<$0.RouteTestRequest> $request) async {
    return routeTest($call, await $request);
  }

  $async.Future<$0.RouteTestResult> routeTest(
      $grpc.ServiceCall call, $0.RouteTestRequest request);
}
