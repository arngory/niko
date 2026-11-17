// This is a generated file - do not edit.
//
// Generated from daemon/managed_service.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:core' as $core;

import 'package:protobuf/protobuf.dart' as $pb;

import 'managed_service.pbenum.dart';

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

export 'managed_service.pbenum.dart';

class SystemProxyStatus extends $pb.GeneratedMessage {
  factory SystemProxyStatus({
    $core.bool? available,
    $core.bool? enabled,
  }) {
    final result = SystemProxyStatus._();
    if (available != null) result.available = available;
    if (enabled != null) result.enabled = enabled;
    return result;
  }

  SystemProxyStatus._();

  factory SystemProxyStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SystemProxyStatus()..mergeFromBuffer(data, registry);
  factory SystemProxyStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SystemProxyStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SystemProxyStatus',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: SystemProxyStatus.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'available')
    ..aOB(2, _omitFieldNames ? '' : 'enabled')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SystemProxyStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SystemProxyStatus copyWith(void Function(SystemProxyStatus) updates) =>
      super.copyWith((message) => updates(message as SystemProxyStatus))
          as SystemProxyStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use SystemProxyStatus() / SystemProxyStatus.new instead')
  static SystemProxyStatus create() => SystemProxyStatus._();
  static $pb.GeneratedMessage $_createMessage() => SystemProxyStatus._();
  @$core.override
  SystemProxyStatus createEmptyInstance() => SystemProxyStatus._();
  @$core.pragma('dart2js:noInline')
  static SystemProxyStatus getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<SystemProxyStatus>(
          SystemProxyStatus.$_createMessage);
  static SystemProxyStatus? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get available => $_getBF(0);
  @$pb.TagNumber(1)
  set available($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasAvailable() => $_has(0);
  @$pb.TagNumber(1)
  void clearAvailable() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get enabled => $_getBF(1);
  @$pb.TagNumber(2)
  set enabled($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasEnabled() => $_has(1);
  @$pb.TagNumber(2)
  void clearEnabled() => $_clearField(2);
}

class SetSystemProxyEnabledRequest extends $pb.GeneratedMessage {
  factory SetSystemProxyEnabledRequest({
    $core.bool? enabled,
  }) {
    final result = SetSystemProxyEnabledRequest._();
    if (enabled != null) result.enabled = enabled;
    return result;
  }

  SetSystemProxyEnabledRequest._();

  factory SetSystemProxyEnabledRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetSystemProxyEnabledRequest()..mergeFromBuffer(data, registry);
  factory SetSystemProxyEnabledRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetSystemProxyEnabledRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SetSystemProxyEnabledRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: SetSystemProxyEnabledRequest.$_createMessage)
    ..aOB(1, _omitFieldNames ? '' : 'enabled')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetSystemProxyEnabledRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetSystemProxyEnabledRequest copyWith(
          void Function(SetSystemProxyEnabledRequest) updates) =>
      super.copyWith(
              (message) => updates(message as SetSystemProxyEnabledRequest))
          as SetSystemProxyEnabledRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SetSystemProxyEnabledRequest() / SetSystemProxyEnabledRequest.new instead')
  static SetSystemProxyEnabledRequest create() =>
      SetSystemProxyEnabledRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      SetSystemProxyEnabledRequest._();
  @$core.override
  SetSystemProxyEnabledRequest createEmptyInstance() =>
      SetSystemProxyEnabledRequest._();
  @$core.pragma('dart2js:noInline')
  static SetSystemProxyEnabledRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SetSystemProxyEnabledRequest>(
          SetSystemProxyEnabledRequest.$_createMessage);
  static SetSystemProxyEnabledRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.bool get enabled => $_getBF(0);
  @$pb.TagNumber(1)
  set enabled($core.bool value) => $_setBool(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEnabled() => $_has(0);
  @$pb.TagNumber(1)
  void clearEnabled() => $_clearField(1);
}

class DebugCrashRequest extends $pb.GeneratedMessage {
  factory DebugCrashRequest({
    DebugCrashRequest_Type? type,
  }) {
    final result = DebugCrashRequest._();
    if (type != null) result.type = type;
    return result;
  }

  DebugCrashRequest._();

  factory DebugCrashRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DebugCrashRequest()..mergeFromBuffer(data, registry);
  factory DebugCrashRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DebugCrashRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DebugCrashRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: DebugCrashRequest.$_createMessage)
    ..aE<DebugCrashRequest_Type>(1, _omitFieldNames ? '' : 'type',
        enumValues: DebugCrashRequest_Type.values)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DebugCrashRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DebugCrashRequest copyWith(void Function(DebugCrashRequest) updates) =>
      super.copyWith((message) => updates(message as DebugCrashRequest))
          as DebugCrashRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use DebugCrashRequest() / DebugCrashRequest.new instead')
  static DebugCrashRequest create() => DebugCrashRequest._();
  static $pb.GeneratedMessage $_createMessage() => DebugCrashRequest._();
  @$core.override
  DebugCrashRequest createEmptyInstance() => DebugCrashRequest._();
  @$core.pragma('dart2js:noInline')
  static DebugCrashRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<DebugCrashRequest>(
          DebugCrashRequest.$_createMessage);
  static DebugCrashRequest? _defaultInstance;

  @$pb.TagNumber(1)
  DebugCrashRequest_Type get type => $_getN(0);
  @$pb.TagNumber(1)
  set type(DebugCrashRequest_Type value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasType() => $_has(0);
  @$pb.TagNumber(1)
  void clearType() => $_clearField(1);
}

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
