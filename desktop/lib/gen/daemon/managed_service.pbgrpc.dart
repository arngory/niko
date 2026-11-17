// This is a generated file - do not edit.
//
// Generated from daemon/managed_service.proto.

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
import 'package:protobuf/well_known_types/google/protobuf/empty.pb.dart' as $0;

import 'managed_service.pb.dart' as $1;

export 'managed_service.pb.dart';

@$pb.GrpcServiceName('daemon.ManagedService')
class ManagedServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  ManagedServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.Empty> stopService(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$stopService, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> reloadService(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$reloadService, request, options: options);
  }

  $grpc.ResponseFuture<$1.SystemProxyStatus> getSystemProxyStatus(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getSystemProxyStatus, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> setSystemProxyEnabled(
    $1.SetSystemProxyEnabledRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$setSystemProxyEnabled, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> triggerDebugCrash(
    $1.DebugCrashRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$triggerDebugCrash, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> triggerOOMReport(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$triggerOOMReport, request, options: options);
  }

  // method descriptors

  static final _$stopService = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/daemon.ManagedService/StopService',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$reloadService = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/daemon.ManagedService/ReloadService',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$getSystemProxyStatus =
      $grpc.ClientMethod<$0.Empty, $1.SystemProxyStatus>(
          '/daemon.ManagedService/GetSystemProxyStatus',
          ($0.Empty value) => value.writeToBuffer(),
          $1.SystemProxyStatus.fromBuffer);
  static final _$setSystemProxyEnabled =
      $grpc.ClientMethod<$1.SetSystemProxyEnabledRequest, $0.Empty>(
          '/daemon.ManagedService/SetSystemProxyEnabled',
          ($1.SetSystemProxyEnabledRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$triggerDebugCrash =
      $grpc.ClientMethod<$1.DebugCrashRequest, $0.Empty>(
          '/daemon.ManagedService/TriggerDebugCrash',
          ($1.DebugCrashRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$triggerOOMReport = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/daemon.ManagedService/TriggerOOMReport',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
}

@$pb.GrpcServiceName('daemon.ManagedService')
abstract class ManagedServiceBase extends $grpc.Service {
  $core.String get $name => 'daemon.ManagedService';

  ManagedServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'StopService',
        stopService_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'ReloadService',
        reloadService_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.SystemProxyStatus>(
        'GetSystemProxyStatus',
        getSystemProxyStatus_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.SystemProxyStatus value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.SetSystemProxyEnabledRequest, $0.Empty>(
        'SetSystemProxyEnabled',
        setSystemProxyEnabled_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.SetSystemProxyEnabledRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.DebugCrashRequest, $0.Empty>(
        'TriggerDebugCrash',
        triggerDebugCrash_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.DebugCrashRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'TriggerOOMReport',
        triggerOOMReport_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
  }

  $async.Future<$0.Empty> stopService_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return stopService($call, await $request);
  }

  $async.Future<$0.Empty> stopService($grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> reloadService_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return reloadService($call, await $request);
  }

  $async.Future<$0.Empty> reloadService(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.SystemProxyStatus> getSystemProxyStatus_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getSystemProxyStatus($call, await $request);
  }

  $async.Future<$1.SystemProxyStatus> getSystemProxyStatus(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> setSystemProxyEnabled_Pre($grpc.ServiceCall $call,
      $async.Future<$1.SetSystemProxyEnabledRequest> $request) async {
    return setSystemProxyEnabled($call, await $request);
  }

  $async.Future<$0.Empty> setSystemProxyEnabled(
      $grpc.ServiceCall call, $1.SetSystemProxyEnabledRequest request);

  $async.Future<$0.Empty> triggerDebugCrash_Pre($grpc.ServiceCall $call,
      $async.Future<$1.DebugCrashRequest> $request) async {
    return triggerDebugCrash($call, await $request);
  }

  $async.Future<$0.Empty> triggerDebugCrash(
      $grpc.ServiceCall call, $1.DebugCrashRequest request);

  $async.Future<$0.Empty> triggerOOMReport_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return triggerOOMReport($call, await $request);
  }

  $async.Future<$0.Empty> triggerOOMReport(
      $grpc.ServiceCall call, $0.Empty request);
}
