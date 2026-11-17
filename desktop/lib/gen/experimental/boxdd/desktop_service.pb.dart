// This is a generated file - do not edit.
//
// Generated from experimental/boxdd/desktop_service.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:core' as $core;

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:protobuf/protobuf.dart' as $pb;

import 'desktop_service.pbenum.dart';

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

export 'desktop_service.pbenum.dart';

class ArchiveReportRequest extends $pb.GeneratedMessage {
  factory ArchiveReportRequest({
    $core.String? sourcePath,
    $core.String? destinationPath,
    $core.bool? encrypt,
  }) {
    final result = ArchiveReportRequest._();
    if (sourcePath != null) result.sourcePath = sourcePath;
    if (destinationPath != null) result.destinationPath = destinationPath;
    if (encrypt != null) result.encrypt = encrypt;
    return result;
  }

  ArchiveReportRequest._();

  factory ArchiveReportRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ArchiveReportRequest()..mergeFromBuffer(data, registry);
  factory ArchiveReportRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ArchiveReportRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ArchiveReportRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: ArchiveReportRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'sourcePath')
    ..aOS(2, _omitFieldNames ? '' : 'destinationPath')
    ..aOB(3, _omitFieldNames ? '' : 'encrypt')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ArchiveReportRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ArchiveReportRequest copyWith(void Function(ArchiveReportRequest) updates) =>
      super.copyWith((message) => updates(message as ArchiveReportRequest))
          as ArchiveReportRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use ArchiveReportRequest() / ArchiveReportRequest.new instead')
  static ArchiveReportRequest create() => ArchiveReportRequest._();
  static $pb.GeneratedMessage $_createMessage() => ArchiveReportRequest._();
  @$core.override
  ArchiveReportRequest createEmptyInstance() => ArchiveReportRequest._();
  @$core.pragma('dart2js:noInline')
  static ArchiveReportRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ArchiveReportRequest>(
          ArchiveReportRequest.$_createMessage);
  static ArchiveReportRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get sourcePath => $_getSZ(0);
  @$pb.TagNumber(1)
  set sourcePath($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasSourcePath() => $_has(0);
  @$pb.TagNumber(1)
  void clearSourcePath() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get destinationPath => $_getSZ(1);
  @$pb.TagNumber(2)
  set destinationPath($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasDestinationPath() => $_has(1);
  @$pb.TagNumber(2)
  void clearDestinationPath() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get encrypt => $_getBF(2);
  @$pb.TagNumber(3)
  set encrypt($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasEncrypt() => $_has(2);
  @$pb.TagNumber(3)
  void clearEncrypt() => $_clearField(3);
}

class StandaloneNetworkQualityTestRequest extends $pb.GeneratedMessage {
  factory StandaloneNetworkQualityTestRequest({
    $core.String? configUrl,
    $core.bool? serial,
    $core.int? maxRuntimeSeconds,
    $core.bool? http3,
  }) {
    final result = StandaloneNetworkQualityTestRequest._();
    if (configUrl != null) result.configUrl = configUrl;
    if (serial != null) result.serial = serial;
    if (maxRuntimeSeconds != null) result.maxRuntimeSeconds = maxRuntimeSeconds;
    if (http3 != null) result.http3 = http3;
    return result;
  }

  StandaloneNetworkQualityTestRequest._();

  factory StandaloneNetworkQualityTestRequest.fromBuffer(
          $core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StandaloneNetworkQualityTestRequest()..mergeFromBuffer(data, registry);
  factory StandaloneNetworkQualityTestRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StandaloneNetworkQualityTestRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StandaloneNetworkQualityTestRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: StandaloneNetworkQualityTestRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'configUrl')
    ..aOB(2, _omitFieldNames ? '' : 'serial')
    ..aI(3, _omitFieldNames ? '' : 'maxRuntimeSeconds')
    ..aOB(4, _omitFieldNames ? '' : 'http3')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StandaloneNetworkQualityTestRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StandaloneNetworkQualityTestRequest copyWith(
          void Function(StandaloneNetworkQualityTestRequest) updates) =>
      super.copyWith((message) =>
              updates(message as StandaloneNetworkQualityTestRequest))
          as StandaloneNetworkQualityTestRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use StandaloneNetworkQualityTestRequest() / StandaloneNetworkQualityTestRequest.new instead')
  static StandaloneNetworkQualityTestRequest create() =>
      StandaloneNetworkQualityTestRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      StandaloneNetworkQualityTestRequest._();
  @$core.override
  StandaloneNetworkQualityTestRequest createEmptyInstance() =>
      StandaloneNetworkQualityTestRequest._();
  @$core.pragma('dart2js:noInline')
  static StandaloneNetworkQualityTestRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<
              StandaloneNetworkQualityTestRequest>(
          StandaloneNetworkQualityTestRequest.$_createMessage);
  static StandaloneNetworkQualityTestRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get configUrl => $_getSZ(0);
  @$pb.TagNumber(1)
  set configUrl($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasConfigUrl() => $_has(0);
  @$pb.TagNumber(1)
  void clearConfigUrl() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get serial => $_getBF(1);
  @$pb.TagNumber(2)
  set serial($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSerial() => $_has(1);
  @$pb.TagNumber(2)
  void clearSerial() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get maxRuntimeSeconds => $_getIZ(2);
  @$pb.TagNumber(3)
  set maxRuntimeSeconds($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasMaxRuntimeSeconds() => $_has(2);
  @$pb.TagNumber(3)
  void clearMaxRuntimeSeconds() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.bool get http3 => $_getBF(3);
  @$pb.TagNumber(4)
  set http3($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasHttp3() => $_has(3);
  @$pb.TagNumber(4)
  void clearHttp3() => $_clearField(4);
}

class StandaloneSTUNTestRequest extends $pb.GeneratedMessage {
  factory StandaloneSTUNTestRequest({
    $core.String? server,
  }) {
    final result = StandaloneSTUNTestRequest._();
    if (server != null) result.server = server;
    return result;
  }

  StandaloneSTUNTestRequest._();

  factory StandaloneSTUNTestRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StandaloneSTUNTestRequest()..mergeFromBuffer(data, registry);
  factory StandaloneSTUNTestRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StandaloneSTUNTestRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StandaloneSTUNTestRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: StandaloneSTUNTestRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'server')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StandaloneSTUNTestRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StandaloneSTUNTestRequest copyWith(
          void Function(StandaloneSTUNTestRequest) updates) =>
      super.copyWith((message) => updates(message as StandaloneSTUNTestRequest))
          as StandaloneSTUNTestRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use StandaloneSTUNTestRequest() / StandaloneSTUNTestRequest.new instead')
  static StandaloneSTUNTestRequest create() => StandaloneSTUNTestRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      StandaloneSTUNTestRequest._();
  @$core.override
  StandaloneSTUNTestRequest createEmptyInstance() =>
      StandaloneSTUNTestRequest._();
  @$core.pragma('dart2js:noInline')
  static StandaloneSTUNTestRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<StandaloneSTUNTestRequest>(
          StandaloneSTUNTestRequest.$_createMessage);
  static StandaloneSTUNTestRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get server => $_getSZ(0);
  @$pb.TagNumber(1)
  set server($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasServer() => $_has(0);
  @$pb.TagNumber(1)
  void clearServer() => $_clearField(1);
}

class DaemonInfo extends $pb.GeneratedMessage {
  factory DaemonInfo({
    $core.String? version,
    DaemonOwnership? ownership,
  }) {
    final result = DaemonInfo._();
    if (version != null) result.version = version;
    if (ownership != null) result.ownership = ownership;
    return result;
  }

  DaemonInfo._();

  factory DaemonInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DaemonInfo()..mergeFromBuffer(data, registry);
  factory DaemonInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DaemonInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DaemonInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: DaemonInfo.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'version')
    ..aE<DaemonOwnership>(2, _omitFieldNames ? '' : 'ownership',
        enumValues: DaemonOwnership.values)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DaemonInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DaemonInfo copyWith(void Function(DaemonInfo) updates) =>
      super.copyWith((message) => updates(message as DaemonInfo)) as DaemonInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use DaemonInfo() / DaemonInfo.new instead')
  static DaemonInfo create() => DaemonInfo._();
  static $pb.GeneratedMessage $_createMessage() => DaemonInfo._();
  @$core.override
  DaemonInfo createEmptyInstance() => DaemonInfo._();
  @$core.pragma('dart2js:noInline')
  static DaemonInfo getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<DaemonInfo>(DaemonInfo.$_createMessage);
  static DaemonInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get version => $_getSZ(0);
  @$pb.TagNumber(1)
  set version($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasVersion() => $_has(0);
  @$pb.TagNumber(1)
  void clearVersion() => $_clearField(1);

  @$pb.TagNumber(2)
  DaemonOwnership get ownership => $_getN(1);
  @$pb.TagNumber(2)
  set ownership(DaemonOwnership value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasOwnership() => $_has(1);
  @$pb.TagNumber(2)
  void clearOwnership() => $_clearField(2);
}

class StartServiceRequest extends $pb.GeneratedMessage {
  factory StartServiceRequest({
    $core.String? configContent,
    StartOptions? options,
  }) {
    final result = StartServiceRequest._();
    if (configContent != null) result.configContent = configContent;
    if (options != null) result.options = options;
    return result;
  }

  StartServiceRequest._();

  factory StartServiceRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StartServiceRequest()..mergeFromBuffer(data, registry);
  factory StartServiceRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StartServiceRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StartServiceRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: StartServiceRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'configContent')
    ..aOM<StartOptions>(2, _omitFieldNames ? '' : 'options',
        subBuilder: StartOptions.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StartServiceRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StartServiceRequest copyWith(void Function(StartServiceRequest) updates) =>
      super.copyWith((message) => updates(message as StartServiceRequest))
          as StartServiceRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core
      .Deprecated('Use StartServiceRequest() / StartServiceRequest.new instead')
  static StartServiceRequest create() => StartServiceRequest._();
  static $pb.GeneratedMessage $_createMessage() => StartServiceRequest._();
  @$core.override
  StartServiceRequest createEmptyInstance() => StartServiceRequest._();
  @$core.pragma('dart2js:noInline')
  static StartServiceRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<StartServiceRequest>(
          StartServiceRequest.$_createMessage);
  static StartServiceRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get configContent => $_getSZ(0);
  @$pb.TagNumber(1)
  set configContent($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasConfigContent() => $_has(0);
  @$pb.TagNumber(1)
  void clearConfigContent() => $_clearField(1);

  @$pb.TagNumber(2)
  StartOptions get options => $_getN(1);
  @$pb.TagNumber(2)
  set options(StartOptions value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasOptions() => $_has(1);
  @$pb.TagNumber(2)
  void clearOptions() => $_clearField(2);
  @$pb.TagNumber(2)
  StartOptions ensureOptions() => $_ensure(1);
}

class StartOptions extends $pb.GeneratedMessage {
  factory StartOptions({
    $core.bool? oomKillerEnabled,
    $core.bool? oomKillerDisabled,
    $fixnum.Int64? oomMemoryLimit,
    $core.bool? powerReportEnabled,
  }) {
    final result = StartOptions._();
    if (oomKillerEnabled != null) result.oomKillerEnabled = oomKillerEnabled;
    if (oomKillerDisabled != null) result.oomKillerDisabled = oomKillerDisabled;
    if (oomMemoryLimit != null) result.oomMemoryLimit = oomMemoryLimit;
    if (powerReportEnabled != null)
      result.powerReportEnabled = powerReportEnabled;
    return result;
  }

  StartOptions._();

  factory StartOptions.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StartOptions()..mergeFromBuffer(data, registry);
  factory StartOptions.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StartOptions()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StartOptions',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: StartOptions.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'oomKillerEnabled')
    ..aOB(2, _omitFieldNames ? '' : 'oomKillerDisabled')
    ..aInt64(3, _omitFieldNames ? '' : 'oomMemoryLimit')
    ..aOB(4, _omitFieldNames ? '' : 'powerReportEnabled')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StartOptions clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StartOptions copyWith(void Function(StartOptions) updates) =>
      super.copyWith((message) => updates(message as StartOptions))
          as StartOptions;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use StartOptions() / StartOptions.new instead')
  static StartOptions create() => StartOptions._();
  static $pb.GeneratedMessage $_createMessage() => StartOptions._();
  @$core.override
  StartOptions createEmptyInstance() => StartOptions._();
  @$core.pragma('dart2js:noInline')
  static StartOptions getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<StartOptions>(
          StartOptions.$_createMessage);
  static StartOptions? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get oomKillerEnabled => $_getBF(0);
  @$pb.TagNumber(1)
  set oomKillerEnabled($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasOomKillerEnabled() => $_has(0);
  @$pb.TagNumber(1)
  void clearOomKillerEnabled() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get oomKillerDisabled => $_getBF(1);
  @$pb.TagNumber(2)
  set oomKillerDisabled($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasOomKillerDisabled() => $_has(1);
  @$pb.TagNumber(2)
  void clearOomKillerDisabled() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get oomMemoryLimit => $_getI64(2);
  @$pb.TagNumber(3)
  set oomMemoryLimit($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasOomMemoryLimit() => $_has(2);
  @$pb.TagNumber(3)
  void clearOomMemoryLimit() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.bool get powerReportEnabled => $_getBF(3);
  @$pb.TagNumber(4)
  set powerReportEnabled($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasPowerReportEnabled() => $_has(3);
  @$pb.TagNumber(4)
  void clearPowerReportEnabled() => $_clearField(4);
}

class ConfigContent extends $pb.GeneratedMessage {
  factory ConfigContent({
    $core.String? content,
  }) {
    final result = ConfigContent._();
    if (content != null) result.content = content;
    return result;
  }

  ConfigContent._();

  factory ConfigContent.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ConfigContent()..mergeFromBuffer(data, registry);
  factory ConfigContent.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ConfigContent()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ConfigContent',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: ConfigContent.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'content')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ConfigContent clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ConfigContent copyWith(void Function(ConfigContent) updates) =>
      super.copyWith((message) => updates(message as ConfigContent))
          as ConfigContent;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ConfigContent() / ConfigContent.new instead')
  static ConfigContent create() => ConfigContent._();
  static $pb.GeneratedMessage $_createMessage() => ConfigContent._();
  @$core.override
  ConfigContent createEmptyInstance() => ConfigContent._();
  @$core.pragma('dart2js:noInline')
  static ConfigContent getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ConfigContent>(
          ConfigContent.$_createMessage);
  static ConfigContent? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get content => $_getSZ(0);
  @$pb.TagNumber(1)
  set content($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasContent() => $_has(0);
  @$pb.TagNumber(1)
  void clearContent() => $_clearField(1);
}

class ProfileContent extends $pb.GeneratedMessage {
  factory ProfileContent({
    ProfileContent_Type? type,
    $core.String? name,
    $core.String? config,
    $core.String? remotePath,
    $core.bool? autoUpdate,
    $core.int? autoUpdateInterval,
    $fixnum.Int64? lastUpdated,
  }) {
    final result = ProfileContent._();
    if (type != null) result.type = type;
    if (name != null) result.name = name;
    if (config != null) result.config = config;
    if (remotePath != null) result.remotePath = remotePath;
    if (autoUpdate != null) result.autoUpdate = autoUpdate;
    if (autoUpdateInterval != null)
      result.autoUpdateInterval = autoUpdateInterval;
    if (lastUpdated != null) result.lastUpdated = lastUpdated;
    return result;
  }

  ProfileContent._();

  factory ProfileContent.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileContent()..mergeFromBuffer(data, registry);
  factory ProfileContent.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileContent()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ProfileContent',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: ProfileContent.$_createMessage)
    ..aE<ProfileContent_Type>(1, _omitFieldNames ? '' : 'type',
        enumValues: ProfileContent_Type.values)
    ..aOS(2, _omitFieldNames ? '' : 'name')
    ..aOS(3, _omitFieldNames ? '' : 'config')
    ..aOS(4, _omitFieldNames ? '' : 'remotePath')
    ..aOB(5, _omitFieldNames ? '' : 'autoUpdate')
    ..aI(6, _omitFieldNames ? '' : 'autoUpdateInterval')
    ..aInt64(7, _omitFieldNames ? '' : 'lastUpdated')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileContent clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileContent copyWith(void Function(ProfileContent) updates) =>
      super.copyWith((message) => updates(message as ProfileContent))
          as ProfileContent;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ProfileContent() / ProfileContent.new instead')
  static ProfileContent create() => ProfileContent._();
  static $pb.GeneratedMessage $_createMessage() => ProfileContent._();
  @$core.override
  ProfileContent createEmptyInstance() => ProfileContent._();
  @$core.pragma('dart2js:noInline')
  static ProfileContent getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ProfileContent>(
          ProfileContent.$_createMessage);
  static ProfileContent? _defaultInstance;

  @$pb.TagNumber(1)
  ProfileContent_Type get type => $_getN(0);
  @$pb.TagNumber(1)
  set type(ProfileContent_Type value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasType() => $_has(0);
  @$pb.TagNumber(1)
  void clearType() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get name => $_getSZ(1);
  @$pb.TagNumber(2)
  set name($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasName() => $_has(1);
  @$pb.TagNumber(2)
  void clearName() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get config => $_getSZ(2);
  @$pb.TagNumber(3)
  set config($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasConfig() => $_has(2);
  @$pb.TagNumber(3)
  void clearConfig() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get remotePath => $_getSZ(3);
  @$pb.TagNumber(4)
  set remotePath($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasRemotePath() => $_has(3);
  @$pb.TagNumber(4)
  void clearRemotePath() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.bool get autoUpdate => $_getBF(4);
  @$pb.TagNumber(5)
  set autoUpdate($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasAutoUpdate() => $_has(4);
  @$pb.TagNumber(5)
  void clearAutoUpdate() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.int get autoUpdateInterval => $_getIZ(5);
  @$pb.TagNumber(6)
  set autoUpdateInterval($core.int value) => $_setSignedInt32(5, value);
  @$pb.TagNumber(6)
  $core.bool hasAutoUpdateInterval() => $_has(5);
  @$pb.TagNumber(6)
  void clearAutoUpdateInterval() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get lastUpdated => $_getI64(6);
  @$pb.TagNumber(7)
  set lastUpdated($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasLastUpdated() => $_has(6);
  @$pb.TagNumber(7)
  void clearLastUpdated() => $_clearField(7);
}

class ProfileData extends $pb.GeneratedMessage {
  factory ProfileData({
    $core.List<$core.int>? data,
  }) {
    final result = ProfileData._();
    if (data != null) result.data = data;
    return result;
  }

  ProfileData._();

  factory ProfileData.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileData()..mergeFromBuffer(data, registry);
  factory ProfileData.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileData()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ProfileData',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: ProfileData.$_createMessage)
    ..a<$core.List<$core.int>>(
        1, _omitFieldNames ? '' : 'data', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileData clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileData copyWith(void Function(ProfileData) updates) =>
      super.copyWith((message) => updates(message as ProfileData))
          as ProfileData;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ProfileData() / ProfileData.new instead')
  static ProfileData create() => ProfileData._();
  static $pb.GeneratedMessage $_createMessage() => ProfileData._();
  @$core.override
  ProfileData createEmptyInstance() => ProfileData._();
  @$core.pragma('dart2js:noInline')
  static ProfileData getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ProfileData>(
          ProfileData.$_createMessage);
  static ProfileData? _defaultInstance;

  @$pb.TagNumber(1)
  $core.List<$core.int> get data => $_getN(0);
  @$pb.TagNumber(1)
  set data($core.List<$core.int> value) => $_setBytes(0, value);
  @$pb.TagNumber(1)
  $core.bool hasData() => $_has(0);
  @$pb.TagNumber(1)
  void clearData() => $_clearField(1);
}

class WorkingDirectoryInfo extends $pb.GeneratedMessage {
  factory WorkingDirectoryInfo({
    $core.String? path,
    $fixnum.Int64? size,
  }) {
    final result = WorkingDirectoryInfo._();
    if (path != null) result.path = path;
    if (size != null) result.size = size;
    return result;
  }

  WorkingDirectoryInfo._();

  factory WorkingDirectoryInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WorkingDirectoryInfo()..mergeFromBuffer(data, registry);
  factory WorkingDirectoryInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WorkingDirectoryInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'WorkingDirectoryInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: WorkingDirectoryInfo.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'path')
    ..aInt64(2, _omitFieldNames ? '' : 'size')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WorkingDirectoryInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WorkingDirectoryInfo copyWith(void Function(WorkingDirectoryInfo) updates) =>
      super.copyWith((message) => updates(message as WorkingDirectoryInfo))
          as WorkingDirectoryInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use WorkingDirectoryInfo() / WorkingDirectoryInfo.new instead')
  static WorkingDirectoryInfo create() => WorkingDirectoryInfo._();
  static $pb.GeneratedMessage $_createMessage() => WorkingDirectoryInfo._();
  @$core.override
  WorkingDirectoryInfo createEmptyInstance() => WorkingDirectoryInfo._();
  @$core.pragma('dart2js:noInline')
  static WorkingDirectoryInfo getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<WorkingDirectoryInfo>(
          WorkingDirectoryInfo.$_createMessage);
  static WorkingDirectoryInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get path => $_getSZ(0);
  @$pb.TagNumber(1)
  set path($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasPath() => $_has(0);
  @$pb.TagNumber(1)
  void clearPath() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get size => $_getI64(1);
  @$pb.TagNumber(2)
  set size($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSize() => $_has(1);
  @$pb.TagNumber(2)
  void clearSize() => $_clearField(2);
}

class CrashReportList extends $pb.GeneratedMessage {
  factory CrashReportList({
    $core.Iterable<CrashReportEntry>? reports,
  }) {
    final result = CrashReportList._();
    if (reports != null) result.reports.addAll(reports);
    return result;
  }

  CrashReportList._();

  factory CrashReportList.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportList()..mergeFromBuffer(data, registry);
  factory CrashReportList.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportList()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CrashReportList',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: CrashReportList.$_createMessage)
    ..pPM<CrashReportEntry>(1, _omitFieldNames ? '' : 'reports',
        subBuilder: CrashReportEntry.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportList clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportList copyWith(void Function(CrashReportList) updates) =>
      super.copyWith((message) => updates(message as CrashReportList))
          as CrashReportList;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CrashReportList() / CrashReportList.new instead')
  static CrashReportList create() => CrashReportList._();
  static $pb.GeneratedMessage $_createMessage() => CrashReportList._();
  @$core.override
  CrashReportList createEmptyInstance() => CrashReportList._();
  @$core.pragma('dart2js:noInline')
  static CrashReportList getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<CrashReportList>(
          CrashReportList.$_createMessage);
  static CrashReportList? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<CrashReportEntry> get reports => $_getList(0);
}

class CrashReportEntry extends $pb.GeneratedMessage {
  factory CrashReportEntry({
    $core.String? name,
    $fixnum.Int64? crashedAt,
    $core.bool? isRead,
  }) {
    final result = CrashReportEntry._();
    if (name != null) result.name = name;
    if (crashedAt != null) result.crashedAt = crashedAt;
    if (isRead != null) result.isRead = isRead;
    return result;
  }

  CrashReportEntry._();

  factory CrashReportEntry.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportEntry()..mergeFromBuffer(data, registry);
  factory CrashReportEntry.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportEntry()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CrashReportEntry',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: CrashReportEntry.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aInt64(2, _omitFieldNames ? '' : 'crashedAt')
    ..aOB(3, _omitFieldNames ? '' : 'isRead')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportEntry clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportEntry copyWith(void Function(CrashReportEntry) updates) =>
      super.copyWith((message) => updates(message as CrashReportEntry))
          as CrashReportEntry;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CrashReportEntry() / CrashReportEntry.new instead')
  static CrashReportEntry create() => CrashReportEntry._();
  static $pb.GeneratedMessage $_createMessage() => CrashReportEntry._();
  @$core.override
  CrashReportEntry createEmptyInstance() => CrashReportEntry._();
  @$core.pragma('dart2js:noInline')
  static CrashReportEntry getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<CrashReportEntry>(
          CrashReportEntry.$_createMessage);
  static CrashReportEntry? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get crashedAt => $_getI64(1);
  @$pb.TagNumber(2)
  set crashedAt($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasCrashedAt() => $_has(1);
  @$pb.TagNumber(2)
  void clearCrashedAt() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get isRead => $_getBF(2);
  @$pb.TagNumber(3)
  set isRead($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasIsRead() => $_has(2);
  @$pb.TagNumber(3)
  void clearIsRead() => $_clearField(3);
}

class CrashReportRequest extends $pb.GeneratedMessage {
  factory CrashReportRequest({
    $core.String? name,
  }) {
    final result = CrashReportRequest._();
    if (name != null) result.name = name;
    return result;
  }

  CrashReportRequest._();

  factory CrashReportRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportRequest()..mergeFromBuffer(data, registry);
  factory CrashReportRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CrashReportRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: CrashReportRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportRequest copyWith(void Function(CrashReportRequest) updates) =>
      super.copyWith((message) => updates(message as CrashReportRequest))
          as CrashReportRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CrashReportRequest() / CrashReportRequest.new instead')
  static CrashReportRequest create() => CrashReportRequest._();
  static $pb.GeneratedMessage $_createMessage() => CrashReportRequest._();
  @$core.override
  CrashReportRequest createEmptyInstance() => CrashReportRequest._();
  @$core.pragma('dart2js:noInline')
  static CrashReportRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<CrashReportRequest>(
          CrashReportRequest.$_createMessage);
  static CrashReportRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);
}

class CrashReportExportRequest extends $pb.GeneratedMessage {
  factory CrashReportExportRequest({
    $core.String? name,
    $core.bool? withConfiguration,
    $core.bool? withLog,
    $core.bool? encrypt,
  }) {
    final result = CrashReportExportRequest._();
    if (name != null) result.name = name;
    if (withConfiguration != null) result.withConfiguration = withConfiguration;
    if (withLog != null) result.withLog = withLog;
    if (encrypt != null) result.encrypt = encrypt;
    return result;
  }

  CrashReportExportRequest._();

  factory CrashReportExportRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportExportRequest()..mergeFromBuffer(data, registry);
  factory CrashReportExportRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportExportRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CrashReportExportRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: CrashReportExportRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aOB(2, _omitFieldNames ? '' : 'withConfiguration')
    ..aOB(3, _omitFieldNames ? '' : 'withLog')
    ..aOB(4, _omitFieldNames ? '' : 'encrypt')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportExportRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportExportRequest copyWith(
          void Function(CrashReportExportRequest) updates) =>
      super.copyWith((message) => updates(message as CrashReportExportRequest))
          as CrashReportExportRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use CrashReportExportRequest() / CrashReportExportRequest.new instead')
  static CrashReportExportRequest create() => CrashReportExportRequest._();
  static $pb.GeneratedMessage $_createMessage() => CrashReportExportRequest._();
  @$core.override
  CrashReportExportRequest createEmptyInstance() =>
      CrashReportExportRequest._();
  @$core.pragma('dart2js:noInline')
  static CrashReportExportRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<CrashReportExportRequest>(
          CrashReportExportRequest.$_createMessage);
  static CrashReportExportRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get withConfiguration => $_getBF(1);
  @$pb.TagNumber(2)
  set withConfiguration($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasWithConfiguration() => $_has(1);
  @$pb.TagNumber(2)
  void clearWithConfiguration() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get withLog => $_getBF(2);
  @$pb.TagNumber(3)
  set withLog($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasWithLog() => $_has(2);
  @$pb.TagNumber(3)
  void clearWithLog() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.bool get encrypt => $_getBF(3);
  @$pb.TagNumber(4)
  set encrypt($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasEncrypt() => $_has(3);
  @$pb.TagNumber(4)
  void clearEncrypt() => $_clearField(4);
}

class CrashReportContent extends $pb.GeneratedMessage {
  factory CrashReportContent({
    $core.Iterable<CrashReportFile>? files,
  }) {
    final result = CrashReportContent._();
    if (files != null) result.files.addAll(files);
    return result;
  }

  CrashReportContent._();

  factory CrashReportContent.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportContent()..mergeFromBuffer(data, registry);
  factory CrashReportContent.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportContent()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CrashReportContent',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: CrashReportContent.$_createMessage)
    ..pPM<CrashReportFile>(1, _omitFieldNames ? '' : 'files',
        subBuilder: CrashReportFile.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportContent clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportContent copyWith(void Function(CrashReportContent) updates) =>
      super.copyWith((message) => updates(message as CrashReportContent))
          as CrashReportContent;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CrashReportContent() / CrashReportContent.new instead')
  static CrashReportContent create() => CrashReportContent._();
  static $pb.GeneratedMessage $_createMessage() => CrashReportContent._();
  @$core.override
  CrashReportContent createEmptyInstance() => CrashReportContent._();
  @$core.pragma('dart2js:noInline')
  static CrashReportContent getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<CrashReportContent>(
          CrashReportContent.$_createMessage);
  static CrashReportContent? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<CrashReportFile> get files => $_getList(0);
}

class CrashReportFile extends $pb.GeneratedMessage {
  factory CrashReportFile({
    $core.String? name,
    $core.String? content,
  }) {
    final result = CrashReportFile._();
    if (name != null) result.name = name;
    if (content != null) result.content = content;
    return result;
  }

  CrashReportFile._();

  factory CrashReportFile.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportFile()..mergeFromBuffer(data, registry);
  factory CrashReportFile.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportFile()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CrashReportFile',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: CrashReportFile.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aOS(2, _omitFieldNames ? '' : 'content')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportFile clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportFile copyWith(void Function(CrashReportFile) updates) =>
      super.copyWith((message) => updates(message as CrashReportFile))
          as CrashReportFile;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CrashReportFile() / CrashReportFile.new instead')
  static CrashReportFile create() => CrashReportFile._();
  static $pb.GeneratedMessage $_createMessage() => CrashReportFile._();
  @$core.override
  CrashReportFile createEmptyInstance() => CrashReportFile._();
  @$core.pragma('dart2js:noInline')
  static CrashReportFile getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<CrashReportFile>(
          CrashReportFile.$_createMessage);
  static CrashReportFile? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get content => $_getSZ(1);
  @$pb.TagNumber(2)
  set content($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasContent() => $_has(1);
  @$pb.TagNumber(2)
  void clearContent() => $_clearField(2);
}

class CrashReportArchive extends $pb.GeneratedMessage {
  factory CrashReportArchive({
    $core.String? fileName,
    $core.List<$core.int>? data,
  }) {
    final result = CrashReportArchive._();
    if (fileName != null) result.fileName = fileName;
    if (data != null) result.data = data;
    return result;
  }

  CrashReportArchive._();

  factory CrashReportArchive.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportArchive()..mergeFromBuffer(data, registry);
  factory CrashReportArchive.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CrashReportArchive()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CrashReportArchive',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: CrashReportArchive.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'fileName')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'data', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportArchive clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CrashReportArchive copyWith(void Function(CrashReportArchive) updates) =>
      super.copyWith((message) => updates(message as CrashReportArchive))
          as CrashReportArchive;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use CrashReportArchive() / CrashReportArchive.new instead')
  static CrashReportArchive create() => CrashReportArchive._();
  static $pb.GeneratedMessage $_createMessage() => CrashReportArchive._();
  @$core.override
  CrashReportArchive createEmptyInstance() => CrashReportArchive._();
  @$core.pragma('dart2js:noInline')
  static CrashReportArchive getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<CrashReportArchive>(
          CrashReportArchive.$_createMessage);
  static CrashReportArchive? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get fileName => $_getSZ(0);
  @$pb.TagNumber(1)
  set fileName($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasFileName() => $_has(0);
  @$pb.TagNumber(1)
  void clearFileName() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get data => $_getN(1);
  @$pb.TagNumber(2)
  set data($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasData() => $_has(1);
  @$pb.TagNumber(2)
  void clearData() => $_clearField(2);
}

class OOMReportList extends $pb.GeneratedMessage {
  factory OOMReportList({
    $core.Iterable<OOMReportEntry>? reports,
  }) {
    final result = OOMReportList._();
    if (reports != null) result.reports.addAll(reports);
    return result;
  }

  OOMReportList._();

  factory OOMReportList.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportList()..mergeFromBuffer(data, registry);
  factory OOMReportList.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportList()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OOMReportList',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: OOMReportList.$_createMessage)
    ..pPM<OOMReportEntry>(1, _omitFieldNames ? '' : 'reports',
        subBuilder: OOMReportEntry.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportList clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportList copyWith(void Function(OOMReportList) updates) =>
      super.copyWith((message) => updates(message as OOMReportList))
          as OOMReportList;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OOMReportList() / OOMReportList.new instead')
  static OOMReportList create() => OOMReportList._();
  static $pb.GeneratedMessage $_createMessage() => OOMReportList._();
  @$core.override
  OOMReportList createEmptyInstance() => OOMReportList._();
  @$core.pragma('dart2js:noInline')
  static OOMReportList getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OOMReportList>(
          OOMReportList.$_createMessage);
  static OOMReportList? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<OOMReportEntry> get reports => $_getList(0);
}

class OOMReportEntry extends $pb.GeneratedMessage {
  factory OOMReportEntry({
    $core.String? name,
    $fixnum.Int64? recordedAt,
    $core.bool? isRead,
  }) {
    final result = OOMReportEntry._();
    if (name != null) result.name = name;
    if (recordedAt != null) result.recordedAt = recordedAt;
    if (isRead != null) result.isRead = isRead;
    return result;
  }

  OOMReportEntry._();

  factory OOMReportEntry.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportEntry()..mergeFromBuffer(data, registry);
  factory OOMReportEntry.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportEntry()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OOMReportEntry',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: OOMReportEntry.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aInt64(2, _omitFieldNames ? '' : 'recordedAt')
    ..aOB(3, _omitFieldNames ? '' : 'isRead')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportEntry clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportEntry copyWith(void Function(OOMReportEntry) updates) =>
      super.copyWith((message) => updates(message as OOMReportEntry))
          as OOMReportEntry;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OOMReportEntry() / OOMReportEntry.new instead')
  static OOMReportEntry create() => OOMReportEntry._();
  static $pb.GeneratedMessage $_createMessage() => OOMReportEntry._();
  @$core.override
  OOMReportEntry createEmptyInstance() => OOMReportEntry._();
  @$core.pragma('dart2js:noInline')
  static OOMReportEntry getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OOMReportEntry>(
          OOMReportEntry.$_createMessage);
  static OOMReportEntry? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get recordedAt => $_getI64(1);
  @$pb.TagNumber(2)
  set recordedAt($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasRecordedAt() => $_has(1);
  @$pb.TagNumber(2)
  void clearRecordedAt() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get isRead => $_getBF(2);
  @$pb.TagNumber(3)
  set isRead($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasIsRead() => $_has(2);
  @$pb.TagNumber(3)
  void clearIsRead() => $_clearField(3);
}

class OOMReportRequest extends $pb.GeneratedMessage {
  factory OOMReportRequest({
    $core.String? name,
  }) {
    final result = OOMReportRequest._();
    if (name != null) result.name = name;
    return result;
  }

  OOMReportRequest._();

  factory OOMReportRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportRequest()..mergeFromBuffer(data, registry);
  factory OOMReportRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OOMReportRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: OOMReportRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportRequest copyWith(void Function(OOMReportRequest) updates) =>
      super.copyWith((message) => updates(message as OOMReportRequest))
          as OOMReportRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OOMReportRequest() / OOMReportRequest.new instead')
  static OOMReportRequest create() => OOMReportRequest._();
  static $pb.GeneratedMessage $_createMessage() => OOMReportRequest._();
  @$core.override
  OOMReportRequest createEmptyInstance() => OOMReportRequest._();
  @$core.pragma('dart2js:noInline')
  static OOMReportRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OOMReportRequest>(
          OOMReportRequest.$_createMessage);
  static OOMReportRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);
}

class OOMReportExportRequest extends $pb.GeneratedMessage {
  factory OOMReportExportRequest({
    $core.String? name,
    $core.bool? withConfiguration,
    $core.bool? withLog,
    $core.bool? encrypt,
  }) {
    final result = OOMReportExportRequest._();
    if (name != null) result.name = name;
    if (withConfiguration != null) result.withConfiguration = withConfiguration;
    if (withLog != null) result.withLog = withLog;
    if (encrypt != null) result.encrypt = encrypt;
    return result;
  }

  OOMReportExportRequest._();

  factory OOMReportExportRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportExportRequest()..mergeFromBuffer(data, registry);
  factory OOMReportExportRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportExportRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OOMReportExportRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: OOMReportExportRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aOB(2, _omitFieldNames ? '' : 'withConfiguration')
    ..aOB(3, _omitFieldNames ? '' : 'withLog')
    ..aOB(4, _omitFieldNames ? '' : 'encrypt')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportExportRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportExportRequest copyWith(
          void Function(OOMReportExportRequest) updates) =>
      super.copyWith((message) => updates(message as OOMReportExportRequest))
          as OOMReportExportRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OOMReportExportRequest() / OOMReportExportRequest.new instead')
  static OOMReportExportRequest create() => OOMReportExportRequest._();
  static $pb.GeneratedMessage $_createMessage() => OOMReportExportRequest._();
  @$core.override
  OOMReportExportRequest createEmptyInstance() => OOMReportExportRequest._();
  @$core.pragma('dart2js:noInline')
  static OOMReportExportRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OOMReportExportRequest>(
          OOMReportExportRequest.$_createMessage);
  static OOMReportExportRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get withConfiguration => $_getBF(1);
  @$pb.TagNumber(2)
  set withConfiguration($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasWithConfiguration() => $_has(1);
  @$pb.TagNumber(2)
  void clearWithConfiguration() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get withLog => $_getBF(2);
  @$pb.TagNumber(3)
  set withLog($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasWithLog() => $_has(2);
  @$pb.TagNumber(3)
  void clearWithLog() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.bool get encrypt => $_getBF(3);
  @$pb.TagNumber(4)
  set encrypt($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasEncrypt() => $_has(3);
  @$pb.TagNumber(4)
  void clearEncrypt() => $_clearField(4);
}

class OOMReportContent extends $pb.GeneratedMessage {
  factory OOMReportContent({
    $core.Iterable<OOMReportFile>? files,
  }) {
    final result = OOMReportContent._();
    if (files != null) result.files.addAll(files);
    return result;
  }

  OOMReportContent._();

  factory OOMReportContent.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportContent()..mergeFromBuffer(data, registry);
  factory OOMReportContent.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportContent()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OOMReportContent',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: OOMReportContent.$_createMessage)
    ..pPM<OOMReportFile>(1, _omitFieldNames ? '' : 'files',
        subBuilder: OOMReportFile.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportContent clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportContent copyWith(void Function(OOMReportContent) updates) =>
      super.copyWith((message) => updates(message as OOMReportContent))
          as OOMReportContent;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OOMReportContent() / OOMReportContent.new instead')
  static OOMReportContent create() => OOMReportContent._();
  static $pb.GeneratedMessage $_createMessage() => OOMReportContent._();
  @$core.override
  OOMReportContent createEmptyInstance() => OOMReportContent._();
  @$core.pragma('dart2js:noInline')
  static OOMReportContent getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OOMReportContent>(
          OOMReportContent.$_createMessage);
  static OOMReportContent? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<OOMReportFile> get files => $_getList(0);
}

class OOMReportFile extends $pb.GeneratedMessage {
  factory OOMReportFile({
    $core.String? name,
    $core.List<$core.int>? content,
    $core.bool? isProfile,
  }) {
    final result = OOMReportFile._();
    if (name != null) result.name = name;
    if (content != null) result.content = content;
    if (isProfile != null) result.isProfile = isProfile;
    return result;
  }

  OOMReportFile._();

  factory OOMReportFile.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportFile()..mergeFromBuffer(data, registry);
  factory OOMReportFile.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OOMReportFile()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OOMReportFile',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: OOMReportFile.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'content', $pb.PbFieldType.OY)
    ..aOB(3, _omitFieldNames ? '' : 'isProfile')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportFile clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OOMReportFile copyWith(void Function(OOMReportFile) updates) =>
      super.copyWith((message) => updates(message as OOMReportFile))
          as OOMReportFile;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OOMReportFile() / OOMReportFile.new instead')
  static OOMReportFile create() => OOMReportFile._();
  static $pb.GeneratedMessage $_createMessage() => OOMReportFile._();
  @$core.override
  OOMReportFile createEmptyInstance() => OOMReportFile._();
  @$core.pragma('dart2js:noInline')
  static OOMReportFile getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OOMReportFile>(
          OOMReportFile.$_createMessage);
  static OOMReportFile? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get content => $_getN(1);
  @$pb.TagNumber(2)
  set content($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasContent() => $_has(1);
  @$pb.TagNumber(2)
  void clearContent() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get isProfile => $_getBF(2);
  @$pb.TagNumber(3)
  set isProfile($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasIsProfile() => $_has(2);
  @$pb.TagNumber(3)
  void clearIsProfile() => $_clearField(3);
}

class SecuritySettings extends $pb.GeneratedMessage {
  factory SecuritySettings({
    $core.bool? available,
    $core.bool? insecureModeEnabled,
  }) {
    final result = SecuritySettings._();
    if (available != null) result.available = available;
    if (insecureModeEnabled != null)
      result.insecureModeEnabled = insecureModeEnabled;
    return result;
  }

  SecuritySettings._();

  factory SecuritySettings.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SecuritySettings()..mergeFromBuffer(data, registry);
  factory SecuritySettings.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SecuritySettings()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SecuritySettings',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: SecuritySettings.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'available')
    ..aOB(2, _omitFieldNames ? '' : 'insecureModeEnabled')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SecuritySettings clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SecuritySettings copyWith(void Function(SecuritySettings) updates) =>
      super.copyWith((message) => updates(message as SecuritySettings))
          as SecuritySettings;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use SecuritySettings() / SecuritySettings.new instead')
  static SecuritySettings create() => SecuritySettings._();
  static $pb.GeneratedMessage $_createMessage() => SecuritySettings._();
  @$core.override
  SecuritySettings createEmptyInstance() => SecuritySettings._();
  @$core.pragma('dart2js:noInline')
  static SecuritySettings getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<SecuritySettings>(
          SecuritySettings.$_createMessage);
  static SecuritySettings? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get available => $_getBF(0);
  @$pb.TagNumber(1)
  set available($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasAvailable() => $_has(0);
  @$pb.TagNumber(1)
  void clearAvailable() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get insecureModeEnabled => $_getBF(1);
  @$pb.TagNumber(2)
  set insecureModeEnabled($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasInsecureModeEnabled() => $_has(1);
  @$pb.TagNumber(2)
  void clearInsecureModeEnabled() => $_clearField(2);
}

class SetInsecureModeEnabledRequest extends $pb.GeneratedMessage {
  factory SetInsecureModeEnabledRequest({
    $core.bool? enabled,
  }) {
    final result = SetInsecureModeEnabledRequest._();
    if (enabled != null) result.enabled = enabled;
    return result;
  }

  SetInsecureModeEnabledRequest._();

  factory SetInsecureModeEnabledRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetInsecureModeEnabledRequest()..mergeFromBuffer(data, registry);
  factory SetInsecureModeEnabledRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetInsecureModeEnabledRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SetInsecureModeEnabledRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: SetInsecureModeEnabledRequest.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'enabled')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetInsecureModeEnabledRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetInsecureModeEnabledRequest copyWith(
          void Function(SetInsecureModeEnabledRequest) updates) =>
      super.copyWith(
              (message) => updates(message as SetInsecureModeEnabledRequest))
          as SetInsecureModeEnabledRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SetInsecureModeEnabledRequest() / SetInsecureModeEnabledRequest.new instead')
  static SetInsecureModeEnabledRequest create() =>
      SetInsecureModeEnabledRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      SetInsecureModeEnabledRequest._();
  @$core.override
  SetInsecureModeEnabledRequest createEmptyInstance() =>
      SetInsecureModeEnabledRequest._();
  @$core.pragma('dart2js:noInline')
  static SetInsecureModeEnabledRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SetInsecureModeEnabledRequest>(
          SetInsecureModeEnabledRequest.$_createMessage);
  static SetInsecureModeEnabledRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get enabled => $_getBF(0);
  @$pb.TagNumber(1)
  set enabled($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEnabled() => $_has(0);
  @$pb.TagNumber(1)
  void clearEnabled() => $_clearField(1);
}

class SetLocaleRequest extends $pb.GeneratedMessage {
  factory SetLocaleRequest({
    $core.String? locale,
  }) {
    final result = SetLocaleRequest._();
    if (locale != null) result.locale = locale;
    return result;
  }

  SetLocaleRequest._();

  factory SetLocaleRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetLocaleRequest()..mergeFromBuffer(data, registry);
  factory SetLocaleRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetLocaleRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SetLocaleRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: SetLocaleRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'locale')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetLocaleRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetLocaleRequest copyWith(void Function(SetLocaleRequest) updates) =>
      super.copyWith((message) => updates(message as SetLocaleRequest))
          as SetLocaleRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use SetLocaleRequest() / SetLocaleRequest.new instead')
  static SetLocaleRequest create() => SetLocaleRequest._();
  static $pb.GeneratedMessage $_createMessage() => SetLocaleRequest._();
  @$core.override
  SetLocaleRequest createEmptyInstance() => SetLocaleRequest._();
  @$core.pragma('dart2js:noInline')
  static SetLocaleRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<SetLocaleRequest>(
          SetLocaleRequest.$_createMessage);
  static SetLocaleRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get locale => $_getSZ(0);
  @$pb.TagNumber(1)
  set locale($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasLocale() => $_has(0);
  @$pb.TagNumber(1)
  void clearLocale() => $_clearField(1);
}

class InstallUpdateRequest extends $pb.GeneratedMessage {
  factory InstallUpdateRequest({
    $core.String? installerPath,
  }) {
    final result = InstallUpdateRequest._();
    if (installerPath != null) result.installerPath = installerPath;
    return result;
  }

  InstallUpdateRequest._();

  factory InstallUpdateRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      InstallUpdateRequest()..mergeFromBuffer(data, registry);
  factory InstallUpdateRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      InstallUpdateRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'InstallUpdateRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: InstallUpdateRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'installerPath')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  InstallUpdateRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  InstallUpdateRequest copyWith(void Function(InstallUpdateRequest) updates) =>
      super.copyWith((message) => updates(message as InstallUpdateRequest))
          as InstallUpdateRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use InstallUpdateRequest() / InstallUpdateRequest.new instead')
  static InstallUpdateRequest create() => InstallUpdateRequest._();
  static $pb.GeneratedMessage $_createMessage() => InstallUpdateRequest._();
  @$core.override
  InstallUpdateRequest createEmptyInstance() => InstallUpdateRequest._();
  @$core.pragma('dart2js:noInline')
  static InstallUpdateRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<InstallUpdateRequest>(
          InstallUpdateRequest.$_createMessage);
  static InstallUpdateRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get installerPath => $_getSZ(0);
  @$pb.TagNumber(1)
  set installerPath($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasInstallerPath() => $_has(0);
  @$pb.TagNumber(1)
  void clearInstallerPath() => $_clearField(1);
}

class InstallUpdateResponse extends $pb.GeneratedMessage {
  factory InstallUpdateResponse({
    InstallUpdateResult? result,
  }) {
    final result$ = InstallUpdateResponse._();
    if (result != null) result$.result = result;
    return result$;
  }

  InstallUpdateResponse._();

  factory InstallUpdateResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      InstallUpdateResponse()..mergeFromBuffer(data, registry);
  factory InstallUpdateResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      InstallUpdateResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'InstallUpdateResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'desktop'),
      createEmptyInstance: InstallUpdateResponse.$_createMessage)
    ..aE<InstallUpdateResult>(1, _omitFieldNames ? '' : 'result',
        enumValues: InstallUpdateResult.values)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  InstallUpdateResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  InstallUpdateResponse copyWith(
          void Function(InstallUpdateResponse) updates) =>
      super.copyWith((message) => updates(message as InstallUpdateResponse))
          as InstallUpdateResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use InstallUpdateResponse() / InstallUpdateResponse.new instead')
  static InstallUpdateResponse create() => InstallUpdateResponse._();
  static $pb.GeneratedMessage $_createMessage() => InstallUpdateResponse._();
  @$core.override
  InstallUpdateResponse createEmptyInstance() => InstallUpdateResponse._();
  @$core.pragma('dart2js:noInline')
  static InstallUpdateResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<InstallUpdateResponse>(
          InstallUpdateResponse.$_createMessage);
  static InstallUpdateResponse? _defaultInstance;

  @$pb.TagNumber(1)
  InstallUpdateResult get result => $_getN(0);
  @$pb.TagNumber(1)
  set result(InstallUpdateResult value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasResult() => $_has(0);
  @$pb.TagNumber(1)
  void clearResult() => $_clearField(1);
}

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
