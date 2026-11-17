// This is a generated file - do not edit.
//
// Generated from experimental/boxdd/desktop_service.proto.

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

import '../../daemon/started_service.pb.dart' as $2;
import 'desktop_service.pb.dart' as $1;

export 'desktop_service.pb.dart';

@$pb.GrpcServiceName('desktop.DesktopService')
class DesktopServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  DesktopServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$1.DaemonInfo> getDaemonInfo(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getDaemonInfo, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> claimService(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$claimService, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> takeOverService(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$takeOverService, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> startService(
    $1.StartServiceRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$startService, request, options: options);
  }

  $grpc.ResponseFuture<$1.WorkingDirectoryInfo> getWorkingDirectory(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getWorkingDirectory, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> destroyWorkingDirectory(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$destroyWorkingDirectory, request,
        options: options);
  }

  $grpc.ResponseFuture<$1.CrashReportList> listCrashReports(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$listCrashReports, request, options: options);
  }

  $grpc.ResponseFuture<$1.CrashReportContent> readCrashReport(
    $1.CrashReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$readCrashReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> markCrashReportRead(
    $1.CrashReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$markCrashReportRead, request, options: options);
  }

  $grpc.ResponseFuture<$1.CrashReportArchive> exportCrashReport(
    $1.CrashReportExportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$exportCrashReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> deleteCrashReport(
    $1.CrashReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteCrashReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> deleteAllCrashReports(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteAllCrashReports, request, options: options);
  }

  $grpc.ResponseFuture<$1.OOMReportList> listOOMReports(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$listOOMReports, request, options: options);
  }

  $grpc.ResponseFuture<$1.OOMReportContent> readOOMReport(
    $1.OOMReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$readOOMReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> markOOMReportRead(
    $1.OOMReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$markOOMReportRead, request, options: options);
  }

  $grpc.ResponseFuture<$1.CrashReportArchive> exportOOMReport(
    $1.OOMReportExportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$exportOOMReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> deleteOOMReport(
    $1.OOMReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteOOMReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> deleteAllOOMReports(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteAllOOMReports, request, options: options);
  }

  $grpc.ResponseFuture<$1.OOMReportList> listPowerReports(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$listPowerReports, request, options: options);
  }

  $grpc.ResponseFuture<$1.OOMReportContent> readPowerReport(
    $1.OOMReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$readPowerReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> markPowerReportRead(
    $1.OOMReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$markPowerReportRead, request, options: options);
  }

  $grpc.ResponseFuture<$1.CrashReportArchive> exportPowerReport(
    $1.OOMReportExportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$exportPowerReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> deletePowerReport(
    $1.OOMReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deletePowerReport, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> deleteAllPowerReports(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteAllPowerReports, request, options: options);
  }

  $grpc.ResponseFuture<$1.InstallUpdateResponse> installUpdate(
    $1.InstallUpdateRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$installUpdate, request, options: options);
  }

  $grpc.ResponseFuture<$1.SecuritySettings> getSecuritySettings(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getSecuritySettings, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> setInsecureModeEnabled(
    $1.SetInsecureModeEnabledRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$setInsecureModeEnabled, request,
        options: options);
  }

  $grpc.ResponseFuture<$0.Empty> setLocale(
    $1.SetLocaleRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$setLocale, request, options: options);
  }

  // method descriptors

  static final _$getDaemonInfo = $grpc.ClientMethod<$0.Empty, $1.DaemonInfo>(
      '/desktop.DesktopService/GetDaemonInfo',
      ($0.Empty value) => value.writeToBuffer(),
      $1.DaemonInfo.fromBuffer);
  static final _$claimService = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/desktop.DesktopService/ClaimService',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$takeOverService = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/desktop.DesktopService/TakeOverService',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$startService =
      $grpc.ClientMethod<$1.StartServiceRequest, $0.Empty>(
          '/desktop.DesktopService/StartService',
          ($1.StartServiceRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$getWorkingDirectory =
      $grpc.ClientMethod<$0.Empty, $1.WorkingDirectoryInfo>(
          '/desktop.DesktopService/GetWorkingDirectory',
          ($0.Empty value) => value.writeToBuffer(),
          $1.WorkingDirectoryInfo.fromBuffer);
  static final _$destroyWorkingDirectory =
      $grpc.ClientMethod<$0.Empty, $0.Empty>(
          '/desktop.DesktopService/DestroyWorkingDirectory',
          ($0.Empty value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$listCrashReports =
      $grpc.ClientMethod<$0.Empty, $1.CrashReportList>(
          '/desktop.DesktopService/ListCrashReports',
          ($0.Empty value) => value.writeToBuffer(),
          $1.CrashReportList.fromBuffer);
  static final _$readCrashReport =
      $grpc.ClientMethod<$1.CrashReportRequest, $1.CrashReportContent>(
          '/desktop.DesktopService/ReadCrashReport',
          ($1.CrashReportRequest value) => value.writeToBuffer(),
          $1.CrashReportContent.fromBuffer);
  static final _$markCrashReportRead =
      $grpc.ClientMethod<$1.CrashReportRequest, $0.Empty>(
          '/desktop.DesktopService/MarkCrashReportRead',
          ($1.CrashReportRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$exportCrashReport =
      $grpc.ClientMethod<$1.CrashReportExportRequest, $1.CrashReportArchive>(
          '/desktop.DesktopService/ExportCrashReport',
          ($1.CrashReportExportRequest value) => value.writeToBuffer(),
          $1.CrashReportArchive.fromBuffer);
  static final _$deleteCrashReport =
      $grpc.ClientMethod<$1.CrashReportRequest, $0.Empty>(
          '/desktop.DesktopService/DeleteCrashReport',
          ($1.CrashReportRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$deleteAllCrashReports = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/desktop.DesktopService/DeleteAllCrashReports',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$listOOMReports =
      $grpc.ClientMethod<$0.Empty, $1.OOMReportList>(
          '/desktop.DesktopService/ListOOMReports',
          ($0.Empty value) => value.writeToBuffer(),
          $1.OOMReportList.fromBuffer);
  static final _$readOOMReport =
      $grpc.ClientMethod<$1.OOMReportRequest, $1.OOMReportContent>(
          '/desktop.DesktopService/ReadOOMReport',
          ($1.OOMReportRequest value) => value.writeToBuffer(),
          $1.OOMReportContent.fromBuffer);
  static final _$markOOMReportRead =
      $grpc.ClientMethod<$1.OOMReportRequest, $0.Empty>(
          '/desktop.DesktopService/MarkOOMReportRead',
          ($1.OOMReportRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$exportOOMReport =
      $grpc.ClientMethod<$1.OOMReportExportRequest, $1.CrashReportArchive>(
          '/desktop.DesktopService/ExportOOMReport',
          ($1.OOMReportExportRequest value) => value.writeToBuffer(),
          $1.CrashReportArchive.fromBuffer);
  static final _$deleteOOMReport =
      $grpc.ClientMethod<$1.OOMReportRequest, $0.Empty>(
          '/desktop.DesktopService/DeleteOOMReport',
          ($1.OOMReportRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$deleteAllOOMReports = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/desktop.DesktopService/DeleteAllOOMReports',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$listPowerReports =
      $grpc.ClientMethod<$0.Empty, $1.OOMReportList>(
          '/desktop.DesktopService/ListPowerReports',
          ($0.Empty value) => value.writeToBuffer(),
          $1.OOMReportList.fromBuffer);
  static final _$readPowerReport =
      $grpc.ClientMethod<$1.OOMReportRequest, $1.OOMReportContent>(
          '/desktop.DesktopService/ReadPowerReport',
          ($1.OOMReportRequest value) => value.writeToBuffer(),
          $1.OOMReportContent.fromBuffer);
  static final _$markPowerReportRead =
      $grpc.ClientMethod<$1.OOMReportRequest, $0.Empty>(
          '/desktop.DesktopService/MarkPowerReportRead',
          ($1.OOMReportRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$exportPowerReport =
      $grpc.ClientMethod<$1.OOMReportExportRequest, $1.CrashReportArchive>(
          '/desktop.DesktopService/ExportPowerReport',
          ($1.OOMReportExportRequest value) => value.writeToBuffer(),
          $1.CrashReportArchive.fromBuffer);
  static final _$deletePowerReport =
      $grpc.ClientMethod<$1.OOMReportRequest, $0.Empty>(
          '/desktop.DesktopService/DeletePowerReport',
          ($1.OOMReportRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$deleteAllPowerReports = $grpc.ClientMethod<$0.Empty, $0.Empty>(
      '/desktop.DesktopService/DeleteAllPowerReports',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$installUpdate =
      $grpc.ClientMethod<$1.InstallUpdateRequest, $1.InstallUpdateResponse>(
          '/desktop.DesktopService/InstallUpdate',
          ($1.InstallUpdateRequest value) => value.writeToBuffer(),
          $1.InstallUpdateResponse.fromBuffer);
  static final _$getSecuritySettings =
      $grpc.ClientMethod<$0.Empty, $1.SecuritySettings>(
          '/desktop.DesktopService/GetSecuritySettings',
          ($0.Empty value) => value.writeToBuffer(),
          $1.SecuritySettings.fromBuffer);
  static final _$setInsecureModeEnabled =
      $grpc.ClientMethod<$1.SetInsecureModeEnabledRequest, $0.Empty>(
          '/desktop.DesktopService/SetInsecureModeEnabled',
          ($1.SetInsecureModeEnabledRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$setLocale = $grpc.ClientMethod<$1.SetLocaleRequest, $0.Empty>(
      '/desktop.DesktopService/SetLocale',
      ($1.SetLocaleRequest value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
}

@$pb.GrpcServiceName('desktop.DesktopService')
abstract class DesktopServiceBase extends $grpc.Service {
  $core.String get $name => 'desktop.DesktopService';

  DesktopServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.DaemonInfo>(
        'GetDaemonInfo',
        getDaemonInfo_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.DaemonInfo value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'ClaimService',
        claimService_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'TakeOverService',
        takeOverService_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.StartServiceRequest, $0.Empty>(
        'StartService',
        startService_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.StartServiceRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.WorkingDirectoryInfo>(
        'GetWorkingDirectory',
        getWorkingDirectory_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.WorkingDirectoryInfo value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'DestroyWorkingDirectory',
        destroyWorkingDirectory_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.CrashReportList>(
        'ListCrashReports',
        listCrashReports_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.CrashReportList value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.CrashReportRequest, $1.CrashReportContent>(
            'ReadCrashReport',
            readCrashReport_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $1.CrashReportRequest.fromBuffer(value),
            ($1.CrashReportContent value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.CrashReportRequest, $0.Empty>(
        'MarkCrashReportRead',
        markCrashReportRead_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.CrashReportRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.CrashReportExportRequest, $1.CrashReportArchive>(
            'ExportCrashReport',
            exportCrashReport_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $1.CrashReportExportRequest.fromBuffer(value),
            ($1.CrashReportArchive value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.CrashReportRequest, $0.Empty>(
        'DeleteCrashReport',
        deleteCrashReport_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.CrashReportRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'DeleteAllCrashReports',
        deleteAllCrashReports_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.OOMReportList>(
        'ListOOMReports',
        listOOMReports_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.OOMReportList value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OOMReportRequest, $1.OOMReportContent>(
        'ReadOOMReport',
        readOOMReport_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.OOMReportRequest.fromBuffer(value),
        ($1.OOMReportContent value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OOMReportRequest, $0.Empty>(
        'MarkOOMReportRead',
        markOOMReportRead_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.OOMReportRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.OOMReportExportRequest, $1.CrashReportArchive>(
            'ExportOOMReport',
            exportOOMReport_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $1.OOMReportExportRequest.fromBuffer(value),
            ($1.CrashReportArchive value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OOMReportRequest, $0.Empty>(
        'DeleteOOMReport',
        deleteOOMReport_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.OOMReportRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'DeleteAllOOMReports',
        deleteAllOOMReports_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.OOMReportList>(
        'ListPowerReports',
        listPowerReports_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.OOMReportList value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OOMReportRequest, $1.OOMReportContent>(
        'ReadPowerReport',
        readPowerReport_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.OOMReportRequest.fromBuffer(value),
        ($1.OOMReportContent value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OOMReportRequest, $0.Empty>(
        'MarkPowerReportRead',
        markPowerReportRead_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.OOMReportRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.OOMReportExportRequest, $1.CrashReportArchive>(
            'ExportPowerReport',
            exportPowerReport_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $1.OOMReportExportRequest.fromBuffer(value),
            ($1.CrashReportArchive value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.OOMReportRequest, $0.Empty>(
        'DeletePowerReport',
        deletePowerReport_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.OOMReportRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Empty>(
        'DeleteAllPowerReports',
        deleteAllPowerReports_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.InstallUpdateRequest, $1.InstallUpdateResponse>(
            'InstallUpdate',
            installUpdate_Pre,
            false,
            false,
            ($core.List<$core.int> value) =>
                $1.InstallUpdateRequest.fromBuffer(value),
            ($1.InstallUpdateResponse value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.SecuritySettings>(
        'GetSecuritySettings',
        getSecuritySettings_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.SecuritySettings value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.SetInsecureModeEnabledRequest, $0.Empty>(
        'SetInsecureModeEnabled',
        setInsecureModeEnabled_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.SetInsecureModeEnabledRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.SetLocaleRequest, $0.Empty>(
        'SetLocale',
        setLocale_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.SetLocaleRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
  }

  $async.Future<$1.DaemonInfo> getDaemonInfo_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getDaemonInfo($call, await $request);
  }

  $async.Future<$1.DaemonInfo> getDaemonInfo(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> claimService_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return claimService($call, await $request);
  }

  $async.Future<$0.Empty> claimService(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> takeOverService_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return takeOverService($call, await $request);
  }

  $async.Future<$0.Empty> takeOverService(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> startService_Pre($grpc.ServiceCall $call,
      $async.Future<$1.StartServiceRequest> $request) async {
    return startService($call, await $request);
  }

  $async.Future<$0.Empty> startService(
      $grpc.ServiceCall call, $1.StartServiceRequest request);

  $async.Future<$1.WorkingDirectoryInfo> getWorkingDirectory_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getWorkingDirectory($call, await $request);
  }

  $async.Future<$1.WorkingDirectoryInfo> getWorkingDirectory(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> destroyWorkingDirectory_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return destroyWorkingDirectory($call, await $request);
  }

  $async.Future<$0.Empty> destroyWorkingDirectory(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.CrashReportList> listCrashReports_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return listCrashReports($call, await $request);
  }

  $async.Future<$1.CrashReportList> listCrashReports(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.CrashReportContent> readCrashReport_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.CrashReportRequest> $request) async {
    return readCrashReport($call, await $request);
  }

  $async.Future<$1.CrashReportContent> readCrashReport(
      $grpc.ServiceCall call, $1.CrashReportRequest request);

  $async.Future<$0.Empty> markCrashReportRead_Pre($grpc.ServiceCall $call,
      $async.Future<$1.CrashReportRequest> $request) async {
    return markCrashReportRead($call, await $request);
  }

  $async.Future<$0.Empty> markCrashReportRead(
      $grpc.ServiceCall call, $1.CrashReportRequest request);

  $async.Future<$1.CrashReportArchive> exportCrashReport_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.CrashReportExportRequest> $request) async {
    return exportCrashReport($call, await $request);
  }

  $async.Future<$1.CrashReportArchive> exportCrashReport(
      $grpc.ServiceCall call, $1.CrashReportExportRequest request);

  $async.Future<$0.Empty> deleteCrashReport_Pre($grpc.ServiceCall $call,
      $async.Future<$1.CrashReportRequest> $request) async {
    return deleteCrashReport($call, await $request);
  }

  $async.Future<$0.Empty> deleteCrashReport(
      $grpc.ServiceCall call, $1.CrashReportRequest request);

  $async.Future<$0.Empty> deleteAllCrashReports_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return deleteAllCrashReports($call, await $request);
  }

  $async.Future<$0.Empty> deleteAllCrashReports(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.OOMReportList> listOOMReports_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return listOOMReports($call, await $request);
  }

  $async.Future<$1.OOMReportList> listOOMReports(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.OOMReportContent> readOOMReport_Pre($grpc.ServiceCall $call,
      $async.Future<$1.OOMReportRequest> $request) async {
    return readOOMReport($call, await $request);
  }

  $async.Future<$1.OOMReportContent> readOOMReport(
      $grpc.ServiceCall call, $1.OOMReportRequest request);

  $async.Future<$0.Empty> markOOMReportRead_Pre($grpc.ServiceCall $call,
      $async.Future<$1.OOMReportRequest> $request) async {
    return markOOMReportRead($call, await $request);
  }

  $async.Future<$0.Empty> markOOMReportRead(
      $grpc.ServiceCall call, $1.OOMReportRequest request);

  $async.Future<$1.CrashReportArchive> exportOOMReport_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.OOMReportExportRequest> $request) async {
    return exportOOMReport($call, await $request);
  }

  $async.Future<$1.CrashReportArchive> exportOOMReport(
      $grpc.ServiceCall call, $1.OOMReportExportRequest request);

  $async.Future<$0.Empty> deleteOOMReport_Pre($grpc.ServiceCall $call,
      $async.Future<$1.OOMReportRequest> $request) async {
    return deleteOOMReport($call, await $request);
  }

  $async.Future<$0.Empty> deleteOOMReport(
      $grpc.ServiceCall call, $1.OOMReportRequest request);

  $async.Future<$0.Empty> deleteAllOOMReports_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return deleteAllOOMReports($call, await $request);
  }

  $async.Future<$0.Empty> deleteAllOOMReports(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.OOMReportList> listPowerReports_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return listPowerReports($call, await $request);
  }

  $async.Future<$1.OOMReportList> listPowerReports(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.OOMReportContent> readPowerReport_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.OOMReportRequest> $request) async {
    return readPowerReport($call, await $request);
  }

  $async.Future<$1.OOMReportContent> readPowerReport(
      $grpc.ServiceCall call, $1.OOMReportRequest request);

  $async.Future<$0.Empty> markPowerReportRead_Pre($grpc.ServiceCall $call,
      $async.Future<$1.OOMReportRequest> $request) async {
    return markPowerReportRead($call, await $request);
  }

  $async.Future<$0.Empty> markPowerReportRead(
      $grpc.ServiceCall call, $1.OOMReportRequest request);

  $async.Future<$1.CrashReportArchive> exportPowerReport_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.OOMReportExportRequest> $request) async {
    return exportPowerReport($call, await $request);
  }

  $async.Future<$1.CrashReportArchive> exportPowerReport(
      $grpc.ServiceCall call, $1.OOMReportExportRequest request);

  $async.Future<$0.Empty> deletePowerReport_Pre($grpc.ServiceCall $call,
      $async.Future<$1.OOMReportRequest> $request) async {
    return deletePowerReport($call, await $request);
  }

  $async.Future<$0.Empty> deletePowerReport(
      $grpc.ServiceCall call, $1.OOMReportRequest request);

  $async.Future<$0.Empty> deleteAllPowerReports_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return deleteAllPowerReports($call, await $request);
  }

  $async.Future<$0.Empty> deleteAllPowerReports(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.InstallUpdateResponse> installUpdate_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.InstallUpdateRequest> $request) async {
    return installUpdate($call, await $request);
  }

  $async.Future<$1.InstallUpdateResponse> installUpdate(
      $grpc.ServiceCall call, $1.InstallUpdateRequest request);

  $async.Future<$1.SecuritySettings> getSecuritySettings_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getSecuritySettings($call, await $request);
  }

  $async.Future<$1.SecuritySettings> getSecuritySettings(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Empty> setInsecureModeEnabled_Pre($grpc.ServiceCall $call,
      $async.Future<$1.SetInsecureModeEnabledRequest> $request) async {
    return setInsecureModeEnabled($call, await $request);
  }

  $async.Future<$0.Empty> setInsecureModeEnabled(
      $grpc.ServiceCall call, $1.SetInsecureModeEnabledRequest request);

  $async.Future<$0.Empty> setLocale_Pre($grpc.ServiceCall $call,
      $async.Future<$1.SetLocaleRequest> $request) async {
    return setLocale($call, await $request);
  }

  $async.Future<$0.Empty> setLocale(
      $grpc.ServiceCall call, $1.SetLocaleRequest request);
}

@$pb.GrpcServiceName('desktop.ApplicationService')
class ApplicationServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  ApplicationServiceClient(super.channel, {super.options, super.interceptors});

  $grpc.ResponseFuture<$0.Empty> checkConfig(
    $1.ConfigContent request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$checkConfig, request, options: options);
  }

  $grpc.ResponseFuture<$1.ConfigContent> formatConfig(
    $1.ConfigContent request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$formatConfig, request, options: options);
  }

  $grpc.ResponseFuture<$1.ConfigContent> generateConfigSchema(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$generateConfigSchema, request, options: options);
  }

  $grpc.ResponseFuture<$1.ProfileData> encodeProfile(
    $1.ProfileContent request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$encodeProfile, request, options: options);
  }

  $grpc.ResponseFuture<$1.ProfileContent> decodeProfile(
    $1.ProfileData request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$decodeProfile, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> archiveReport(
    $1.ArchiveReportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$archiveReport, request, options: options);
  }

  $grpc.ResponseStream<$2.NetworkQualityTestProgress>
      startStandaloneNetworkQualityTest(
    $1.StandaloneNetworkQualityTestRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(_$startStandaloneNetworkQualityTest,
        $async.Stream.fromIterable([request]),
        options: options);
  }

  $grpc.ResponseStream<$2.STUNTestProgress> startStandaloneSTUNTest(
    $1.StandaloneSTUNTestRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$startStandaloneSTUNTest, $async.Stream.fromIterable([request]),
        options: options);
  }

  // method descriptors

  static final _$checkConfig = $grpc.ClientMethod<$1.ConfigContent, $0.Empty>(
      '/desktop.ApplicationService/CheckConfig',
      ($1.ConfigContent value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$formatConfig =
      $grpc.ClientMethod<$1.ConfigContent, $1.ConfigContent>(
          '/desktop.ApplicationService/FormatConfig',
          ($1.ConfigContent value) => value.writeToBuffer(),
          $1.ConfigContent.fromBuffer);
  static final _$generateConfigSchema =
      $grpc.ClientMethod<$0.Empty, $1.ConfigContent>(
          '/desktop.ApplicationService/GenerateConfigSchema',
          ($0.Empty value) => value.writeToBuffer(),
          $1.ConfigContent.fromBuffer);
  static final _$encodeProfile =
      $grpc.ClientMethod<$1.ProfileContent, $1.ProfileData>(
          '/desktop.ApplicationService/EncodeProfile',
          ($1.ProfileContent value) => value.writeToBuffer(),
          $1.ProfileData.fromBuffer);
  static final _$decodeProfile =
      $grpc.ClientMethod<$1.ProfileData, $1.ProfileContent>(
          '/desktop.ApplicationService/DecodeProfile',
          ($1.ProfileData value) => value.writeToBuffer(),
          $1.ProfileContent.fromBuffer);
  static final _$archiveReport =
      $grpc.ClientMethod<$1.ArchiveReportRequest, $0.Empty>(
          '/desktop.ApplicationService/ArchiveReport',
          ($1.ArchiveReportRequest value) => value.writeToBuffer(),
          $0.Empty.fromBuffer);
  static final _$startStandaloneNetworkQualityTest = $grpc.ClientMethod<
          $1.StandaloneNetworkQualityTestRequest,
          $2.NetworkQualityTestProgress>(
      '/desktop.ApplicationService/StartStandaloneNetworkQualityTest',
      ($1.StandaloneNetworkQualityTestRequest value) => value.writeToBuffer(),
      $2.NetworkQualityTestProgress.fromBuffer);
  static final _$startStandaloneSTUNTest =
      $grpc.ClientMethod<$1.StandaloneSTUNTestRequest, $2.STUNTestProgress>(
          '/desktop.ApplicationService/StartStandaloneSTUNTest',
          ($1.StandaloneSTUNTestRequest value) => value.writeToBuffer(),
          $2.STUNTestProgress.fromBuffer);
}

@$pb.GrpcServiceName('desktop.ApplicationService')
abstract class ApplicationServiceBase extends $grpc.Service {
  $core.String get $name => 'desktop.ApplicationService';

  ApplicationServiceBase() {
    $addMethod($grpc.ServiceMethod<$1.ConfigContent, $0.Empty>(
        'CheckConfig',
        checkConfig_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.ConfigContent.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.ConfigContent, $1.ConfigContent>(
        'FormatConfig',
        formatConfig_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.ConfigContent.fromBuffer(value),
        ($1.ConfigContent value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $1.ConfigContent>(
        'GenerateConfigSchema',
        generateConfigSchema_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($1.ConfigContent value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.ProfileContent, $1.ProfileData>(
        'EncodeProfile',
        encodeProfile_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.ProfileContent.fromBuffer(value),
        ($1.ProfileData value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.ProfileData, $1.ProfileContent>(
        'DecodeProfile',
        decodeProfile_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $1.ProfileData.fromBuffer(value),
        ($1.ProfileContent value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.ArchiveReportRequest, $0.Empty>(
        'ArchiveReport',
        archiveReport_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $1.ArchiveReportRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$1.StandaloneNetworkQualityTestRequest,
            $2.NetworkQualityTestProgress>(
        'StartStandaloneNetworkQualityTest',
        startStandaloneNetworkQualityTest_Pre,
        false,
        true,
        ($core.List<$core.int> value) =>
            $1.StandaloneNetworkQualityTestRequest.fromBuffer(value),
        ($2.NetworkQualityTestProgress value) => value.writeToBuffer()));
    $addMethod(
        $grpc.ServiceMethod<$1.StandaloneSTUNTestRequest, $2.STUNTestProgress>(
            'StartStandaloneSTUNTest',
            startStandaloneSTUNTest_Pre,
            false,
            true,
            ($core.List<$core.int> value) =>
                $1.StandaloneSTUNTestRequest.fromBuffer(value),
            ($2.STUNTestProgress value) => value.writeToBuffer()));
  }

  $async.Future<$0.Empty> checkConfig_Pre(
      $grpc.ServiceCall $call, $async.Future<$1.ConfigContent> $request) async {
    return checkConfig($call, await $request);
  }

  $async.Future<$0.Empty> checkConfig(
      $grpc.ServiceCall call, $1.ConfigContent request);

  $async.Future<$1.ConfigContent> formatConfig_Pre(
      $grpc.ServiceCall $call, $async.Future<$1.ConfigContent> $request) async {
    return formatConfig($call, await $request);
  }

  $async.Future<$1.ConfigContent> formatConfig(
      $grpc.ServiceCall call, $1.ConfigContent request);

  $async.Future<$1.ConfigContent> generateConfigSchema_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return generateConfigSchema($call, await $request);
  }

  $async.Future<$1.ConfigContent> generateConfigSchema(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$1.ProfileData> encodeProfile_Pre($grpc.ServiceCall $call,
      $async.Future<$1.ProfileContent> $request) async {
    return encodeProfile($call, await $request);
  }

  $async.Future<$1.ProfileData> encodeProfile(
      $grpc.ServiceCall call, $1.ProfileContent request);

  $async.Future<$1.ProfileContent> decodeProfile_Pre(
      $grpc.ServiceCall $call, $async.Future<$1.ProfileData> $request) async {
    return decodeProfile($call, await $request);
  }

  $async.Future<$1.ProfileContent> decodeProfile(
      $grpc.ServiceCall call, $1.ProfileData request);

  $async.Future<$0.Empty> archiveReport_Pre($grpc.ServiceCall $call,
      $async.Future<$1.ArchiveReportRequest> $request) async {
    return archiveReport($call, await $request);
  }

  $async.Future<$0.Empty> archiveReport(
      $grpc.ServiceCall call, $1.ArchiveReportRequest request);

  $async.Stream<$2.NetworkQualityTestProgress>
      startStandaloneNetworkQualityTest_Pre(
          $grpc.ServiceCall $call,
          $async.Future<$1.StandaloneNetworkQualityTestRequest>
              $request) async* {
    yield* startStandaloneNetworkQualityTest($call, await $request);
  }

  $async.Stream<$2.NetworkQualityTestProgress>
      startStandaloneNetworkQualityTest($grpc.ServiceCall call,
          $1.StandaloneNetworkQualityTestRequest request);

  $async.Stream<$2.STUNTestProgress> startStandaloneSTUNTest_Pre(
      $grpc.ServiceCall $call,
      $async.Future<$1.StandaloneSTUNTestRequest> $request) async* {
    yield* startStandaloneSTUNTest($call, await $request);
  }

  $async.Stream<$2.STUNTestProgress> startStandaloneSTUNTest(
      $grpc.ServiceCall call, $1.StandaloneSTUNTestRequest request);
}
