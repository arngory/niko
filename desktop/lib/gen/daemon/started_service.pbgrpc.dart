// This is a generated file - do not edit.
//
// Generated from daemon/started_service.proto.

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

import 'started_service.pb.dart' as $1;

export 'started_service.pb.dart';

@$pb.GrpcServiceName('daemon.StartedService')
class StartedServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  StartedServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$1.Version> getVersion(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getVersion, request, options: options);
  }

  $grpc.ResponseStream<$1.ServiceStatus> subscribeServiceStatus(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeServiceStatus, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseStream<$1.Log> subscribeLog(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeLog, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$1.DefaultLogLevel> getDefaultLogLevel(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getDefaultLogLevel, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> clearLogs(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$clearLogs, request, options: options);
  }

  $grpc.ResponseStream<$1.Status> subscribeStatus(
    $1.SubscribeStatusRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeStatus, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseStream<$1.Groups> subscribeGroups(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeGroups, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$1.ClashModeStatus> getClashModeStatus(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getClashModeStatus, request, options: options);
  }

  $grpc.ResponseStream<$1.ClashMode> subscribeClashMode(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeClashMode, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> setClashMode(
    $1.ClashMode request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$setClashMode, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> uRLTest(
    $1.URLTestRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$uRLTest, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> selectOutbound(
    $1.SelectOutboundRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$selectOutbound, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> setGroupExpand(
    $1.SetGroupExpandRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$setGroupExpand, request, options: options);
  }

  $grpc.ResponseStream<$1.ConnectionEvents> subscribeConnections(
    $1.SubscribeConnectionsRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeConnections, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> closeConnection(
    $1.CloseConnectionRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$closeConnection, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> closeAllConnections(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$closeAllConnections, request, options: options);
  }

  $grpc.ResponseFuture<$1.DeprecatedWarnings> getDeprecatedWarnings(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getDeprecatedWarnings, request, options: options);
  }

  $grpc.ResponseFuture<$1.StartedAt> getStartedAt(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getStartedAt, request, options: options);
  }

  $grpc.ResponseStream<$1.OutboundList> subscribeOutbounds(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeOutbounds, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseStream<$1.NetworkQualityTestProgress> startNetworkQualityTest(
    $1.NetworkQualityTestRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$startNetworkQualityTest, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseStream<$1.STUNTestProgress> startSTUNTest(
    $1.STUNTestRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$startSTUNTest, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseStream<$1.TailscaleStatusUpdate> subscribeTailscaleStatus(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeTailscaleStatus, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseStream<$1.TailscalePingResponse> startTailscalePing(
    $1.TailscalePingRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$startTailscalePing, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> setTailscaleExitNode(
    $1.SetTailscaleExitNodeRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$setTailscaleExitNode, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> tailscaleLogout(
    $1.TailscaleLogoutRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$tailscaleLogout, request, options: options);
  }

  $grpc.ResponseFuture<$1.TailscaleCertificate> getTailscaleCertificate(
    $1.TailscaleCertificateRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getTailscaleCertificate, request,
        options: options);
  }

  $grpc.ResponseStream<$1.TailscaleSSHServerMessage> startTailscaleSSHSession(
    $async.Stream<$1.TailscaleSSHClientMessage> request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(_$startTailscaleSSHSession, request,
        options: options);
  }

  $grpc.ResponseStream<$1.TaildropInbox> subscribeTaildropInbox(
    $1.SubscribeTaildropInboxRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeTaildropInbox, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> markTaildropInboxRead(
    $1.MarkTaildropInboxReadRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$markTaildropInboxRead, request, options: options);
  }

  $grpc.ResponseStream<$1.TaildropSendServerMessage> sendTaildropFiles(
    $async.Stream<$1.TaildropSendClientMessage> request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(_$sendTaildropFiles, request, options: options);
  }

  $grpc.ResponseStream<$1.DownloadTaildropFileChunk> downloadTaildropFile(
    $1.DownloadTaildropFileRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$downloadTaildropFile, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> deleteTaildropFile(
    $1.DeleteTaildropFileRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteTaildropFile, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> cancelTaildropReceiving(
    $1.CancelTaildropReceivingRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$cancelTaildropReceiving, request,
        options: options);
  }

  $grpc.ResponseStream<$1.USBServerMessage> provideUSBDevices(
    $async.Stream<$1.USBProviderMessage> request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(_$provideUSBDevices, request, options: options);
  }

  $grpc.ResponseStream<$1.USBIPServerStatusUpdate> subscribeUSBIPServerStatus(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeUSBIPServerStatus, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseStream<$1.OpenConnectStatusUpdate> subscribeOpenConnectStatus(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeOpenConnectStatus, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> submitOpenConnectAuthResponse(
    $1.OpenConnectAuthResponseSubmission request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$submitOpenConnectAuthResponse, request,
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> cancelOpenConnectAuthChallenge(
    $1.OpenConnectAuthChallengeCancel request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$cancelOpenConnectAuthChallenge, request,
        options: options);
  }

  $grpc.ResponseStream<$1.OpenVPNStatusUpdate> subscribeOpenVPNStatus(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeOpenVPNStatus, $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> submitOpenVPNChallengeResponse(
    $1.OpenVPNChallengeSubmission request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$submitOpenVPNChallengeResponse, request,
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> cancelOpenVPNChallenge(
    $1.OpenVPNChallengeCancel request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$cancelOpenVPNChallenge, request,
        options: options);
  }

  $grpc.ResponseStream<$1.NotificationEvent> subscribeNotifications(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$subscribeNotifications, $async.Stream.fromIterable([request]),
        options: options);
  }

  // method descriptors

  static final _$getVersion = $grpc.ClientMethod<$0.Empty, $1.Version>(
      '/daemon.StartedService/GetVersion',
      ($0.Empty value) => value.writeToBuffer(),
      $1.Version.fromBuffer);
  static final _$subscribeServiceStatus =
      $grpc.ClientMethod<$0.Empty, $1.ServiceStatus>(
          '/daemon.StartedService/SubscribeServiceStatus',
          ($0.Empty value) => value.writeToBuffer(),
          $1.ServiceStatus.fromBuffer);
  static final _$subscribeLog = $grpc.ClientMethod<$0.Empty, $1.Log>(
      '/daemon.StartedService/SubscribeLog',
      ($0.Empty value) => value.writeToBuffer(),
      $1.Log.fromBuffer);
  static final _$getDefaultLogLevel =
      $grpc.ClientMethod<$0.Empty, $1.DefaultLogLevel>(
          '/daemon.StartedService/GetDefaultLogLevel',
          ($0.Empty value) => value.writeToBuffer(),
          $1.DefaultLogLevel.fromBuffer);
  static final _$clearLogs = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/daemon.StartedService/ClearLogs',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$subscribeStatus =
      $grpc.ClientMethod<$1.SubscribeStatusRequest, $1.Status>(
          '/daemon.StartedService/SubscribeStatus',
          ($1.SubscribeStatusRequest value) => value.writeToBuffer(),
          $1.Status.fromBuffer);
  static final _$subscribeGroups = $grpc.ClientMethod<$0.Empty, $1.Groups>(
      '/daemon.StartedService/SubscribeGroups',
      ($0.Empty value) => value.writeToBuffer(),
      $1.Groups.fromBuffer);
  static final _$getClashModeStatus =
      $grpc.ClientMethod<$0.Empty, $1.ClashModeStatus>(
          '/daemon.StartedService/GetClashModeStatus',
          ($0.Empty value) => value.writeToBuffer(),
          $1.ClashModeStatus.fromBuffer);
  static final _$subscribeClashMode =
      $grpc.ClientMethod<$0.Empty, $1.ClashMode>(
          '/daemon.StartedService/SubscribeClashMode',
          ($0.Empty value) => value.writeToBuffer(),
          $1.ClashMode.fromBuffer);
  static final _$setClashMode = $grpc.ClientMethod<$1.ClashMode, $0.Empty>(
      '/daemon.StartedService/SetClashMode',
      ($1.ClashMode value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$uRLTest = $grpc.ClientMethod<$1.URLTestRequest, $0.Empty>(
      '/daemon.StartedService/URLTest',
      ($1.URLTestRequest value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$selectOutbound =
      $grpc.ClientMethod<$1.SelectOutboundRequest, $0.Empty>(
          '/daemon.StartedService/SelectOutbound',
          ($1.SelectOutboundRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$setGroupExpand =
      $grpc.ClientMethod<$1.SetGroupExpandRequest, $0.Empty>(
          '/daemon.StartedService/SetGroupExpand',
          ($1.SetGroupExpandRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$subscribeConnections =
      $grpc.ClientMethod<$1.SubscribeConnectionsRequest, $1.ConnectionEvents>(
          '/daemon.StartedService/SubscribeConnections',
          ($1.SubscribeConnectionsRequest value) => value.writeToBuffer(),
          $1.ConnectionEvents.fromBuffer);
  static final _$closeConnection =
      $grpc.ClientMethod<$1.CloseConnectionRequest, $0.Empty>(
          '/daemon.StartedService/CloseConnection',
          ($1.CloseConnectionRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$closeAllConnections = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/daemon.StartedService/CloseAllConnections',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$getDeprecatedWarnings =
      $grpc.ClientMethod<$0.Empty, $1.DeprecatedWarnings>(
          '/daemon.StartedService/GetDeprecatedWarnings',
          ($0.Empty value) => value.writeToBuffer(),
          $1.DeprecatedWarnings.fromBuffer);
  static final _$getStartedAt = $grpc.ClientMethod<$0.Empty, $1.StartedAt>(
      '/daemon.StartedService/GetStartedAt',
      ($0.Empty value) => value.writeToBuffer(),
      $1.StartedAt.fromBuffer);
  static final _$subscribeOutbounds =
      $grpc.ClientMethod<$0.Empty, $1.OutboundList>(
          '/daemon.StartedService/SubscribeOutbounds',
          ($0.Empty value) => value.writeToBuffer(),
          $1.OutboundList.fromBuffer);
  static final _$startNetworkQualityTest = $grpc.ClientMethod<
          $1.NetworkQualityTestRequest, $1.NetworkQualityTestProgress>(
      '/daemon.StartedService/StartNetworkQualityTest',
      ($1.NetworkQualityTestRequest value) => value.writeToBuffer(),
      $1.NetworkQualityTestProgress.fromBuffer);
  static final _$startSTUNTest =
      $grpc.ClientMethod<$1.STUNTestRequest, $1.STUNTestProgress>(
          '/daemon.StartedService/StartSTUNTest',
          ($1.STUNTestRequest value) => value.writeToBuffer(),
          $1.STUNTestProgress.fromBuffer);
  static final _$subscribeTailscaleStatus =
      $grpc.ClientMethod<$0.Empty, $1.TailscaleStatusUpdate>(
          '/daemon.StartedService/SubscribeTailscaleStatus',
          ($0.Empty value) => value.writeToBuffer(),
          $1.TailscaleStatusUpdate.fromBuffer);
  static final _$startTailscalePing =
      $grpc.ClientMethod<$1.TailscalePingRequest, $1.TailscalePingResponse>(
          '/daemon.StartedService/StartTailscalePing',
          ($1.TailscalePingRequest value) => value.writeToBuffer(),
          $1.TailscalePingResponse.fromBuffer);
  static final _$setTailscaleExitNode =
      $grpc.ClientMethod<$1.SetTailscaleExitNodeRequest, $0.Empty>(
          '/daemon.StartedService/SetTailscaleExitNode',
          ($1.SetTailscaleExitNodeRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$tailscaleLogout =
      $grpc.ClientMethod<$1.TailscaleLogoutRequest, $0.Empty>(
          '/daemon.StartedService/TailscaleLogout',
          ($1.TailscaleLogoutRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$getTailscaleCertificate = $grpc.ClientMethod<
          $1.TailscaleCertificateRequest, $1.TailscaleCertificate>(
      '/daemon.StartedService/GetTailscaleCertificate',
      ($1.TailscaleCertificateRequest value) => value.writeToBuffer(),
      $1.TailscaleCertificate.fromBuffer);
  static final _$startTailscaleSSHSession = $grpc.ClientMethod<
          $1.TailscaleSSHClientMessage, $1.TailscaleSSHServerMessage>(
      '/daemon.StartedService/StartTailscaleSSHSession',
      ($1.TailscaleSSHClientMessage value) => value.writeToBuffer(),
      $1.TailscaleSSHServerMessage.fromBuffer);
  static final _$subscribeTaildropInbox =
      $grpc.ClientMethod<$1.SubscribeTaildropInboxRequest, $1.TaildropInbox>(
          '/daemon.StartedService/SubscribeTaildropInbox',
          ($1.SubscribeTaildropInboxRequest value) => value.writeToBuffer(),
          $1.TaildropInbox.fromBuffer);
  static final _$markTaildropInboxRead =
      $grpc.ClientMethod<$1.MarkTaildropInboxReadRequest, $0.Empty>(
          '/daemon.StartedService/MarkTaildropInboxRead',
          ($1.MarkTaildropInboxReadRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$sendTaildropFiles = $grpc.ClientMethod<
          $1.TaildropSendClientMessage, $1.TaildropSendServerMessage>(
      '/daemon.StartedService/SendTaildropFiles',
      ($1.TaildropSendClientMessage value) => value.writeToBuffer(),
      $1.TaildropSendServerMessage.fromBuffer);
  static final _$downloadTaildropFile = $grpc.ClientMethod<
          $1.DownloadTaildropFileRequest, $1.DownloadTaildropFileChunk>(
      '/daemon.StartedService/DownloadTaildropFile',
      ($1.DownloadTaildropFileRequest value) => value.writeToBuffer(),
      $1.DownloadTaildropFileChunk.fromBuffer);
  static final _$deleteTaildropFile =
      $grpc.ClientMethod<$1.DeleteTaildropFileRequest, $0.Empty>(
          '/daemon.StartedService/DeleteTaildropFile',
          ($1.DeleteTaildropFileRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$cancelTaildropReceiving =
      $grpc.ClientMethod<$1.CancelTaildropReceivingRequest, $0.Empty>(
          '/daemon.StartedService/CancelTaildropReceiving',
          ($1.CancelTaildropReceivingRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$provideUSBDevices =
      $grpc.ClientMethod<$1.USBProviderMessage, $1.USBServerMessage>(
          '/daemon.StartedService/ProvideUSBDevices',
          ($1.USBProviderMessage value) => value.writeToBuffer(),
          $1.USBServerMessage.fromBuffer);
  static final _$subscribeUSBIPServerStatus =
      $grpc.ClientMethod<$0.Empty, $1.USBIPServerStatusUpdate>(
          '/daemon.StartedService/SubscribeUSBIPServerStatus',
          ($0.Empty value) => value.writeToBuffer(),
          $1.USBIPServerStatusUpdate.fromBuffer);
  static final _$subscribeOpenConnectStatus =
      $grpc.ClientMethod<$0.Empty, $1.OpenConnectStatusUpdate>(
          '/daemon.StartedService/SubscribeOpenConnectStatus',
          ($0.Empty value) => value.writeToBuffer(),
          $1.OpenConnectStatusUpdate.fromBuffer);
  static final _$submitOpenConnectAuthResponse =
      $grpc.ClientMethod<$1.OpenConnectAuthResponseSubmission, $0.Empty>(
          '/daemon.StartedService/SubmitOpenConnectAuthResponse',
          ($1.OpenConnectAuthResponseSubmission value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$cancelOpenConnectAuthChallenge =
      $grpc.ClientMethod<$1.OpenConnectAuthChallengeCancel, $0.Empty>(
          '/daemon.StartedService/CancelOpenConnectAuthChallenge',
          ($1.OpenConnectAuthChallengeCancel value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$subscribeOpenVPNStatus =
      $grpc.ClientMethod<$0.Empty, $1.OpenVPNStatusUpdate>(
          '/daemon.StartedService/SubscribeOpenVPNStatus',
          ($0.Empty value) => value.writeToBuffer(),
          $1.OpenVPNStatusUpdate.fromBuffer);
  static final _$submitOpenVPNChallengeResponse =
      $grpc.ClientMethod<$1.OpenVPNChallengeSubmission, $0.Empty>(
          '/daemon.StartedService/SubmitOpenVPNChallengeResponse',
          ($1.OpenVPNChallengeSubmission value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$cancelOpenVPNChallenge =
      $grpc.ClientMethod<$1.OpenVPNChallengeCancel, $0.Empty>(
          '/daemon.StartedService/CancelOpenVPNChallenge',
          ($1.OpenVPNChallengeCancel value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$subscribeNotifications =
      $grpc.ClientMethod<$0.Empty, $1.NotificationEvent>(
          '/daemon.StartedService/SubscribeNotifications',
          ($0.Empty value) => value.writeToBuffer(),
          $1.NotificationEvent.fromBuffer);
}

@$pb.GrpcServiceName('daemon.StartedService')
abstract class StartedServiceBase extends $grpc.Service {
  $core.String get $name => 'daemon.StartedService';

  StartedServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.Version>(
        'GetVersion',
        getVersion_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.Version value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.ServiceStatus>(
        'SubscribeServiceStatus',
        subscribeServiceStatus_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.ServiceStatus value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.Log>(
        'SubscribeLog',
        subscribeLog_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.Log value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.DefaultLogLevel>(
        'GetDefaultLogLevel',
        getDefaultLogLevel_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.DefaultLogLevel value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'ClearLogs',
        clearLogs_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.SubscribeStatusRequest, $1.Status>(
        'SubscribeStatus',
        subscribeStatus_Pre,
        false,
        true,
        ($core.List<$core.int> value) =>
            $1.SubscribeStatusRequest.fromBuffer(value),
        ($1.Status value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.Groups>(
        'SubscribeGroups',
        subscribeGroups_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.Groups value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.ClashModeStatus>(
        'GetClashModeStatus',
        getClashModeStatus_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.ClashModeStatus value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.ClashMode>(
        'SubscribeClashMode',
        subscribeClashMode_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.ClashMode value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.ClashMode, $0.Empty>(
        'SetClashMode',
        setClashMode_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.ClashMode.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.URLTestRequest, $0.Empty>(
        'URLTest',
        uRLTest_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.URLTestRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.SelectOutboundRequest, $0.Empty>(
        'SelectOutbound',
        selectOutbound_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.SelectOutboundRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.SetGroupExpandRequest, $0.Empty>(
        'SetGroupExpand',
        setGroupExpand_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.SetGroupExpandRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.SubscribeConnectionsRequest,
            $1.ConnectionEvents>(
        'SubscribeConnections',
        subscribeConnections_Pre,
        false,
        true,
        ($core.List<$core.int> value) =>
            $1.SubscribeConnectionsRequest.fromBuffer(value),
        ($1.ConnectionEvents value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.CloseConnectionRequest, $0.Empty>(
        'CloseConnection',
        closeConnection_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.CloseConnectionRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'CloseAllConnections',
        closeAllConnections_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.DeprecatedWarnings>(
        'GetDeprecatedWarnings',
        getDeprecatedWarnings_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.DeprecatedWarnings value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.StartedAt>(
        'GetStartedAt',
        getStartedAt_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.StartedAt value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.OutboundList>(
        'SubscribeOutbounds',
        subscribeOutbounds_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.OutboundList value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.NetworkQualityTestRequest,
            $1.NetworkQualityTestProgress>(
        'StartNetworkQualityTest',
        startNetworkQualityTest_Pre,
        false,
        true,
        ($core.List<$core.int> value) =>
            $1.NetworkQualityTestRequest.fromBuffer(value),
        ($1.NetworkQualityTestProgress value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.STUNTestRequest, $1.STUNTestProgress>(
        'StartSTUNTest',
        startSTUNTest_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $1.STUNTestRequest.fromBuffer(value),
        ($1.STUNTestProgress value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.TailscaleStatusUpdate>(
        'SubscribeTailscaleStatus',
        subscribeTailscaleStatus_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.TailscaleStatusUpdate value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.TailscalePingRequest, $1.TailscalePingResponse>(
            'StartTailscalePing',
            startTailscalePing_Pre,
            false,
            true,
            ($core.List<$core.int> value) =>
                $1.TailscalePingRequest.fromBuffer(value),
            ($1.TailscalePingResponse value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.SetTailscaleExitNodeRequest, $0.Empty>(
        'SetTailscaleExitNode',
        setTailscaleExitNode_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.SetTailscaleExitNodeRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.TailscaleLogoutRequest, $0.Empty>(
        'TailscaleLogout',
        tailscaleLogout_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.TailscaleLogoutRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.TailscaleCertificateRequest,
            $1.TailscaleCertificate>(
        'GetTailscaleCertificate',
        getTailscaleCertificate_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.TailscaleCertificateRequest.fromBuffer(value),
        ($1.TailscaleCertificate value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.TailscaleSSHClientMessage,
            $1.TailscaleSSHServerMessage>(
        'StartTailscaleSSHSession',
        startTailscaleSSHSession,
        true,
        true,
        ($core.List<$core.int> value) =>
            $1.TailscaleSSHClientMessage.fromBuffer(value),
        ($1.TailscaleSSHServerMessage value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.SubscribeTaildropInboxRequest, $1.TaildropInbox>(
            'SubscribeTaildropInbox',
            subscribeTaildropInbox_Pre,
            false,
            true,
            ($core.List<$core.int> value) =>
                $1.SubscribeTaildropInboxRequest.fromBuffer(value),
            ($1.TaildropInbox value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.MarkTaildropInboxReadRequest, $0.Empty>(
        'MarkTaildropInboxRead',
        markTaildropInboxRead_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.MarkTaildropInboxReadRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.TaildropSendClientMessage,
            $1.TaildropSendServerMessage>(
        'SendTaildropFiles',
        sendTaildropFiles,
        true,
        true,
        ($core.List<$core.int> value) =>
            $1.TaildropSendClientMessage.fromBuffer(value),
        ($1.TaildropSendServerMessage value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.DownloadTaildropFileRequest,
            $1.DownloadTaildropFileChunk>(
        'DownloadTaildropFile',
        downloadTaildropFile_Pre,
        false,
        true,
        ($core.List<$core.int> value) =>
            $1.DownloadTaildropFileRequest.fromBuffer(value),
        ($1.DownloadTaildropFileChunk value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.DeleteTaildropFileRequest, $0.Empty>(
        'DeleteTaildropFile',
        deleteTaildropFile_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.DeleteTaildropFileRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.CancelTaildropReceivingRequest, $0.Empty>(
        'CancelTaildropReceiving',
        cancelTaildropReceiving_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.CancelTaildropReceivingRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.USBProviderMessage, $1.USBServerMessage>(
        'ProvideUSBDevices',
        provideUSBDevices,
        true,
        true,
        ($core.List<$core.int> value) =>
            $1.USBProviderMessage.fromBuffer(value),
        ($1.USBServerMessage value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.USBIPServerStatusUpdate>(
        'SubscribeUSBIPServerStatus',
        subscribeUSBIPServerStatus_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.USBIPServerStatusUpdate value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.OpenConnectStatusUpdate>(
        'SubscribeOpenConnectStatus',
        subscribeOpenConnectStatus_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.OpenConnectStatusUpdate value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.OpenConnectAuthResponseSubmission, $0.Empty>(
            'SubmitOpenConnectAuthResponse',
            submitOpenConnectAuthResponse_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $1.OpenConnectAuthResponseSubmission.fromBuffer(value),
            ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OpenConnectAuthChallengeCancel, $0.Empty>(
        'CancelOpenConnectAuthChallenge',
        cancelOpenConnectAuthChallenge_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.OpenConnectAuthChallengeCancel.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.OpenVPNStatusUpdate>(
        'SubscribeOpenVPNStatus',
        subscribeOpenVPNStatus_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.OpenVPNStatusUpdate value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OpenVPNChallengeSubmission, $0.Empty>(
        'SubmitOpenVPNChallengeResponse',
        submitOpenVPNChallengeResponse_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.OpenVPNChallengeSubmission.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OpenVPNChallengeCancel, $0.Empty>(
        'CancelOpenVPNChallenge',
        cancelOpenVPNChallenge_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.OpenVPNChallengeCancel.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.NotificationEvent>(
        'SubscribeNotifications',
        subscribeNotifications_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.NotificationEvent value) => value.writeToBuffer()));
  }

  $async.Future<$1.Version> getVersion_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getVersion($call, await $request);
  }

  $async.Future<$1.Version> getVersion(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$1.ServiceStatus> subscribeServiceStatus_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeServiceStatus($call, await $request);
  }

  $async.Stream<$1.ServiceStatus> subscribeServiceStatus(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$1.Log> subscribeLog_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeLog($call, await $request);
  }

  $async.Stream<$1.Log> subscribeLog($grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.DefaultLogLevel> getDefaultLogLevel_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getDefaultLogLevel($call, await $request);
  }

  $async.Future<$1.DefaultLogLevel> getDefaultLogLevel(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> clearLogs_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return clearLogs($call, await $request);
  }

  $async.Future<$0.Empty> clearLogs($grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$1.Status> subscribeStatus_Pre($grpc.ServiceCall $call,
      $async.Future<$1.SubscribeStatusRequest> $request) async* {
    yield* subscribeStatus($call, await $request);
  }

  $async.Stream<$1.Status> subscribeStatus(
      $grpc.ServiceCall call, $1.SubscribeStatusRequest request);

  $async.Stream<$1.Groups> subscribeGroups_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeGroups($call, await $request);
  }

  $async.Stream<$1.Groups> subscribeGroups(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.ClashModeStatus> getClashModeStatus_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getClashModeStatus($call, await $request);
  }

  $async.Future<$1.ClashModeStatus> getClashModeStatus(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$1.ClashMode> subscribeClashMode_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeClashMode($call, await $request);
  }

  $async.Stream<$1.ClashMode> subscribeClashMode(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> setClashMode_Pre(
      $grpc.ServiceCall $call, $async.Future<$1.ClashMode> $request) async {
    return setClashMode($call, await $request);
  }

  $async.Future<$0.Empty> setClashMode(
      $grpc.ServiceCall call, $1.ClashMode request);

  $async.Future<$0.Empty> uRLTest_Pre($grpc.ServiceCall $call,
      $async.Future<$1.URLTestRequest> $request) async {
    return uRLTest($call, await $request);
  }

  $async.Future<$0.Empty> uRLTest(
      $grpc.ServiceCall call, $1.URLTestRequest request);

  $async.Future<$0.Empty> selectOutbound_Pre($grpc.ServiceCall $call,
      $async.Future<$1.SelectOutboundRequest> $request) async {
    return selectOutbound($call, await $request);
  }

  $async.Future<$0.Empty> selectOutbound(
      $grpc.ServiceCall call, $1.SelectOutboundRequest request);

  $async.Future<$0.Empty> setGroupExpand_Pre($grpc.ServiceCall $call,
      $async.Future<$1.SetGroupExpandRequest> $request) async {
    return setGroupExpand($call, await $request);
  }

  $async.Future<$0.Empty> setGroupExpand(
      $grpc.ServiceCall call, $1.SetGroupExpandRequest request);

  $async.Stream<$1.ConnectionEvents> subscribeConnections_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.SubscribeConnectionsRequest> $request) async* {
    yield* subscribeConnections($call, await $request);
  }

  $async.Stream<$1.ConnectionEvents> subscribeConnections(
      $grpc.ServiceCall call, $1.SubscribeConnectionsRequest request);

  $async.Future<$0.Empty> closeConnection_Pre($grpc.ServiceCall $call,
      $async.Future<$1.CloseConnectionRequest> $request) async {
    return closeConnection($call, await $request);
  }

  $async.Future<$0.Empty> closeConnection(
      $grpc.ServiceCall call, $1.CloseConnectionRequest request);

  $async.Future<$0.Empty> closeAllConnections_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return closeAllConnections($call, await $request);
  }

  $async.Future<$0.Empty> closeAllConnections(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.DeprecatedWarnings> getDeprecatedWarnings_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getDeprecatedWarnings($call, await $request);
  }

  $async.Future<$1.DeprecatedWarnings> getDeprecatedWarnings(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.StartedAt> getStartedAt_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getStartedAt($call, await $request);
  }

  $async.Future<$1.StartedAt> getStartedAt(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$1.OutboundList> subscribeOutbounds_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeOutbounds($call, await $request);
  }

  $async.Stream<$1.OutboundList> subscribeOutbounds(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$1.NetworkQualityTestProgress> startNetworkQualityTest_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.NetworkQualityTestRequest> $request) async* {
    yield* startNetworkQualityTest($call, await $request);
  }

  $async.Stream<$1.NetworkQualityTestProgress> startNetworkQualityTest(
      $grpc.ServiceCall call, $1.NetworkQualityTestRequest request);

  $async.Stream<$1.STUNTestProgress> startSTUNTest_Pre($grpc.ServiceCall $call,
      $async.Future<$1.STUNTestRequest> $request) async* {
    yield* startSTUNTest($call, await $request);
  }

  $async.Stream<$1.STUNTestProgress> startSTUNTest(
      $grpc.ServiceCall call, $1.STUNTestRequest request);

  $async.Stream<$1.TailscaleStatusUpdate> subscribeTailscaleStatus_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeTailscaleStatus($call, await $request);
  }

  $async.Stream<$1.TailscaleStatusUpdate> subscribeTailscaleStatus(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$1.TailscalePingResponse> startTailscalePing_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.TailscalePingRequest> $request) async* {
    yield* startTailscalePing($call, await $request);
  }

  $async.Stream<$1.TailscalePingResponse> startTailscalePing(
      $grpc.ServiceCall call, $1.TailscalePingRequest request);

  $async.Future<$0.Empty> setTailscaleExitNode_Pre($grpc.ServiceCall $call,
      $async.Future<$1.SetTailscaleExitNodeRequest> $request) async {
    return setTailscaleExitNode($call, await $request);
  }

  $async.Future<$0.Empty> setTailscaleExitNode(
      $grpc.ServiceCall call, $1.SetTailscaleExitNodeRequest request);

  $async.Future<$0.Empty> tailscaleLogout_Pre($grpc.ServiceCall $call,
      $async.Future<$1.TailscaleLogoutRequest> $request) async {
    return tailscaleLogout($call, await $request);
  }

  $async.Future<$0.Empty> tailscaleLogout(
      $grpc.ServiceCall call, $1.TailscaleLogoutRequest request);

  $async.Future<$1.TailscaleCertificate> getTailscaleCertificate_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.TailscaleCertificateRequest> $request) async {
    return getTailscaleCertificate($call, await $request);
  }

  $async.Future<$1.TailscaleCertificate> getTailscaleCertificate(
      $grpc.ServiceCall call, $1.TailscaleCertificateRequest request);

  $async.Stream<$1.TailscaleSSHServerMessage> startTailscaleSSHSession(
      $grpc.ServiceCall call,
      $async.Stream<$1.TailscaleSSHClientMessage> request);

  $async.Stream<$1.TaildropInbox> subscribeTaildropInbox_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.SubscribeTaildropInboxRequest> $request) async* {
    yield* subscribeTaildropInbox($call, await $request);
  }

  $async.Stream<$1.TaildropInbox> subscribeTaildropInbox(
      $grpc.ServiceCall call, $1.SubscribeTaildropInboxRequest request);

  $async.Future<$0.Empty> markTaildropInboxRead_Pre($grpc.ServiceCall $call,
      $async.Future<$1.MarkTaildropInboxReadRequest> $request) async {
    return markTaildropInboxRead($call, await $request);
  }

  $async.Future<$0.Empty> markTaildropInboxRead(
      $grpc.ServiceCall call, $1.MarkTaildropInboxReadRequest request);

  $async.Stream<$1.TaildropSendServerMessage> sendTaildropFiles(
      $grpc.ServiceCall call,
      $async.Stream<$1.TaildropSendClientMessage> request);

  $async.Stream<$1.DownloadTaildropFileChunk> downloadTaildropFile_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.DownloadTaildropFileRequest> $request) async* {
    yield* downloadTaildropFile($call, await $request);
  }

  $async.Stream<$1.DownloadTaildropFileChunk> downloadTaildropFile(
      $grpc.ServiceCall call, $1.DownloadTaildropFileRequest request);

  $async.Future<$0.Empty> deleteTaildropFile_Pre($grpc.ServiceCall $call,
      $async.Future<$1.DeleteTaildropFileRequest> $request) async {
    return deleteTaildropFile($call, await $request);
  }

  $async.Future<$0.Empty> deleteTaildropFile(
      $grpc.ServiceCall call, $1.DeleteTaildropFileRequest request);

  $async.Future<$0.Empty> cancelTaildropReceiving_Pre($grpc.ServiceCall $call,
      $async.Future<$1.CancelTaildropReceivingRequest> $request) async {
    return cancelTaildropReceiving($call, await $request);
  }

  $async.Future<$0.Empty> cancelTaildropReceiving(
      $grpc.ServiceCall call, $1.CancelTaildropReceivingRequest request);

  $async.Stream<$1.USBServerMessage> provideUSBDevices(
      $grpc.ServiceCall call, $async.Stream<$1.USBProviderMessage> request);

  $async.Stream<$1.USBIPServerStatusUpdate> subscribeUSBIPServerStatus_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeUSBIPServerStatus($call, await $request);
  }

  $async.Stream<$1.USBIPServerStatusUpdate> subscribeUSBIPServerStatus(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$1.OpenConnectStatusUpdate> subscribeOpenConnectStatus_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeOpenConnectStatus($call, await $request);
  }

  $async.Stream<$1.OpenConnectStatusUpdate> subscribeOpenConnectStatus(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> submitOpenConnectAuthResponse_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.OpenConnectAuthResponseSubmission> $request) async {
    return submitOpenConnectAuthResponse($call, await $request);
  }

  $async.Future<$0.Empty> submitOpenConnectAuthResponse(
      $grpc.ServiceCall call, $1.OpenConnectAuthResponseSubmission request);

  $async.Future<$0.Empty> cancelOpenConnectAuthChallenge_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.OpenConnectAuthChallengeCancel> $request) async {
    return cancelOpenConnectAuthChallenge($call, await $request);
  }

  $async.Future<$0.Empty> cancelOpenConnectAuthChallenge(
      $grpc.ServiceCall call, $1.OpenConnectAuthChallengeCancel request);

  $async.Stream<$1.OpenVPNStatusUpdate> subscribeOpenVPNStatus_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeOpenVPNStatus($call, await $request);
  }

  $async.Stream<$1.OpenVPNStatusUpdate> subscribeOpenVPNStatus(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> submitOpenVPNChallengeResponse_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.OpenVPNChallengeSubmission> $request) async {
    return submitOpenVPNChallengeResponse($call, await $request);
  }

  $async.Future<$0.Empty> submitOpenVPNChallengeResponse(
      $grpc.ServiceCall call, $1.OpenVPNChallengeSubmission request);

  $async.Future<$0.Empty> cancelOpenVPNChallenge_Pre($grpc.ServiceCall $call,
      $async.Future<$1.OpenVPNChallengeCancel> $request) async {
    return cancelOpenVPNChallenge($call, await $request);
  }

  $async.Future<$0.Empty> cancelOpenVPNChallenge(
      $grpc.ServiceCall call, $1.OpenVPNChallengeCancel request);

  $async.Stream<$1.NotificationEvent> subscribeNotifications_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async* {
    yield* subscribeNotifications($call, await $request);
  }

  $async.Stream<$1.NotificationEvent> subscribeNotifications(
      $grpc.ServiceCall call, $0.Empty request);
}
