// This is a generated file - do not edit.
//
// Generated from daemon/started_service.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:core' as $core;

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:protobuf/protobuf.dart' as $pb;

import 'started_service.pbenum.dart';

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

export 'started_service.pbenum.dart';

class Version extends $pb.GeneratedMessage {
  factory Version({
    $core.String? version,
    $core.int? apiVersion,
  }) {
    final result = Version._();
    if (version != null) result.version = version;
    if (apiVersion != null) result.apiVersion = apiVersion;
    return result;
  }

  Version._();

  factory Version.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Version()..mergeFromBuffer(data, registry);
  factory Version.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Version()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Version',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: Version.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'version')
    ..aI(2, _omitFieldNames ? '' : 'apiVersion', protoName: 'apiVersion')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Version clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Version copyWith(void Function(Version) updates) =>
      super.copyWith((message) => updates(message as Version)) as Version;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Version() / Version.new instead')
  static Version create() => Version._();
  static $pb.GeneratedMessage $_createMessage() => Version._();
  @$core.override
  Version createEmptyInstance() => Version._();
  @$core.pragma('dart2js:noInline')
  static Version getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Version>(Version.$_createMessage);
  static Version? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get version => $_getSZ(0);
  @$pb.TagNumber(1)
  set version($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasVersion() => $_has(0);
  @$pb.TagNumber(1)
  void clearVersion() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get apiVersion => $_getIZ(1);
  @$pb.TagNumber(2)
  set apiVersion($core.int value) => $_setSignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasApiVersion() => $_has(1);
  @$pb.TagNumber(2)
  void clearApiVersion() => $_clearField(2);
}

class ServiceStatus extends $pb.GeneratedMessage {
  factory ServiceStatus({
    ServiceStatus_Type? status,
    $core.String? errorMessage,
  }) {
    final result = ServiceStatus._();
    if (status != null) result.status = status;
    if (errorMessage != null) result.errorMessage = errorMessage;
    return result;
  }

  ServiceStatus._();

  factory ServiceStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ServiceStatus()..mergeFromBuffer(data, registry);
  factory ServiceStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ServiceStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ServiceStatus',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: ServiceStatus.$_createMessage)
    ..aE<ServiceStatus_Type>(1, _omitFieldNames ? '' : 'status',
        enumValues: ServiceStatus_Type.values)
    ..aOS(2, _omitFieldNames ? '' : 'errorMessage', protoName: 'errorMessage')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ServiceStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ServiceStatus copyWith(void Function(ServiceStatus) updates) =>
      super.copyWith((message) => updates(message as ServiceStatus))
          as ServiceStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ServiceStatus() / ServiceStatus.new instead')
  static ServiceStatus create() => ServiceStatus._();
  static $pb.GeneratedMessage $_createMessage() => ServiceStatus._();
  @$core.override
  ServiceStatus createEmptyInstance() => ServiceStatus._();
  @$core.pragma('dart2js:noInline')
  static ServiceStatus getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ServiceStatus>(
          ServiceStatus.$_createMessage);
  static ServiceStatus? _defaultInstance;

  @$pb.TagNumber(1)
  ServiceStatus_Type get status => $_getN(0);
  @$pb.TagNumber(1)
  set status(ServiceStatus_Type value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasStatus() => $_has(0);
  @$pb.TagNumber(1)
  void clearStatus() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get errorMessage => $_getSZ(1);
  @$pb.TagNumber(2)
  set errorMessage($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasErrorMessage() => $_has(1);
  @$pb.TagNumber(2)
  void clearErrorMessage() => $_clearField(2);
}

class SubscribeStatusRequest extends $pb.GeneratedMessage {
  factory SubscribeStatusRequest({
    $fixnum.Int64? interval,
  }) {
    final result = SubscribeStatusRequest._();
    if (interval != null) result.interval = interval;
    return result;
  }

  SubscribeStatusRequest._();

  factory SubscribeStatusRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SubscribeStatusRequest()..mergeFromBuffer(data, registry);
  factory SubscribeStatusRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SubscribeStatusRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SubscribeStatusRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: SubscribeStatusRequest.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'interval')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubscribeStatusRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubscribeStatusRequest copyWith(
          void Function(SubscribeStatusRequest) updates) =>
      super.copyWith((message) => updates(message as SubscribeStatusRequest))
          as SubscribeStatusRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SubscribeStatusRequest() / SubscribeStatusRequest.new instead')
  static SubscribeStatusRequest create() => SubscribeStatusRequest._();
  static $pb.GeneratedMessage $_createMessage() => SubscribeStatusRequest._();
  @$core.override
  SubscribeStatusRequest createEmptyInstance() => SubscribeStatusRequest._();
  @$core.pragma('dart2js:noInline')
  static SubscribeStatusRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SubscribeStatusRequest>(
          SubscribeStatusRequest.$_createMessage);
  static SubscribeStatusRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get interval => $_getI64(0);
  @$pb.TagNumber(1)
  set interval($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasInterval() => $_has(0);
  @$pb.TagNumber(1)
  void clearInterval() => $_clearField(1);
}

class Log_Message extends $pb.GeneratedMessage {
  factory Log_Message({
    LogLevel? level,
    $core.String? message,
  }) {
    final result = Log_Message._();
    if (level != null) result.level = level;
    if (message != null) result.message = message;
    return result;
  }

  Log_Message._();

  factory Log_Message.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Log_Message()..mergeFromBuffer(data, registry);
  factory Log_Message.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Log_Message()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Log.Message',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: Log_Message.$_createMessage)
    ..aE<LogLevel>(1, _omitFieldNames ? '' : 'level',
        enumValues: LogLevel.values)
    ..aOS(2, _omitFieldNames ? '' : 'message')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Log_Message clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Log_Message copyWith(void Function(Log_Message) updates) =>
      super.copyWith((message) => updates(message as Log_Message))
          as Log_Message;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Log_Message() / Log_Message.new instead')
  static Log_Message create() => Log_Message._();
  static $pb.GeneratedMessage $_createMessage() => Log_Message._();
  @$core.override
  Log_Message createEmptyInstance() => Log_Message._();
  @$core.pragma('dart2js:noInline')
  static Log_Message getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<Log_Message>(
          Log_Message.$_createMessage);
  static Log_Message? _defaultInstance;

  @$pb.TagNumber(1)
  LogLevel get level => $_getN(0);
  @$pb.TagNumber(1)
  set level(LogLevel value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasLevel() => $_has(0);
  @$pb.TagNumber(1)
  void clearLevel() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get message => $_getSZ(1);
  @$pb.TagNumber(2)
  set message($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasMessage() => $_has(1);
  @$pb.TagNumber(2)
  void clearMessage() => $_clearField(2);
}

class Log extends $pb.GeneratedMessage {
  factory Log({
    $core.Iterable<Log_Message>? messages,
    $core.bool? reset,
  }) {
    final result = Log._();
    if (messages != null) result.messages.addAll(messages);
    if (reset != null) result.reset = reset;
    return result;
  }

  Log._();

  factory Log.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Log()..mergeFromBuffer(data, registry);
  factory Log.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Log()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Log',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: Log.$_createMessage)
    ..pPM<Log_Message>(1, _omitFieldNames ? '' : 'messages',
        subBuilder: Log_Message.$_createMessage)
    ..aOB(2, _omitFieldNames ? '' : 'reset')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Log clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Log copyWith(void Function(Log) updates) =>
      super.copyWith((message) => updates(message as Log)) as Log;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Log() / Log.new instead')
  static Log create() => Log._();
  static $pb.GeneratedMessage $_createMessage() => Log._();
  @$core.override
  Log createEmptyInstance() => Log._();
  @$core.pragma('dart2js:noInline')
  static Log getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Log>(Log.$_createMessage);
  static Log? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<Log_Message> get messages => $_getList(0);

  @$pb.TagNumber(2)
  $core.bool get reset => $_getBF(1);
  @$pb.TagNumber(2)
  set reset($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasReset() => $_has(1);
  @$pb.TagNumber(2)
  void clearReset() => $_clearField(2);
}

class DefaultLogLevel extends $pb.GeneratedMessage {
  factory DefaultLogLevel({
    LogLevel? level,
  }) {
    final result = DefaultLogLevel._();
    if (level != null) result.level = level;
    return result;
  }

  DefaultLogLevel._();

  factory DefaultLogLevel.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DefaultLogLevel()..mergeFromBuffer(data, registry);
  factory DefaultLogLevel.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DefaultLogLevel()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DefaultLogLevel',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: DefaultLogLevel.$_createMessage)
    ..aE<LogLevel>(1, _omitFieldNames ? '' : 'level',
        enumValues: LogLevel.values)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DefaultLogLevel clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DefaultLogLevel copyWith(void Function(DefaultLogLevel) updates) =>
      super.copyWith((message) => updates(message as DefaultLogLevel))
          as DefaultLogLevel;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use DefaultLogLevel() / DefaultLogLevel.new instead')
  static DefaultLogLevel create() => DefaultLogLevel._();
  static $pb.GeneratedMessage $_createMessage() => DefaultLogLevel._();
  @$core.override
  DefaultLogLevel createEmptyInstance() => DefaultLogLevel._();
  @$core.pragma('dart2js:noInline')
  static DefaultLogLevel getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<DefaultLogLevel>(
          DefaultLogLevel.$_createMessage);
  static DefaultLogLevel? _defaultInstance;

  @$pb.TagNumber(1)
  LogLevel get level => $_getN(0);
  @$pb.TagNumber(1)
  set level(LogLevel value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasLevel() => $_has(0);
  @$pb.TagNumber(1)
  void clearLevel() => $_clearField(1);
}

class Status extends $pb.GeneratedMessage {
  factory Status({
    $fixnum.Int64? memory,
    $core.int? goroutines,
    $core.int? connectionsIn,
    $core.int? connectionsOut,
    $core.bool? trafficAvailable,
    $fixnum.Int64? uplink,
    $fixnum.Int64? downlink,
    $fixnum.Int64? uplinkTotal,
    $fixnum.Int64? downlinkTotal,
  }) {
    final result = Status._();
    if (memory != null) result.memory = memory;
    if (goroutines != null) result.goroutines = goroutines;
    if (connectionsIn != null) result.connectionsIn = connectionsIn;
    if (connectionsOut != null) result.connectionsOut = connectionsOut;
    if (trafficAvailable != null) result.trafficAvailable = trafficAvailable;
    if (uplink != null) result.uplink = uplink;
    if (downlink != null) result.downlink = downlink;
    if (uplinkTotal != null) result.uplinkTotal = uplinkTotal;
    if (downlinkTotal != null) result.downlinkTotal = downlinkTotal;
    return result;
  }

  Status._();

  factory Status.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Status()..mergeFromBuffer(data, registry);
  factory Status.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Status()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Status',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: Status.$_createMessage)
    ..a<$fixnum.Int64>(1, _omitFieldNames ? '' : 'memory', $pb.PbFieldType.OU6,
        defaultOrMaker: $fixnum.Int64.ZERO)
    ..aI(2, _omitFieldNames ? '' : 'goroutines')
    ..aI(3, _omitFieldNames ? '' : 'connectionsIn', protoName: 'connectionsIn')
    ..aI(4, _omitFieldNames ? '' : 'connectionsOut',
        protoName: 'connectionsOut')
    ..aOB(5, _omitFieldNames ? '' : 'trafficAvailable',
        protoName: 'trafficAvailable')
    ..aInt64(6, _omitFieldNames ? '' : 'uplink')
    ..aInt64(7, _omitFieldNames ? '' : 'downlink')
    ..aInt64(8, _omitFieldNames ? '' : 'uplinkTotal', protoName: 'uplinkTotal')
    ..aInt64(9, _omitFieldNames ? '' : 'downlinkTotal',
        protoName: 'downlinkTotal')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Status clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Status copyWith(void Function(Status) updates) =>
      super.copyWith((message) => updates(message as Status)) as Status;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Status() / Status.new instead')
  static Status create() => Status._();
  static $pb.GeneratedMessage $_createMessage() => Status._();
  @$core.override
  Status createEmptyInstance() => Status._();
  @$core.pragma('dart2js:noInline')
  static Status getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Status>(Status.$_createMessage);
  static Status? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get memory => $_getI64(0);
  @$pb.TagNumber(1)
  set memory($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasMemory() => $_has(0);
  @$pb.TagNumber(1)
  void clearMemory() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get goroutines => $_getIZ(1);
  @$pb.TagNumber(2)
  set goroutines($core.int value) => $_setSignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasGoroutines() => $_has(1);
  @$pb.TagNumber(2)
  void clearGoroutines() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get connectionsIn => $_getIZ(2);
  @$pb.TagNumber(3)
  set connectionsIn($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasConnectionsIn() => $_has(2);
  @$pb.TagNumber(3)
  void clearConnectionsIn() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get connectionsOut => $_getIZ(3);
  @$pb.TagNumber(4)
  set connectionsOut($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasConnectionsOut() => $_has(3);
  @$pb.TagNumber(4)
  void clearConnectionsOut() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.bool get trafficAvailable => $_getBF(4);
  @$pb.TagNumber(5)
  set trafficAvailable($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasTrafficAvailable() => $_has(4);
  @$pb.TagNumber(5)
  void clearTrafficAvailable() => $_clearField(5);

  @$pb.TagNumber(6)
  $fixnum.Int64 get uplink => $_getI64(5);
  @$pb.TagNumber(6)
  set uplink($fixnum.Int64 value) => $_setInt64(5, value);
  @$pb.TagNumber(6)
  $core.bool hasUplink() => $_has(5);
  @$pb.TagNumber(6)
  void clearUplink() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get downlink => $_getI64(6);
  @$pb.TagNumber(7)
  set downlink($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasDownlink() => $_has(6);
  @$pb.TagNumber(7)
  void clearDownlink() => $_clearField(7);

  @$pb.TagNumber(8)
  $fixnum.Int64 get uplinkTotal => $_getI64(7);
  @$pb.TagNumber(8)
  set uplinkTotal($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasUplinkTotal() => $_has(7);
  @$pb.TagNumber(8)
  void clearUplinkTotal() => $_clearField(8);

  @$pb.TagNumber(9)
  $fixnum.Int64 get downlinkTotal => $_getI64(8);
  @$pb.TagNumber(9)
  set downlinkTotal($fixnum.Int64 value) => $_setInt64(8, value);
  @$pb.TagNumber(9)
  $core.bool hasDownlinkTotal() => $_has(8);
  @$pb.TagNumber(9)
  void clearDownlinkTotal() => $_clearField(9);
}

class Groups extends $pb.GeneratedMessage {
  factory Groups({
    $core.Iterable<Group>? group,
  }) {
    final result = Groups._();
    if (group != null) result.group.addAll(group);
    return result;
  }

  Groups._();

  factory Groups.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Groups()..mergeFromBuffer(data, registry);
  factory Groups.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Groups()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Groups',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: Groups.$_createMessage)
    ..pPM<Group>(1, _omitFieldNames ? '' : 'group',
        subBuilder: Group.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Groups clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Groups copyWith(void Function(Groups) updates) =>
      super.copyWith((message) => updates(message as Groups)) as Groups;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Groups() / Groups.new instead')
  static Groups create() => Groups._();
  static $pb.GeneratedMessage $_createMessage() => Groups._();
  @$core.override
  Groups createEmptyInstance() => Groups._();
  @$core.pragma('dart2js:noInline')
  static Groups getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Groups>(Groups.$_createMessage);
  static Groups? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<Group> get group => $_getList(0);
}

class Group extends $pb.GeneratedMessage {
  factory Group({
    $core.String? tag,
    $core.String? type,
    $core.bool? selectable,
    $core.String? selected,
    $core.bool? isExpand,
    $core.Iterable<GroupItem>? items,
  }) {
    final result = Group._();
    if (tag != null) result.tag = tag;
    if (type != null) result.type = type;
    if (selectable != null) result.selectable = selectable;
    if (selected != null) result.selected = selected;
    if (isExpand != null) result.isExpand = isExpand;
    if (items != null) result.items.addAll(items);
    return result;
  }

  Group._();

  factory Group.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Group()..mergeFromBuffer(data, registry);
  factory Group.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Group()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Group',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: Group.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'tag')
    ..aOS(2, _omitFieldNames ? '' : 'type')
    ..aOB(3, _omitFieldNames ? '' : 'selectable')
    ..aOS(4, _omitFieldNames ? '' : 'selected')
    ..aOB(5, _omitFieldNames ? '' : 'isExpand', protoName: 'isExpand')
    ..pPM<GroupItem>(6, _omitFieldNames ? '' : 'items',
        subBuilder: GroupItem.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Group clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Group copyWith(void Function(Group) updates) =>
      super.copyWith((message) => updates(message as Group)) as Group;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Group() / Group.new instead')
  static Group create() => Group._();
  static $pb.GeneratedMessage $_createMessage() => Group._();
  @$core.override
  Group createEmptyInstance() => Group._();
  @$core.pragma('dart2js:noInline')
  static Group getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Group>(Group.$_createMessage);
  static Group? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get tag => $_getSZ(0);
  @$pb.TagNumber(1)
  set tag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get type => $_getSZ(1);
  @$pb.TagNumber(2)
  set type($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasType() => $_has(1);
  @$pb.TagNumber(2)
  void clearType() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get selectable => $_getBF(2);
  @$pb.TagNumber(3)
  set selectable($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasSelectable() => $_has(2);
  @$pb.TagNumber(3)
  void clearSelectable() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get selected => $_getSZ(3);
  @$pb.TagNumber(4)
  set selected($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasSelected() => $_has(3);
  @$pb.TagNumber(4)
  void clearSelected() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.bool get isExpand => $_getBF(4);
  @$pb.TagNumber(5)
  set isExpand($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasIsExpand() => $_has(4);
  @$pb.TagNumber(5)
  void clearIsExpand() => $_clearField(5);

  @$pb.TagNumber(6)
  $pb.PbList<GroupItem> get items => $_getList(5);
}

class GroupItem extends $pb.GeneratedMessage {
  factory GroupItem({
    $core.String? tag,
    $core.String? type,
    $fixnum.Int64? urlTestTime,
    $core.int? urlTestDelay,
  }) {
    final result = GroupItem._();
    if (tag != null) result.tag = tag;
    if (type != null) result.type = type;
    if (urlTestTime != null) result.urlTestTime = urlTestTime;
    if (urlTestDelay != null) result.urlTestDelay = urlTestDelay;
    return result;
  }

  GroupItem._();

  factory GroupItem.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GroupItem()..mergeFromBuffer(data, registry);
  factory GroupItem.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GroupItem()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GroupItem',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: GroupItem.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'tag')
    ..aOS(2, _omitFieldNames ? '' : 'type')
    ..aInt64(3, _omitFieldNames ? '' : 'urlTestTime', protoName: 'urlTestTime')
    ..aI(4, _omitFieldNames ? '' : 'urlTestDelay', protoName: 'urlTestDelay')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GroupItem clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GroupItem copyWith(void Function(GroupItem) updates) =>
      super.copyWith((message) => updates(message as GroupItem)) as GroupItem;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use GroupItem() / GroupItem.new instead')
  static GroupItem create() => GroupItem._();
  static $pb.GeneratedMessage $_createMessage() => GroupItem._();
  @$core.override
  GroupItem createEmptyInstance() => GroupItem._();
  @$core.pragma('dart2js:noInline')
  static GroupItem getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<GroupItem>(GroupItem.$_createMessage);
  static GroupItem? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get tag => $_getSZ(0);
  @$pb.TagNumber(1)
  set tag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get type => $_getSZ(1);
  @$pb.TagNumber(2)
  set type($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasType() => $_has(1);
  @$pb.TagNumber(2)
  void clearType() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get urlTestTime => $_getI64(2);
  @$pb.TagNumber(3)
  set urlTestTime($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasUrlTestTime() => $_has(2);
  @$pb.TagNumber(3)
  void clearUrlTestTime() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get urlTestDelay => $_getIZ(3);
  @$pb.TagNumber(4)
  set urlTestDelay($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasUrlTestDelay() => $_has(3);
  @$pb.TagNumber(4)
  void clearUrlTestDelay() => $_clearField(4);
}

class URLTestRequest extends $pb.GeneratedMessage {
  factory URLTestRequest({
    $core.String? outboundTag,
  }) {
    final result = URLTestRequest._();
    if (outboundTag != null) result.outboundTag = outboundTag;
    return result;
  }

  URLTestRequest._();

  factory URLTestRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      URLTestRequest()..mergeFromBuffer(data, registry);
  factory URLTestRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      URLTestRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'URLTestRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: URLTestRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'outboundTag', protoName: 'outboundTag')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  URLTestRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  URLTestRequest copyWith(void Function(URLTestRequest) updates) =>
      super.copyWith((message) => updates(message as URLTestRequest))
          as URLTestRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use URLTestRequest() / URLTestRequest.new instead')
  static URLTestRequest create() => URLTestRequest._();
  static $pb.GeneratedMessage $_createMessage() => URLTestRequest._();
  @$core.override
  URLTestRequest createEmptyInstance() => URLTestRequest._();
  @$core.pragma('dart2js:noInline')
  static URLTestRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<URLTestRequest>(
          URLTestRequest.$_createMessage);
  static URLTestRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get outboundTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set outboundTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasOutboundTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearOutboundTag() => $_clearField(1);
}

class SelectOutboundRequest extends $pb.GeneratedMessage {
  factory SelectOutboundRequest({
    $core.String? groupTag,
    $core.String? outboundTag,
  }) {
    final result = SelectOutboundRequest._();
    if (groupTag != null) result.groupTag = groupTag;
    if (outboundTag != null) result.outboundTag = outboundTag;
    return result;
  }

  SelectOutboundRequest._();

  factory SelectOutboundRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SelectOutboundRequest()..mergeFromBuffer(data, registry);
  factory SelectOutboundRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SelectOutboundRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SelectOutboundRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: SelectOutboundRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'groupTag', protoName: 'groupTag')
    ..aOS(2, _omitFieldNames ? '' : 'outboundTag', protoName: 'outboundTag')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SelectOutboundRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SelectOutboundRequest copyWith(
          void Function(SelectOutboundRequest) updates) =>
      super.copyWith((message) => updates(message as SelectOutboundRequest))
          as SelectOutboundRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SelectOutboundRequest() / SelectOutboundRequest.new instead')
  static SelectOutboundRequest create() => SelectOutboundRequest._();
  static $pb.GeneratedMessage $_createMessage() => SelectOutboundRequest._();
  @$core.override
  SelectOutboundRequest createEmptyInstance() => SelectOutboundRequest._();
  @$core.pragma('dart2js:noInline')
  static SelectOutboundRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SelectOutboundRequest>(
          SelectOutboundRequest.$_createMessage);
  static SelectOutboundRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get groupTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set groupTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasGroupTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearGroupTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get outboundTag => $_getSZ(1);
  @$pb.TagNumber(2)
  set outboundTag($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasOutboundTag() => $_has(1);
  @$pb.TagNumber(2)
  void clearOutboundTag() => $_clearField(2);
}

class SetGroupExpandRequest extends $pb.GeneratedMessage {
  factory SetGroupExpandRequest({
    $core.String? groupTag,
    $core.bool? isExpand,
  }) {
    final result = SetGroupExpandRequest._();
    if (groupTag != null) result.groupTag = groupTag;
    if (isExpand != null) result.isExpand = isExpand;
    return result;
  }

  SetGroupExpandRequest._();

  factory SetGroupExpandRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetGroupExpandRequest()..mergeFromBuffer(data, registry);
  factory SetGroupExpandRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetGroupExpandRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SetGroupExpandRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: SetGroupExpandRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'groupTag', protoName: 'groupTag')
    ..aOB(2, _omitFieldNames ? '' : 'isExpand', protoName: 'isExpand')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetGroupExpandRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetGroupExpandRequest copyWith(
          void Function(SetGroupExpandRequest) updates) =>
      super.copyWith((message) => updates(message as SetGroupExpandRequest))
          as SetGroupExpandRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SetGroupExpandRequest() / SetGroupExpandRequest.new instead')
  static SetGroupExpandRequest create() => SetGroupExpandRequest._();
  static $pb.GeneratedMessage $_createMessage() => SetGroupExpandRequest._();
  @$core.override
  SetGroupExpandRequest createEmptyInstance() => SetGroupExpandRequest._();
  @$core.pragma('dart2js:noInline')
  static SetGroupExpandRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SetGroupExpandRequest>(
          SetGroupExpandRequest.$_createMessage);
  static SetGroupExpandRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get groupTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set groupTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasGroupTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearGroupTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get isExpand => $_getBF(1);
  @$pb.TagNumber(2)
  set isExpand($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasIsExpand() => $_has(1);
  @$pb.TagNumber(2)
  void clearIsExpand() => $_clearField(2);
}

class ClashMode extends $pb.GeneratedMessage {
  factory ClashMode({
    $core.String? mode,
  }) {
    final result = ClashMode._();
    if (mode != null) result.mode = mode;
    return result;
  }

  ClashMode._();

  factory ClashMode.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ClashMode()..mergeFromBuffer(data, registry);
  factory ClashMode.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ClashMode()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ClashMode',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: ClashMode.$_createMessage)
    ..aOS(3, _omitFieldNames ? '' : 'mode')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ClashMode clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ClashMode copyWith(void Function(ClashMode) updates) =>
      super.copyWith((message) => updates(message as ClashMode)) as ClashMode;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ClashMode() / ClashMode.new instead')
  static ClashMode create() => ClashMode._();
  static $pb.GeneratedMessage $_createMessage() => ClashMode._();
  @$core.override
  ClashMode createEmptyInstance() => ClashMode._();
  @$core.pragma('dart2js:noInline')
  static ClashMode getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ClashMode>(ClashMode.$_createMessage);
  static ClashMode? _defaultInstance;

  @$pb.TagNumber(3)
  $core.String get mode => $_getSZ(0);
  @$pb.TagNumber(3)
  set mode($core.String value) => $_setString(0, value);
  @$pb.TagNumber(3)
  $core.bool hasMode() => $_has(0);
  @$pb.TagNumber(3)
  void clearMode() => $_clearField(3);
}

class ClashModeStatus extends $pb.GeneratedMessage {
  factory ClashModeStatus({
    $core.Iterable<$core.String>? modeList,
    $core.String? currentMode,
  }) {
    final result = ClashModeStatus._();
    if (modeList != null) result.modeList.addAll(modeList);
    if (currentMode != null) result.currentMode = currentMode;
    return result;
  }

  ClashModeStatus._();

  factory ClashModeStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ClashModeStatus()..mergeFromBuffer(data, registry);
  factory ClashModeStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ClashModeStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ClashModeStatus',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: ClashModeStatus.$_createMessage)
    ..pPS(1, _omitFieldNames ? '' : 'modeList', protoName: 'modeList')
    ..aOS(2, _omitFieldNames ? '' : 'currentMode', protoName: 'currentMode')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ClashModeStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ClashModeStatus copyWith(void Function(ClashModeStatus) updates) =>
      super.copyWith((message) => updates(message as ClashModeStatus))
          as ClashModeStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ClashModeStatus() / ClashModeStatus.new instead')
  static ClashModeStatus create() => ClashModeStatus._();
  static $pb.GeneratedMessage $_createMessage() => ClashModeStatus._();
  @$core.override
  ClashModeStatus createEmptyInstance() => ClashModeStatus._();
  @$core.pragma('dart2js:noInline')
  static ClashModeStatus getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ClashModeStatus>(
          ClashModeStatus.$_createMessage);
  static ClashModeStatus? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<$core.String> get modeList => $_getList(0);

  @$pb.TagNumber(2)
  $core.String get currentMode => $_getSZ(1);
  @$pb.TagNumber(2)
  set currentMode($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasCurrentMode() => $_has(1);
  @$pb.TagNumber(2)
  void clearCurrentMode() => $_clearField(2);
}

class SubscribeConnectionsRequest extends $pb.GeneratedMessage {
  factory SubscribeConnectionsRequest({
    $fixnum.Int64? interval,
  }) {
    final result = SubscribeConnectionsRequest._();
    if (interval != null) result.interval = interval;
    return result;
  }

  SubscribeConnectionsRequest._();

  factory SubscribeConnectionsRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SubscribeConnectionsRequest()..mergeFromBuffer(data, registry);
  factory SubscribeConnectionsRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SubscribeConnectionsRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SubscribeConnectionsRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: SubscribeConnectionsRequest.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'interval')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubscribeConnectionsRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubscribeConnectionsRequest copyWith(
          void Function(SubscribeConnectionsRequest) updates) =>
      super.copyWith(
              (message) => updates(message as SubscribeConnectionsRequest))
          as SubscribeConnectionsRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SubscribeConnectionsRequest() / SubscribeConnectionsRequest.new instead')
  static SubscribeConnectionsRequest create() =>
      SubscribeConnectionsRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      SubscribeConnectionsRequest._();
  @$core.override
  SubscribeConnectionsRequest createEmptyInstance() =>
      SubscribeConnectionsRequest._();
  @$core.pragma('dart2js:noInline')
  static SubscribeConnectionsRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SubscribeConnectionsRequest>(
          SubscribeConnectionsRequest.$_createMessage);
  static SubscribeConnectionsRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get interval => $_getI64(0);
  @$pb.TagNumber(1)
  set interval($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasInterval() => $_has(0);
  @$pb.TagNumber(1)
  void clearInterval() => $_clearField(1);
}

class ConnectionEvent extends $pb.GeneratedMessage {
  factory ConnectionEvent({
    ConnectionEventType? type,
    $core.String? id,
    Connection? connection,
    $fixnum.Int64? uplinkDelta,
    $fixnum.Int64? downlinkDelta,
    $fixnum.Int64? closedAt,
  }) {
    final result = ConnectionEvent._();
    if (type != null) result.type = type;
    if (id != null) result.id = id;
    if (connection != null) result.connection = connection;
    if (uplinkDelta != null) result.uplinkDelta = uplinkDelta;
    if (downlinkDelta != null) result.downlinkDelta = downlinkDelta;
    if (closedAt != null) result.closedAt = closedAt;
    return result;
  }

  ConnectionEvent._();

  factory ConnectionEvent.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ConnectionEvent()..mergeFromBuffer(data, registry);
  factory ConnectionEvent.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ConnectionEvent()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ConnectionEvent',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: ConnectionEvent.$_createMessage)
    ..aE<ConnectionEventType>(1, _omitFieldNames ? '' : 'type',
        enumValues: ConnectionEventType.values)
    ..aOS(2, _omitFieldNames ? '' : 'id')
    ..aOM<Connection>(3, _omitFieldNames ? '' : 'connection',
        subBuilder: Connection.$_createMessage)
    ..aInt64(4, _omitFieldNames ? '' : 'uplinkDelta', protoName: 'uplinkDelta')
    ..aInt64(5, _omitFieldNames ? '' : 'downlinkDelta',
        protoName: 'downlinkDelta')
    ..aInt64(6, _omitFieldNames ? '' : 'closedAt', protoName: 'closedAt')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ConnectionEvent clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ConnectionEvent copyWith(void Function(ConnectionEvent) updates) =>
      super.copyWith((message) => updates(message as ConnectionEvent))
          as ConnectionEvent;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ConnectionEvent() / ConnectionEvent.new instead')
  static ConnectionEvent create() => ConnectionEvent._();
  static $pb.GeneratedMessage $_createMessage() => ConnectionEvent._();
  @$core.override
  ConnectionEvent createEmptyInstance() => ConnectionEvent._();
  @$core.pragma('dart2js:noInline')
  static ConnectionEvent getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ConnectionEvent>(
          ConnectionEvent.$_createMessage);
  static ConnectionEvent? _defaultInstance;

  @$pb.TagNumber(1)
  ConnectionEventType get type => $_getN(0);
  @$pb.TagNumber(1)
  set type(ConnectionEventType value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasType() => $_has(0);
  @$pb.TagNumber(1)
  void clearType() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get id => $_getSZ(1);
  @$pb.TagNumber(2)
  set id($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasId() => $_has(1);
  @$pb.TagNumber(2)
  void clearId() => $_clearField(2);

  @$pb.TagNumber(3)
  Connection get connection => $_getN(2);
  @$pb.TagNumber(3)
  set connection(Connection value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasConnection() => $_has(2);
  @$pb.TagNumber(3)
  void clearConnection() => $_clearField(3);
  @$pb.TagNumber(3)
  Connection ensureConnection() => $_ensure(2);

  @$pb.TagNumber(4)
  $fixnum.Int64 get uplinkDelta => $_getI64(3);
  @$pb.TagNumber(4)
  set uplinkDelta($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasUplinkDelta() => $_has(3);
  @$pb.TagNumber(4)
  void clearUplinkDelta() => $_clearField(4);

  @$pb.TagNumber(5)
  $fixnum.Int64 get downlinkDelta => $_getI64(4);
  @$pb.TagNumber(5)
  set downlinkDelta($fixnum.Int64 value) => $_setInt64(4, value);
  @$pb.TagNumber(5)
  $core.bool hasDownlinkDelta() => $_has(4);
  @$pb.TagNumber(5)
  void clearDownlinkDelta() => $_clearField(5);

  @$pb.TagNumber(6)
  $fixnum.Int64 get closedAt => $_getI64(5);
  @$pb.TagNumber(6)
  set closedAt($fixnum.Int64 value) => $_setInt64(5, value);
  @$pb.TagNumber(6)
  $core.bool hasClosedAt() => $_has(5);
  @$pb.TagNumber(6)
  void clearClosedAt() => $_clearField(6);
}

class ConnectionEvents extends $pb.GeneratedMessage {
  factory ConnectionEvents({
    $core.Iterable<ConnectionEvent>? events,
    $core.bool? reset,
  }) {
    final result = ConnectionEvents._();
    if (events != null) result.events.addAll(events);
    if (reset != null) result.reset = reset;
    return result;
  }

  ConnectionEvents._();

  factory ConnectionEvents.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ConnectionEvents()..mergeFromBuffer(data, registry);
  factory ConnectionEvents.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ConnectionEvents()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ConnectionEvents',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: ConnectionEvents.$_createMessage)
    ..pPM<ConnectionEvent>(1, _omitFieldNames ? '' : 'events',
        subBuilder: ConnectionEvent.$_createMessage)
    ..aOB(2, _omitFieldNames ? '' : 'reset')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ConnectionEvents clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ConnectionEvents copyWith(void Function(ConnectionEvents) updates) =>
      super.copyWith((message) => updates(message as ConnectionEvents))
          as ConnectionEvents;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ConnectionEvents() / ConnectionEvents.new instead')
  static ConnectionEvents create() => ConnectionEvents._();
  static $pb.GeneratedMessage $_createMessage() => ConnectionEvents._();
  @$core.override
  ConnectionEvents createEmptyInstance() => ConnectionEvents._();
  @$core.pragma('dart2js:noInline')
  static ConnectionEvents getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ConnectionEvents>(
          ConnectionEvents.$_createMessage);
  static ConnectionEvents? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<ConnectionEvent> get events => $_getList(0);

  @$pb.TagNumber(2)
  $core.bool get reset => $_getBF(1);
  @$pb.TagNumber(2)
  set reset($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasReset() => $_has(1);
  @$pb.TagNumber(2)
  void clearReset() => $_clearField(2);
}

class Connection extends $pb.GeneratedMessage {
  factory Connection({
    $core.String? id,
    $core.String? inbound,
    $core.String? inboundType,
    $core.int? ipVersion,
    $core.String? network,
    $core.String? source,
    $core.String? destination,
    $core.String? domain,
    $core.String? protocol,
    $core.String? user,
    $core.String? fromOutbound,
    $fixnum.Int64? createdAt,
    $fixnum.Int64? closedAt,
    $fixnum.Int64? uplink,
    $fixnum.Int64? downlink,
    $fixnum.Int64? uplinkTotal,
    $fixnum.Int64? downlinkTotal,
    $core.String? rule,
    $core.String? outbound,
    $core.String? outboundType,
    $core.Iterable<$core.String>? chainList,
    ProcessInfo? processInfo,
  }) {
    final result = Connection._();
    if (id != null) result.id = id;
    if (inbound != null) result.inbound = inbound;
    if (inboundType != null) result.inboundType = inboundType;
    if (ipVersion != null) result.ipVersion = ipVersion;
    if (network != null) result.network = network;
    if (source != null) result.source = source;
    if (destination != null) result.destination = destination;
    if (domain != null) result.domain = domain;
    if (protocol != null) result.protocol = protocol;
    if (user != null) result.user = user;
    if (fromOutbound != null) result.fromOutbound = fromOutbound;
    if (createdAt != null) result.createdAt = createdAt;
    if (closedAt != null) result.closedAt = closedAt;
    if (uplink != null) result.uplink = uplink;
    if (downlink != null) result.downlink = downlink;
    if (uplinkTotal != null) result.uplinkTotal = uplinkTotal;
    if (downlinkTotal != null) result.downlinkTotal = downlinkTotal;
    if (rule != null) result.rule = rule;
    if (outbound != null) result.outbound = outbound;
    if (outboundType != null) result.outboundType = outboundType;
    if (chainList != null) result.chainList.addAll(chainList);
    if (processInfo != null) result.processInfo = processInfo;
    return result;
  }

  Connection._();

  factory Connection.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Connection()..mergeFromBuffer(data, registry);
  factory Connection.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Connection()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Connection',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: Connection.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'id')
    ..aOS(2, _omitFieldNames ? '' : 'inbound')
    ..aOS(3, _omitFieldNames ? '' : 'inboundType', protoName: 'inboundType')
    ..aI(4, _omitFieldNames ? '' : 'ipVersion', protoName: 'ipVersion')
    ..aOS(5, _omitFieldNames ? '' : 'network')
    ..aOS(6, _omitFieldNames ? '' : 'source')
    ..aOS(7, _omitFieldNames ? '' : 'destination')
    ..aOS(8, _omitFieldNames ? '' : 'domain')
    ..aOS(9, _omitFieldNames ? '' : 'protocol')
    ..aOS(10, _omitFieldNames ? '' : 'user')
    ..aOS(11, _omitFieldNames ? '' : 'fromOutbound', protoName: 'fromOutbound')
    ..aInt64(12, _omitFieldNames ? '' : 'createdAt', protoName: 'createdAt')
    ..aInt64(13, _omitFieldNames ? '' : 'closedAt', protoName: 'closedAt')
    ..aInt64(14, _omitFieldNames ? '' : 'uplink')
    ..aInt64(15, _omitFieldNames ? '' : 'downlink')
    ..aInt64(16, _omitFieldNames ? '' : 'uplinkTotal', protoName: 'uplinkTotal')
    ..aInt64(17, _omitFieldNames ? '' : 'downlinkTotal',
        protoName: 'downlinkTotal')
    ..aOS(18, _omitFieldNames ? '' : 'rule')
    ..aOS(19, _omitFieldNames ? '' : 'outbound')
    ..aOS(20, _omitFieldNames ? '' : 'outboundType', protoName: 'outboundType')
    ..pPS(21, _omitFieldNames ? '' : 'chainList', protoName: 'chainList')
    ..aOM<ProcessInfo>(22, _omitFieldNames ? '' : 'processInfo',
        protoName: 'processInfo', subBuilder: ProcessInfo.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Connection clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Connection copyWith(void Function(Connection) updates) =>
      super.copyWith((message) => updates(message as Connection)) as Connection;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Connection() / Connection.new instead')
  static Connection create() => Connection._();
  static $pb.GeneratedMessage $_createMessage() => Connection._();
  @$core.override
  Connection createEmptyInstance() => Connection._();
  @$core.pragma('dart2js:noInline')
  static Connection getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Connection>(Connection.$_createMessage);
  static Connection? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get id => $_getSZ(0);
  @$pb.TagNumber(1)
  set id($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasId() => $_has(0);
  @$pb.TagNumber(1)
  void clearId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get inbound => $_getSZ(1);
  @$pb.TagNumber(2)
  set inbound($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasInbound() => $_has(1);
  @$pb.TagNumber(2)
  void clearInbound() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get inboundType => $_getSZ(2);
  @$pb.TagNumber(3)
  set inboundType($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasInboundType() => $_has(2);
  @$pb.TagNumber(3)
  void clearInboundType() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get ipVersion => $_getIZ(3);
  @$pb.TagNumber(4)
  set ipVersion($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasIpVersion() => $_has(3);
  @$pb.TagNumber(4)
  void clearIpVersion() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get network => $_getSZ(4);
  @$pb.TagNumber(5)
  set network($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasNetwork() => $_has(4);
  @$pb.TagNumber(5)
  void clearNetwork() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get source => $_getSZ(5);
  @$pb.TagNumber(6)
  set source($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasSource() => $_has(5);
  @$pb.TagNumber(6)
  void clearSource() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.String get destination => $_getSZ(6);
  @$pb.TagNumber(7)
  set destination($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasDestination() => $_has(6);
  @$pb.TagNumber(7)
  void clearDestination() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.String get domain => $_getSZ(7);
  @$pb.TagNumber(8)
  set domain($core.String value) => $_setString(7, value);
  @$pb.TagNumber(8)
  $core.bool hasDomain() => $_has(7);
  @$pb.TagNumber(8)
  void clearDomain() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.String get protocol => $_getSZ(8);
  @$pb.TagNumber(9)
  set protocol($core.String value) => $_setString(8, value);
  @$pb.TagNumber(9)
  $core.bool hasProtocol() => $_has(8);
  @$pb.TagNumber(9)
  void clearProtocol() => $_clearField(9);

  @$pb.TagNumber(10)
  $core.String get user => $_getSZ(9);
  @$pb.TagNumber(10)
  set user($core.String value) => $_setString(9, value);
  @$pb.TagNumber(10)
  $core.bool hasUser() => $_has(9);
  @$pb.TagNumber(10)
  void clearUser() => $_clearField(10);

  @$pb.TagNumber(11)
  $core.String get fromOutbound => $_getSZ(10);
  @$pb.TagNumber(11)
  set fromOutbound($core.String value) => $_setString(10, value);
  @$pb.TagNumber(11)
  $core.bool hasFromOutbound() => $_has(10);
  @$pb.TagNumber(11)
  void clearFromOutbound() => $_clearField(11);

  @$pb.TagNumber(12)
  $fixnum.Int64 get createdAt => $_getI64(11);
  @$pb.TagNumber(12)
  set createdAt($fixnum.Int64 value) => $_setInt64(11, value);
  @$pb.TagNumber(12)
  $core.bool hasCreatedAt() => $_has(11);
  @$pb.TagNumber(12)
  void clearCreatedAt() => $_clearField(12);

  @$pb.TagNumber(13)
  $fixnum.Int64 get closedAt => $_getI64(12);
  @$pb.TagNumber(13)
  set closedAt($fixnum.Int64 value) => $_setInt64(12, value);
  @$pb.TagNumber(13)
  $core.bool hasClosedAt() => $_has(12);
  @$pb.TagNumber(13)
  void clearClosedAt() => $_clearField(13);

  @$pb.TagNumber(14)
  $fixnum.Int64 get uplink => $_getI64(13);
  @$pb.TagNumber(14)
  set uplink($fixnum.Int64 value) => $_setInt64(13, value);
  @$pb.TagNumber(14)
  $core.bool hasUplink() => $_has(13);
  @$pb.TagNumber(14)
  void clearUplink() => $_clearField(14);

  @$pb.TagNumber(15)
  $fixnum.Int64 get downlink => $_getI64(14);
  @$pb.TagNumber(15)
  set downlink($fixnum.Int64 value) => $_setInt64(14, value);
  @$pb.TagNumber(15)
  $core.bool hasDownlink() => $_has(14);
  @$pb.TagNumber(15)
  void clearDownlink() => $_clearField(15);

  @$pb.TagNumber(16)
  $fixnum.Int64 get uplinkTotal => $_getI64(15);
  @$pb.TagNumber(16)
  set uplinkTotal($fixnum.Int64 value) => $_setInt64(15, value);
  @$pb.TagNumber(16)
  $core.bool hasUplinkTotal() => $_has(15);
  @$pb.TagNumber(16)
  void clearUplinkTotal() => $_clearField(16);

  @$pb.TagNumber(17)
  $fixnum.Int64 get downlinkTotal => $_getI64(16);
  @$pb.TagNumber(17)
  set downlinkTotal($fixnum.Int64 value) => $_setInt64(16, value);
  @$pb.TagNumber(17)
  $core.bool hasDownlinkTotal() => $_has(16);
  @$pb.TagNumber(17)
  void clearDownlinkTotal() => $_clearField(17);

  @$pb.TagNumber(18)
  $core.String get rule => $_getSZ(17);
  @$pb.TagNumber(18)
  set rule($core.String value) => $_setString(17, value);
  @$pb.TagNumber(18)
  $core.bool hasRule() => $_has(17);
  @$pb.TagNumber(18)
  void clearRule() => $_clearField(18);

  @$pb.TagNumber(19)
  $core.String get outbound => $_getSZ(18);
  @$pb.TagNumber(19)
  set outbound($core.String value) => $_setString(18, value);
  @$pb.TagNumber(19)
  $core.bool hasOutbound() => $_has(18);
  @$pb.TagNumber(19)
  void clearOutbound() => $_clearField(19);

  @$pb.TagNumber(20)
  $core.String get outboundType => $_getSZ(19);
  @$pb.TagNumber(20)
  set outboundType($core.String value) => $_setString(19, value);
  @$pb.TagNumber(20)
  $core.bool hasOutboundType() => $_has(19);
  @$pb.TagNumber(20)
  void clearOutboundType() => $_clearField(20);

  @$pb.TagNumber(21)
  $pb.PbList<$core.String> get chainList => $_getList(20);

  @$pb.TagNumber(22)
  ProcessInfo get processInfo => $_getN(21);
  @$pb.TagNumber(22)
  set processInfo(ProcessInfo value) => $_setField(22, value);
  @$pb.TagNumber(22)
  $core.bool hasProcessInfo() => $_has(21);
  @$pb.TagNumber(22)
  void clearProcessInfo() => $_clearField(22);
  @$pb.TagNumber(22)
  ProcessInfo ensureProcessInfo() => $_ensure(21);
}

class ProcessInfo extends $pb.GeneratedMessage {
  factory ProcessInfo({
    $core.int? processId,
    $core.int? userId,
    $core.String? userName,
    $core.String? processPath,
    $core.Iterable<$core.String>? packageNames,
  }) {
    final result = ProcessInfo._();
    if (processId != null) result.processId = processId;
    if (userId != null) result.userId = userId;
    if (userName != null) result.userName = userName;
    if (processPath != null) result.processPath = processPath;
    if (packageNames != null) result.packageNames.addAll(packageNames);
    return result;
  }

  ProcessInfo._();

  factory ProcessInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProcessInfo()..mergeFromBuffer(data, registry);
  factory ProcessInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProcessInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ProcessInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: ProcessInfo.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'processId',
        protoName: 'processId', fieldType: $pb.PbFieldType.OU3)
    ..aI(2, _omitFieldNames ? '' : 'userId', protoName: 'userId')
    ..aOS(3, _omitFieldNames ? '' : 'userName', protoName: 'userName')
    ..aOS(4, _omitFieldNames ? '' : 'processPath', protoName: 'processPath')
    ..pPS(5, _omitFieldNames ? '' : 'packageNames', protoName: 'packageNames')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProcessInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProcessInfo copyWith(void Function(ProcessInfo) updates) =>
      super.copyWith((message) => updates(message as ProcessInfo))
          as ProcessInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ProcessInfo() / ProcessInfo.new instead')
  static ProcessInfo create() => ProcessInfo._();
  static $pb.GeneratedMessage $_createMessage() => ProcessInfo._();
  @$core.override
  ProcessInfo createEmptyInstance() => ProcessInfo._();
  @$core.pragma('dart2js:noInline')
  static ProcessInfo getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ProcessInfo>(
          ProcessInfo.$_createMessage);
  static ProcessInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get processId => $_getIZ(0);
  @$pb.TagNumber(1)
  set processId($core.int value) => $_setUnsignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasProcessId() => $_has(0);
  @$pb.TagNumber(1)
  void clearProcessId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get userId => $_getIZ(1);
  @$pb.TagNumber(2)
  set userId($core.int value) => $_setSignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasUserId() => $_has(1);
  @$pb.TagNumber(2)
  void clearUserId() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get userName => $_getSZ(2);
  @$pb.TagNumber(3)
  set userName($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasUserName() => $_has(2);
  @$pb.TagNumber(3)
  void clearUserName() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get processPath => $_getSZ(3);
  @$pb.TagNumber(4)
  set processPath($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasProcessPath() => $_has(3);
  @$pb.TagNumber(4)
  void clearProcessPath() => $_clearField(4);

  @$pb.TagNumber(5)
  $pb.PbList<$core.String> get packageNames => $_getList(4);
}

class CloseConnectionRequest extends $pb.GeneratedMessage {
  factory CloseConnectionRequest({
    $core.String? id,
  }) {
    final result = CloseConnectionRequest._();
    if (id != null) result.id = id;
    return result;
  }

  CloseConnectionRequest._();

  factory CloseConnectionRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CloseConnectionRequest()..mergeFromBuffer(data, registry);
  factory CloseConnectionRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CloseConnectionRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CloseConnectionRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: CloseConnectionRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'id')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CloseConnectionRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CloseConnectionRequest copyWith(
          void Function(CloseConnectionRequest) updates) =>
      super.copyWith((message) => updates(message as CloseConnectionRequest))
          as CloseConnectionRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use CloseConnectionRequest() / CloseConnectionRequest.new instead')
  static CloseConnectionRequest create() => CloseConnectionRequest._();
  static $pb.GeneratedMessage $_createMessage() => CloseConnectionRequest._();
  @$core.override
  CloseConnectionRequest createEmptyInstance() => CloseConnectionRequest._();
  @$core.pragma('dart2js:noInline')
  static CloseConnectionRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<CloseConnectionRequest>(
          CloseConnectionRequest.$_createMessage);
  static CloseConnectionRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get id => $_getSZ(0);
  @$pb.TagNumber(1)
  set id($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasId() => $_has(0);
  @$pb.TagNumber(1)
  void clearId() => $_clearField(1);
}

class DeprecatedWarnings extends $pb.GeneratedMessage {
  factory DeprecatedWarnings({
    $core.Iterable<DeprecatedWarning>? warnings,
  }) {
    final result = DeprecatedWarnings._();
    if (warnings != null) result.warnings.addAll(warnings);
    return result;
  }

  DeprecatedWarnings._();

  factory DeprecatedWarnings.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DeprecatedWarnings()..mergeFromBuffer(data, registry);
  factory DeprecatedWarnings.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DeprecatedWarnings()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DeprecatedWarnings',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: DeprecatedWarnings.$_createMessage)
    ..pPM<DeprecatedWarning>(1, _omitFieldNames ? '' : 'warnings',
        subBuilder: DeprecatedWarning.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DeprecatedWarnings clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DeprecatedWarnings copyWith(void Function(DeprecatedWarnings) updates) =>
      super.copyWith((message) => updates(message as DeprecatedWarnings))
          as DeprecatedWarnings;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use DeprecatedWarnings() / DeprecatedWarnings.new instead')
  static DeprecatedWarnings create() => DeprecatedWarnings._();
  static $pb.GeneratedMessage $_createMessage() => DeprecatedWarnings._();
  @$core.override
  DeprecatedWarnings createEmptyInstance() => DeprecatedWarnings._();
  @$core.pragma('dart2js:noInline')
  static DeprecatedWarnings getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<DeprecatedWarnings>(
          DeprecatedWarnings.$_createMessage);
  static DeprecatedWarnings? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<DeprecatedWarning> get warnings => $_getList(0);
}

class DeprecatedWarning extends $pb.GeneratedMessage {
  factory DeprecatedWarning({
    $core.String? message,
    $core.bool? impending,
    $core.String? migrationLink,
    $core.String? description,
    $core.String? deprecatedVersion,
    $core.String? scheduledVersion,
  }) {
    final result = DeprecatedWarning._();
    if (message != null) result.message = message;
    if (impending != null) result.impending = impending;
    if (migrationLink != null) result.migrationLink = migrationLink;
    if (description != null) result.description = description;
    if (deprecatedVersion != null) result.deprecatedVersion = deprecatedVersion;
    if (scheduledVersion != null) result.scheduledVersion = scheduledVersion;
    return result;
  }

  DeprecatedWarning._();

  factory DeprecatedWarning.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DeprecatedWarning()..mergeFromBuffer(data, registry);
  factory DeprecatedWarning.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DeprecatedWarning()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DeprecatedWarning',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: DeprecatedWarning.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'message')
    ..aOB(2, _omitFieldNames ? '' : 'impending')
    ..aOS(3, _omitFieldNames ? '' : 'migrationLink', protoName: 'migrationLink')
    ..aOS(4, _omitFieldNames ? '' : 'description')
    ..aOS(5, _omitFieldNames ? '' : 'deprecatedVersion',
        protoName: 'deprecatedVersion')
    ..aOS(6, _omitFieldNames ? '' : 'scheduledVersion',
        protoName: 'scheduledVersion')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DeprecatedWarning clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DeprecatedWarning copyWith(void Function(DeprecatedWarning) updates) =>
      super.copyWith((message) => updates(message as DeprecatedWarning))
          as DeprecatedWarning;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use DeprecatedWarning() / DeprecatedWarning.new instead')
  static DeprecatedWarning create() => DeprecatedWarning._();
  static $pb.GeneratedMessage $_createMessage() => DeprecatedWarning._();
  @$core.override
  DeprecatedWarning createEmptyInstance() => DeprecatedWarning._();
  @$core.pragma('dart2js:noInline')
  static DeprecatedWarning getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<DeprecatedWarning>(
          DeprecatedWarning.$_createMessage);
  static DeprecatedWarning? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get message => $_getSZ(0);
  @$pb.TagNumber(1)
  set message($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasMessage() => $_has(0);
  @$pb.TagNumber(1)
  void clearMessage() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get impending => $_getBF(1);
  @$pb.TagNumber(2)
  set impending($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasImpending() => $_has(1);
  @$pb.TagNumber(2)
  void clearImpending() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get migrationLink => $_getSZ(2);
  @$pb.TagNumber(3)
  set migrationLink($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasMigrationLink() => $_has(2);
  @$pb.TagNumber(3)
  void clearMigrationLink() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get description => $_getSZ(3);
  @$pb.TagNumber(4)
  set description($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasDescription() => $_has(3);
  @$pb.TagNumber(4)
  void clearDescription() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get deprecatedVersion => $_getSZ(4);
  @$pb.TagNumber(5)
  set deprecatedVersion($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasDeprecatedVersion() => $_has(4);
  @$pb.TagNumber(5)
  void clearDeprecatedVersion() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get scheduledVersion => $_getSZ(5);
  @$pb.TagNumber(6)
  set scheduledVersion($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasScheduledVersion() => $_has(5);
  @$pb.TagNumber(6)
  void clearScheduledVersion() => $_clearField(6);
}

class StartedAt extends $pb.GeneratedMessage {
  factory StartedAt({
    $fixnum.Int64? startedAt,
  }) {
    final result = StartedAt._();
    if (startedAt != null) result.startedAt = startedAt;
    return result;
  }

  StartedAt._();

  factory StartedAt.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StartedAt()..mergeFromBuffer(data, registry);
  factory StartedAt.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      StartedAt()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'StartedAt',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: StartedAt.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'startedAt', protoName: 'startedAt')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StartedAt clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  StartedAt copyWith(void Function(StartedAt) updates) =>
      super.copyWith((message) => updates(message as StartedAt)) as StartedAt;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use StartedAt() / StartedAt.new instead')
  static StartedAt create() => StartedAt._();
  static $pb.GeneratedMessage $_createMessage() => StartedAt._();
  @$core.override
  StartedAt createEmptyInstance() => StartedAt._();
  @$core.pragma('dart2js:noInline')
  static StartedAt getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<StartedAt>(StartedAt.$_createMessage);
  static StartedAt? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get startedAt => $_getI64(0);
  @$pb.TagNumber(1)
  set startedAt($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasStartedAt() => $_has(0);
  @$pb.TagNumber(1)
  void clearStartedAt() => $_clearField(1);
}

class OutboundList extends $pb.GeneratedMessage {
  factory OutboundList({
    $core.Iterable<GroupItem>? outbounds,
  }) {
    final result = OutboundList._();
    if (outbounds != null) result.outbounds.addAll(outbounds);
    return result;
  }

  OutboundList._();

  factory OutboundList.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OutboundList()..mergeFromBuffer(data, registry);
  factory OutboundList.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OutboundList()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OutboundList',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OutboundList.$_createMessage)
    ..pPM<GroupItem>(1, _omitFieldNames ? '' : 'outbounds',
        subBuilder: GroupItem.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OutboundList clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OutboundList copyWith(void Function(OutboundList) updates) =>
      super.copyWith((message) => updates(message as OutboundList))
          as OutboundList;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OutboundList() / OutboundList.new instead')
  static OutboundList create() => OutboundList._();
  static $pb.GeneratedMessage $_createMessage() => OutboundList._();
  @$core.override
  OutboundList createEmptyInstance() => OutboundList._();
  @$core.pragma('dart2js:noInline')
  static OutboundList getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OutboundList>(
          OutboundList.$_createMessage);
  static OutboundList? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<GroupItem> get outbounds => $_getList(0);
}

class NetworkQualityTestRequest extends $pb.GeneratedMessage {
  factory NetworkQualityTestRequest({
    $core.String? configURL,
    $core.String? outboundTag,
    $core.bool? serial,
    $core.int? maxRuntimeSeconds,
    $core.bool? http3,
  }) {
    final result = NetworkQualityTestRequest._();
    if (configURL != null) result.configURL = configURL;
    if (outboundTag != null) result.outboundTag = outboundTag;
    if (serial != null) result.serial = serial;
    if (maxRuntimeSeconds != null) result.maxRuntimeSeconds = maxRuntimeSeconds;
    if (http3 != null) result.http3 = http3;
    return result;
  }

  NetworkQualityTestRequest._();

  factory NetworkQualityTestRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      NetworkQualityTestRequest()..mergeFromBuffer(data, registry);
  factory NetworkQualityTestRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      NetworkQualityTestRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'NetworkQualityTestRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: NetworkQualityTestRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'configURL', protoName: 'configURL')
    ..aOS(2, _omitFieldNames ? '' : 'outboundTag', protoName: 'outboundTag')
    ..aOB(3, _omitFieldNames ? '' : 'serial')
    ..aI(4, _omitFieldNames ? '' : 'maxRuntimeSeconds',
        protoName: 'maxRuntimeSeconds')
    ..aOB(5, _omitFieldNames ? '' : 'http3')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NetworkQualityTestRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NetworkQualityTestRequest copyWith(
          void Function(NetworkQualityTestRequest) updates) =>
      super.copyWith((message) => updates(message as NetworkQualityTestRequest))
          as NetworkQualityTestRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use NetworkQualityTestRequest() / NetworkQualityTestRequest.new instead')
  static NetworkQualityTestRequest create() => NetworkQualityTestRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      NetworkQualityTestRequest._();
  @$core.override
  NetworkQualityTestRequest createEmptyInstance() =>
      NetworkQualityTestRequest._();
  @$core.pragma('dart2js:noInline')
  static NetworkQualityTestRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<NetworkQualityTestRequest>(
          NetworkQualityTestRequest.$_createMessage);
  static NetworkQualityTestRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get configURL => $_getSZ(0);
  @$pb.TagNumber(1)
  set configURL($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasConfigURL() => $_has(0);
  @$pb.TagNumber(1)
  void clearConfigURL() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get outboundTag => $_getSZ(1);
  @$pb.TagNumber(2)
  set outboundTag($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasOutboundTag() => $_has(1);
  @$pb.TagNumber(2)
  void clearOutboundTag() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get serial => $_getBF(2);
  @$pb.TagNumber(3)
  set serial($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasSerial() => $_has(2);
  @$pb.TagNumber(3)
  void clearSerial() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get maxRuntimeSeconds => $_getIZ(3);
  @$pb.TagNumber(4)
  set maxRuntimeSeconds($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasMaxRuntimeSeconds() => $_has(3);
  @$pb.TagNumber(4)
  void clearMaxRuntimeSeconds() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.bool get http3 => $_getBF(4);
  @$pb.TagNumber(5)
  set http3($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasHttp3() => $_has(4);
  @$pb.TagNumber(5)
  void clearHttp3() => $_clearField(5);
}

class NetworkQualityTestProgress extends $pb.GeneratedMessage {
  factory NetworkQualityTestProgress({
    $core.int? phase,
    $fixnum.Int64? downloadCapacity,
    $fixnum.Int64? uploadCapacity,
    $core.int? downloadRPM,
    $core.int? uploadRPM,
    $core.int? idleLatencyMs,
    $fixnum.Int64? elapsedMs,
    $core.bool? isFinal,
    $core.String? error,
    $core.int? downloadCapacityAccuracy,
    $core.int? uploadCapacityAccuracy,
    $core.int? downloadRPMAccuracy,
    $core.int? uploadRPMAccuracy,
  }) {
    final result = NetworkQualityTestProgress._();
    if (phase != null) result.phase = phase;
    if (downloadCapacity != null) result.downloadCapacity = downloadCapacity;
    if (uploadCapacity != null) result.uploadCapacity = uploadCapacity;
    if (downloadRPM != null) result.downloadRPM = downloadRPM;
    if (uploadRPM != null) result.uploadRPM = uploadRPM;
    if (idleLatencyMs != null) result.idleLatencyMs = idleLatencyMs;
    if (elapsedMs != null) result.elapsedMs = elapsedMs;
    if (isFinal != null) result.isFinal = isFinal;
    if (error != null) result.error = error;
    if (downloadCapacityAccuracy != null)
      result.downloadCapacityAccuracy = downloadCapacityAccuracy;
    if (uploadCapacityAccuracy != null)
      result.uploadCapacityAccuracy = uploadCapacityAccuracy;
    if (downloadRPMAccuracy != null)
      result.downloadRPMAccuracy = downloadRPMAccuracy;
    if (uploadRPMAccuracy != null) result.uploadRPMAccuracy = uploadRPMAccuracy;
    return result;
  }

  NetworkQualityTestProgress._();

  factory NetworkQualityTestProgress.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      NetworkQualityTestProgress()..mergeFromBuffer(data, registry);
  factory NetworkQualityTestProgress.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      NetworkQualityTestProgress()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'NetworkQualityTestProgress',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: NetworkQualityTestProgress.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'phase')
    ..aInt64(2, _omitFieldNames ? '' : 'downloadCapacity',
        protoName: 'downloadCapacity')
    ..aInt64(3, _omitFieldNames ? '' : 'uploadCapacity',
        protoName: 'uploadCapacity')
    ..aI(4, _omitFieldNames ? '' : 'downloadRPM', protoName: 'downloadRPM')
    ..aI(5, _omitFieldNames ? '' : 'uploadRPM', protoName: 'uploadRPM')
    ..aI(6, _omitFieldNames ? '' : 'idleLatencyMs', protoName: 'idleLatencyMs')
    ..aInt64(7, _omitFieldNames ? '' : 'elapsedMs', protoName: 'elapsedMs')
    ..aOB(8, _omitFieldNames ? '' : 'isFinal', protoName: 'isFinal')
    ..aOS(9, _omitFieldNames ? '' : 'error')
    ..aI(10, _omitFieldNames ? '' : 'downloadCapacityAccuracy',
        protoName: 'downloadCapacityAccuracy')
    ..aI(11, _omitFieldNames ? '' : 'uploadCapacityAccuracy',
        protoName: 'uploadCapacityAccuracy')
    ..aI(12, _omitFieldNames ? '' : 'downloadRPMAccuracy',
        protoName: 'downloadRPMAccuracy')
    ..aI(13, _omitFieldNames ? '' : 'uploadRPMAccuracy',
        protoName: 'uploadRPMAccuracy')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NetworkQualityTestProgress clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NetworkQualityTestProgress copyWith(
          void Function(NetworkQualityTestProgress) updates) =>
      super.copyWith(
              (message) => updates(message as NetworkQualityTestProgress))
          as NetworkQualityTestProgress;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use NetworkQualityTestProgress() / NetworkQualityTestProgress.new instead')
  static NetworkQualityTestProgress create() => NetworkQualityTestProgress._();
  static $pb.GeneratedMessage $_createMessage() =>
      NetworkQualityTestProgress._();
  @$core.override
  NetworkQualityTestProgress createEmptyInstance() =>
      NetworkQualityTestProgress._();
  @$core.pragma('dart2js:noInline')
  static NetworkQualityTestProgress getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<NetworkQualityTestProgress>(
          NetworkQualityTestProgress.$_createMessage);
  static NetworkQualityTestProgress? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get phase => $_getIZ(0);
  @$pb.TagNumber(1)
  set phase($core.int value) => $_setSignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasPhase() => $_has(0);
  @$pb.TagNumber(1)
  void clearPhase() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get downloadCapacity => $_getI64(1);
  @$pb.TagNumber(2)
  set downloadCapacity($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasDownloadCapacity() => $_has(1);
  @$pb.TagNumber(2)
  void clearDownloadCapacity() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get uploadCapacity => $_getI64(2);
  @$pb.TagNumber(3)
  set uploadCapacity($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasUploadCapacity() => $_has(2);
  @$pb.TagNumber(3)
  void clearUploadCapacity() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get downloadRPM => $_getIZ(3);
  @$pb.TagNumber(4)
  set downloadRPM($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasDownloadRPM() => $_has(3);
  @$pb.TagNumber(4)
  void clearDownloadRPM() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.int get uploadRPM => $_getIZ(4);
  @$pb.TagNumber(5)
  set uploadRPM($core.int value) => $_setSignedInt32(4, value);
  @$pb.TagNumber(5)
  $core.bool hasUploadRPM() => $_has(4);
  @$pb.TagNumber(5)
  void clearUploadRPM() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.int get idleLatencyMs => $_getIZ(5);
  @$pb.TagNumber(6)
  set idleLatencyMs($core.int value) => $_setSignedInt32(5, value);
  @$pb.TagNumber(6)
  $core.bool hasIdleLatencyMs() => $_has(5);
  @$pb.TagNumber(6)
  void clearIdleLatencyMs() => $_clearField(6);

  @$pb.TagNumber(7)
  $fixnum.Int64 get elapsedMs => $_getI64(6);
  @$pb.TagNumber(7)
  set elapsedMs($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(7)
  $core.bool hasElapsedMs() => $_has(6);
  @$pb.TagNumber(7)
  void clearElapsedMs() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.bool get isFinal => $_getBF(7);
  @$pb.TagNumber(8)
  set isFinal($core.bool value) => $_setBool(7, value);
  @$pb.TagNumber(8)
  $core.bool hasIsFinal() => $_has(7);
  @$pb.TagNumber(8)
  void clearIsFinal() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.String get error => $_getSZ(8);
  @$pb.TagNumber(9)
  set error($core.String value) => $_setString(8, value);
  @$pb.TagNumber(9)
  $core.bool hasError() => $_has(8);
  @$pb.TagNumber(9)
  void clearError() => $_clearField(9);

  @$pb.TagNumber(10)
  $core.int get downloadCapacityAccuracy => $_getIZ(9);
  @$pb.TagNumber(10)
  set downloadCapacityAccuracy($core.int value) => $_setSignedInt32(9, value);
  @$pb.TagNumber(10)
  $core.bool hasDownloadCapacityAccuracy() => $_has(9);
  @$pb.TagNumber(10)
  void clearDownloadCapacityAccuracy() => $_clearField(10);

  @$pb.TagNumber(11)
  $core.int get uploadCapacityAccuracy => $_getIZ(10);
  @$pb.TagNumber(11)
  set uploadCapacityAccuracy($core.int value) => $_setSignedInt32(10, value);
  @$pb.TagNumber(11)
  $core.bool hasUploadCapacityAccuracy() => $_has(10);
  @$pb.TagNumber(11)
  void clearUploadCapacityAccuracy() => $_clearField(11);

  @$pb.TagNumber(12)
  $core.int get downloadRPMAccuracy => $_getIZ(11);
  @$pb.TagNumber(12)
  set downloadRPMAccuracy($core.int value) => $_setSignedInt32(11, value);
  @$pb.TagNumber(12)
  $core.bool hasDownloadRPMAccuracy() => $_has(11);
  @$pb.TagNumber(12)
  void clearDownloadRPMAccuracy() => $_clearField(12);

  @$pb.TagNumber(13)
  $core.int get uploadRPMAccuracy => $_getIZ(12);
  @$pb.TagNumber(13)
  set uploadRPMAccuracy($core.int value) => $_setSignedInt32(12, value);
  @$pb.TagNumber(13)
  $core.bool hasUploadRPMAccuracy() => $_has(12);
  @$pb.TagNumber(13)
  void clearUploadRPMAccuracy() => $_clearField(13);
}

class STUNTestRequest extends $pb.GeneratedMessage {
  factory STUNTestRequest({
    $core.String? server,
    $core.String? outboundTag,
  }) {
    final result = STUNTestRequest._();
    if (server != null) result.server = server;
    if (outboundTag != null) result.outboundTag = outboundTag;
    return result;
  }

  STUNTestRequest._();

  factory STUNTestRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      STUNTestRequest()..mergeFromBuffer(data, registry);
  factory STUNTestRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      STUNTestRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'STUNTestRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: STUNTestRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'server')
    ..aOS(2, _omitFieldNames ? '' : 'outboundTag', protoName: 'outboundTag')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  STUNTestRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  STUNTestRequest copyWith(void Function(STUNTestRequest) updates) =>
      super.copyWith((message) => updates(message as STUNTestRequest))
          as STUNTestRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use STUNTestRequest() / STUNTestRequest.new instead')
  static STUNTestRequest create() => STUNTestRequest._();
  static $pb.GeneratedMessage $_createMessage() => STUNTestRequest._();
  @$core.override
  STUNTestRequest createEmptyInstance() => STUNTestRequest._();
  @$core.pragma('dart2js:noInline')
  static STUNTestRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<STUNTestRequest>(
          STUNTestRequest.$_createMessage);
  static STUNTestRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get server => $_getSZ(0);
  @$pb.TagNumber(1)
  set server($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasServer() => $_has(0);
  @$pb.TagNumber(1)
  void clearServer() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get outboundTag => $_getSZ(1);
  @$pb.TagNumber(2)
  set outboundTag($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasOutboundTag() => $_has(1);
  @$pb.TagNumber(2)
  void clearOutboundTag() => $_clearField(2);
}

class STUNTestProgress extends $pb.GeneratedMessage {
  factory STUNTestProgress({
    $core.int? phase,
    $core.String? externalAddr,
    $core.int? latencyMs,
    $core.int? natMapping,
    $core.int? natFiltering,
    $core.bool? isFinal,
    $core.String? error,
    $core.bool? natTypeSupported,
  }) {
    final result = STUNTestProgress._();
    if (phase != null) result.phase = phase;
    if (externalAddr != null) result.externalAddr = externalAddr;
    if (latencyMs != null) result.latencyMs = latencyMs;
    if (natMapping != null) result.natMapping = natMapping;
    if (natFiltering != null) result.natFiltering = natFiltering;
    if (isFinal != null) result.isFinal = isFinal;
    if (error != null) result.error = error;
    if (natTypeSupported != null) result.natTypeSupported = natTypeSupported;
    return result;
  }

  STUNTestProgress._();

  factory STUNTestProgress.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      STUNTestProgress()..mergeFromBuffer(data, registry);
  factory STUNTestProgress.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      STUNTestProgress()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'STUNTestProgress',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: STUNTestProgress.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'phase')
    ..aOS(2, _omitFieldNames ? '' : 'externalAddr', protoName: 'externalAddr')
    ..aI(3, _omitFieldNames ? '' : 'latencyMs', protoName: 'latencyMs')
    ..aI(4, _omitFieldNames ? '' : 'natMapping', protoName: 'natMapping')
    ..aI(5, _omitFieldNames ? '' : 'natFiltering', protoName: 'natFiltering')
    ..aOB(6, _omitFieldNames ? '' : 'isFinal', protoName: 'isFinal')
    ..aOS(7, _omitFieldNames ? '' : 'error')
    ..aOB(8, _omitFieldNames ? '' : 'natTypeSupported',
        protoName: 'natTypeSupported')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  STUNTestProgress clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  STUNTestProgress copyWith(void Function(STUNTestProgress) updates) =>
      super.copyWith((message) => updates(message as STUNTestProgress))
          as STUNTestProgress;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use STUNTestProgress() / STUNTestProgress.new instead')
  static STUNTestProgress create() => STUNTestProgress._();
  static $pb.GeneratedMessage $_createMessage() => STUNTestProgress._();
  @$core.override
  STUNTestProgress createEmptyInstance() => STUNTestProgress._();
  @$core.pragma('dart2js:noInline')
  static STUNTestProgress getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<STUNTestProgress>(
          STUNTestProgress.$_createMessage);
  static STUNTestProgress? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get phase => $_getIZ(0);
  @$pb.TagNumber(1)
  set phase($core.int value) => $_setSignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasPhase() => $_has(0);
  @$pb.TagNumber(1)
  void clearPhase() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get externalAddr => $_getSZ(1);
  @$pb.TagNumber(2)
  set externalAddr($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasExternalAddr() => $_has(1);
  @$pb.TagNumber(2)
  void clearExternalAddr() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get latencyMs => $_getIZ(2);
  @$pb.TagNumber(3)
  set latencyMs($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasLatencyMs() => $_has(2);
  @$pb.TagNumber(3)
  void clearLatencyMs() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get natMapping => $_getIZ(3);
  @$pb.TagNumber(4)
  set natMapping($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasNatMapping() => $_has(3);
  @$pb.TagNumber(4)
  void clearNatMapping() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.int get natFiltering => $_getIZ(4);
  @$pb.TagNumber(5)
  set natFiltering($core.int value) => $_setSignedInt32(4, value);
  @$pb.TagNumber(5)
  $core.bool hasNatFiltering() => $_has(4);
  @$pb.TagNumber(5)
  void clearNatFiltering() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.bool get isFinal => $_getBF(5);
  @$pb.TagNumber(6)
  set isFinal($core.bool value) => $_setBool(5, value);
  @$pb.TagNumber(6)
  $core.bool hasIsFinal() => $_has(5);
  @$pb.TagNumber(6)
  void clearIsFinal() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.String get error => $_getSZ(6);
  @$pb.TagNumber(7)
  set error($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasError() => $_has(6);
  @$pb.TagNumber(7)
  void clearError() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.bool get natTypeSupported => $_getBF(7);
  @$pb.TagNumber(8)
  set natTypeSupported($core.bool value) => $_setBool(7, value);
  @$pb.TagNumber(8)
  $core.bool hasNatTypeSupported() => $_has(7);
  @$pb.TagNumber(8)
  void clearNatTypeSupported() => $_clearField(8);
}

class TailscaleStatusUpdate extends $pb.GeneratedMessage {
  factory TailscaleStatusUpdate({
    $core.Iterable<TailscaleEndpointStatus>? endpoints,
  }) {
    final result = TailscaleStatusUpdate._();
    if (endpoints != null) result.endpoints.addAll(endpoints);
    return result;
  }

  TailscaleStatusUpdate._();

  factory TailscaleStatusUpdate.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleStatusUpdate()..mergeFromBuffer(data, registry);
  factory TailscaleStatusUpdate.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleStatusUpdate()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleStatusUpdate',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleStatusUpdate.$_createMessage)
    ..pPM<TailscaleEndpointStatus>(1, _omitFieldNames ? '' : 'endpoints',
        subBuilder: TailscaleEndpointStatus.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleStatusUpdate clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleStatusUpdate copyWith(
          void Function(TailscaleStatusUpdate) updates) =>
      super.copyWith((message) => updates(message as TailscaleStatusUpdate))
          as TailscaleStatusUpdate;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscaleStatusUpdate() / TailscaleStatusUpdate.new instead')
  static TailscaleStatusUpdate create() => TailscaleStatusUpdate._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleStatusUpdate._();
  @$core.override
  TailscaleStatusUpdate createEmptyInstance() => TailscaleStatusUpdate._();
  @$core.pragma('dart2js:noInline')
  static TailscaleStatusUpdate getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleStatusUpdate>(
          TailscaleStatusUpdate.$_createMessage);
  static TailscaleStatusUpdate? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<TailscaleEndpointStatus> get endpoints => $_getList(0);
}

class TailscaleEndpointStatus extends $pb.GeneratedMessage {
  factory TailscaleEndpointStatus({
    $core.String? endpointTag,
    $core.String? backendState,
    $core.String? stateText,
    $core.String? authURL,
    $core.String? networkName,
    $core.String? magicDNSSuffix,
    TailscalePeer? self,
    $core.Iterable<TailscaleUserGroup>? userGroups,
    TailscalePeer? exitNode,
    $core.bool? keyAuth,
    $core.bool? canShareFiles,
    $core.int? waitingFileCount,
    $core.int? receivingFileCount,
    $core.int? unreadFileCount,
    $core.Iterable<$core.String>? certDomains,
  }) {
    final result = TailscaleEndpointStatus._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (backendState != null) result.backendState = backendState;
    if (stateText != null) result.stateText = stateText;
    if (authURL != null) result.authURL = authURL;
    if (networkName != null) result.networkName = networkName;
    if (magicDNSSuffix != null) result.magicDNSSuffix = magicDNSSuffix;
    if (self != null) result.self = self;
    if (userGroups != null) result.userGroups.addAll(userGroups);
    if (exitNode != null) result.exitNode = exitNode;
    if (keyAuth != null) result.keyAuth = keyAuth;
    if (canShareFiles != null) result.canShareFiles = canShareFiles;
    if (waitingFileCount != null) result.waitingFileCount = waitingFileCount;
    if (receivingFileCount != null)
      result.receivingFileCount = receivingFileCount;
    if (unreadFileCount != null) result.unreadFileCount = unreadFileCount;
    if (certDomains != null) result.certDomains.addAll(certDomains);
    return result;
  }

  TailscaleEndpointStatus._();

  factory TailscaleEndpointStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleEndpointStatus()..mergeFromBuffer(data, registry);
  factory TailscaleEndpointStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleEndpointStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleEndpointStatus',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleEndpointStatus.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'backendState', protoName: 'backendState')
    ..aOS(3, _omitFieldNames ? '' : 'stateText', protoName: 'stateText')
    ..aOS(4, _omitFieldNames ? '' : 'authURL', protoName: 'authURL')
    ..aOS(5, _omitFieldNames ? '' : 'networkName', protoName: 'networkName')
    ..aOS(6, _omitFieldNames ? '' : 'magicDNSSuffix',
        protoName: 'magicDNSSuffix')
    ..aOM<TailscalePeer>(7, _omitFieldNames ? '' : 'self',
        subBuilder: TailscalePeer.$_createMessage)
    ..pPM<TailscaleUserGroup>(8, _omitFieldNames ? '' : 'userGroups',
        protoName: 'userGroups', subBuilder: TailscaleUserGroup.$_createMessage)
    ..aOM<TailscalePeer>(9, _omitFieldNames ? '' : 'exitNode',
        protoName: 'exitNode', subBuilder: TailscalePeer.$_createMessage)
    ..aOB(10, _omitFieldNames ? '' : 'keyAuth', protoName: 'keyAuth')
    ..aOB(11, _omitFieldNames ? '' : 'canShareFiles',
        protoName: 'canShareFiles')
    ..aI(12, _omitFieldNames ? '' : 'waitingFileCount',
        protoName: 'waitingFileCount')
    ..aI(13, _omitFieldNames ? '' : 'receivingFileCount',
        protoName: 'receivingFileCount')
    ..aI(14, _omitFieldNames ? '' : 'unreadFileCount',
        protoName: 'unreadFileCount')
    ..pPS(15, _omitFieldNames ? '' : 'certDomains', protoName: 'certDomains')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleEndpointStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleEndpointStatus copyWith(
          void Function(TailscaleEndpointStatus) updates) =>
      super.copyWith((message) => updates(message as TailscaleEndpointStatus))
          as TailscaleEndpointStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscaleEndpointStatus() / TailscaleEndpointStatus.new instead')
  static TailscaleEndpointStatus create() => TailscaleEndpointStatus._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleEndpointStatus._();
  @$core.override
  TailscaleEndpointStatus createEmptyInstance() => TailscaleEndpointStatus._();
  @$core.pragma('dart2js:noInline')
  static TailscaleEndpointStatus getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleEndpointStatus>(
          TailscaleEndpointStatus.$_createMessage);
  static TailscaleEndpointStatus? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get backendState => $_getSZ(1);
  @$pb.TagNumber(2)
  set backendState($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasBackendState() => $_has(1);
  @$pb.TagNumber(2)
  void clearBackendState() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get stateText => $_getSZ(2);
  @$pb.TagNumber(3)
  set stateText($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasStateText() => $_has(2);
  @$pb.TagNumber(3)
  void clearStateText() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get authURL => $_getSZ(3);
  @$pb.TagNumber(4)
  set authURL($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasAuthURL() => $_has(3);
  @$pb.TagNumber(4)
  void clearAuthURL() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get networkName => $_getSZ(4);
  @$pb.TagNumber(5)
  set networkName($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasNetworkName() => $_has(4);
  @$pb.TagNumber(5)
  void clearNetworkName() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get magicDNSSuffix => $_getSZ(5);
  @$pb.TagNumber(6)
  set magicDNSSuffix($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasMagicDNSSuffix() => $_has(5);
  @$pb.TagNumber(6)
  void clearMagicDNSSuffix() => $_clearField(6);

  @$pb.TagNumber(7)
  TailscalePeer get self => $_getN(6);
  @$pb.TagNumber(7)
  set self(TailscalePeer value) => $_setField(7, value);
  @$pb.TagNumber(7)
  $core.bool hasSelf() => $_has(6);
  @$pb.TagNumber(7)
  void clearSelf() => $_clearField(7);
  @$pb.TagNumber(7)
  TailscalePeer ensureSelf() => $_ensure(6);

  @$pb.TagNumber(8)
  $pb.PbList<TailscaleUserGroup> get userGroups => $_getList(7);

  @$pb.TagNumber(9)
  TailscalePeer get exitNode => $_getN(8);
  @$pb.TagNumber(9)
  set exitNode(TailscalePeer value) => $_setField(9, value);
  @$pb.TagNumber(9)
  $core.bool hasExitNode() => $_has(8);
  @$pb.TagNumber(9)
  void clearExitNode() => $_clearField(9);
  @$pb.TagNumber(9)
  TailscalePeer ensureExitNode() => $_ensure(8);

  @$pb.TagNumber(10)
  $core.bool get keyAuth => $_getBF(9);
  @$pb.TagNumber(10)
  set keyAuth($core.bool value) => $_setBool(9, value);
  @$pb.TagNumber(10)
  $core.bool hasKeyAuth() => $_has(9);
  @$pb.TagNumber(10)
  void clearKeyAuth() => $_clearField(10);

  @$pb.TagNumber(11)
  $core.bool get canShareFiles => $_getBF(10);
  @$pb.TagNumber(11)
  set canShareFiles($core.bool value) => $_setBool(10, value);
  @$pb.TagNumber(11)
  $core.bool hasCanShareFiles() => $_has(10);
  @$pb.TagNumber(11)
  void clearCanShareFiles() => $_clearField(11);

  @$pb.TagNumber(12)
  $core.int get waitingFileCount => $_getIZ(11);
  @$pb.TagNumber(12)
  set waitingFileCount($core.int value) => $_setSignedInt32(11, value);
  @$pb.TagNumber(12)
  $core.bool hasWaitingFileCount() => $_has(11);
  @$pb.TagNumber(12)
  void clearWaitingFileCount() => $_clearField(12);

  @$pb.TagNumber(13)
  $core.int get receivingFileCount => $_getIZ(12);
  @$pb.TagNumber(13)
  set receivingFileCount($core.int value) => $_setSignedInt32(12, value);
  @$pb.TagNumber(13)
  $core.bool hasReceivingFileCount() => $_has(12);
  @$pb.TagNumber(13)
  void clearReceivingFileCount() => $_clearField(13);

  @$pb.TagNumber(14)
  $core.int get unreadFileCount => $_getIZ(13);
  @$pb.TagNumber(14)
  set unreadFileCount($core.int value) => $_setSignedInt32(13, value);
  @$pb.TagNumber(14)
  $core.bool hasUnreadFileCount() => $_has(13);
  @$pb.TagNumber(14)
  void clearUnreadFileCount() => $_clearField(14);

  @$pb.TagNumber(15)
  $pb.PbList<$core.String> get certDomains => $_getList(14);
}

class TailscaleUserGroup extends $pb.GeneratedMessage {
  factory TailscaleUserGroup({
    $fixnum.Int64? userID,
    $core.String? loginName,
    $core.String? displayName,
    $core.String? profilePicURL,
    $core.Iterable<TailscalePeer>? peers,
  }) {
    final result = TailscaleUserGroup._();
    if (userID != null) result.userID = userID;
    if (loginName != null) result.loginName = loginName;
    if (displayName != null) result.displayName = displayName;
    if (profilePicURL != null) result.profilePicURL = profilePicURL;
    if (peers != null) result.peers.addAll(peers);
    return result;
  }

  TailscaleUserGroup._();

  factory TailscaleUserGroup.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleUserGroup()..mergeFromBuffer(data, registry);
  factory TailscaleUserGroup.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleUserGroup()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleUserGroup',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleUserGroup.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'userID', protoName: 'userID')
    ..aOS(2, _omitFieldNames ? '' : 'loginName', protoName: 'loginName')
    ..aOS(3, _omitFieldNames ? '' : 'displayName', protoName: 'displayName')
    ..aOS(4, _omitFieldNames ? '' : 'profilePicURL', protoName: 'profilePicURL')
    ..pPM<TailscalePeer>(5, _omitFieldNames ? '' : 'peers',
        subBuilder: TailscalePeer.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleUserGroup clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleUserGroup copyWith(void Function(TailscaleUserGroup) updates) =>
      super.copyWith((message) => updates(message as TailscaleUserGroup))
          as TailscaleUserGroup;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscaleUserGroup() / TailscaleUserGroup.new instead')
  static TailscaleUserGroup create() => TailscaleUserGroup._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleUserGroup._();
  @$core.override
  TailscaleUserGroup createEmptyInstance() => TailscaleUserGroup._();
  @$core.pragma('dart2js:noInline')
  static TailscaleUserGroup getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleUserGroup>(
          TailscaleUserGroup.$_createMessage);
  static TailscaleUserGroup? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get userID => $_getI64(0);
  @$pb.TagNumber(1)
  set userID($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUserID() => $_has(0);
  @$pb.TagNumber(1)
  void clearUserID() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get loginName => $_getSZ(1);
  @$pb.TagNumber(2)
  set loginName($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasLoginName() => $_has(1);
  @$pb.TagNumber(2)
  void clearLoginName() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get displayName => $_getSZ(2);
  @$pb.TagNumber(3)
  set displayName($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDisplayName() => $_has(2);
  @$pb.TagNumber(3)
  void clearDisplayName() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get profilePicURL => $_getSZ(3);
  @$pb.TagNumber(4)
  set profilePicURL($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasProfilePicURL() => $_has(3);
  @$pb.TagNumber(4)
  void clearProfilePicURL() => $_clearField(4);

  @$pb.TagNumber(5)
  $pb.PbList<TailscalePeer> get peers => $_getList(4);
}

class TailscalePeer extends $pb.GeneratedMessage {
  factory TailscalePeer({
    $core.String? hostName,
    $core.String? dnsName,
    $core.String? os,
    $core.Iterable<$core.String>? tailscaleIPs,
    $core.bool? online,
    $core.bool? exitNode,
    $core.bool? exitNodeOption,
    $core.bool? active,
    $fixnum.Int64? rxBytes,
    $fixnum.Int64? txBytes,
    $fixnum.Int64? keyExpiry,
    $core.String? stableID,
    $core.bool? expired,
    $core.Iterable<$core.String>? sshHostKeys,
    $core.bool? shareeNode,
    $fixnum.Int64? lastSeen,
    $core.bool? canReceiveFiles,
  }) {
    final result = TailscalePeer._();
    if (hostName != null) result.hostName = hostName;
    if (dnsName != null) result.dnsName = dnsName;
    if (os != null) result.os = os;
    if (tailscaleIPs != null) result.tailscaleIPs.addAll(tailscaleIPs);
    if (online != null) result.online = online;
    if (exitNode != null) result.exitNode = exitNode;
    if (exitNodeOption != null) result.exitNodeOption = exitNodeOption;
    if (active != null) result.active = active;
    if (rxBytes != null) result.rxBytes = rxBytes;
    if (txBytes != null) result.txBytes = txBytes;
    if (keyExpiry != null) result.keyExpiry = keyExpiry;
    if (stableID != null) result.stableID = stableID;
    if (expired != null) result.expired = expired;
    if (sshHostKeys != null) result.sshHostKeys.addAll(sshHostKeys);
    if (shareeNode != null) result.shareeNode = shareeNode;
    if (lastSeen != null) result.lastSeen = lastSeen;
    if (canReceiveFiles != null) result.canReceiveFiles = canReceiveFiles;
    return result;
  }

  TailscalePeer._();

  factory TailscalePeer.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscalePeer()..mergeFromBuffer(data, registry);
  factory TailscalePeer.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscalePeer()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscalePeer',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscalePeer.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'hostName', protoName: 'hostName')
    ..aOS(2, _omitFieldNames ? '' : 'dnsName', protoName: 'dnsName')
    ..aOS(3, _omitFieldNames ? '' : 'os')
    ..pPS(4, _omitFieldNames ? '' : 'tailscaleIPs', protoName: 'tailscaleIPs')
    ..aOB(5, _omitFieldNames ? '' : 'online')
    ..aOB(6, _omitFieldNames ? '' : 'exitNode', protoName: 'exitNode')
    ..aOB(7, _omitFieldNames ? '' : 'exitNodeOption',
        protoName: 'exitNodeOption')
    ..aOB(8, _omitFieldNames ? '' : 'active')
    ..aInt64(9, _omitFieldNames ? '' : 'rxBytes', protoName: 'rxBytes')
    ..aInt64(10, _omitFieldNames ? '' : 'txBytes', protoName: 'txBytes')
    ..aInt64(11, _omitFieldNames ? '' : 'keyExpiry', protoName: 'keyExpiry')
    ..aOS(12, _omitFieldNames ? '' : 'stableID', protoName: 'stableID')
    ..aOB(13, _omitFieldNames ? '' : 'expired')
    ..pPS(14, _omitFieldNames ? '' : 'sshHostKeys', protoName: 'sshHostKeys')
    ..aOB(15, _omitFieldNames ? '' : 'shareeNode', protoName: 'shareeNode')
    ..aInt64(16, _omitFieldNames ? '' : 'lastSeen', protoName: 'lastSeen')
    ..aOB(17, _omitFieldNames ? '' : 'canReceiveFiles',
        protoName: 'canReceiveFiles')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscalePeer clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscalePeer copyWith(void Function(TailscalePeer) updates) =>
      super.copyWith((message) => updates(message as TailscalePeer))
          as TailscalePeer;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscalePeer() / TailscalePeer.new instead')
  static TailscalePeer create() => TailscalePeer._();
  static $pb.GeneratedMessage $_createMessage() => TailscalePeer._();
  @$core.override
  TailscalePeer createEmptyInstance() => TailscalePeer._();
  @$core.pragma('dart2js:noInline')
  static TailscalePeer getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TailscalePeer>(
          TailscalePeer.$_createMessage);
  static TailscalePeer? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get hostName => $_getSZ(0);
  @$pb.TagNumber(1)
  set hostName($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasHostName() => $_has(0);
  @$pb.TagNumber(1)
  void clearHostName() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get dnsName => $_getSZ(1);
  @$pb.TagNumber(2)
  set dnsName($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasDnsName() => $_has(1);
  @$pb.TagNumber(2)
  void clearDnsName() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get os => $_getSZ(2);
  @$pb.TagNumber(3)
  set os($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasOs() => $_has(2);
  @$pb.TagNumber(3)
  void clearOs() => $_clearField(3);

  @$pb.TagNumber(4)
  $pb.PbList<$core.String> get tailscaleIPs => $_getList(3);

  @$pb.TagNumber(5)
  $core.bool get online => $_getBF(4);
  @$pb.TagNumber(5)
  set online($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasOnline() => $_has(4);
  @$pb.TagNumber(5)
  void clearOnline() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.bool get exitNode => $_getBF(5);
  @$pb.TagNumber(6)
  set exitNode($core.bool value) => $_setBool(5, value);
  @$pb.TagNumber(6)
  $core.bool hasExitNode() => $_has(5);
  @$pb.TagNumber(6)
  void clearExitNode() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.bool get exitNodeOption => $_getBF(6);
  @$pb.TagNumber(7)
  set exitNodeOption($core.bool value) => $_setBool(6, value);
  @$pb.TagNumber(7)
  $core.bool hasExitNodeOption() => $_has(6);
  @$pb.TagNumber(7)
  void clearExitNodeOption() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.bool get active => $_getBF(7);
  @$pb.TagNumber(8)
  set active($core.bool value) => $_setBool(7, value);
  @$pb.TagNumber(8)
  $core.bool hasActive() => $_has(7);
  @$pb.TagNumber(8)
  void clearActive() => $_clearField(8);

  @$pb.TagNumber(9)
  $fixnum.Int64 get rxBytes => $_getI64(8);
  @$pb.TagNumber(9)
  set rxBytes($fixnum.Int64 value) => $_setInt64(8, value);
  @$pb.TagNumber(9)
  $core.bool hasRxBytes() => $_has(8);
  @$pb.TagNumber(9)
  void clearRxBytes() => $_clearField(9);

  @$pb.TagNumber(10)
  $fixnum.Int64 get txBytes => $_getI64(9);
  @$pb.TagNumber(10)
  set txBytes($fixnum.Int64 value) => $_setInt64(9, value);
  @$pb.TagNumber(10)
  $core.bool hasTxBytes() => $_has(9);
  @$pb.TagNumber(10)
  void clearTxBytes() => $_clearField(10);

  @$pb.TagNumber(11)
  $fixnum.Int64 get keyExpiry => $_getI64(10);
  @$pb.TagNumber(11)
  set keyExpiry($fixnum.Int64 value) => $_setInt64(10, value);
  @$pb.TagNumber(11)
  $core.bool hasKeyExpiry() => $_has(10);
  @$pb.TagNumber(11)
  void clearKeyExpiry() => $_clearField(11);

  @$pb.TagNumber(12)
  $core.String get stableID => $_getSZ(11);
  @$pb.TagNumber(12)
  set stableID($core.String value) => $_setString(11, value);
  @$pb.TagNumber(12)
  $core.bool hasStableID() => $_has(11);
  @$pb.TagNumber(12)
  void clearStableID() => $_clearField(12);

  @$pb.TagNumber(13)
  $core.bool get expired => $_getBF(12);
  @$pb.TagNumber(13)
  set expired($core.bool value) => $_setBool(12, value);
  @$pb.TagNumber(13)
  $core.bool hasExpired() => $_has(12);
  @$pb.TagNumber(13)
  void clearExpired() => $_clearField(13);

  @$pb.TagNumber(14)
  $pb.PbList<$core.String> get sshHostKeys => $_getList(13);

  @$pb.TagNumber(15)
  $core.bool get shareeNode => $_getBF(14);
  @$pb.TagNumber(15)
  set shareeNode($core.bool value) => $_setBool(14, value);
  @$pb.TagNumber(15)
  $core.bool hasShareeNode() => $_has(14);
  @$pb.TagNumber(15)
  void clearShareeNode() => $_clearField(15);

  @$pb.TagNumber(16)
  $fixnum.Int64 get lastSeen => $_getI64(15);
  @$pb.TagNumber(16)
  set lastSeen($fixnum.Int64 value) => $_setInt64(15, value);
  @$pb.TagNumber(16)
  $core.bool hasLastSeen() => $_has(15);
  @$pb.TagNumber(16)
  void clearLastSeen() => $_clearField(16);

  @$pb.TagNumber(17)
  $core.bool get canReceiveFiles => $_getBF(16);
  @$pb.TagNumber(17)
  set canReceiveFiles($core.bool value) => $_setBool(16, value);
  @$pb.TagNumber(17)
  $core.bool hasCanReceiveFiles() => $_has(16);
  @$pb.TagNumber(17)
  void clearCanReceiveFiles() => $_clearField(17);
}

class TailscalePingRequest extends $pb.GeneratedMessage {
  factory TailscalePingRequest({
    $core.String? endpointTag,
    $core.String? peerIP,
  }) {
    final result = TailscalePingRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (peerIP != null) result.peerIP = peerIP;
    return result;
  }

  TailscalePingRequest._();

  factory TailscalePingRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscalePingRequest()..mergeFromBuffer(data, registry);
  factory TailscalePingRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscalePingRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscalePingRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscalePingRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'peerIP', protoName: 'peerIP')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscalePingRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscalePingRequest copyWith(void Function(TailscalePingRequest) updates) =>
      super.copyWith((message) => updates(message as TailscalePingRequest))
          as TailscalePingRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscalePingRequest() / TailscalePingRequest.new instead')
  static TailscalePingRequest create() => TailscalePingRequest._();
  static $pb.GeneratedMessage $_createMessage() => TailscalePingRequest._();
  @$core.override
  TailscalePingRequest createEmptyInstance() => TailscalePingRequest._();
  @$core.pragma('dart2js:noInline')
  static TailscalePingRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscalePingRequest>(
          TailscalePingRequest.$_createMessage);
  static TailscalePingRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get peerIP => $_getSZ(1);
  @$pb.TagNumber(2)
  set peerIP($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasPeerIP() => $_has(1);
  @$pb.TagNumber(2)
  void clearPeerIP() => $_clearField(2);
}

class TailscalePingResponse extends $pb.GeneratedMessage {
  factory TailscalePingResponse({
    $core.double? latencyMs,
    $core.bool? isDirect,
    $core.String? endpoint,
    $core.int? derpRegionID,
    $core.String? derpRegionCode,
    $core.String? error,
    $core.String? peerRelay,
  }) {
    final result = TailscalePingResponse._();
    if (latencyMs != null) result.latencyMs = latencyMs;
    if (isDirect != null) result.isDirect = isDirect;
    if (endpoint != null) result.endpoint = endpoint;
    if (derpRegionID != null) result.derpRegionID = derpRegionID;
    if (derpRegionCode != null) result.derpRegionCode = derpRegionCode;
    if (error != null) result.error = error;
    if (peerRelay != null) result.peerRelay = peerRelay;
    return result;
  }

  TailscalePingResponse._();

  factory TailscalePingResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscalePingResponse()..mergeFromBuffer(data, registry);
  factory TailscalePingResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscalePingResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscalePingResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscalePingResponse.$_createMessage)
    ..aD(1, _omitFieldNames ? '' : 'latencyMs', protoName: 'latencyMs')
    ..aOB(2, _omitFieldNames ? '' : 'isDirect', protoName: 'isDirect')
    ..aOS(3, _omitFieldNames ? '' : 'endpoint')
    ..aI(4, _omitFieldNames ? '' : 'derpRegionID', protoName: 'derpRegionID')
    ..aOS(5, _omitFieldNames ? '' : 'derpRegionCode',
        protoName: 'derpRegionCode')
    ..aOS(6, _omitFieldNames ? '' : 'error')
    ..aOS(7, _omitFieldNames ? '' : 'peerRelay', protoName: 'peerRelay')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscalePingResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscalePingResponse copyWith(
          void Function(TailscalePingResponse) updates) =>
      super.copyWith((message) => updates(message as TailscalePingResponse))
          as TailscalePingResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscalePingResponse() / TailscalePingResponse.new instead')
  static TailscalePingResponse create() => TailscalePingResponse._();
  static $pb.GeneratedMessage $_createMessage() => TailscalePingResponse._();
  @$core.override
  TailscalePingResponse createEmptyInstance() => TailscalePingResponse._();
  @$core.pragma('dart2js:noInline')
  static TailscalePingResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscalePingResponse>(
          TailscalePingResponse.$_createMessage);
  static TailscalePingResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.double get latencyMs => $_getN(0);
  @$pb.TagNumber(1)
  set latencyMs($core.double value) => $_setDouble(0, value);
  @$pb.TagNumber(1)
  $core.bool hasLatencyMs() => $_has(0);
  @$pb.TagNumber(1)
  void clearLatencyMs() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.bool get isDirect => $_getBF(1);
  @$pb.TagNumber(2)
  set isDirect($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasIsDirect() => $_has(1);
  @$pb.TagNumber(2)
  void clearIsDirect() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get endpoint => $_getSZ(2);
  @$pb.TagNumber(3)
  set endpoint($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasEndpoint() => $_has(2);
  @$pb.TagNumber(3)
  void clearEndpoint() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get derpRegionID => $_getIZ(3);
  @$pb.TagNumber(4)
  set derpRegionID($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasDerpRegionID() => $_has(3);
  @$pb.TagNumber(4)
  void clearDerpRegionID() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get derpRegionCode => $_getSZ(4);
  @$pb.TagNumber(5)
  set derpRegionCode($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasDerpRegionCode() => $_has(4);
  @$pb.TagNumber(5)
  void clearDerpRegionCode() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get error => $_getSZ(5);
  @$pb.TagNumber(6)
  set error($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasError() => $_has(5);
  @$pb.TagNumber(6)
  void clearError() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.String get peerRelay => $_getSZ(6);
  @$pb.TagNumber(7)
  set peerRelay($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasPeerRelay() => $_has(6);
  @$pb.TagNumber(7)
  void clearPeerRelay() => $_clearField(7);
}

class SetTailscaleExitNodeRequest extends $pb.GeneratedMessage {
  factory SetTailscaleExitNodeRequest({
    $core.String? endpointTag,
    $core.String? stableID,
  }) {
    final result = SetTailscaleExitNodeRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (stableID != null) result.stableID = stableID;
    return result;
  }

  SetTailscaleExitNodeRequest._();

  factory SetTailscaleExitNodeRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetTailscaleExitNodeRequest()..mergeFromBuffer(data, registry);
  factory SetTailscaleExitNodeRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SetTailscaleExitNodeRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SetTailscaleExitNodeRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: SetTailscaleExitNodeRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'stableID', protoName: 'stableID')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetTailscaleExitNodeRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SetTailscaleExitNodeRequest copyWith(
          void Function(SetTailscaleExitNodeRequest) updates) =>
      super.copyWith(
              (message) => updates(message as SetTailscaleExitNodeRequest))
          as SetTailscaleExitNodeRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SetTailscaleExitNodeRequest() / SetTailscaleExitNodeRequest.new instead')
  static SetTailscaleExitNodeRequest create() =>
      SetTailscaleExitNodeRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      SetTailscaleExitNodeRequest._();
  @$core.override
  SetTailscaleExitNodeRequest createEmptyInstance() =>
      SetTailscaleExitNodeRequest._();
  @$core.pragma('dart2js:noInline')
  static SetTailscaleExitNodeRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SetTailscaleExitNodeRequest>(
          SetTailscaleExitNodeRequest.$_createMessage);
  static SetTailscaleExitNodeRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get stableID => $_getSZ(1);
  @$pb.TagNumber(2)
  set stableID($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasStableID() => $_has(1);
  @$pb.TagNumber(2)
  void clearStableID() => $_clearField(2);
}

class TailscaleLogoutRequest extends $pb.GeneratedMessage {
  factory TailscaleLogoutRequest({
    $core.String? endpointTag,
  }) {
    final result = TailscaleLogoutRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    return result;
  }

  TailscaleLogoutRequest._();

  factory TailscaleLogoutRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleLogoutRequest()..mergeFromBuffer(data, registry);
  factory TailscaleLogoutRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleLogoutRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleLogoutRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleLogoutRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleLogoutRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleLogoutRequest copyWith(
          void Function(TailscaleLogoutRequest) updates) =>
      super.copyWith((message) => updates(message as TailscaleLogoutRequest))
          as TailscaleLogoutRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscaleLogoutRequest() / TailscaleLogoutRequest.new instead')
  static TailscaleLogoutRequest create() => TailscaleLogoutRequest._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleLogoutRequest._();
  @$core.override
  TailscaleLogoutRequest createEmptyInstance() => TailscaleLogoutRequest._();
  @$core.pragma('dart2js:noInline')
  static TailscaleLogoutRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleLogoutRequest>(
          TailscaleLogoutRequest.$_createMessage);
  static TailscaleLogoutRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);
}

class TailscaleCertificateRequest extends $pb.GeneratedMessage {
  factory TailscaleCertificateRequest({
    $core.String? endpointTag,
    $core.String? domain,
    $fixnum.Int64? minValiditySeconds,
  }) {
    final result = TailscaleCertificateRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (domain != null) result.domain = domain;
    if (minValiditySeconds != null)
      result.minValiditySeconds = minValiditySeconds;
    return result;
  }

  TailscaleCertificateRequest._();

  factory TailscaleCertificateRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleCertificateRequest()..mergeFromBuffer(data, registry);
  factory TailscaleCertificateRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleCertificateRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleCertificateRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleCertificateRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'domain')
    ..aInt64(3, _omitFieldNames ? '' : 'minValiditySeconds',
        protoName: 'minValiditySeconds')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleCertificateRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleCertificateRequest copyWith(
          void Function(TailscaleCertificateRequest) updates) =>
      super.copyWith(
              (message) => updates(message as TailscaleCertificateRequest))
          as TailscaleCertificateRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscaleCertificateRequest() / TailscaleCertificateRequest.new instead')
  static TailscaleCertificateRequest create() =>
      TailscaleCertificateRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      TailscaleCertificateRequest._();
  @$core.override
  TailscaleCertificateRequest createEmptyInstance() =>
      TailscaleCertificateRequest._();
  @$core.pragma('dart2js:noInline')
  static TailscaleCertificateRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleCertificateRequest>(
          TailscaleCertificateRequest.$_createMessage);
  static TailscaleCertificateRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get domain => $_getSZ(1);
  @$pb.TagNumber(2)
  set domain($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasDomain() => $_has(1);
  @$pb.TagNumber(2)
  void clearDomain() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get minValiditySeconds => $_getI64(2);
  @$pb.TagNumber(3)
  set minValiditySeconds($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasMinValiditySeconds() => $_has(2);
  @$pb.TagNumber(3)
  void clearMinValiditySeconds() => $_clearField(3);
}

class TailscaleCertificate extends $pb.GeneratedMessage {
  factory TailscaleCertificate({
    $core.List<$core.int>? certificatePEM,
    $core.List<$core.int>? privateKeyPEM,
  }) {
    final result = TailscaleCertificate._();
    if (certificatePEM != null) result.certificatePEM = certificatePEM;
    if (privateKeyPEM != null) result.privateKeyPEM = privateKeyPEM;
    return result;
  }

  TailscaleCertificate._();

  factory TailscaleCertificate.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleCertificate()..mergeFromBuffer(data, registry);
  factory TailscaleCertificate.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleCertificate()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleCertificate',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleCertificate.$_createMessage)
    ..a<$core.List<$core.int>>(
        1, _omitFieldNames ? '' : 'certificatePEM', $pb.PbFieldType.OY,
        protoName: 'certificatePEM')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'privateKeyPEM', $pb.PbFieldType.OY,
        protoName: 'privateKeyPEM')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleCertificate clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleCertificate copyWith(void Function(TailscaleCertificate) updates) =>
      super.copyWith((message) => updates(message as TailscaleCertificate))
          as TailscaleCertificate;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscaleCertificate() / TailscaleCertificate.new instead')
  static TailscaleCertificate create() => TailscaleCertificate._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleCertificate._();
  @$core.override
  TailscaleCertificate createEmptyInstance() => TailscaleCertificate._();
  @$core.pragma('dart2js:noInline')
  static TailscaleCertificate getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleCertificate>(
          TailscaleCertificate.$_createMessage);
  static TailscaleCertificate? _defaultInstance;

  @$pb.TagNumber(1)
  $core.List<$core.int> get certificatePEM => $_getN(0);
  @$pb.TagNumber(1)
  set certificatePEM($core.List<$core.int> value) => $_setBytes(0, value);
  @$pb.TagNumber(1)
  $core.bool hasCertificatePEM() => $_has(0);
  @$pb.TagNumber(1)
  void clearCertificatePEM() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get privateKeyPEM => $_getN(1);
  @$pb.TagNumber(2)
  set privateKeyPEM($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasPrivateKeyPEM() => $_has(1);
  @$pb.TagNumber(2)
  void clearPrivateKeyPEM() => $_clearField(2);
}

enum TailscaleSSHClientMessage_Message { start, input, resize, notSet }

class TailscaleSSHClientMessage extends $pb.GeneratedMessage {
  factory TailscaleSSHClientMessage({
    TailscaleSSHStart? start,
    TailscaleSSHInput? input,
    TailscaleSSHResize? resize,
  }) {
    final result = TailscaleSSHClientMessage._();
    if (start != null) result.start = start;
    if (input != null) result.input = input;
    if (resize != null) result.resize = resize;
    return result;
  }

  TailscaleSSHClientMessage._();

  factory TailscaleSSHClientMessage.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHClientMessage()..mergeFromBuffer(data, registry);
  factory TailscaleSSHClientMessage.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHClientMessage()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, TailscaleSSHClientMessage_Message>
      _TailscaleSSHClientMessage_MessageByTag = {
    1: TailscaleSSHClientMessage_Message.start,
    2: TailscaleSSHClientMessage_Message.input,
    3: TailscaleSSHClientMessage_Message.resize,
    0: TailscaleSSHClientMessage_Message.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHClientMessage',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHClientMessage.$_createMessage)
    ..oo(0, [1, 2, 3])
    ..aOM<TailscaleSSHStart>(1, _omitFieldNames ? '' : 'start',
        subBuilder: TailscaleSSHStart.$_createMessage)
    ..aOM<TailscaleSSHInput>(2, _omitFieldNames ? '' : 'input',
        subBuilder: TailscaleSSHInput.$_createMessage)
    ..aOM<TailscaleSSHResize>(3, _omitFieldNames ? '' : 'resize',
        subBuilder: TailscaleSSHResize.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHClientMessage clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHClientMessage copyWith(
          void Function(TailscaleSSHClientMessage) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHClientMessage))
          as TailscaleSSHClientMessage;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscaleSSHClientMessage() / TailscaleSSHClientMessage.new instead')
  static TailscaleSSHClientMessage create() => TailscaleSSHClientMessage._();
  static $pb.GeneratedMessage $_createMessage() =>
      TailscaleSSHClientMessage._();
  @$core.override
  TailscaleSSHClientMessage createEmptyInstance() =>
      TailscaleSSHClientMessage._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHClientMessage getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleSSHClientMessage>(
          TailscaleSSHClientMessage.$_createMessage);
  static TailscaleSSHClientMessage? _defaultInstance;

  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  TailscaleSSHClientMessage_Message whichMessage() =>
      _TailscaleSSHClientMessage_MessageByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  void clearMessage() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  TailscaleSSHStart get start => $_getN(0);
  @$pb.TagNumber(1)
  set start(TailscaleSSHStart value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasStart() => $_has(0);
  @$pb.TagNumber(1)
  void clearStart() => $_clearField(1);
  @$pb.TagNumber(1)
  TailscaleSSHStart ensureStart() => $_ensure(0);

  @$pb.TagNumber(2)
  TailscaleSSHInput get input => $_getN(1);
  @$pb.TagNumber(2)
  set input(TailscaleSSHInput value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasInput() => $_has(1);
  @$pb.TagNumber(2)
  void clearInput() => $_clearField(2);
  @$pb.TagNumber(2)
  TailscaleSSHInput ensureInput() => $_ensure(1);

  @$pb.TagNumber(3)
  TailscaleSSHResize get resize => $_getN(2);
  @$pb.TagNumber(3)
  set resize(TailscaleSSHResize value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasResize() => $_has(2);
  @$pb.TagNumber(3)
  void clearResize() => $_clearField(3);
  @$pb.TagNumber(3)
  TailscaleSSHResize ensureResize() => $_ensure(2);
}

class TailscaleSSHStart extends $pb.GeneratedMessage {
  factory TailscaleSSHStart({
    $core.String? endpointTag,
    $core.String? peerAddress,
    $core.String? username,
    $core.String? terminalType,
    $core.int? columns,
    $core.int? rows,
    $core.int? widthPixels,
    $core.int? heightPixels,
    $core.Iterable<$core.String>? hostKeys,
    $core.bool? forwardAgent,
  }) {
    final result = TailscaleSSHStart._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (peerAddress != null) result.peerAddress = peerAddress;
    if (username != null) result.username = username;
    if (terminalType != null) result.terminalType = terminalType;
    if (columns != null) result.columns = columns;
    if (rows != null) result.rows = rows;
    if (widthPixels != null) result.widthPixels = widthPixels;
    if (heightPixels != null) result.heightPixels = heightPixels;
    if (hostKeys != null) result.hostKeys.addAll(hostKeys);
    if (forwardAgent != null) result.forwardAgent = forwardAgent;
    return result;
  }

  TailscaleSSHStart._();

  factory TailscaleSSHStart.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHStart()..mergeFromBuffer(data, registry);
  factory TailscaleSSHStart.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHStart()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHStart',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHStart.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'peerAddress', protoName: 'peerAddress')
    ..aOS(3, _omitFieldNames ? '' : 'username')
    ..aOS(4, _omitFieldNames ? '' : 'terminalType', protoName: 'terminalType')
    ..aI(5, _omitFieldNames ? '' : 'columns')
    ..aI(6, _omitFieldNames ? '' : 'rows')
    ..aI(7, _omitFieldNames ? '' : 'widthPixels', protoName: 'widthPixels')
    ..aI(8, _omitFieldNames ? '' : 'heightPixels', protoName: 'heightPixels')
    ..pPS(9, _omitFieldNames ? '' : 'hostKeys', protoName: 'hostKeys')
    ..aOB(10, _omitFieldNames ? '' : 'forwardAgent')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHStart clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHStart copyWith(void Function(TailscaleSSHStart) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHStart))
          as TailscaleSSHStart;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscaleSSHStart() / TailscaleSSHStart.new instead')
  static TailscaleSSHStart create() => TailscaleSSHStart._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleSSHStart._();
  @$core.override
  TailscaleSSHStart createEmptyInstance() => TailscaleSSHStart._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHStart getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TailscaleSSHStart>(
          TailscaleSSHStart.$_createMessage);
  static TailscaleSSHStart? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get peerAddress => $_getSZ(1);
  @$pb.TagNumber(2)
  set peerAddress($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasPeerAddress() => $_has(1);
  @$pb.TagNumber(2)
  void clearPeerAddress() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get username => $_getSZ(2);
  @$pb.TagNumber(3)
  set username($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasUsername() => $_has(2);
  @$pb.TagNumber(3)
  void clearUsername() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get terminalType => $_getSZ(3);
  @$pb.TagNumber(4)
  set terminalType($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasTerminalType() => $_has(3);
  @$pb.TagNumber(4)
  void clearTerminalType() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.int get columns => $_getIZ(4);
  @$pb.TagNumber(5)
  set columns($core.int value) => $_setSignedInt32(4, value);
  @$pb.TagNumber(5)
  $core.bool hasColumns() => $_has(4);
  @$pb.TagNumber(5)
  void clearColumns() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.int get rows => $_getIZ(5);
  @$pb.TagNumber(6)
  set rows($core.int value) => $_setSignedInt32(5, value);
  @$pb.TagNumber(6)
  $core.bool hasRows() => $_has(5);
  @$pb.TagNumber(6)
  void clearRows() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.int get widthPixels => $_getIZ(6);
  @$pb.TagNumber(7)
  set widthPixels($core.int value) => $_setSignedInt32(6, value);
  @$pb.TagNumber(7)
  $core.bool hasWidthPixels() => $_has(6);
  @$pb.TagNumber(7)
  void clearWidthPixels() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.int get heightPixels => $_getIZ(7);
  @$pb.TagNumber(8)
  set heightPixels($core.int value) => $_setSignedInt32(7, value);
  @$pb.TagNumber(8)
  $core.bool hasHeightPixels() => $_has(7);
  @$pb.TagNumber(8)
  void clearHeightPixels() => $_clearField(8);

  @$pb.TagNumber(9)
  $pb.PbList<$core.String> get hostKeys => $_getList(8);

  @$pb.TagNumber(10)
  $core.bool get forwardAgent => $_getBF(9);
  @$pb.TagNumber(10)
  set forwardAgent($core.bool value) => $_setBool(9, value);
  @$pb.TagNumber(10)
  $core.bool hasForwardAgent() => $_has(9);
  @$pb.TagNumber(10)
  void clearForwardAgent() => $_clearField(10);
}

class TailscaleSSHInput extends $pb.GeneratedMessage {
  factory TailscaleSSHInput({
    $core.List<$core.int>? data,
  }) {
    final result = TailscaleSSHInput._();
    if (data != null) result.data = data;
    return result;
  }

  TailscaleSSHInput._();

  factory TailscaleSSHInput.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHInput()..mergeFromBuffer(data, registry);
  factory TailscaleSSHInput.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHInput()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHInput',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHInput.$_createMessage)
    ..a<$core.List<$core.int>>(
        1, _omitFieldNames ? '' : 'data', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHInput clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHInput copyWith(void Function(TailscaleSSHInput) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHInput))
          as TailscaleSSHInput;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscaleSSHInput() / TailscaleSSHInput.new instead')
  static TailscaleSSHInput create() => TailscaleSSHInput._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleSSHInput._();
  @$core.override
  TailscaleSSHInput createEmptyInstance() => TailscaleSSHInput._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHInput getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TailscaleSSHInput>(
          TailscaleSSHInput.$_createMessage);
  static TailscaleSSHInput? _defaultInstance;

  @$pb.TagNumber(1)
  $core.List<$core.int> get data => $_getN(0);
  @$pb.TagNumber(1)
  set data($core.List<$core.int> value) => $_setBytes(0, value);
  @$pb.TagNumber(1)
  $core.bool hasData() => $_has(0);
  @$pb.TagNumber(1)
  void clearData() => $_clearField(1);
}

class TailscaleSSHResize extends $pb.GeneratedMessage {
  factory TailscaleSSHResize({
    $core.int? columns,
    $core.int? rows,
    $core.int? widthPixels,
    $core.int? heightPixels,
  }) {
    final result = TailscaleSSHResize._();
    if (columns != null) result.columns = columns;
    if (rows != null) result.rows = rows;
    if (widthPixels != null) result.widthPixels = widthPixels;
    if (heightPixels != null) result.heightPixels = heightPixels;
    return result;
  }

  TailscaleSSHResize._();

  factory TailscaleSSHResize.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHResize()..mergeFromBuffer(data, registry);
  factory TailscaleSSHResize.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHResize()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHResize',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHResize.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'columns')
    ..aI(2, _omitFieldNames ? '' : 'rows')
    ..aI(3, _omitFieldNames ? '' : 'widthPixels', protoName: 'widthPixels')
    ..aI(4, _omitFieldNames ? '' : 'heightPixels', protoName: 'heightPixels')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHResize clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHResize copyWith(void Function(TailscaleSSHResize) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHResize))
          as TailscaleSSHResize;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscaleSSHResize() / TailscaleSSHResize.new instead')
  static TailscaleSSHResize create() => TailscaleSSHResize._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleSSHResize._();
  @$core.override
  TailscaleSSHResize createEmptyInstance() => TailscaleSSHResize._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHResize getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleSSHResize>(
          TailscaleSSHResize.$_createMessage);
  static TailscaleSSHResize? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get columns => $_getIZ(0);
  @$pb.TagNumber(1)
  set columns($core.int value) => $_setSignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasColumns() => $_has(0);
  @$pb.TagNumber(1)
  void clearColumns() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get rows => $_getIZ(1);
  @$pb.TagNumber(2)
  set rows($core.int value) => $_setSignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasRows() => $_has(1);
  @$pb.TagNumber(2)
  void clearRows() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get widthPixels => $_getIZ(2);
  @$pb.TagNumber(3)
  set widthPixels($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasWidthPixels() => $_has(2);
  @$pb.TagNumber(3)
  void clearWidthPixels() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get heightPixels => $_getIZ(3);
  @$pb.TagNumber(4)
  set heightPixels($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasHeightPixels() => $_has(3);
  @$pb.TagNumber(4)
  void clearHeightPixels() => $_clearField(4);
}

enum TailscaleSSHServerMessage_Message {
  authBanner,
  ready,
  output,
  exit,
  error,
  notSet
}

class TailscaleSSHServerMessage extends $pb.GeneratedMessage {
  factory TailscaleSSHServerMessage({
    TailscaleSSHAuthBanner? authBanner,
    TailscaleSSHReady? ready,
    TailscaleSSHOutput? output,
    TailscaleSSHExit? exit,
    TailscaleSSHError? error,
  }) {
    final result = TailscaleSSHServerMessage._();
    if (authBanner != null) result.authBanner = authBanner;
    if (ready != null) result.ready = ready;
    if (output != null) result.output = output;
    if (exit != null) result.exit = exit;
    if (error != null) result.error = error;
    return result;
  }

  TailscaleSSHServerMessage._();

  factory TailscaleSSHServerMessage.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHServerMessage()..mergeFromBuffer(data, registry);
  factory TailscaleSSHServerMessage.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHServerMessage()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, TailscaleSSHServerMessage_Message>
      _TailscaleSSHServerMessage_MessageByTag = {
    1: TailscaleSSHServerMessage_Message.authBanner,
    2: TailscaleSSHServerMessage_Message.ready,
    3: TailscaleSSHServerMessage_Message.output,
    4: TailscaleSSHServerMessage_Message.exit,
    5: TailscaleSSHServerMessage_Message.error,
    0: TailscaleSSHServerMessage_Message.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHServerMessage',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHServerMessage.$_createMessage)
    ..oo(0, [1, 2, 3, 4, 5])
    ..aOM<TailscaleSSHAuthBanner>(1, _omitFieldNames ? '' : 'authBanner',
        protoName: 'authBanner',
        subBuilder: TailscaleSSHAuthBanner.$_createMessage)
    ..aOM<TailscaleSSHReady>(2, _omitFieldNames ? '' : 'ready',
        subBuilder: TailscaleSSHReady.$_createMessage)
    ..aOM<TailscaleSSHOutput>(3, _omitFieldNames ? '' : 'output',
        subBuilder: TailscaleSSHOutput.$_createMessage)
    ..aOM<TailscaleSSHExit>(4, _omitFieldNames ? '' : 'exit',
        subBuilder: TailscaleSSHExit.$_createMessage)
    ..aOM<TailscaleSSHError>(5, _omitFieldNames ? '' : 'error',
        subBuilder: TailscaleSSHError.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHServerMessage clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHServerMessage copyWith(
          void Function(TailscaleSSHServerMessage) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHServerMessage))
          as TailscaleSSHServerMessage;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscaleSSHServerMessage() / TailscaleSSHServerMessage.new instead')
  static TailscaleSSHServerMessage create() => TailscaleSSHServerMessage._();
  static $pb.GeneratedMessage $_createMessage() =>
      TailscaleSSHServerMessage._();
  @$core.override
  TailscaleSSHServerMessage createEmptyInstance() =>
      TailscaleSSHServerMessage._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHServerMessage getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleSSHServerMessage>(
          TailscaleSSHServerMessage.$_createMessage);
  static TailscaleSSHServerMessage? _defaultInstance;

  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  @$pb.TagNumber(4)
  @$pb.TagNumber(5)
  TailscaleSSHServerMessage_Message whichMessage() =>
      _TailscaleSSHServerMessage_MessageByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  @$pb.TagNumber(4)
  @$pb.TagNumber(5)
  void clearMessage() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  TailscaleSSHAuthBanner get authBanner => $_getN(0);
  @$pb.TagNumber(1)
  set authBanner(TailscaleSSHAuthBanner value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasAuthBanner() => $_has(0);
  @$pb.TagNumber(1)
  void clearAuthBanner() => $_clearField(1);
  @$pb.TagNumber(1)
  TailscaleSSHAuthBanner ensureAuthBanner() => $_ensure(0);

  @$pb.TagNumber(2)
  TailscaleSSHReady get ready => $_getN(1);
  @$pb.TagNumber(2)
  set ready(TailscaleSSHReady value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasReady() => $_has(1);
  @$pb.TagNumber(2)
  void clearReady() => $_clearField(2);
  @$pb.TagNumber(2)
  TailscaleSSHReady ensureReady() => $_ensure(1);

  @$pb.TagNumber(3)
  TailscaleSSHOutput get output => $_getN(2);
  @$pb.TagNumber(3)
  set output(TailscaleSSHOutput value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasOutput() => $_has(2);
  @$pb.TagNumber(3)
  void clearOutput() => $_clearField(3);
  @$pb.TagNumber(3)
  TailscaleSSHOutput ensureOutput() => $_ensure(2);

  @$pb.TagNumber(4)
  TailscaleSSHExit get exit => $_getN(3);
  @$pb.TagNumber(4)
  set exit(TailscaleSSHExit value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasExit() => $_has(3);
  @$pb.TagNumber(4)
  void clearExit() => $_clearField(4);
  @$pb.TagNumber(4)
  TailscaleSSHExit ensureExit() => $_ensure(3);

  @$pb.TagNumber(5)
  TailscaleSSHError get error => $_getN(4);
  @$pb.TagNumber(5)
  set error(TailscaleSSHError value) => $_setField(5, value);
  @$pb.TagNumber(5)
  $core.bool hasError() => $_has(4);
  @$pb.TagNumber(5)
  void clearError() => $_clearField(5);
  @$pb.TagNumber(5)
  TailscaleSSHError ensureError() => $_ensure(4);
}

class TailscaleSSHAuthBanner extends $pb.GeneratedMessage {
  factory TailscaleSSHAuthBanner({
    $core.String? message,
  }) {
    final result = TailscaleSSHAuthBanner._();
    if (message != null) result.message = message;
    return result;
  }

  TailscaleSSHAuthBanner._();

  factory TailscaleSSHAuthBanner.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHAuthBanner()..mergeFromBuffer(data, registry);
  factory TailscaleSSHAuthBanner.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHAuthBanner()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHAuthBanner',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHAuthBanner.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'message')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHAuthBanner clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHAuthBanner copyWith(
          void Function(TailscaleSSHAuthBanner) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHAuthBanner))
          as TailscaleSSHAuthBanner;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TailscaleSSHAuthBanner() / TailscaleSSHAuthBanner.new instead')
  static TailscaleSSHAuthBanner create() => TailscaleSSHAuthBanner._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleSSHAuthBanner._();
  @$core.override
  TailscaleSSHAuthBanner createEmptyInstance() => TailscaleSSHAuthBanner._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHAuthBanner getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleSSHAuthBanner>(
          TailscaleSSHAuthBanner.$_createMessage);
  static TailscaleSSHAuthBanner? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get message => $_getSZ(0);
  @$pb.TagNumber(1)
  set message($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasMessage() => $_has(0);
  @$pb.TagNumber(1)
  void clearMessage() => $_clearField(1);
}

class TailscaleSSHReady extends $pb.GeneratedMessage {
  factory TailscaleSSHReady() => TailscaleSSHReady._();

  TailscaleSSHReady._();

  factory TailscaleSSHReady.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHReady()..mergeFromBuffer(data, registry);
  factory TailscaleSSHReady.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHReady()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHReady',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHReady.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHReady clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHReady copyWith(void Function(TailscaleSSHReady) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHReady))
          as TailscaleSSHReady;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscaleSSHReady() / TailscaleSSHReady.new instead')
  static TailscaleSSHReady create() => TailscaleSSHReady._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleSSHReady._();
  @$core.override
  TailscaleSSHReady createEmptyInstance() => TailscaleSSHReady._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHReady getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TailscaleSSHReady>(
          TailscaleSSHReady.$_createMessage);
  static TailscaleSSHReady? _defaultInstance;
}

class TailscaleSSHOutput extends $pb.GeneratedMessage {
  factory TailscaleSSHOutput({
    $core.List<$core.int>? data,
  }) {
    final result = TailscaleSSHOutput._();
    if (data != null) result.data = data;
    return result;
  }

  TailscaleSSHOutput._();

  factory TailscaleSSHOutput.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHOutput()..mergeFromBuffer(data, registry);
  factory TailscaleSSHOutput.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHOutput()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHOutput',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHOutput.$_createMessage)
    ..a<$core.List<$core.int>>(
        1, _omitFieldNames ? '' : 'data', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHOutput clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHOutput copyWith(void Function(TailscaleSSHOutput) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHOutput))
          as TailscaleSSHOutput;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscaleSSHOutput() / TailscaleSSHOutput.new instead')
  static TailscaleSSHOutput create() => TailscaleSSHOutput._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleSSHOutput._();
  @$core.override
  TailscaleSSHOutput createEmptyInstance() => TailscaleSSHOutput._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHOutput getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TailscaleSSHOutput>(
          TailscaleSSHOutput.$_createMessage);
  static TailscaleSSHOutput? _defaultInstance;

  @$pb.TagNumber(1)
  $core.List<$core.int> get data => $_getN(0);
  @$pb.TagNumber(1)
  set data($core.List<$core.int> value) => $_setBytes(0, value);
  @$pb.TagNumber(1)
  $core.bool hasData() => $_has(0);
  @$pb.TagNumber(1)
  void clearData() => $_clearField(1);
}

class TailscaleSSHExit extends $pb.GeneratedMessage {
  factory TailscaleSSHExit({
    $core.int? exitCode,
    $core.String? signal,
    $core.String? errorMessage,
  }) {
    final result = TailscaleSSHExit._();
    if (exitCode != null) result.exitCode = exitCode;
    if (signal != null) result.signal = signal;
    if (errorMessage != null) result.errorMessage = errorMessage;
    return result;
  }

  TailscaleSSHExit._();

  factory TailscaleSSHExit.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHExit()..mergeFromBuffer(data, registry);
  factory TailscaleSSHExit.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHExit()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHExit',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHExit.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'exitCode', protoName: 'exitCode')
    ..aOS(2, _omitFieldNames ? '' : 'signal')
    ..aOS(3, _omitFieldNames ? '' : 'errorMessage', protoName: 'errorMessage')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHExit clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHExit copyWith(void Function(TailscaleSSHExit) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHExit))
          as TailscaleSSHExit;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscaleSSHExit() / TailscaleSSHExit.new instead')
  static TailscaleSSHExit create() => TailscaleSSHExit._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleSSHExit._();
  @$core.override
  TailscaleSSHExit createEmptyInstance() => TailscaleSSHExit._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHExit getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TailscaleSSHExit>(
          TailscaleSSHExit.$_createMessage);
  static TailscaleSSHExit? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get exitCode => $_getIZ(0);
  @$pb.TagNumber(1)
  set exitCode($core.int value) => $_setSignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasExitCode() => $_has(0);
  @$pb.TagNumber(1)
  void clearExitCode() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get signal => $_getSZ(1);
  @$pb.TagNumber(2)
  set signal($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSignal() => $_has(1);
  @$pb.TagNumber(2)
  void clearSignal() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get errorMessage => $_getSZ(2);
  @$pb.TagNumber(3)
  set errorMessage($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasErrorMessage() => $_has(2);
  @$pb.TagNumber(3)
  void clearErrorMessage() => $_clearField(3);
}

class TailscaleSSHError extends $pb.GeneratedMessage {
  factory TailscaleSSHError({
    $core.String? message,
  }) {
    final result = TailscaleSSHError._();
    if (message != null) result.message = message;
    return result;
  }

  TailscaleSSHError._();

  factory TailscaleSSHError.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHError()..mergeFromBuffer(data, registry);
  factory TailscaleSSHError.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TailscaleSSHError()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TailscaleSSHError',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TailscaleSSHError.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'message')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHError clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TailscaleSSHError copyWith(void Function(TailscaleSSHError) updates) =>
      super.copyWith((message) => updates(message as TailscaleSSHError))
          as TailscaleSSHError;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TailscaleSSHError() / TailscaleSSHError.new instead')
  static TailscaleSSHError create() => TailscaleSSHError._();
  static $pb.GeneratedMessage $_createMessage() => TailscaleSSHError._();
  @$core.override
  TailscaleSSHError createEmptyInstance() => TailscaleSSHError._();
  @$core.pragma('dart2js:noInline')
  static TailscaleSSHError getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TailscaleSSHError>(
          TailscaleSSHError.$_createMessage);
  static TailscaleSSHError? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get message => $_getSZ(0);
  @$pb.TagNumber(1)
  set message($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasMessage() => $_has(0);
  @$pb.TagNumber(1)
  void clearMessage() => $_clearField(1);
}

class SubscribeTaildropInboxRequest extends $pb.GeneratedMessage {
  factory SubscribeTaildropInboxRequest({
    $core.String? endpointTag,
  }) {
    final result = SubscribeTaildropInboxRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    return result;
  }

  SubscribeTaildropInboxRequest._();

  factory SubscribeTaildropInboxRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SubscribeTaildropInboxRequest()..mergeFromBuffer(data, registry);
  factory SubscribeTaildropInboxRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SubscribeTaildropInboxRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SubscribeTaildropInboxRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: SubscribeTaildropInboxRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubscribeTaildropInboxRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SubscribeTaildropInboxRequest copyWith(
          void Function(SubscribeTaildropInboxRequest) updates) =>
      super.copyWith(
              (message) => updates(message as SubscribeTaildropInboxRequest))
          as SubscribeTaildropInboxRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use SubscribeTaildropInboxRequest() / SubscribeTaildropInboxRequest.new instead')
  static SubscribeTaildropInboxRequest create() =>
      SubscribeTaildropInboxRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      SubscribeTaildropInboxRequest._();
  @$core.override
  SubscribeTaildropInboxRequest createEmptyInstance() =>
      SubscribeTaildropInboxRequest._();
  @$core.pragma('dart2js:noInline')
  static SubscribeTaildropInboxRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<SubscribeTaildropInboxRequest>(
          SubscribeTaildropInboxRequest.$_createMessage);
  static SubscribeTaildropInboxRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);
}

class MarkTaildropInboxReadRequest extends $pb.GeneratedMessage {
  factory MarkTaildropInboxReadRequest({
    $core.String? endpointTag,
  }) {
    final result = MarkTaildropInboxReadRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    return result;
  }

  MarkTaildropInboxReadRequest._();

  factory MarkTaildropInboxReadRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      MarkTaildropInboxReadRequest()..mergeFromBuffer(data, registry);
  factory MarkTaildropInboxReadRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      MarkTaildropInboxReadRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'MarkTaildropInboxReadRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: MarkTaildropInboxReadRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  MarkTaildropInboxReadRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  MarkTaildropInboxReadRequest copyWith(
          void Function(MarkTaildropInboxReadRequest) updates) =>
      super.copyWith(
              (message) => updates(message as MarkTaildropInboxReadRequest))
          as MarkTaildropInboxReadRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use MarkTaildropInboxReadRequest() / MarkTaildropInboxReadRequest.new instead')
  static MarkTaildropInboxReadRequest create() =>
      MarkTaildropInboxReadRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      MarkTaildropInboxReadRequest._();
  @$core.override
  MarkTaildropInboxReadRequest createEmptyInstance() =>
      MarkTaildropInboxReadRequest._();
  @$core.pragma('dart2js:noInline')
  static MarkTaildropInboxReadRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<MarkTaildropInboxReadRequest>(
          MarkTaildropInboxReadRequest.$_createMessage);
  static MarkTaildropInboxReadRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);
}

class TaildropInbox extends $pb.GeneratedMessage {
  factory TaildropInbox({
    $core.String? endpointTag,
    $core.Iterable<TaildropFile>? files,
    $core.Iterable<TaildropReceivingFile>? receiving,
  }) {
    final result = TaildropInbox._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (files != null) result.files.addAll(files);
    if (receiving != null) result.receiving.addAll(receiving);
    return result;
  }

  TaildropInbox._();

  factory TaildropInbox.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropInbox()..mergeFromBuffer(data, registry);
  factory TaildropInbox.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropInbox()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropInbox',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropInbox.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..pPM<TaildropFile>(2, _omitFieldNames ? '' : 'files',
        subBuilder: TaildropFile.$_createMessage)
    ..pPM<TaildropReceivingFile>(3, _omitFieldNames ? '' : 'receiving',
        subBuilder: TaildropReceivingFile.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropInbox clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropInbox copyWith(void Function(TaildropInbox) updates) =>
      super.copyWith((message) => updates(message as TaildropInbox))
          as TaildropInbox;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TaildropInbox() / TaildropInbox.new instead')
  static TaildropInbox create() => TaildropInbox._();
  static $pb.GeneratedMessage $_createMessage() => TaildropInbox._();
  @$core.override
  TaildropInbox createEmptyInstance() => TaildropInbox._();
  @$core.pragma('dart2js:noInline')
  static TaildropInbox getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TaildropInbox>(
          TaildropInbox.$_createMessage);
  static TaildropInbox? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $pb.PbList<TaildropFile> get files => $_getList(1);

  @$pb.TagNumber(3)
  $pb.PbList<TaildropReceivingFile> get receiving => $_getList(2);
}

class TaildropFile extends $pb.GeneratedMessage {
  factory TaildropFile({
    $core.String? name,
    $fixnum.Int64? size,
    $core.String? senderName,
    $fixnum.Int64? modifiedAt,
  }) {
    final result = TaildropFile._();
    if (name != null) result.name = name;
    if (size != null) result.size = size;
    if (senderName != null) result.senderName = senderName;
    if (modifiedAt != null) result.modifiedAt = modifiedAt;
    return result;
  }

  TaildropFile._();

  factory TaildropFile.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropFile()..mergeFromBuffer(data, registry);
  factory TaildropFile.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropFile()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropFile',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropFile.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aInt64(2, _omitFieldNames ? '' : 'size')
    ..aOS(3, _omitFieldNames ? '' : 'senderName', protoName: 'senderName')
    ..aInt64(4, _omitFieldNames ? '' : 'modifiedAt', protoName: 'modifiedAt')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropFile clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropFile copyWith(void Function(TaildropFile) updates) =>
      super.copyWith((message) => updates(message as TaildropFile))
          as TaildropFile;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TaildropFile() / TaildropFile.new instead')
  static TaildropFile create() => TaildropFile._();
  static $pb.GeneratedMessage $_createMessage() => TaildropFile._();
  @$core.override
  TaildropFile createEmptyInstance() => TaildropFile._();
  @$core.pragma('dart2js:noInline')
  static TaildropFile getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TaildropFile>(
          TaildropFile.$_createMessage);
  static TaildropFile? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get size => $_getI64(1);
  @$pb.TagNumber(2)
  set size($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSize() => $_has(1);
  @$pb.TagNumber(2)
  void clearSize() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get senderName => $_getSZ(2);
  @$pb.TagNumber(3)
  set senderName($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasSenderName() => $_has(2);
  @$pb.TagNumber(3)
  void clearSenderName() => $_clearField(3);

  @$pb.TagNumber(4)
  $fixnum.Int64 get modifiedAt => $_getI64(3);
  @$pb.TagNumber(4)
  set modifiedAt($fixnum.Int64 value) => $_setInt64(3, value);
  @$pb.TagNumber(4)
  $core.bool hasModifiedAt() => $_has(3);
  @$pb.TagNumber(4)
  void clearModifiedAt() => $_clearField(4);
}

class TaildropReceivingFile extends $pb.GeneratedMessage {
  factory TaildropReceivingFile({
    $core.String? name,
    $fixnum.Int64? size,
    $fixnum.Int64? receivedBytes,
    $core.String? senderID,
    $core.String? senderName,
  }) {
    final result = TaildropReceivingFile._();
    if (name != null) result.name = name;
    if (size != null) result.size = size;
    if (receivedBytes != null) result.receivedBytes = receivedBytes;
    if (senderID != null) result.senderID = senderID;
    if (senderName != null) result.senderName = senderName;
    return result;
  }

  TaildropReceivingFile._();

  factory TaildropReceivingFile.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropReceivingFile()..mergeFromBuffer(data, registry);
  factory TaildropReceivingFile.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropReceivingFile()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropReceivingFile',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropReceivingFile.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aInt64(2, _omitFieldNames ? '' : 'size')
    ..aInt64(3, _omitFieldNames ? '' : 'receivedBytes',
        protoName: 'receivedBytes')
    ..aOS(4, _omitFieldNames ? '' : 'senderID', protoName: 'senderID')
    ..aOS(5, _omitFieldNames ? '' : 'senderName', protoName: 'senderName')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropReceivingFile clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropReceivingFile copyWith(
          void Function(TaildropReceivingFile) updates) =>
      super.copyWith((message) => updates(message as TaildropReceivingFile))
          as TaildropReceivingFile;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TaildropReceivingFile() / TaildropReceivingFile.new instead')
  static TaildropReceivingFile create() => TaildropReceivingFile._();
  static $pb.GeneratedMessage $_createMessage() => TaildropReceivingFile._();
  @$core.override
  TaildropReceivingFile createEmptyInstance() => TaildropReceivingFile._();
  @$core.pragma('dart2js:noInline')
  static TaildropReceivingFile getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TaildropReceivingFile>(
          TaildropReceivingFile.$_createMessage);
  static TaildropReceivingFile? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get size => $_getI64(1);
  @$pb.TagNumber(2)
  set size($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSize() => $_has(1);
  @$pb.TagNumber(2)
  void clearSize() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get receivedBytes => $_getI64(2);
  @$pb.TagNumber(3)
  set receivedBytes($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasReceivedBytes() => $_has(2);
  @$pb.TagNumber(3)
  void clearReceivedBytes() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get senderID => $_getSZ(3);
  @$pb.TagNumber(4)
  set senderID($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasSenderID() => $_has(3);
  @$pb.TagNumber(4)
  void clearSenderID() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get senderName => $_getSZ(4);
  @$pb.TagNumber(5)
  set senderName($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSenderName() => $_has(4);
  @$pb.TagNumber(5)
  void clearSenderName() => $_clearField(5);
}

enum TaildropSendClientMessage_Message { start, chunk, fileDone, notSet }

class TaildropSendClientMessage extends $pb.GeneratedMessage {
  factory TaildropSendClientMessage({
    TaildropSendStart? start,
    TaildropFileChunk? chunk,
    TaildropFileDone? fileDone,
  }) {
    final result = TaildropSendClientMessage._();
    if (start != null) result.start = start;
    if (chunk != null) result.chunk = chunk;
    if (fileDone != null) result.fileDone = fileDone;
    return result;
  }

  TaildropSendClientMessage._();

  factory TaildropSendClientMessage.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropSendClientMessage()..mergeFromBuffer(data, registry);
  factory TaildropSendClientMessage.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropSendClientMessage()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, TaildropSendClientMessage_Message>
      _TaildropSendClientMessage_MessageByTag = {
    1: TaildropSendClientMessage_Message.start,
    2: TaildropSendClientMessage_Message.chunk,
    3: TaildropSendClientMessage_Message.fileDone,
    0: TaildropSendClientMessage_Message.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropSendClientMessage',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropSendClientMessage.$_createMessage)
    ..oo(0, [1, 2, 3])
    ..aOM<TaildropSendStart>(1, _omitFieldNames ? '' : 'start',
        subBuilder: TaildropSendStart.$_createMessage)
    ..aOM<TaildropFileChunk>(2, _omitFieldNames ? '' : 'chunk',
        subBuilder: TaildropFileChunk.$_createMessage)
    ..aOM<TaildropFileDone>(3, _omitFieldNames ? '' : 'fileDone',
        protoName: 'fileDone', subBuilder: TaildropFileDone.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropSendClientMessage clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropSendClientMessage copyWith(
          void Function(TaildropSendClientMessage) updates) =>
      super.copyWith((message) => updates(message as TaildropSendClientMessage))
          as TaildropSendClientMessage;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TaildropSendClientMessage() / TaildropSendClientMessage.new instead')
  static TaildropSendClientMessage create() => TaildropSendClientMessage._();
  static $pb.GeneratedMessage $_createMessage() =>
      TaildropSendClientMessage._();
  @$core.override
  TaildropSendClientMessage createEmptyInstance() =>
      TaildropSendClientMessage._();
  @$core.pragma('dart2js:noInline')
  static TaildropSendClientMessage getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TaildropSendClientMessage>(
          TaildropSendClientMessage.$_createMessage);
  static TaildropSendClientMessage? _defaultInstance;

  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  TaildropSendClientMessage_Message whichMessage() =>
      _TaildropSendClientMessage_MessageByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  void clearMessage() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  TaildropSendStart get start => $_getN(0);
  @$pb.TagNumber(1)
  set start(TaildropSendStart value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasStart() => $_has(0);
  @$pb.TagNumber(1)
  void clearStart() => $_clearField(1);
  @$pb.TagNumber(1)
  TaildropSendStart ensureStart() => $_ensure(0);

  @$pb.TagNumber(2)
  TaildropFileChunk get chunk => $_getN(1);
  @$pb.TagNumber(2)
  set chunk(TaildropFileChunk value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasChunk() => $_has(1);
  @$pb.TagNumber(2)
  void clearChunk() => $_clearField(2);
  @$pb.TagNumber(2)
  TaildropFileChunk ensureChunk() => $_ensure(1);

  @$pb.TagNumber(3)
  TaildropFileDone get fileDone => $_getN(2);
  @$pb.TagNumber(3)
  set fileDone(TaildropFileDone value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasFileDone() => $_has(2);
  @$pb.TagNumber(3)
  void clearFileDone() => $_clearField(3);
  @$pb.TagNumber(3)
  TaildropFileDone ensureFileDone() => $_ensure(2);
}

class TaildropSendStart extends $pb.GeneratedMessage {
  factory TaildropSendStart({
    $core.String? endpointTag,
    $core.String? peerStableID,
    $core.Iterable<TaildropOutgoingFile>? files,
  }) {
    final result = TaildropSendStart._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (peerStableID != null) result.peerStableID = peerStableID;
    if (files != null) result.files.addAll(files);
    return result;
  }

  TaildropSendStart._();

  factory TaildropSendStart.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropSendStart()..mergeFromBuffer(data, registry);
  factory TaildropSendStart.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropSendStart()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropSendStart',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropSendStart.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'peerStableID', protoName: 'peerStableID')
    ..pPM<TaildropOutgoingFile>(3, _omitFieldNames ? '' : 'files',
        subBuilder: TaildropOutgoingFile.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropSendStart clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropSendStart copyWith(void Function(TaildropSendStart) updates) =>
      super.copyWith((message) => updates(message as TaildropSendStart))
          as TaildropSendStart;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TaildropSendStart() / TaildropSendStart.new instead')
  static TaildropSendStart create() => TaildropSendStart._();
  static $pb.GeneratedMessage $_createMessage() => TaildropSendStart._();
  @$core.override
  TaildropSendStart createEmptyInstance() => TaildropSendStart._();
  @$core.pragma('dart2js:noInline')
  static TaildropSendStart getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TaildropSendStart>(
          TaildropSendStart.$_createMessage);
  static TaildropSendStart? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get peerStableID => $_getSZ(1);
  @$pb.TagNumber(2)
  set peerStableID($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasPeerStableID() => $_has(1);
  @$pb.TagNumber(2)
  void clearPeerStableID() => $_clearField(2);

  @$pb.TagNumber(3)
  $pb.PbList<TaildropOutgoingFile> get files => $_getList(2);
}

class TaildropOutgoingFile extends $pb.GeneratedMessage {
  factory TaildropOutgoingFile({
    $core.String? name,
    $fixnum.Int64? size,
  }) {
    final result = TaildropOutgoingFile._();
    if (name != null) result.name = name;
    if (size != null) result.size = size;
    return result;
  }

  TaildropOutgoingFile._();

  factory TaildropOutgoingFile.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropOutgoingFile()..mergeFromBuffer(data, registry);
  factory TaildropOutgoingFile.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropOutgoingFile()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropOutgoingFile',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropOutgoingFile.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aInt64(2, _omitFieldNames ? '' : 'size')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropOutgoingFile clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropOutgoingFile copyWith(void Function(TaildropOutgoingFile) updates) =>
      super.copyWith((message) => updates(message as TaildropOutgoingFile))
          as TaildropOutgoingFile;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TaildropOutgoingFile() / TaildropOutgoingFile.new instead')
  static TaildropOutgoingFile create() => TaildropOutgoingFile._();
  static $pb.GeneratedMessage $_createMessage() => TaildropOutgoingFile._();
  @$core.override
  TaildropOutgoingFile createEmptyInstance() => TaildropOutgoingFile._();
  @$core.pragma('dart2js:noInline')
  static TaildropOutgoingFile getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TaildropOutgoingFile>(
          TaildropOutgoingFile.$_createMessage);
  static TaildropOutgoingFile? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get size => $_getI64(1);
  @$pb.TagNumber(2)
  set size($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSize() => $_has(1);
  @$pb.TagNumber(2)
  void clearSize() => $_clearField(2);
}

class TaildropFileChunk extends $pb.GeneratedMessage {
  factory TaildropFileChunk({
    $core.List<$core.int>? data,
  }) {
    final result = TaildropFileChunk._();
    if (data != null) result.data = data;
    return result;
  }

  TaildropFileChunk._();

  factory TaildropFileChunk.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropFileChunk()..mergeFromBuffer(data, registry);
  factory TaildropFileChunk.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropFileChunk()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropFileChunk',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropFileChunk.$_createMessage)
    ..a<$core.List<$core.int>>(
        1, _omitFieldNames ? '' : 'data', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropFileChunk clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropFileChunk copyWith(void Function(TaildropFileChunk) updates) =>
      super.copyWith((message) => updates(message as TaildropFileChunk))
          as TaildropFileChunk;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TaildropFileChunk() / TaildropFileChunk.new instead')
  static TaildropFileChunk create() => TaildropFileChunk._();
  static $pb.GeneratedMessage $_createMessage() => TaildropFileChunk._();
  @$core.override
  TaildropFileChunk createEmptyInstance() => TaildropFileChunk._();
  @$core.pragma('dart2js:noInline')
  static TaildropFileChunk getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TaildropFileChunk>(
          TaildropFileChunk.$_createMessage);
  static TaildropFileChunk? _defaultInstance;

  @$pb.TagNumber(1)
  $core.List<$core.int> get data => $_getN(0);
  @$pb.TagNumber(1)
  set data($core.List<$core.int> value) => $_setBytes(0, value);
  @$pb.TagNumber(1)
  $core.bool hasData() => $_has(0);
  @$pb.TagNumber(1)
  void clearData() => $_clearField(1);
}

class TaildropFileDone extends $pb.GeneratedMessage {
  factory TaildropFileDone() => TaildropFileDone._();

  TaildropFileDone._();

  factory TaildropFileDone.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropFileDone()..mergeFromBuffer(data, registry);
  factory TaildropFileDone.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropFileDone()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropFileDone',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropFileDone.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropFileDone clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropFileDone copyWith(void Function(TaildropFileDone) updates) =>
      super.copyWith((message) => updates(message as TaildropFileDone))
          as TaildropFileDone;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TaildropFileDone() / TaildropFileDone.new instead')
  static TaildropFileDone create() => TaildropFileDone._();
  static $pb.GeneratedMessage $_createMessage() => TaildropFileDone._();
  @$core.override
  TaildropFileDone createEmptyInstance() => TaildropFileDone._();
  @$core.pragma('dart2js:noInline')
  static TaildropFileDone getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TaildropFileDone>(
          TaildropFileDone.$_createMessage);
  static TaildropFileDone? _defaultInstance;
}

enum TaildropSendServerMessage_Message { progress, receivedBytes, notSet }

class TaildropSendServerMessage extends $pb.GeneratedMessage {
  factory TaildropSendServerMessage({
    TaildropSendProgress? progress,
    $fixnum.Int64? receivedBytes,
  }) {
    final result = TaildropSendServerMessage._();
    if (progress != null) result.progress = progress;
    if (receivedBytes != null) result.receivedBytes = receivedBytes;
    return result;
  }

  TaildropSendServerMessage._();

  factory TaildropSendServerMessage.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropSendServerMessage()..mergeFromBuffer(data, registry);
  factory TaildropSendServerMessage.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropSendServerMessage()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, TaildropSendServerMessage_Message>
      _TaildropSendServerMessage_MessageByTag = {
    1: TaildropSendServerMessage_Message.progress,
    2: TaildropSendServerMessage_Message.receivedBytes,
    0: TaildropSendServerMessage_Message.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropSendServerMessage',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropSendServerMessage.$_createMessage)
    ..oo(0, [1, 2])
    ..aOM<TaildropSendProgress>(1, _omitFieldNames ? '' : 'progress',
        subBuilder: TaildropSendProgress.$_createMessage)
    ..aInt64(2, _omitFieldNames ? '' : 'receivedBytes',
        protoName: 'receivedBytes')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropSendServerMessage clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropSendServerMessage copyWith(
          void Function(TaildropSendServerMessage) updates) =>
      super.copyWith((message) => updates(message as TaildropSendServerMessage))
          as TaildropSendServerMessage;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TaildropSendServerMessage() / TaildropSendServerMessage.new instead')
  static TaildropSendServerMessage create() => TaildropSendServerMessage._();
  static $pb.GeneratedMessage $_createMessage() =>
      TaildropSendServerMessage._();
  @$core.override
  TaildropSendServerMessage createEmptyInstance() =>
      TaildropSendServerMessage._();
  @$core.pragma('dart2js:noInline')
  static TaildropSendServerMessage getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TaildropSendServerMessage>(
          TaildropSendServerMessage.$_createMessage);
  static TaildropSendServerMessage? _defaultInstance;

  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  TaildropSendServerMessage_Message whichMessage() =>
      _TaildropSendServerMessage_MessageByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  void clearMessage() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  TaildropSendProgress get progress => $_getN(0);
  @$pb.TagNumber(1)
  set progress(TaildropSendProgress value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasProgress() => $_has(0);
  @$pb.TagNumber(1)
  void clearProgress() => $_clearField(1);
  @$pb.TagNumber(1)
  TaildropSendProgress ensureProgress() => $_ensure(0);

  @$pb.TagNumber(2)
  $fixnum.Int64 get receivedBytes => $_getI64(1);
  @$pb.TagNumber(2)
  set receivedBytes($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasReceivedBytes() => $_has(1);
  @$pb.TagNumber(2)
  void clearReceivedBytes() => $_clearField(2);
}

class TaildropSendProgress extends $pb.GeneratedMessage {
  factory TaildropSendProgress({
    $core.int? fileIndex,
    $fixnum.Int64? sentBytes,
    $core.bool? fileCompleted,
  }) {
    final result = TaildropSendProgress._();
    if (fileIndex != null) result.fileIndex = fileIndex;
    if (sentBytes != null) result.sentBytes = sentBytes;
    if (fileCompleted != null) result.fileCompleted = fileCompleted;
    return result;
  }

  TaildropSendProgress._();

  factory TaildropSendProgress.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropSendProgress()..mergeFromBuffer(data, registry);
  factory TaildropSendProgress.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaildropSendProgress()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaildropSendProgress',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: TaildropSendProgress.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'fileIndex', protoName: 'fileIndex')
    ..aInt64(2, _omitFieldNames ? '' : 'sentBytes', protoName: 'sentBytes')
    ..aOB(3, _omitFieldNames ? '' : 'fileCompleted', protoName: 'fileCompleted')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropSendProgress clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaildropSendProgress copyWith(void Function(TaildropSendProgress) updates) =>
      super.copyWith((message) => updates(message as TaildropSendProgress))
          as TaildropSendProgress;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use TaildropSendProgress() / TaildropSendProgress.new instead')
  static TaildropSendProgress create() => TaildropSendProgress._();
  static $pb.GeneratedMessage $_createMessage() => TaildropSendProgress._();
  @$core.override
  TaildropSendProgress createEmptyInstance() => TaildropSendProgress._();
  @$core.pragma('dart2js:noInline')
  static TaildropSendProgress getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<TaildropSendProgress>(
          TaildropSendProgress.$_createMessage);
  static TaildropSendProgress? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get fileIndex => $_getIZ(0);
  @$pb.TagNumber(1)
  set fileIndex($core.int value) => $_setSignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasFileIndex() => $_has(0);
  @$pb.TagNumber(1)
  void clearFileIndex() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get sentBytes => $_getI64(1);
  @$pb.TagNumber(2)
  set sentBytes($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSentBytes() => $_has(1);
  @$pb.TagNumber(2)
  void clearSentBytes() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.bool get fileCompleted => $_getBF(2);
  @$pb.TagNumber(3)
  set fileCompleted($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasFileCompleted() => $_has(2);
  @$pb.TagNumber(3)
  void clearFileCompleted() => $_clearField(3);
}

class DownloadTaildropFileRequest extends $pb.GeneratedMessage {
  factory DownloadTaildropFileRequest({
    $core.String? endpointTag,
    $core.String? name,
  }) {
    final result = DownloadTaildropFileRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (name != null) result.name = name;
    return result;
  }

  DownloadTaildropFileRequest._();

  factory DownloadTaildropFileRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DownloadTaildropFileRequest()..mergeFromBuffer(data, registry);
  factory DownloadTaildropFileRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DownloadTaildropFileRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DownloadTaildropFileRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: DownloadTaildropFileRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'name')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DownloadTaildropFileRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DownloadTaildropFileRequest copyWith(
          void Function(DownloadTaildropFileRequest) updates) =>
      super.copyWith(
              (message) => updates(message as DownloadTaildropFileRequest))
          as DownloadTaildropFileRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use DownloadTaildropFileRequest() / DownloadTaildropFileRequest.new instead')
  static DownloadTaildropFileRequest create() =>
      DownloadTaildropFileRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      DownloadTaildropFileRequest._();
  @$core.override
  DownloadTaildropFileRequest createEmptyInstance() =>
      DownloadTaildropFileRequest._();
  @$core.pragma('dart2js:noInline')
  static DownloadTaildropFileRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<DownloadTaildropFileRequest>(
          DownloadTaildropFileRequest.$_createMessage);
  static DownloadTaildropFileRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get name => $_getSZ(1);
  @$pb.TagNumber(2)
  set name($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasName() => $_has(1);
  @$pb.TagNumber(2)
  void clearName() => $_clearField(2);
}

class DownloadTaildropFileChunk extends $pb.GeneratedMessage {
  factory DownloadTaildropFileChunk({
    $fixnum.Int64? size,
    $core.List<$core.int>? data,
  }) {
    final result = DownloadTaildropFileChunk._();
    if (size != null) result.size = size;
    if (data != null) result.data = data;
    return result;
  }

  DownloadTaildropFileChunk._();

  factory DownloadTaildropFileChunk.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DownloadTaildropFileChunk()..mergeFromBuffer(data, registry);
  factory DownloadTaildropFileChunk.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DownloadTaildropFileChunk()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DownloadTaildropFileChunk',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: DownloadTaildropFileChunk.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'size')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'data', $pb.PbFieldType.OY)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DownloadTaildropFileChunk clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DownloadTaildropFileChunk copyWith(
          void Function(DownloadTaildropFileChunk) updates) =>
      super.copyWith((message) => updates(message as DownloadTaildropFileChunk))
          as DownloadTaildropFileChunk;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use DownloadTaildropFileChunk() / DownloadTaildropFileChunk.new instead')
  static DownloadTaildropFileChunk create() => DownloadTaildropFileChunk._();
  static $pb.GeneratedMessage $_createMessage() =>
      DownloadTaildropFileChunk._();
  @$core.override
  DownloadTaildropFileChunk createEmptyInstance() =>
      DownloadTaildropFileChunk._();
  @$core.pragma('dart2js:noInline')
  static DownloadTaildropFileChunk getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<DownloadTaildropFileChunk>(
          DownloadTaildropFileChunk.$_createMessage);
  static DownloadTaildropFileChunk? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get size => $_getI64(0);
  @$pb.TagNumber(1)
  set size($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasSize() => $_has(0);
  @$pb.TagNumber(1)
  void clearSize() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.List<$core.int> get data => $_getN(1);
  @$pb.TagNumber(2)
  set data($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasData() => $_has(1);
  @$pb.TagNumber(2)
  void clearData() => $_clearField(2);
}

class DeleteTaildropFileRequest extends $pb.GeneratedMessage {
  factory DeleteTaildropFileRequest({
    $core.String? endpointTag,
    $core.String? name,
  }) {
    final result = DeleteTaildropFileRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (name != null) result.name = name;
    return result;
  }

  DeleteTaildropFileRequest._();

  factory DeleteTaildropFileRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DeleteTaildropFileRequest()..mergeFromBuffer(data, registry);
  factory DeleteTaildropFileRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      DeleteTaildropFileRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'DeleteTaildropFileRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: DeleteTaildropFileRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'name')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DeleteTaildropFileRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  DeleteTaildropFileRequest copyWith(
          void Function(DeleteTaildropFileRequest) updates) =>
      super.copyWith((message) => updates(message as DeleteTaildropFileRequest))
          as DeleteTaildropFileRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use DeleteTaildropFileRequest() / DeleteTaildropFileRequest.new instead')
  static DeleteTaildropFileRequest create() => DeleteTaildropFileRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      DeleteTaildropFileRequest._();
  @$core.override
  DeleteTaildropFileRequest createEmptyInstance() =>
      DeleteTaildropFileRequest._();
  @$core.pragma('dart2js:noInline')
  static DeleteTaildropFileRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<DeleteTaildropFileRequest>(
          DeleteTaildropFileRequest.$_createMessage);
  static DeleteTaildropFileRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get name => $_getSZ(1);
  @$pb.TagNumber(2)
  set name($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasName() => $_has(1);
  @$pb.TagNumber(2)
  void clearName() => $_clearField(2);
}

class CancelTaildropReceivingRequest extends $pb.GeneratedMessage {
  factory CancelTaildropReceivingRequest({
    $core.String? endpointTag,
    $core.String? senderID,
    $core.String? name,
  }) {
    final result = CancelTaildropReceivingRequest._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (senderID != null) result.senderID = senderID;
    if (name != null) result.name = name;
    return result;
  }

  CancelTaildropReceivingRequest._();

  factory CancelTaildropReceivingRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CancelTaildropReceivingRequest()..mergeFromBuffer(data, registry);
  factory CancelTaildropReceivingRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      CancelTaildropReceivingRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'CancelTaildropReceivingRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: CancelTaildropReceivingRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'senderID', protoName: 'senderID')
    ..aOS(3, _omitFieldNames ? '' : 'name')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CancelTaildropReceivingRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  CancelTaildropReceivingRequest copyWith(
          void Function(CancelTaildropReceivingRequest) updates) =>
      super.copyWith(
              (message) => updates(message as CancelTaildropReceivingRequest))
          as CancelTaildropReceivingRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use CancelTaildropReceivingRequest() / CancelTaildropReceivingRequest.new instead')
  static CancelTaildropReceivingRequest create() =>
      CancelTaildropReceivingRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      CancelTaildropReceivingRequest._();
  @$core.override
  CancelTaildropReceivingRequest createEmptyInstance() =>
      CancelTaildropReceivingRequest._();
  @$core.pragma('dart2js:noInline')
  static CancelTaildropReceivingRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<CancelTaildropReceivingRequest>(
          CancelTaildropReceivingRequest.$_createMessage);
  static CancelTaildropReceivingRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get senderID => $_getSZ(1);
  @$pb.TagNumber(2)
  set senderID($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSenderID() => $_has(1);
  @$pb.TagNumber(2)
  void clearSenderID() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get name => $_getSZ(2);
  @$pb.TagNumber(3)
  set name($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasName() => $_has(2);
  @$pb.TagNumber(3)
  void clearName() => $_clearField(3);
}

enum USBProviderMessage_Message { attach, detach, urbResponse, notSet }

class USBProviderMessage extends $pb.GeneratedMessage {
  factory USBProviderMessage({
    USBDeviceAttach? attach,
    USBDeviceDetach? detach,
    USBURBResponse? urbResponse,
  }) {
    final result = USBProviderMessage._();
    if (attach != null) result.attach = attach;
    if (detach != null) result.detach = detach;
    if (urbResponse != null) result.urbResponse = urbResponse;
    return result;
  }

  USBProviderMessage._();

  factory USBProviderMessage.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBProviderMessage()..mergeFromBuffer(data, registry);
  factory USBProviderMessage.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBProviderMessage()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, USBProviderMessage_Message>
      _USBProviderMessage_MessageByTag = {
    1: USBProviderMessage_Message.attach,
    2: USBProviderMessage_Message.detach,
    3: USBProviderMessage_Message.urbResponse,
    0: USBProviderMessage_Message.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBProviderMessage',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBProviderMessage.$_createMessage)
    ..oo(0, [1, 2, 3])
    ..aOM<USBDeviceAttach>(1, _omitFieldNames ? '' : 'attach',
        subBuilder: USBDeviceAttach.$_createMessage)
    ..aOM<USBDeviceDetach>(2, _omitFieldNames ? '' : 'detach',
        subBuilder: USBDeviceDetach.$_createMessage)
    ..aOM<USBURBResponse>(3, _omitFieldNames ? '' : 'urbResponse',
        protoName: 'urbResponse', subBuilder: USBURBResponse.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBProviderMessage clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBProviderMessage copyWith(void Function(USBProviderMessage) updates) =>
      super.copyWith((message) => updates(message as USBProviderMessage))
          as USBProviderMessage;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBProviderMessage() / USBProviderMessage.new instead')
  static USBProviderMessage create() => USBProviderMessage._();
  static $pb.GeneratedMessage $_createMessage() => USBProviderMessage._();
  @$core.override
  USBProviderMessage createEmptyInstance() => USBProviderMessage._();
  @$core.pragma('dart2js:noInline')
  static USBProviderMessage getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<USBProviderMessage>(
          USBProviderMessage.$_createMessage);
  static USBProviderMessage? _defaultInstance;

  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  USBProviderMessage_Message whichMessage() =>
      _USBProviderMessage_MessageByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  void clearMessage() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  USBDeviceAttach get attach => $_getN(0);
  @$pb.TagNumber(1)
  set attach(USBDeviceAttach value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasAttach() => $_has(0);
  @$pb.TagNumber(1)
  void clearAttach() => $_clearField(1);
  @$pb.TagNumber(1)
  USBDeviceAttach ensureAttach() => $_ensure(0);

  @$pb.TagNumber(2)
  USBDeviceDetach get detach => $_getN(1);
  @$pb.TagNumber(2)
  set detach(USBDeviceDetach value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasDetach() => $_has(1);
  @$pb.TagNumber(2)
  void clearDetach() => $_clearField(2);
  @$pb.TagNumber(2)
  USBDeviceDetach ensureDetach() => $_ensure(1);

  @$pb.TagNumber(3)
  USBURBResponse get urbResponse => $_getN(2);
  @$pb.TagNumber(3)
  set urbResponse(USBURBResponse value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasUrbResponse() => $_has(2);
  @$pb.TagNumber(3)
  void clearUrbResponse() => $_clearField(3);
  @$pb.TagNumber(3)
  USBURBResponse ensureUrbResponse() => $_ensure(2);
}

enum USBServerMessage_Message { ready, urbRequest, abort, error, notSet }

class USBServerMessage extends $pb.GeneratedMessage {
  factory USBServerMessage({
    USBDeviceReady? ready,
    USBURBRequest? urbRequest,
    USBEndpointAbort? abort,
    USBError? error,
  }) {
    final result = USBServerMessage._();
    if (ready != null) result.ready = ready;
    if (urbRequest != null) result.urbRequest = urbRequest;
    if (abort != null) result.abort = abort;
    if (error != null) result.error = error;
    return result;
  }

  USBServerMessage._();

  factory USBServerMessage.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBServerMessage()..mergeFromBuffer(data, registry);
  factory USBServerMessage.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBServerMessage()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, USBServerMessage_Message>
      _USBServerMessage_MessageByTag = {
    1: USBServerMessage_Message.ready,
    2: USBServerMessage_Message.urbRequest,
    3: USBServerMessage_Message.abort,
    4: USBServerMessage_Message.error,
    0: USBServerMessage_Message.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBServerMessage',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBServerMessage.$_createMessage)
    ..oo(0, [1, 2, 3, 4])
    ..aOM<USBDeviceReady>(1, _omitFieldNames ? '' : 'ready',
        subBuilder: USBDeviceReady.$_createMessage)
    ..aOM<USBURBRequest>(2, _omitFieldNames ? '' : 'urbRequest',
        protoName: 'urbRequest', subBuilder: USBURBRequest.$_createMessage)
    ..aOM<USBEndpointAbort>(3, _omitFieldNames ? '' : 'abort',
        subBuilder: USBEndpointAbort.$_createMessage)
    ..aOM<USBError>(4, _omitFieldNames ? '' : 'error',
        subBuilder: USBError.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBServerMessage clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBServerMessage copyWith(void Function(USBServerMessage) updates) =>
      super.copyWith((message) => updates(message as USBServerMessage))
          as USBServerMessage;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBServerMessage() / USBServerMessage.new instead')
  static USBServerMessage create() => USBServerMessage._();
  static $pb.GeneratedMessage $_createMessage() => USBServerMessage._();
  @$core.override
  USBServerMessage createEmptyInstance() => USBServerMessage._();
  @$core.pragma('dart2js:noInline')
  static USBServerMessage getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBServerMessage>(
          USBServerMessage.$_createMessage);
  static USBServerMessage? _defaultInstance;

  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  @$pb.TagNumber(4)
  USBServerMessage_Message whichMessage() =>
      _USBServerMessage_MessageByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  @$pb.TagNumber(3)
  @$pb.TagNumber(4)
  void clearMessage() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  USBDeviceReady get ready => $_getN(0);
  @$pb.TagNumber(1)
  set ready(USBDeviceReady value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasReady() => $_has(0);
  @$pb.TagNumber(1)
  void clearReady() => $_clearField(1);
  @$pb.TagNumber(1)
  USBDeviceReady ensureReady() => $_ensure(0);

  @$pb.TagNumber(2)
  USBURBRequest get urbRequest => $_getN(1);
  @$pb.TagNumber(2)
  set urbRequest(USBURBRequest value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasUrbRequest() => $_has(1);
  @$pb.TagNumber(2)
  void clearUrbRequest() => $_clearField(2);
  @$pb.TagNumber(2)
  USBURBRequest ensureUrbRequest() => $_ensure(1);

  @$pb.TagNumber(3)
  USBEndpointAbort get abort => $_getN(2);
  @$pb.TagNumber(3)
  set abort(USBEndpointAbort value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasAbort() => $_has(2);
  @$pb.TagNumber(3)
  void clearAbort() => $_clearField(3);
  @$pb.TagNumber(3)
  USBEndpointAbort ensureAbort() => $_ensure(2);

  @$pb.TagNumber(4)
  USBError get error => $_getN(3);
  @$pb.TagNumber(4)
  set error(USBError value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasError() => $_has(3);
  @$pb.TagNumber(4)
  void clearError() => $_clearField(4);
  @$pb.TagNumber(4)
  USBError ensureError() => $_ensure(3);
}

class USBDeviceDescriptor extends $pb.GeneratedMessage {
  factory USBDeviceDescriptor({
    $core.String? deviceId,
    $core.int? busNum,
    $core.int? devNum,
    $core.int? speed,
    $core.int? vendorId,
    $core.int? productId,
    $core.int? bcdDevice,
    $core.int? deviceClass,
    $core.int? deviceSubClass,
    $core.int? deviceProtocol,
    $core.int? configurationValue,
    $core.int? numConfigurations,
    $core.Iterable<USBInterface>? interfaces,
    $core.String? serial,
    $core.String? product,
  }) {
    final result = USBDeviceDescriptor._();
    if (deviceId != null) result.deviceId = deviceId;
    if (busNum != null) result.busNum = busNum;
    if (devNum != null) result.devNum = devNum;
    if (speed != null) result.speed = speed;
    if (vendorId != null) result.vendorId = vendorId;
    if (productId != null) result.productId = productId;
    if (bcdDevice != null) result.bcdDevice = bcdDevice;
    if (deviceClass != null) result.deviceClass = deviceClass;
    if (deviceSubClass != null) result.deviceSubClass = deviceSubClass;
    if (deviceProtocol != null) result.deviceProtocol = deviceProtocol;
    if (configurationValue != null)
      result.configurationValue = configurationValue;
    if (numConfigurations != null) result.numConfigurations = numConfigurations;
    if (interfaces != null) result.interfaces.addAll(interfaces);
    if (serial != null) result.serial = serial;
    if (product != null) result.product = product;
    return result;
  }

  USBDeviceDescriptor._();

  factory USBDeviceDescriptor.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBDeviceDescriptor()..mergeFromBuffer(data, registry);
  factory USBDeviceDescriptor.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBDeviceDescriptor()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBDeviceDescriptor',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBDeviceDescriptor.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'deviceId', protoName: 'deviceId')
    ..aI(2, _omitFieldNames ? '' : 'busNum',
        protoName: 'busNum', fieldType: $pb.PbFieldType.OU3)
    ..aI(3, _omitFieldNames ? '' : 'devNum',
        protoName: 'devNum', fieldType: $pb.PbFieldType.OU3)
    ..aI(4, _omitFieldNames ? '' : 'speed', fieldType: $pb.PbFieldType.OU3)
    ..aI(5, _omitFieldNames ? '' : 'vendorId',
        protoName: 'vendorId', fieldType: $pb.PbFieldType.OU3)
    ..aI(6, _omitFieldNames ? '' : 'productId',
        protoName: 'productId', fieldType: $pb.PbFieldType.OU3)
    ..aI(7, _omitFieldNames ? '' : 'bcdDevice',
        protoName: 'bcdDevice', fieldType: $pb.PbFieldType.OU3)
    ..aI(8, _omitFieldNames ? '' : 'deviceClass',
        protoName: 'deviceClass', fieldType: $pb.PbFieldType.OU3)
    ..aI(9, _omitFieldNames ? '' : 'deviceSubClass',
        protoName: 'deviceSubClass', fieldType: $pb.PbFieldType.OU3)
    ..aI(10, _omitFieldNames ? '' : 'deviceProtocol',
        protoName: 'deviceProtocol', fieldType: $pb.PbFieldType.OU3)
    ..aI(11, _omitFieldNames ? '' : 'configurationValue',
        protoName: 'configurationValue', fieldType: $pb.PbFieldType.OU3)
    ..aI(12, _omitFieldNames ? '' : 'numConfigurations',
        protoName: 'numConfigurations', fieldType: $pb.PbFieldType.OU3)
    ..pPM<USBInterface>(13, _omitFieldNames ? '' : 'interfaces',
        subBuilder: USBInterface.$_createMessage)
    ..aOS(14, _omitFieldNames ? '' : 'serial')
    ..aOS(15, _omitFieldNames ? '' : 'product')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBDeviceDescriptor clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBDeviceDescriptor copyWith(void Function(USBDeviceDescriptor) updates) =>
      super.copyWith((message) => updates(message as USBDeviceDescriptor))
          as USBDeviceDescriptor;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core
      .Deprecated('Use USBDeviceDescriptor() / USBDeviceDescriptor.new instead')
  static USBDeviceDescriptor create() => USBDeviceDescriptor._();
  static $pb.GeneratedMessage $_createMessage() => USBDeviceDescriptor._();
  @$core.override
  USBDeviceDescriptor createEmptyInstance() => USBDeviceDescriptor._();
  @$core.pragma('dart2js:noInline')
  static USBDeviceDescriptor getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<USBDeviceDescriptor>(
          USBDeviceDescriptor.$_createMessage);
  static USBDeviceDescriptor? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get deviceId => $_getSZ(0);
  @$pb.TagNumber(1)
  set deviceId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasDeviceId() => $_has(0);
  @$pb.TagNumber(1)
  void clearDeviceId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get busNum => $_getIZ(1);
  @$pb.TagNumber(2)
  set busNum($core.int value) => $_setUnsignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasBusNum() => $_has(1);
  @$pb.TagNumber(2)
  void clearBusNum() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get devNum => $_getIZ(2);
  @$pb.TagNumber(3)
  set devNum($core.int value) => $_setUnsignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDevNum() => $_has(2);
  @$pb.TagNumber(3)
  void clearDevNum() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get speed => $_getIZ(3);
  @$pb.TagNumber(4)
  set speed($core.int value) => $_setUnsignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasSpeed() => $_has(3);
  @$pb.TagNumber(4)
  void clearSpeed() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.int get vendorId => $_getIZ(4);
  @$pb.TagNumber(5)
  set vendorId($core.int value) => $_setUnsignedInt32(4, value);
  @$pb.TagNumber(5)
  $core.bool hasVendorId() => $_has(4);
  @$pb.TagNumber(5)
  void clearVendorId() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.int get productId => $_getIZ(5);
  @$pb.TagNumber(6)
  set productId($core.int value) => $_setUnsignedInt32(5, value);
  @$pb.TagNumber(6)
  $core.bool hasProductId() => $_has(5);
  @$pb.TagNumber(6)
  void clearProductId() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.int get bcdDevice => $_getIZ(6);
  @$pb.TagNumber(7)
  set bcdDevice($core.int value) => $_setUnsignedInt32(6, value);
  @$pb.TagNumber(7)
  $core.bool hasBcdDevice() => $_has(6);
  @$pb.TagNumber(7)
  void clearBcdDevice() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.int get deviceClass => $_getIZ(7);
  @$pb.TagNumber(8)
  set deviceClass($core.int value) => $_setUnsignedInt32(7, value);
  @$pb.TagNumber(8)
  $core.bool hasDeviceClass() => $_has(7);
  @$pb.TagNumber(8)
  void clearDeviceClass() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.int get deviceSubClass => $_getIZ(8);
  @$pb.TagNumber(9)
  set deviceSubClass($core.int value) => $_setUnsignedInt32(8, value);
  @$pb.TagNumber(9)
  $core.bool hasDeviceSubClass() => $_has(8);
  @$pb.TagNumber(9)
  void clearDeviceSubClass() => $_clearField(9);

  @$pb.TagNumber(10)
  $core.int get deviceProtocol => $_getIZ(9);
  @$pb.TagNumber(10)
  set deviceProtocol($core.int value) => $_setUnsignedInt32(9, value);
  @$pb.TagNumber(10)
  $core.bool hasDeviceProtocol() => $_has(9);
  @$pb.TagNumber(10)
  void clearDeviceProtocol() => $_clearField(10);

  @$pb.TagNumber(11)
  $core.int get configurationValue => $_getIZ(10);
  @$pb.TagNumber(11)
  set configurationValue($core.int value) => $_setUnsignedInt32(10, value);
  @$pb.TagNumber(11)
  $core.bool hasConfigurationValue() => $_has(10);
  @$pb.TagNumber(11)
  void clearConfigurationValue() => $_clearField(11);

  @$pb.TagNumber(12)
  $core.int get numConfigurations => $_getIZ(11);
  @$pb.TagNumber(12)
  set numConfigurations($core.int value) => $_setUnsignedInt32(11, value);
  @$pb.TagNumber(12)
  $core.bool hasNumConfigurations() => $_has(11);
  @$pb.TagNumber(12)
  void clearNumConfigurations() => $_clearField(12);

  @$pb.TagNumber(13)
  $pb.PbList<USBInterface> get interfaces => $_getList(12);

  @$pb.TagNumber(14)
  $core.String get serial => $_getSZ(13);
  @$pb.TagNumber(14)
  set serial($core.String value) => $_setString(13, value);
  @$pb.TagNumber(14)
  $core.bool hasSerial() => $_has(13);
  @$pb.TagNumber(14)
  void clearSerial() => $_clearField(14);

  @$pb.TagNumber(15)
  $core.String get product => $_getSZ(14);
  @$pb.TagNumber(15)
  set product($core.String value) => $_setString(14, value);
  @$pb.TagNumber(15)
  $core.bool hasProduct() => $_has(14);
  @$pb.TagNumber(15)
  void clearProduct() => $_clearField(15);
}

class USBDeviceAttach extends $pb.GeneratedMessage {
  factory USBDeviceAttach({
    $core.String? serverTag,
    USBDeviceDescriptor? descriptor,
  }) {
    final result = USBDeviceAttach._();
    if (serverTag != null) result.serverTag = serverTag;
    if (descriptor != null) result.descriptor = descriptor;
    return result;
  }

  USBDeviceAttach._();

  factory USBDeviceAttach.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBDeviceAttach()..mergeFromBuffer(data, registry);
  factory USBDeviceAttach.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBDeviceAttach()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBDeviceAttach',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBDeviceAttach.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'serverTag', protoName: 'serverTag')
    ..aOM<USBDeviceDescriptor>(2, _omitFieldNames ? '' : 'descriptor',
        subBuilder: USBDeviceDescriptor.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBDeviceAttach clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBDeviceAttach copyWith(void Function(USBDeviceAttach) updates) =>
      super.copyWith((message) => updates(message as USBDeviceAttach))
          as USBDeviceAttach;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBDeviceAttach() / USBDeviceAttach.new instead')
  static USBDeviceAttach create() => USBDeviceAttach._();
  static $pb.GeneratedMessage $_createMessage() => USBDeviceAttach._();
  @$core.override
  USBDeviceAttach createEmptyInstance() => USBDeviceAttach._();
  @$core.pragma('dart2js:noInline')
  static USBDeviceAttach getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBDeviceAttach>(
          USBDeviceAttach.$_createMessage);
  static USBDeviceAttach? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get serverTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set serverTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasServerTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearServerTag() => $_clearField(1);

  @$pb.TagNumber(2)
  USBDeviceDescriptor get descriptor => $_getN(1);
  @$pb.TagNumber(2)
  set descriptor(USBDeviceDescriptor value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasDescriptor() => $_has(1);
  @$pb.TagNumber(2)
  void clearDescriptor() => $_clearField(2);
  @$pb.TagNumber(2)
  USBDeviceDescriptor ensureDescriptor() => $_ensure(1);
}

class USBInterface extends $pb.GeneratedMessage {
  factory USBInterface({
    $core.int? interfaceClass,
    $core.int? interfaceSubClass,
    $core.int? interfaceProtocol,
  }) {
    final result = USBInterface._();
    if (interfaceClass != null) result.interfaceClass = interfaceClass;
    if (interfaceSubClass != null) result.interfaceSubClass = interfaceSubClass;
    if (interfaceProtocol != null) result.interfaceProtocol = interfaceProtocol;
    return result;
  }

  USBInterface._();

  factory USBInterface.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBInterface()..mergeFromBuffer(data, registry);
  factory USBInterface.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBInterface()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBInterface',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBInterface.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'interfaceClass',
        protoName: 'interfaceClass', fieldType: $pb.PbFieldType.OU3)
    ..aI(2, _omitFieldNames ? '' : 'interfaceSubClass',
        protoName: 'interfaceSubClass', fieldType: $pb.PbFieldType.OU3)
    ..aI(3, _omitFieldNames ? '' : 'interfaceProtocol',
        protoName: 'interfaceProtocol', fieldType: $pb.PbFieldType.OU3)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBInterface clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBInterface copyWith(void Function(USBInterface) updates) =>
      super.copyWith((message) => updates(message as USBInterface))
          as USBInterface;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBInterface() / USBInterface.new instead')
  static USBInterface create() => USBInterface._();
  static $pb.GeneratedMessage $_createMessage() => USBInterface._();
  @$core.override
  USBInterface createEmptyInstance() => USBInterface._();
  @$core.pragma('dart2js:noInline')
  static USBInterface getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBInterface>(
          USBInterface.$_createMessage);
  static USBInterface? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get interfaceClass => $_getIZ(0);
  @$pb.TagNumber(1)
  set interfaceClass($core.int value) => $_setUnsignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasInterfaceClass() => $_has(0);
  @$pb.TagNumber(1)
  void clearInterfaceClass() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get interfaceSubClass => $_getIZ(1);
  @$pb.TagNumber(2)
  set interfaceSubClass($core.int value) => $_setUnsignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasInterfaceSubClass() => $_has(1);
  @$pb.TagNumber(2)
  void clearInterfaceSubClass() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get interfaceProtocol => $_getIZ(2);
  @$pb.TagNumber(3)
  set interfaceProtocol($core.int value) => $_setUnsignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasInterfaceProtocol() => $_has(2);
  @$pb.TagNumber(3)
  void clearInterfaceProtocol() => $_clearField(3);
}

class USBDeviceDetach extends $pb.GeneratedMessage {
  factory USBDeviceDetach({
    $core.String? deviceId,
  }) {
    final result = USBDeviceDetach._();
    if (deviceId != null) result.deviceId = deviceId;
    return result;
  }

  USBDeviceDetach._();

  factory USBDeviceDetach.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBDeviceDetach()..mergeFromBuffer(data, registry);
  factory USBDeviceDetach.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBDeviceDetach()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBDeviceDetach',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBDeviceDetach.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'deviceId', protoName: 'deviceId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBDeviceDetach clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBDeviceDetach copyWith(void Function(USBDeviceDetach) updates) =>
      super.copyWith((message) => updates(message as USBDeviceDetach))
          as USBDeviceDetach;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBDeviceDetach() / USBDeviceDetach.new instead')
  static USBDeviceDetach create() => USBDeviceDetach._();
  static $pb.GeneratedMessage $_createMessage() => USBDeviceDetach._();
  @$core.override
  USBDeviceDetach createEmptyInstance() => USBDeviceDetach._();
  @$core.pragma('dart2js:noInline')
  static USBDeviceDetach getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBDeviceDetach>(
          USBDeviceDetach.$_createMessage);
  static USBDeviceDetach? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get deviceId => $_getSZ(0);
  @$pb.TagNumber(1)
  set deviceId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasDeviceId() => $_has(0);
  @$pb.TagNumber(1)
  void clearDeviceId() => $_clearField(1);
}

class USBDeviceReady extends $pb.GeneratedMessage {
  factory USBDeviceReady({
    $core.String? deviceId,
    $core.String? busId,
  }) {
    final result = USBDeviceReady._();
    if (deviceId != null) result.deviceId = deviceId;
    if (busId != null) result.busId = busId;
    return result;
  }

  USBDeviceReady._();

  factory USBDeviceReady.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBDeviceReady()..mergeFromBuffer(data, registry);
  factory USBDeviceReady.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBDeviceReady()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBDeviceReady',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBDeviceReady.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'deviceId', protoName: 'deviceId')
    ..aOS(2, _omitFieldNames ? '' : 'busId', protoName: 'busId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBDeviceReady clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBDeviceReady copyWith(void Function(USBDeviceReady) updates) =>
      super.copyWith((message) => updates(message as USBDeviceReady))
          as USBDeviceReady;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBDeviceReady() / USBDeviceReady.new instead')
  static USBDeviceReady create() => USBDeviceReady._();
  static $pb.GeneratedMessage $_createMessage() => USBDeviceReady._();
  @$core.override
  USBDeviceReady createEmptyInstance() => USBDeviceReady._();
  @$core.pragma('dart2js:noInline')
  static USBDeviceReady getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBDeviceReady>(
          USBDeviceReady.$_createMessage);
  static USBDeviceReady? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get deviceId => $_getSZ(0);
  @$pb.TagNumber(1)
  set deviceId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasDeviceId() => $_has(0);
  @$pb.TagNumber(1)
  void clearDeviceId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get busId => $_getSZ(1);
  @$pb.TagNumber(2)
  set busId($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasBusId() => $_has(1);
  @$pb.TagNumber(2)
  void clearBusId() => $_clearField(2);
}

class USBURBRequest extends $pb.GeneratedMessage {
  factory USBURBRequest({
    $core.String? deviceId,
    $fixnum.Int64? seq,
    $core.int? endpoint,
    $core.bool? directionIn,
    $core.int? transferFlags,
    $core.List<$core.int>? setup,
    $core.int? transferBufferLength,
    $core.List<$core.int>? outData,
    $core.int? numberOfPackets,
    $core.int? startFrame,
    $core.int? interval,
    $core.Iterable<USBIsoPacket>? isoPackets,
  }) {
    final result = USBURBRequest._();
    if (deviceId != null) result.deviceId = deviceId;
    if (seq != null) result.seq = seq;
    if (endpoint != null) result.endpoint = endpoint;
    if (directionIn != null) result.directionIn = directionIn;
    if (transferFlags != null) result.transferFlags = transferFlags;
    if (setup != null) result.setup = setup;
    if (transferBufferLength != null)
      result.transferBufferLength = transferBufferLength;
    if (outData != null) result.outData = outData;
    if (numberOfPackets != null) result.numberOfPackets = numberOfPackets;
    if (startFrame != null) result.startFrame = startFrame;
    if (interval != null) result.interval = interval;
    if (isoPackets != null) result.isoPackets.addAll(isoPackets);
    return result;
  }

  USBURBRequest._();

  factory USBURBRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBURBRequest()..mergeFromBuffer(data, registry);
  factory USBURBRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBURBRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBURBRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBURBRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'deviceId', protoName: 'deviceId')
    ..a<$fixnum.Int64>(2, _omitFieldNames ? '' : 'seq', $pb.PbFieldType.OU6,
        defaultOrMaker: $fixnum.Int64.ZERO)
    ..aI(3, _omitFieldNames ? '' : 'endpoint', fieldType: $pb.PbFieldType.OU3)
    ..aOB(4, _omitFieldNames ? '' : 'directionIn', protoName: 'directionIn')
    ..aI(5, _omitFieldNames ? '' : 'transferFlags',
        protoName: 'transferFlags', fieldType: $pb.PbFieldType.OU3)
    ..a<$core.List<$core.int>>(
        6, _omitFieldNames ? '' : 'setup', $pb.PbFieldType.OY)
    ..aI(7, _omitFieldNames ? '' : 'transferBufferLength',
        protoName: 'transferBufferLength', fieldType: $pb.PbFieldType.OU3)
    ..a<$core.List<$core.int>>(
        8, _omitFieldNames ? '' : 'outData', $pb.PbFieldType.OY,
        protoName: 'outData')
    ..aI(9, _omitFieldNames ? '' : 'numberOfPackets',
        protoName: 'numberOfPackets')
    ..aI(10, _omitFieldNames ? '' : 'startFrame', protoName: 'startFrame')
    ..aI(11, _omitFieldNames ? '' : 'interval')
    ..pPM<USBIsoPacket>(12, _omitFieldNames ? '' : 'isoPackets',
        protoName: 'isoPackets', subBuilder: USBIsoPacket.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBURBRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBURBRequest copyWith(void Function(USBURBRequest) updates) =>
      super.copyWith((message) => updates(message as USBURBRequest))
          as USBURBRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBURBRequest() / USBURBRequest.new instead')
  static USBURBRequest create() => USBURBRequest._();
  static $pb.GeneratedMessage $_createMessage() => USBURBRequest._();
  @$core.override
  USBURBRequest createEmptyInstance() => USBURBRequest._();
  @$core.pragma('dart2js:noInline')
  static USBURBRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBURBRequest>(
          USBURBRequest.$_createMessage);
  static USBURBRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get deviceId => $_getSZ(0);
  @$pb.TagNumber(1)
  set deviceId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasDeviceId() => $_has(0);
  @$pb.TagNumber(1)
  void clearDeviceId() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get seq => $_getI64(1);
  @$pb.TagNumber(2)
  set seq($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSeq() => $_has(1);
  @$pb.TagNumber(2)
  void clearSeq() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get endpoint => $_getIZ(2);
  @$pb.TagNumber(3)
  set endpoint($core.int value) => $_setUnsignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasEndpoint() => $_has(2);
  @$pb.TagNumber(3)
  void clearEndpoint() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.bool get directionIn => $_getBF(3);
  @$pb.TagNumber(4)
  set directionIn($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasDirectionIn() => $_has(3);
  @$pb.TagNumber(4)
  void clearDirectionIn() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.int get transferFlags => $_getIZ(4);
  @$pb.TagNumber(5)
  set transferFlags($core.int value) => $_setUnsignedInt32(4, value);
  @$pb.TagNumber(5)
  $core.bool hasTransferFlags() => $_has(4);
  @$pb.TagNumber(5)
  void clearTransferFlags() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.List<$core.int> get setup => $_getN(5);
  @$pb.TagNumber(6)
  set setup($core.List<$core.int> value) => $_setBytes(5, value);
  @$pb.TagNumber(6)
  $core.bool hasSetup() => $_has(5);
  @$pb.TagNumber(6)
  void clearSetup() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.int get transferBufferLength => $_getIZ(6);
  @$pb.TagNumber(7)
  set transferBufferLength($core.int value) => $_setUnsignedInt32(6, value);
  @$pb.TagNumber(7)
  $core.bool hasTransferBufferLength() => $_has(6);
  @$pb.TagNumber(7)
  void clearTransferBufferLength() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.List<$core.int> get outData => $_getN(7);
  @$pb.TagNumber(8)
  set outData($core.List<$core.int> value) => $_setBytes(7, value);
  @$pb.TagNumber(8)
  $core.bool hasOutData() => $_has(7);
  @$pb.TagNumber(8)
  void clearOutData() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.int get numberOfPackets => $_getIZ(8);
  @$pb.TagNumber(9)
  set numberOfPackets($core.int value) => $_setSignedInt32(8, value);
  @$pb.TagNumber(9)
  $core.bool hasNumberOfPackets() => $_has(8);
  @$pb.TagNumber(9)
  void clearNumberOfPackets() => $_clearField(9);

  @$pb.TagNumber(10)
  $core.int get startFrame => $_getIZ(9);
  @$pb.TagNumber(10)
  set startFrame($core.int value) => $_setSignedInt32(9, value);
  @$pb.TagNumber(10)
  $core.bool hasStartFrame() => $_has(9);
  @$pb.TagNumber(10)
  void clearStartFrame() => $_clearField(10);

  @$pb.TagNumber(11)
  $core.int get interval => $_getIZ(10);
  @$pb.TagNumber(11)
  set interval($core.int value) => $_setSignedInt32(10, value);
  @$pb.TagNumber(11)
  $core.bool hasInterval() => $_has(10);
  @$pb.TagNumber(11)
  void clearInterval() => $_clearField(11);

  @$pb.TagNumber(12)
  $pb.PbList<USBIsoPacket> get isoPackets => $_getList(11);
}

class USBURBResponse extends $pb.GeneratedMessage {
  factory USBURBResponse({
    $core.String? deviceId,
    $fixnum.Int64? seq,
    $core.int? status,
    $core.int? actualLength,
    $core.List<$core.int>? inData,
    $core.Iterable<USBIsoPacket>? isoPackets,
  }) {
    final result = USBURBResponse._();
    if (deviceId != null) result.deviceId = deviceId;
    if (seq != null) result.seq = seq;
    if (status != null) result.status = status;
    if (actualLength != null) result.actualLength = actualLength;
    if (inData != null) result.inData = inData;
    if (isoPackets != null) result.isoPackets.addAll(isoPackets);
    return result;
  }

  USBURBResponse._();

  factory USBURBResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBURBResponse()..mergeFromBuffer(data, registry);
  factory USBURBResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBURBResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBURBResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBURBResponse.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'deviceId', protoName: 'deviceId')
    ..a<$fixnum.Int64>(2, _omitFieldNames ? '' : 'seq', $pb.PbFieldType.OU6,
        defaultOrMaker: $fixnum.Int64.ZERO)
    ..aI(3, _omitFieldNames ? '' : 'status')
    ..aI(4, _omitFieldNames ? '' : 'actualLength', protoName: 'actualLength')
    ..a<$core.List<$core.int>>(
        5, _omitFieldNames ? '' : 'inData', $pb.PbFieldType.OY,
        protoName: 'inData')
    ..pPM<USBIsoPacket>(6, _omitFieldNames ? '' : 'isoPackets',
        protoName: 'isoPackets', subBuilder: USBIsoPacket.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBURBResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBURBResponse copyWith(void Function(USBURBResponse) updates) =>
      super.copyWith((message) => updates(message as USBURBResponse))
          as USBURBResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBURBResponse() / USBURBResponse.new instead')
  static USBURBResponse create() => USBURBResponse._();
  static $pb.GeneratedMessage $_createMessage() => USBURBResponse._();
  @$core.override
  USBURBResponse createEmptyInstance() => USBURBResponse._();
  @$core.pragma('dart2js:noInline')
  static USBURBResponse getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBURBResponse>(
          USBURBResponse.$_createMessage);
  static USBURBResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get deviceId => $_getSZ(0);
  @$pb.TagNumber(1)
  set deviceId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasDeviceId() => $_has(0);
  @$pb.TagNumber(1)
  void clearDeviceId() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get seq => $_getI64(1);
  @$pb.TagNumber(2)
  set seq($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSeq() => $_has(1);
  @$pb.TagNumber(2)
  void clearSeq() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get status => $_getIZ(2);
  @$pb.TagNumber(3)
  set status($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasStatus() => $_has(2);
  @$pb.TagNumber(3)
  void clearStatus() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get actualLength => $_getIZ(3);
  @$pb.TagNumber(4)
  set actualLength($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasActualLength() => $_has(3);
  @$pb.TagNumber(4)
  void clearActualLength() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.List<$core.int> get inData => $_getN(4);
  @$pb.TagNumber(5)
  set inData($core.List<$core.int> value) => $_setBytes(4, value);
  @$pb.TagNumber(5)
  $core.bool hasInData() => $_has(4);
  @$pb.TagNumber(5)
  void clearInData() => $_clearField(5);

  @$pb.TagNumber(6)
  $pb.PbList<USBIsoPacket> get isoPackets => $_getList(5);
}

class USBIsoPacket extends $pb.GeneratedMessage {
  factory USBIsoPacket({
    $core.int? offset,
    $core.int? length,
    $core.int? actualLength,
    $core.int? status,
  }) {
    final result = USBIsoPacket._();
    if (offset != null) result.offset = offset;
    if (length != null) result.length = length;
    if (actualLength != null) result.actualLength = actualLength;
    if (status != null) result.status = status;
    return result;
  }

  USBIsoPacket._();

  factory USBIsoPacket.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBIsoPacket()..mergeFromBuffer(data, registry);
  factory USBIsoPacket.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBIsoPacket()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBIsoPacket',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBIsoPacket.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'offset')
    ..aI(2, _omitFieldNames ? '' : 'length')
    ..aI(3, _omitFieldNames ? '' : 'actualLength', protoName: 'actualLength')
    ..aI(4, _omitFieldNames ? '' : 'status')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBIsoPacket clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBIsoPacket copyWith(void Function(USBIsoPacket) updates) =>
      super.copyWith((message) => updates(message as USBIsoPacket))
          as USBIsoPacket;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBIsoPacket() / USBIsoPacket.new instead')
  static USBIsoPacket create() => USBIsoPacket._();
  static $pb.GeneratedMessage $_createMessage() => USBIsoPacket._();
  @$core.override
  USBIsoPacket createEmptyInstance() => USBIsoPacket._();
  @$core.pragma('dart2js:noInline')
  static USBIsoPacket getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBIsoPacket>(
          USBIsoPacket.$_createMessage);
  static USBIsoPacket? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get offset => $_getIZ(0);
  @$pb.TagNumber(1)
  set offset($core.int value) => $_setSignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasOffset() => $_has(0);
  @$pb.TagNumber(1)
  void clearOffset() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get length => $_getIZ(1);
  @$pb.TagNumber(2)
  set length($core.int value) => $_setSignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasLength() => $_has(1);
  @$pb.TagNumber(2)
  void clearLength() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get actualLength => $_getIZ(2);
  @$pb.TagNumber(3)
  set actualLength($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasActualLength() => $_has(2);
  @$pb.TagNumber(3)
  void clearActualLength() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.int get status => $_getIZ(3);
  @$pb.TagNumber(4)
  set status($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasStatus() => $_has(3);
  @$pb.TagNumber(4)
  void clearStatus() => $_clearField(4);
}

class USBEndpointAbort extends $pb.GeneratedMessage {
  factory USBEndpointAbort({
    $core.String? deviceId,
    $core.int? endpoint,
  }) {
    final result = USBEndpointAbort._();
    if (deviceId != null) result.deviceId = deviceId;
    if (endpoint != null) result.endpoint = endpoint;
    return result;
  }

  USBEndpointAbort._();

  factory USBEndpointAbort.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBEndpointAbort()..mergeFromBuffer(data, registry);
  factory USBEndpointAbort.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBEndpointAbort()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBEndpointAbort',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBEndpointAbort.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'deviceId', protoName: 'deviceId')
    ..aI(2, _omitFieldNames ? '' : 'endpoint', fieldType: $pb.PbFieldType.OU3)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBEndpointAbort clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBEndpointAbort copyWith(void Function(USBEndpointAbort) updates) =>
      super.copyWith((message) => updates(message as USBEndpointAbort))
          as USBEndpointAbort;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBEndpointAbort() / USBEndpointAbort.new instead')
  static USBEndpointAbort create() => USBEndpointAbort._();
  static $pb.GeneratedMessage $_createMessage() => USBEndpointAbort._();
  @$core.override
  USBEndpointAbort createEmptyInstance() => USBEndpointAbort._();
  @$core.pragma('dart2js:noInline')
  static USBEndpointAbort getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBEndpointAbort>(
          USBEndpointAbort.$_createMessage);
  static USBEndpointAbort? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get deviceId => $_getSZ(0);
  @$pb.TagNumber(1)
  set deviceId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasDeviceId() => $_has(0);
  @$pb.TagNumber(1)
  void clearDeviceId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get endpoint => $_getIZ(1);
  @$pb.TagNumber(2)
  set endpoint($core.int value) => $_setUnsignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasEndpoint() => $_has(1);
  @$pb.TagNumber(2)
  void clearEndpoint() => $_clearField(2);
}

class USBError extends $pb.GeneratedMessage {
  factory USBError({
    $core.String? deviceId,
    $core.String? message,
  }) {
    final result = USBError._();
    if (deviceId != null) result.deviceId = deviceId;
    if (message != null) result.message = message;
    return result;
  }

  USBError._();

  factory USBError.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBError()..mergeFromBuffer(data, registry);
  factory USBError.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBError()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBError',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBError.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'deviceId', protoName: 'deviceId')
    ..aOS(2, _omitFieldNames ? '' : 'message')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBError clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBError copyWith(void Function(USBError) updates) =>
      super.copyWith((message) => updates(message as USBError)) as USBError;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBError() / USBError.new instead')
  static USBError create() => USBError._();
  static $pb.GeneratedMessage $_createMessage() => USBError._();
  @$core.override
  USBError createEmptyInstance() => USBError._();
  @$core.pragma('dart2js:noInline')
  static USBError getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<USBError>(USBError.$_createMessage);
  static USBError? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get deviceId => $_getSZ(0);
  @$pb.TagNumber(1)
  set deviceId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasDeviceId() => $_has(0);
  @$pb.TagNumber(1)
  void clearDeviceId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get message => $_getSZ(1);
  @$pb.TagNumber(2)
  set message($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasMessage() => $_has(1);
  @$pb.TagNumber(2)
  void clearMessage() => $_clearField(2);
}

class USBIPServerStatusUpdate extends $pb.GeneratedMessage {
  factory USBIPServerStatusUpdate({
    $core.Iterable<USBIPServerStatus>? servers,
  }) {
    final result = USBIPServerStatusUpdate._();
    if (servers != null) result.servers.addAll(servers);
    return result;
  }

  USBIPServerStatusUpdate._();

  factory USBIPServerStatusUpdate.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBIPServerStatusUpdate()..mergeFromBuffer(data, registry);
  factory USBIPServerStatusUpdate.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBIPServerStatusUpdate()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBIPServerStatusUpdate',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBIPServerStatusUpdate.$_createMessage)
    ..pPM<USBIPServerStatus>(1, _omitFieldNames ? '' : 'servers',
        subBuilder: USBIPServerStatus.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBIPServerStatusUpdate clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBIPServerStatusUpdate copyWith(
          void Function(USBIPServerStatusUpdate) updates) =>
      super.copyWith((message) => updates(message as USBIPServerStatusUpdate))
          as USBIPServerStatusUpdate;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use USBIPServerStatusUpdate() / USBIPServerStatusUpdate.new instead')
  static USBIPServerStatusUpdate create() => USBIPServerStatusUpdate._();
  static $pb.GeneratedMessage $_createMessage() => USBIPServerStatusUpdate._();
  @$core.override
  USBIPServerStatusUpdate createEmptyInstance() => USBIPServerStatusUpdate._();
  @$core.pragma('dart2js:noInline')
  static USBIPServerStatusUpdate getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<USBIPServerStatusUpdate>(
          USBIPServerStatusUpdate.$_createMessage);
  static USBIPServerStatusUpdate? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<USBIPServerStatus> get servers => $_getList(0);
}

class USBIPServerStatus extends $pb.GeneratedMessage {
  factory USBIPServerStatus({
    $core.String? serverTag,
    $core.Iterable<USBSharedDevice>? devices,
  }) {
    final result = USBIPServerStatus._();
    if (serverTag != null) result.serverTag = serverTag;
    if (devices != null) result.devices.addAll(devices);
    return result;
  }

  USBIPServerStatus._();

  factory USBIPServerStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBIPServerStatus()..mergeFromBuffer(data, registry);
  factory USBIPServerStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBIPServerStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBIPServerStatus',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBIPServerStatus.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'serverTag', protoName: 'serverTag')
    ..pPM<USBSharedDevice>(2, _omitFieldNames ? '' : 'devices',
        subBuilder: USBSharedDevice.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBIPServerStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBIPServerStatus copyWith(void Function(USBIPServerStatus) updates) =>
      super.copyWith((message) => updates(message as USBIPServerStatus))
          as USBIPServerStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBIPServerStatus() / USBIPServerStatus.new instead')
  static USBIPServerStatus create() => USBIPServerStatus._();
  static $pb.GeneratedMessage $_createMessage() => USBIPServerStatus._();
  @$core.override
  USBIPServerStatus createEmptyInstance() => USBIPServerStatus._();
  @$core.pragma('dart2js:noInline')
  static USBIPServerStatus getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBIPServerStatus>(
          USBIPServerStatus.$_createMessage);
  static USBIPServerStatus? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get serverTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set serverTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasServerTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearServerTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $pb.PbList<USBSharedDevice> get devices => $_getList(1);
}

class USBSharedDevice extends $pb.GeneratedMessage {
  factory USBSharedDevice({
    USBDeviceDescriptor? descriptor,
    $core.String? busId,
    $core.String? stableId,
    USBBackend? backend,
    USBDeviceState? state,
  }) {
    final result = USBSharedDevice._();
    if (descriptor != null) result.descriptor = descriptor;
    if (busId != null) result.busId = busId;
    if (stableId != null) result.stableId = stableId;
    if (backend != null) result.backend = backend;
    if (state != null) result.state = state;
    return result;
  }

  USBSharedDevice._();

  factory USBSharedDevice.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBSharedDevice()..mergeFromBuffer(data, registry);
  factory USBSharedDevice.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      USBSharedDevice()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'USBSharedDevice',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: USBSharedDevice.$_createMessage)
    ..aOM<USBDeviceDescriptor>(1, _omitFieldNames ? '' : 'descriptor',
        subBuilder: USBDeviceDescriptor.$_createMessage)
    ..aOS(2, _omitFieldNames ? '' : 'busId', protoName: 'busId')
    ..aOS(3, _omitFieldNames ? '' : 'stableId', protoName: 'stableId')
    ..aE<USBBackend>(4, _omitFieldNames ? '' : 'backend',
        enumValues: USBBackend.values)
    ..aE<USBDeviceState>(5, _omitFieldNames ? '' : 'state',
        enumValues: USBDeviceState.values)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBSharedDevice clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  USBSharedDevice copyWith(void Function(USBSharedDevice) updates) =>
      super.copyWith((message) => updates(message as USBSharedDevice))
          as USBSharedDevice;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use USBSharedDevice() / USBSharedDevice.new instead')
  static USBSharedDevice create() => USBSharedDevice._();
  static $pb.GeneratedMessage $_createMessage() => USBSharedDevice._();
  @$core.override
  USBSharedDevice createEmptyInstance() => USBSharedDevice._();
  @$core.pragma('dart2js:noInline')
  static USBSharedDevice getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<USBSharedDevice>(
          USBSharedDevice.$_createMessage);
  static USBSharedDevice? _defaultInstance;

  @$pb.TagNumber(1)
  USBDeviceDescriptor get descriptor => $_getN(0);
  @$pb.TagNumber(1)
  set descriptor(USBDeviceDescriptor value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasDescriptor() => $_has(0);
  @$pb.TagNumber(1)
  void clearDescriptor() => $_clearField(1);
  @$pb.TagNumber(1)
  USBDeviceDescriptor ensureDescriptor() => $_ensure(0);

  @$pb.TagNumber(2)
  $core.String get busId => $_getSZ(1);
  @$pb.TagNumber(2)
  set busId($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasBusId() => $_has(1);
  @$pb.TagNumber(2)
  void clearBusId() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get stableId => $_getSZ(2);
  @$pb.TagNumber(3)
  set stableId($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasStableId() => $_has(2);
  @$pb.TagNumber(3)
  void clearStableId() => $_clearField(3);

  @$pb.TagNumber(4)
  USBBackend get backend => $_getN(3);
  @$pb.TagNumber(4)
  set backend(USBBackend value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasBackend() => $_has(3);
  @$pb.TagNumber(4)
  void clearBackend() => $_clearField(4);

  @$pb.TagNumber(5)
  USBDeviceState get state => $_getN(4);
  @$pb.TagNumber(5)
  set state(USBDeviceState value) => $_setField(5, value);
  @$pb.TagNumber(5)
  $core.bool hasState() => $_has(4);
  @$pb.TagNumber(5)
  void clearState() => $_clearField(5);
}

class OpenConnectStatusUpdate extends $pb.GeneratedMessage {
  factory OpenConnectStatusUpdate({
    $core.Iterable<OpenConnectEndpointStatus>? endpoints,
  }) {
    final result = OpenConnectStatusUpdate._();
    if (endpoints != null) result.endpoints.addAll(endpoints);
    return result;
  }

  OpenConnectStatusUpdate._();

  factory OpenConnectStatusUpdate.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectStatusUpdate()..mergeFromBuffer(data, registry);
  factory OpenConnectStatusUpdate.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectStatusUpdate()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectStatusUpdate',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectStatusUpdate.$_createMessage)
    ..pPM<OpenConnectEndpointStatus>(1, _omitFieldNames ? '' : 'endpoints',
        subBuilder: OpenConnectEndpointStatus.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectStatusUpdate clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectStatusUpdate copyWith(
          void Function(OpenConnectStatusUpdate) updates) =>
      super.copyWith((message) => updates(message as OpenConnectStatusUpdate))
          as OpenConnectStatusUpdate;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectStatusUpdate() / OpenConnectStatusUpdate.new instead')
  static OpenConnectStatusUpdate create() => OpenConnectStatusUpdate._();
  static $pb.GeneratedMessage $_createMessage() => OpenConnectStatusUpdate._();
  @$core.override
  OpenConnectStatusUpdate createEmptyInstance() => OpenConnectStatusUpdate._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectStatusUpdate getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectStatusUpdate>(
          OpenConnectStatusUpdate.$_createMessage);
  static OpenConnectStatusUpdate? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<OpenConnectEndpointStatus> get endpoints => $_getList(0);
}

class OpenConnectEndpointStatus extends $pb.GeneratedMessage {
  factory OpenConnectEndpointStatus({
    $core.String? endpointTag,
    $core.String? state,
    $core.String? stateText,
    OpenConnectAuthChallenge? authChallenge,
    $core.String? error,
    OpenConnectTunnelInfo? tunnelInfo,
  }) {
    final result = OpenConnectEndpointStatus._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (state != null) result.state = state;
    if (stateText != null) result.stateText = stateText;
    if (authChallenge != null) result.authChallenge = authChallenge;
    if (error != null) result.error = error;
    if (tunnelInfo != null) result.tunnelInfo = tunnelInfo;
    return result;
  }

  OpenConnectEndpointStatus._();

  factory OpenConnectEndpointStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectEndpointStatus()..mergeFromBuffer(data, registry);
  factory OpenConnectEndpointStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectEndpointStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectEndpointStatus',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectEndpointStatus.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'state')
    ..aOS(3, _omitFieldNames ? '' : 'stateText', protoName: 'stateText')
    ..aOM<OpenConnectAuthChallenge>(4, _omitFieldNames ? '' : 'authChallenge',
        protoName: 'authChallenge',
        subBuilder: OpenConnectAuthChallenge.$_createMessage)
    ..aOS(5, _omitFieldNames ? '' : 'error')
    ..aOM<OpenConnectTunnelInfo>(6, _omitFieldNames ? '' : 'tunnelInfo',
        protoName: 'tunnelInfo',
        subBuilder: OpenConnectTunnelInfo.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectEndpointStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectEndpointStatus copyWith(
          void Function(OpenConnectEndpointStatus) updates) =>
      super.copyWith((message) => updates(message as OpenConnectEndpointStatus))
          as OpenConnectEndpointStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectEndpointStatus() / OpenConnectEndpointStatus.new instead')
  static OpenConnectEndpointStatus create() => OpenConnectEndpointStatus._();
  static $pb.GeneratedMessage $_createMessage() =>
      OpenConnectEndpointStatus._();
  @$core.override
  OpenConnectEndpointStatus createEmptyInstance() =>
      OpenConnectEndpointStatus._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectEndpointStatus getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectEndpointStatus>(
          OpenConnectEndpointStatus.$_createMessage);
  static OpenConnectEndpointStatus? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get state => $_getSZ(1);
  @$pb.TagNumber(2)
  set state($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasState() => $_has(1);
  @$pb.TagNumber(2)
  void clearState() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get stateText => $_getSZ(2);
  @$pb.TagNumber(3)
  set stateText($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasStateText() => $_has(2);
  @$pb.TagNumber(3)
  void clearStateText() => $_clearField(3);

  @$pb.TagNumber(4)
  OpenConnectAuthChallenge get authChallenge => $_getN(3);
  @$pb.TagNumber(4)
  set authChallenge(OpenConnectAuthChallenge value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasAuthChallenge() => $_has(3);
  @$pb.TagNumber(4)
  void clearAuthChallenge() => $_clearField(4);
  @$pb.TagNumber(4)
  OpenConnectAuthChallenge ensureAuthChallenge() => $_ensure(3);

  @$pb.TagNumber(5)
  $core.String get error => $_getSZ(4);
  @$pb.TagNumber(5)
  set error($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasError() => $_has(4);
  @$pb.TagNumber(5)
  void clearError() => $_clearField(5);

  @$pb.TagNumber(6)
  OpenConnectTunnelInfo get tunnelInfo => $_getN(5);
  @$pb.TagNumber(6)
  set tunnelInfo(OpenConnectTunnelInfo value) => $_setField(6, value);
  @$pb.TagNumber(6)
  $core.bool hasTunnelInfo() => $_has(5);
  @$pb.TagNumber(6)
  void clearTunnelInfo() => $_clearField(6);
  @$pb.TagNumber(6)
  OpenConnectTunnelInfo ensureTunnelInfo() => $_ensure(5);
}

class OpenConnectTunnelInfo extends $pb.GeneratedMessage {
  factory OpenConnectTunnelInfo({
    $core.String? server,
    $core.String? flavor,
    $core.String? transport,
    $core.Iterable<$core.String>? ipv4,
    $core.Iterable<$core.String>? ipv6,
    $core.Iterable<$core.String>? dns,
    $core.int? mtu,
    $fixnum.Int64? connectedSince,
  }) {
    final result = OpenConnectTunnelInfo._();
    if (server != null) result.server = server;
    if (flavor != null) result.flavor = flavor;
    if (transport != null) result.transport = transport;
    if (ipv4 != null) result.ipv4.addAll(ipv4);
    if (ipv6 != null) result.ipv6.addAll(ipv6);
    if (dns != null) result.dns.addAll(dns);
    if (mtu != null) result.mtu = mtu;
    if (connectedSince != null) result.connectedSince = connectedSince;
    return result;
  }

  OpenConnectTunnelInfo._();

  factory OpenConnectTunnelInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectTunnelInfo()..mergeFromBuffer(data, registry);
  factory OpenConnectTunnelInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectTunnelInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectTunnelInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectTunnelInfo.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'server')
    ..aOS(2, _omitFieldNames ? '' : 'flavor')
    ..aOS(3, _omitFieldNames ? '' : 'transport')
    ..pPS(4, _omitFieldNames ? '' : 'ipv4')
    ..pPS(5, _omitFieldNames ? '' : 'ipv6')
    ..pPS(6, _omitFieldNames ? '' : 'dns')
    ..aI(7, _omitFieldNames ? '' : 'mtu', fieldType: $pb.PbFieldType.OU3)
    ..aInt64(8, _omitFieldNames ? '' : 'connectedSince',
        protoName: 'connectedSince')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectTunnelInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectTunnelInfo copyWith(
          void Function(OpenConnectTunnelInfo) updates) =>
      super.copyWith((message) => updates(message as OpenConnectTunnelInfo))
          as OpenConnectTunnelInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectTunnelInfo() / OpenConnectTunnelInfo.new instead')
  static OpenConnectTunnelInfo create() => OpenConnectTunnelInfo._();
  static $pb.GeneratedMessage $_createMessage() => OpenConnectTunnelInfo._();
  @$core.override
  OpenConnectTunnelInfo createEmptyInstance() => OpenConnectTunnelInfo._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectTunnelInfo getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectTunnelInfo>(
          OpenConnectTunnelInfo.$_createMessage);
  static OpenConnectTunnelInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get server => $_getSZ(0);
  @$pb.TagNumber(1)
  set server($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasServer() => $_has(0);
  @$pb.TagNumber(1)
  void clearServer() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get flavor => $_getSZ(1);
  @$pb.TagNumber(2)
  set flavor($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasFlavor() => $_has(1);
  @$pb.TagNumber(2)
  void clearFlavor() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get transport => $_getSZ(2);
  @$pb.TagNumber(3)
  set transport($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTransport() => $_has(2);
  @$pb.TagNumber(3)
  void clearTransport() => $_clearField(3);

  @$pb.TagNumber(4)
  $pb.PbList<$core.String> get ipv4 => $_getList(3);

  @$pb.TagNumber(5)
  $pb.PbList<$core.String> get ipv6 => $_getList(4);

  @$pb.TagNumber(6)
  $pb.PbList<$core.String> get dns => $_getList(5);

  @$pb.TagNumber(7)
  $core.int get mtu => $_getIZ(6);
  @$pb.TagNumber(7)
  set mtu($core.int value) => $_setUnsignedInt32(6, value);
  @$pb.TagNumber(7)
  $core.bool hasMtu() => $_has(6);
  @$pb.TagNumber(7)
  void clearMtu() => $_clearField(7);

  @$pb.TagNumber(8)
  $fixnum.Int64 get connectedSince => $_getI64(7);
  @$pb.TagNumber(8)
  set connectedSince($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasConnectedSince() => $_has(7);
  @$pb.TagNumber(8)
  void clearConnectedSince() => $_clearField(8);
}

enum OpenConnectAuthChallenge_Challenge { form, browser, notSet }

class OpenConnectAuthChallenge extends $pb.GeneratedMessage {
  factory OpenConnectAuthChallenge({
    $core.String? id,
    $core.String? banner,
    $core.String? message,
    $core.String? error,
    OpenConnectAuthForm? form,
    OpenConnectBrowserRequest? browser,
  }) {
    final result = OpenConnectAuthChallenge._();
    if (id != null) result.id = id;
    if (banner != null) result.banner = banner;
    if (message != null) result.message = message;
    if (error != null) result.error = error;
    if (form != null) result.form = form;
    if (browser != null) result.browser = browser;
    return result;
  }

  OpenConnectAuthChallenge._();

  factory OpenConnectAuthChallenge.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthChallenge()..mergeFromBuffer(data, registry);
  factory OpenConnectAuthChallenge.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthChallenge()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, OpenConnectAuthChallenge_Challenge>
      _OpenConnectAuthChallenge_ChallengeByTag = {
    5: OpenConnectAuthChallenge_Challenge.form,
    6: OpenConnectAuthChallenge_Challenge.browser,
    0: OpenConnectAuthChallenge_Challenge.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectAuthChallenge',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectAuthChallenge.$_createMessage)
    ..oo(0, [5, 6])
    ..aOS(1, _omitFieldNames ? '' : 'id')
    ..aOS(2, _omitFieldNames ? '' : 'banner')
    ..aOS(3, _omitFieldNames ? '' : 'message')
    ..aOS(4, _omitFieldNames ? '' : 'error')
    ..aOM<OpenConnectAuthForm>(5, _omitFieldNames ? '' : 'form',
        subBuilder: OpenConnectAuthForm.$_createMessage)
    ..aOM<OpenConnectBrowserRequest>(6, _omitFieldNames ? '' : 'browser',
        subBuilder: OpenConnectBrowserRequest.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthChallenge clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthChallenge copyWith(
          void Function(OpenConnectAuthChallenge) updates) =>
      super.copyWith((message) => updates(message as OpenConnectAuthChallenge))
          as OpenConnectAuthChallenge;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectAuthChallenge() / OpenConnectAuthChallenge.new instead')
  static OpenConnectAuthChallenge create() => OpenConnectAuthChallenge._();
  static $pb.GeneratedMessage $_createMessage() => OpenConnectAuthChallenge._();
  @$core.override
  OpenConnectAuthChallenge createEmptyInstance() =>
      OpenConnectAuthChallenge._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectAuthChallenge getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectAuthChallenge>(
          OpenConnectAuthChallenge.$_createMessage);
  static OpenConnectAuthChallenge? _defaultInstance;

  @$pb.TagNumber(5)
  @$pb.TagNumber(6)
  OpenConnectAuthChallenge_Challenge whichChallenge() =>
      _OpenConnectAuthChallenge_ChallengeByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(5)
  @$pb.TagNumber(6)
  void clearChallenge() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  $core.String get id => $_getSZ(0);
  @$pb.TagNumber(1)
  set id($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasId() => $_has(0);
  @$pb.TagNumber(1)
  void clearId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get banner => $_getSZ(1);
  @$pb.TagNumber(2)
  set banner($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasBanner() => $_has(1);
  @$pb.TagNumber(2)
  void clearBanner() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get message => $_getSZ(2);
  @$pb.TagNumber(3)
  set message($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasMessage() => $_has(2);
  @$pb.TagNumber(3)
  void clearMessage() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get error => $_getSZ(3);
  @$pb.TagNumber(4)
  set error($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasError() => $_has(3);
  @$pb.TagNumber(4)
  void clearError() => $_clearField(4);

  @$pb.TagNumber(5)
  OpenConnectAuthForm get form => $_getN(4);
  @$pb.TagNumber(5)
  set form(OpenConnectAuthForm value) => $_setField(5, value);
  @$pb.TagNumber(5)
  $core.bool hasForm() => $_has(4);
  @$pb.TagNumber(5)
  void clearForm() => $_clearField(5);
  @$pb.TagNumber(5)
  OpenConnectAuthForm ensureForm() => $_ensure(4);

  @$pb.TagNumber(6)
  OpenConnectBrowserRequest get browser => $_getN(5);
  @$pb.TagNumber(6)
  set browser(OpenConnectBrowserRequest value) => $_setField(6, value);
  @$pb.TagNumber(6)
  $core.bool hasBrowser() => $_has(5);
  @$pb.TagNumber(6)
  void clearBrowser() => $_clearField(6);
  @$pb.TagNumber(6)
  OpenConnectBrowserRequest ensureBrowser() => $_ensure(5);
}

class OpenConnectAuthForm extends $pb.GeneratedMessage {
  factory OpenConnectAuthForm({
    $core.Iterable<OpenConnectAuthFormField>? fields,
  }) {
    final result = OpenConnectAuthForm._();
    if (fields != null) result.fields.addAll(fields);
    return result;
  }

  OpenConnectAuthForm._();

  factory OpenConnectAuthForm.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthForm()..mergeFromBuffer(data, registry);
  factory OpenConnectAuthForm.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthForm()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectAuthForm',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectAuthForm.$_createMessage)
    ..pPM<OpenConnectAuthFormField>(1, _omitFieldNames ? '' : 'fields',
        subBuilder: OpenConnectAuthFormField.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthForm clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthForm copyWith(void Function(OpenConnectAuthForm) updates) =>
      super.copyWith((message) => updates(message as OpenConnectAuthForm))
          as OpenConnectAuthForm;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core
      .Deprecated('Use OpenConnectAuthForm() / OpenConnectAuthForm.new instead')
  static OpenConnectAuthForm create() => OpenConnectAuthForm._();
  static $pb.GeneratedMessage $_createMessage() => OpenConnectAuthForm._();
  @$core.override
  OpenConnectAuthForm createEmptyInstance() => OpenConnectAuthForm._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectAuthForm getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectAuthForm>(
          OpenConnectAuthForm.$_createMessage);
  static OpenConnectAuthForm? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<OpenConnectAuthFormField> get fields => $_getList(0);
}

class OpenConnectAuthFormField extends $pb.GeneratedMessage {
  factory OpenConnectAuthFormField({
    $core.String? submissionKey,
    $core.String? name,
    $core.String? label,
    $core.String? kind,
    $core.String? value,
    $core.Iterable<OpenConnectAuthFormChoice>? options,
  }) {
    final result = OpenConnectAuthFormField._();
    if (submissionKey != null) result.submissionKey = submissionKey;
    if (name != null) result.name = name;
    if (label != null) result.label = label;
    if (kind != null) result.kind = kind;
    if (value != null) result.value = value;
    if (options != null) result.options.addAll(options);
    return result;
  }

  OpenConnectAuthFormField._();

  factory OpenConnectAuthFormField.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthFormField()..mergeFromBuffer(data, registry);
  factory OpenConnectAuthFormField.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthFormField()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectAuthFormField',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectAuthFormField.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'submissionKey', protoName: 'submissionKey')
    ..aOS(2, _omitFieldNames ? '' : 'name')
    ..aOS(3, _omitFieldNames ? '' : 'label')
    ..aOS(4, _omitFieldNames ? '' : 'kind')
    ..aOS(5, _omitFieldNames ? '' : 'value')
    ..pPM<OpenConnectAuthFormChoice>(6, _omitFieldNames ? '' : 'options',
        subBuilder: OpenConnectAuthFormChoice.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthFormField clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthFormField copyWith(
          void Function(OpenConnectAuthFormField) updates) =>
      super.copyWith((message) => updates(message as OpenConnectAuthFormField))
          as OpenConnectAuthFormField;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectAuthFormField() / OpenConnectAuthFormField.new instead')
  static OpenConnectAuthFormField create() => OpenConnectAuthFormField._();
  static $pb.GeneratedMessage $_createMessage() => OpenConnectAuthFormField._();
  @$core.override
  OpenConnectAuthFormField createEmptyInstance() =>
      OpenConnectAuthFormField._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectAuthFormField getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectAuthFormField>(
          OpenConnectAuthFormField.$_createMessage);
  static OpenConnectAuthFormField? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get submissionKey => $_getSZ(0);
  @$pb.TagNumber(1)
  set submissionKey($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasSubmissionKey() => $_has(0);
  @$pb.TagNumber(1)
  void clearSubmissionKey() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get name => $_getSZ(1);
  @$pb.TagNumber(2)
  set name($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasName() => $_has(1);
  @$pb.TagNumber(2)
  void clearName() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get label => $_getSZ(2);
  @$pb.TagNumber(3)
  set label($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasLabel() => $_has(2);
  @$pb.TagNumber(3)
  void clearLabel() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get kind => $_getSZ(3);
  @$pb.TagNumber(4)
  set kind($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasKind() => $_has(3);
  @$pb.TagNumber(4)
  void clearKind() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get value => $_getSZ(4);
  @$pb.TagNumber(5)
  set value($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasValue() => $_has(4);
  @$pb.TagNumber(5)
  void clearValue() => $_clearField(5);

  @$pb.TagNumber(6)
  $pb.PbList<OpenConnectAuthFormChoice> get options => $_getList(5);
}

class OpenConnectAuthFormChoice extends $pb.GeneratedMessage {
  factory OpenConnectAuthFormChoice({
    $core.String? value,
    $core.String? label,
  }) {
    final result = OpenConnectAuthFormChoice._();
    if (value != null) result.value = value;
    if (label != null) result.label = label;
    return result;
  }

  OpenConnectAuthFormChoice._();

  factory OpenConnectAuthFormChoice.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthFormChoice()..mergeFromBuffer(data, registry);
  factory OpenConnectAuthFormChoice.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthFormChoice()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectAuthFormChoice',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectAuthFormChoice.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'value')
    ..aOS(2, _omitFieldNames ? '' : 'label')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthFormChoice clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthFormChoice copyWith(
          void Function(OpenConnectAuthFormChoice) updates) =>
      super.copyWith((message) => updates(message as OpenConnectAuthFormChoice))
          as OpenConnectAuthFormChoice;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectAuthFormChoice() / OpenConnectAuthFormChoice.new instead')
  static OpenConnectAuthFormChoice create() => OpenConnectAuthFormChoice._();
  static $pb.GeneratedMessage $_createMessage() =>
      OpenConnectAuthFormChoice._();
  @$core.override
  OpenConnectAuthFormChoice createEmptyInstance() =>
      OpenConnectAuthFormChoice._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectAuthFormChoice getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectAuthFormChoice>(
          OpenConnectAuthFormChoice.$_createMessage);
  static OpenConnectAuthFormChoice? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get value => $_getSZ(0);
  @$pb.TagNumber(1)
  set value($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasValue() => $_has(0);
  @$pb.TagNumber(1)
  void clearValue() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get label => $_getSZ(1);
  @$pb.TagNumber(2)
  set label($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasLabel() => $_has(1);
  @$pb.TagNumber(2)
  void clearLabel() => $_clearField(2);
}

class OpenConnectBrowserRequest extends $pb.GeneratedMessage {
  factory OpenConnectBrowserRequest({
    $core.String? url,
    $core.String? finalURL,
    $core.Iterable<$core.String>? cookieNames,
    $core.Iterable<$core.String>? headerNames,
    $core.Iterable<$core.String>? callbackURLPrefixes,
    $core.Iterable<$core.String>? earlyCookieNames,
    $core.String? cacheID,
  }) {
    final result = OpenConnectBrowserRequest._();
    if (url != null) result.url = url;
    if (finalURL != null) result.finalURL = finalURL;
    if (cookieNames != null) result.cookieNames.addAll(cookieNames);
    if (headerNames != null) result.headerNames.addAll(headerNames);
    if (callbackURLPrefixes != null)
      result.callbackURLPrefixes.addAll(callbackURLPrefixes);
    if (earlyCookieNames != null)
      result.earlyCookieNames.addAll(earlyCookieNames);
    if (cacheID != null) result.cacheID = cacheID;
    return result;
  }

  OpenConnectBrowserRequest._();

  factory OpenConnectBrowserRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectBrowserRequest()..mergeFromBuffer(data, registry);
  factory OpenConnectBrowserRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectBrowserRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectBrowserRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectBrowserRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'url')
    ..aOS(2, _omitFieldNames ? '' : 'finalURL', protoName: 'finalURL')
    ..pPS(3, _omitFieldNames ? '' : 'cookieNames', protoName: 'cookieNames')
    ..pPS(4, _omitFieldNames ? '' : 'headerNames', protoName: 'headerNames')
    ..pPS(5, _omitFieldNames ? '' : 'callbackURLPrefixes',
        protoName: 'callbackURLPrefixes')
    ..pPS(6, _omitFieldNames ? '' : 'earlyCookieNames',
        protoName: 'earlyCookieNames')
    ..aOS(7, _omitFieldNames ? '' : 'cacheID', protoName: 'cacheID')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectBrowserRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectBrowserRequest copyWith(
          void Function(OpenConnectBrowserRequest) updates) =>
      super.copyWith((message) => updates(message as OpenConnectBrowserRequest))
          as OpenConnectBrowserRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectBrowserRequest() / OpenConnectBrowserRequest.new instead')
  static OpenConnectBrowserRequest create() => OpenConnectBrowserRequest._();
  static $pb.GeneratedMessage $_createMessage() =>
      OpenConnectBrowserRequest._();
  @$core.override
  OpenConnectBrowserRequest createEmptyInstance() =>
      OpenConnectBrowserRequest._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectBrowserRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectBrowserRequest>(
          OpenConnectBrowserRequest.$_createMessage);
  static OpenConnectBrowserRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get url => $_getSZ(0);
  @$pb.TagNumber(1)
  set url($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasUrl() => $_has(0);
  @$pb.TagNumber(1)
  void clearUrl() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get finalURL => $_getSZ(1);
  @$pb.TagNumber(2)
  set finalURL($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasFinalURL() => $_has(1);
  @$pb.TagNumber(2)
  void clearFinalURL() => $_clearField(2);

  @$pb.TagNumber(3)
  $pb.PbList<$core.String> get cookieNames => $_getList(2);

  @$pb.TagNumber(4)
  $pb.PbList<$core.String> get headerNames => $_getList(3);

  @$pb.TagNumber(5)
  $pb.PbList<$core.String> get callbackURLPrefixes => $_getList(4);

  @$pb.TagNumber(6)
  $pb.PbList<$core.String> get earlyCookieNames => $_getList(5);

  @$pb.TagNumber(7)
  $core.String get cacheID => $_getSZ(6);
  @$pb.TagNumber(7)
  set cacheID($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasCacheID() => $_has(6);
  @$pb.TagNumber(7)
  void clearCacheID() => $_clearField(7);
}

class OpenConnectBrowserCookie extends $pb.GeneratedMessage {
  factory OpenConnectBrowserCookie({
    $core.String? name,
    $core.String? value,
  }) {
    final result = OpenConnectBrowserCookie._();
    if (name != null) result.name = name;
    if (value != null) result.value = value;
    return result;
  }

  OpenConnectBrowserCookie._();

  factory OpenConnectBrowserCookie.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectBrowserCookie()..mergeFromBuffer(data, registry);
  factory OpenConnectBrowserCookie.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectBrowserCookie()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectBrowserCookie',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectBrowserCookie.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..aOS(2, _omitFieldNames ? '' : 'value')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectBrowserCookie clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectBrowserCookie copyWith(
          void Function(OpenConnectBrowserCookie) updates) =>
      super.copyWith((message) => updates(message as OpenConnectBrowserCookie))
          as OpenConnectBrowserCookie;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectBrowserCookie() / OpenConnectBrowserCookie.new instead')
  static OpenConnectBrowserCookie create() => OpenConnectBrowserCookie._();
  static $pb.GeneratedMessage $_createMessage() => OpenConnectBrowserCookie._();
  @$core.override
  OpenConnectBrowserCookie createEmptyInstance() =>
      OpenConnectBrowserCookie._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectBrowserCookie getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectBrowserCookie>(
          OpenConnectBrowserCookie.$_createMessage);
  static OpenConnectBrowserCookie? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get value => $_getSZ(1);
  @$pb.TagNumber(2)
  set value($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasValue() => $_has(1);
  @$pb.TagNumber(2)
  void clearValue() => $_clearField(2);
}

class OpenConnectBrowserHeader extends $pb.GeneratedMessage {
  factory OpenConnectBrowserHeader({
    $core.String? name,
    $core.Iterable<$core.String>? values,
  }) {
    final result = OpenConnectBrowserHeader._();
    if (name != null) result.name = name;
    if (values != null) result.values.addAll(values);
    return result;
  }

  OpenConnectBrowserHeader._();

  factory OpenConnectBrowserHeader.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectBrowserHeader()..mergeFromBuffer(data, registry);
  factory OpenConnectBrowserHeader.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectBrowserHeader()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectBrowserHeader',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectBrowserHeader.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'name')
    ..pPS(2, _omitFieldNames ? '' : 'values')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectBrowserHeader clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectBrowserHeader copyWith(
          void Function(OpenConnectBrowserHeader) updates) =>
      super.copyWith((message) => updates(message as OpenConnectBrowserHeader))
          as OpenConnectBrowserHeader;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectBrowserHeader() / OpenConnectBrowserHeader.new instead')
  static OpenConnectBrowserHeader create() => OpenConnectBrowserHeader._();
  static $pb.GeneratedMessage $_createMessage() => OpenConnectBrowserHeader._();
  @$core.override
  OpenConnectBrowserHeader createEmptyInstance() =>
      OpenConnectBrowserHeader._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectBrowserHeader getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectBrowserHeader>(
          OpenConnectBrowserHeader.$_createMessage);
  static OpenConnectBrowserHeader? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get name => $_getSZ(0);
  @$pb.TagNumber(1)
  set name($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasName() => $_has(0);
  @$pb.TagNumber(1)
  void clearName() => $_clearField(1);

  @$pb.TagNumber(2)
  $pb.PbList<$core.String> get values => $_getList(1);
}

class OpenConnectAuthFormResponse extends $pb.GeneratedMessage {
  factory OpenConnectAuthFormResponse({
    $core.Iterable<$core.MapEntry<$core.String, $core.String>>? values,
  }) {
    final result = OpenConnectAuthFormResponse._();
    if (values != null) result.values.addEntries(values);
    return result;
  }

  OpenConnectAuthFormResponse._();

  factory OpenConnectAuthFormResponse.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthFormResponse()..mergeFromBuffer(data, registry);
  factory OpenConnectAuthFormResponse.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthFormResponse()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectAuthFormResponse',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectAuthFormResponse.$_createMessage)
    ..m<$core.String, $core.String>(1, _omitFieldNames ? '' : 'values',
        entryClassName: 'OpenConnectAuthFormResponse.ValuesEntry',
        keyFieldType: $pb.PbFieldType.OS,
        valueFieldType: $pb.PbFieldType.OS,
        packageName: const $pb.PackageName('daemon'))
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthFormResponse clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthFormResponse copyWith(
          void Function(OpenConnectAuthFormResponse) updates) =>
      super.copyWith(
              (message) => updates(message as OpenConnectAuthFormResponse))
          as OpenConnectAuthFormResponse;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectAuthFormResponse() / OpenConnectAuthFormResponse.new instead')
  static OpenConnectAuthFormResponse create() =>
      OpenConnectAuthFormResponse._();
  static $pb.GeneratedMessage $_createMessage() =>
      OpenConnectAuthFormResponse._();
  @$core.override
  OpenConnectAuthFormResponse createEmptyInstance() =>
      OpenConnectAuthFormResponse._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectAuthFormResponse getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectAuthFormResponse>(
          OpenConnectAuthFormResponse.$_createMessage);
  static OpenConnectAuthFormResponse? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbMap<$core.String, $core.String> get values => $_getMap(0);
}

class OpenConnectBrowserResult extends $pb.GeneratedMessage {
  factory OpenConnectBrowserResult({
    $core.String? finalURL,
    $core.Iterable<OpenConnectBrowserCookie>? cookies,
    $core.Iterable<OpenConnectBrowserHeader>? headers,
  }) {
    final result = OpenConnectBrowserResult._();
    if (finalURL != null) result.finalURL = finalURL;
    if (cookies != null) result.cookies.addAll(cookies);
    if (headers != null) result.headers.addAll(headers);
    return result;
  }

  OpenConnectBrowserResult._();

  factory OpenConnectBrowserResult.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectBrowserResult()..mergeFromBuffer(data, registry);
  factory OpenConnectBrowserResult.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectBrowserResult()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectBrowserResult',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectBrowserResult.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'finalURL', protoName: 'finalURL')
    ..pPM<OpenConnectBrowserCookie>(2, _omitFieldNames ? '' : 'cookies',
        subBuilder: OpenConnectBrowserCookie.$_createMessage)
    ..pPM<OpenConnectBrowserHeader>(3, _omitFieldNames ? '' : 'headers',
        subBuilder: OpenConnectBrowserHeader.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectBrowserResult clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectBrowserResult copyWith(
          void Function(OpenConnectBrowserResult) updates) =>
      super.copyWith((message) => updates(message as OpenConnectBrowserResult))
          as OpenConnectBrowserResult;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectBrowserResult() / OpenConnectBrowserResult.new instead')
  static OpenConnectBrowserResult create() => OpenConnectBrowserResult._();
  static $pb.GeneratedMessage $_createMessage() => OpenConnectBrowserResult._();
  @$core.override
  OpenConnectBrowserResult createEmptyInstance() =>
      OpenConnectBrowserResult._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectBrowserResult getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectBrowserResult>(
          OpenConnectBrowserResult.$_createMessage);
  static OpenConnectBrowserResult? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get finalURL => $_getSZ(0);
  @$pb.TagNumber(1)
  set finalURL($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasFinalURL() => $_has(0);
  @$pb.TagNumber(1)
  void clearFinalURL() => $_clearField(1);

  @$pb.TagNumber(2)
  $pb.PbList<OpenConnectBrowserCookie> get cookies => $_getList(1);

  @$pb.TagNumber(3)
  $pb.PbList<OpenConnectBrowserHeader> get headers => $_getList(2);
}

enum OpenConnectAuthResponseSubmission_Response { form, browser, notSet }

class OpenConnectAuthResponseSubmission extends $pb.GeneratedMessage {
  factory OpenConnectAuthResponseSubmission({
    $core.String? endpointTag,
    $core.String? challengeID,
    OpenConnectAuthFormResponse? form,
    OpenConnectBrowserResult? browser,
  }) {
    final result = OpenConnectAuthResponseSubmission._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (challengeID != null) result.challengeID = challengeID;
    if (form != null) result.form = form;
    if (browser != null) result.browser = browser;
    return result;
  }

  OpenConnectAuthResponseSubmission._();

  factory OpenConnectAuthResponseSubmission.fromBuffer(
          $core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthResponseSubmission()..mergeFromBuffer(data, registry);
  factory OpenConnectAuthResponseSubmission.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthResponseSubmission()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, OpenConnectAuthResponseSubmission_Response>
      _OpenConnectAuthResponseSubmission_ResponseByTag = {
    3: OpenConnectAuthResponseSubmission_Response.form,
    4: OpenConnectAuthResponseSubmission_Response.browser,
    0: OpenConnectAuthResponseSubmission_Response.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectAuthResponseSubmission',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectAuthResponseSubmission.$_createMessage)
    ..oo(0, [3, 4])
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'challengeID', protoName: 'challengeID')
    ..aOM<OpenConnectAuthFormResponse>(3, _omitFieldNames ? '' : 'form',
        subBuilder: OpenConnectAuthFormResponse.$_createMessage)
    ..aOM<OpenConnectBrowserResult>(4, _omitFieldNames ? '' : 'browser',
        subBuilder: OpenConnectBrowserResult.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthResponseSubmission clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthResponseSubmission copyWith(
          void Function(OpenConnectAuthResponseSubmission) updates) =>
      super.copyWith((message) =>
              updates(message as OpenConnectAuthResponseSubmission))
          as OpenConnectAuthResponseSubmission;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectAuthResponseSubmission() / OpenConnectAuthResponseSubmission.new instead')
  static OpenConnectAuthResponseSubmission create() =>
      OpenConnectAuthResponseSubmission._();
  static $pb.GeneratedMessage $_createMessage() =>
      OpenConnectAuthResponseSubmission._();
  @$core.override
  OpenConnectAuthResponseSubmission createEmptyInstance() =>
      OpenConnectAuthResponseSubmission._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectAuthResponseSubmission getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectAuthResponseSubmission>(
          OpenConnectAuthResponseSubmission.$_createMessage);
  static OpenConnectAuthResponseSubmission? _defaultInstance;

  @$pb.TagNumber(3)
  @$pb.TagNumber(4)
  OpenConnectAuthResponseSubmission_Response whichResponse() =>
      _OpenConnectAuthResponseSubmission_ResponseByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(3)
  @$pb.TagNumber(4)
  void clearResponse() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get challengeID => $_getSZ(1);
  @$pb.TagNumber(2)
  set challengeID($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasChallengeID() => $_has(1);
  @$pb.TagNumber(2)
  void clearChallengeID() => $_clearField(2);

  @$pb.TagNumber(3)
  OpenConnectAuthFormResponse get form => $_getN(2);
  @$pb.TagNumber(3)
  set form(OpenConnectAuthFormResponse value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasForm() => $_has(2);
  @$pb.TagNumber(3)
  void clearForm() => $_clearField(3);
  @$pb.TagNumber(3)
  OpenConnectAuthFormResponse ensureForm() => $_ensure(2);

  @$pb.TagNumber(4)
  OpenConnectBrowserResult get browser => $_getN(3);
  @$pb.TagNumber(4)
  set browser(OpenConnectBrowserResult value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasBrowser() => $_has(3);
  @$pb.TagNumber(4)
  void clearBrowser() => $_clearField(4);
  @$pb.TagNumber(4)
  OpenConnectBrowserResult ensureBrowser() => $_ensure(3);
}

class OpenConnectAuthChallengeCancel extends $pb.GeneratedMessage {
  factory OpenConnectAuthChallengeCancel({
    $core.String? endpointTag,
    $core.String? challengeID,
  }) {
    final result = OpenConnectAuthChallengeCancel._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (challengeID != null) result.challengeID = challengeID;
    return result;
  }

  OpenConnectAuthChallengeCancel._();

  factory OpenConnectAuthChallengeCancel.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthChallengeCancel()..mergeFromBuffer(data, registry);
  factory OpenConnectAuthChallengeCancel.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenConnectAuthChallengeCancel()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenConnectAuthChallengeCancel',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenConnectAuthChallengeCancel.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'challengeID', protoName: 'challengeID')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthChallengeCancel clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenConnectAuthChallengeCancel copyWith(
          void Function(OpenConnectAuthChallengeCancel) updates) =>
      super.copyWith(
              (message) => updates(message as OpenConnectAuthChallengeCancel))
          as OpenConnectAuthChallengeCancel;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenConnectAuthChallengeCancel() / OpenConnectAuthChallengeCancel.new instead')
  static OpenConnectAuthChallengeCancel create() =>
      OpenConnectAuthChallengeCancel._();
  static $pb.GeneratedMessage $_createMessage() =>
      OpenConnectAuthChallengeCancel._();
  @$core.override
  OpenConnectAuthChallengeCancel createEmptyInstance() =>
      OpenConnectAuthChallengeCancel._();
  @$core.pragma('dart2js:noInline')
  static OpenConnectAuthChallengeCancel getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenConnectAuthChallengeCancel>(
          OpenConnectAuthChallengeCancel.$_createMessage);
  static OpenConnectAuthChallengeCancel? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get challengeID => $_getSZ(1);
  @$pb.TagNumber(2)
  set challengeID($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasChallengeID() => $_has(1);
  @$pb.TagNumber(2)
  void clearChallengeID() => $_clearField(2);
}

class OpenVPNStatusUpdate extends $pb.GeneratedMessage {
  factory OpenVPNStatusUpdate({
    $core.Iterable<OpenVPNEndpointStatus>? endpoints,
  }) {
    final result = OpenVPNStatusUpdate._();
    if (endpoints != null) result.endpoints.addAll(endpoints);
    return result;
  }

  OpenVPNStatusUpdate._();

  factory OpenVPNStatusUpdate.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNStatusUpdate()..mergeFromBuffer(data, registry);
  factory OpenVPNStatusUpdate.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNStatusUpdate()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenVPNStatusUpdate',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenVPNStatusUpdate.$_createMessage)
    ..pPM<OpenVPNEndpointStatus>(1, _omitFieldNames ? '' : 'endpoints',
        subBuilder: OpenVPNEndpointStatus.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNStatusUpdate clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNStatusUpdate copyWith(void Function(OpenVPNStatusUpdate) updates) =>
      super.copyWith((message) => updates(message as OpenVPNStatusUpdate))
          as OpenVPNStatusUpdate;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core
      .Deprecated('Use OpenVPNStatusUpdate() / OpenVPNStatusUpdate.new instead')
  static OpenVPNStatusUpdate create() => OpenVPNStatusUpdate._();
  static $pb.GeneratedMessage $_createMessage() => OpenVPNStatusUpdate._();
  @$core.override
  OpenVPNStatusUpdate createEmptyInstance() => OpenVPNStatusUpdate._();
  @$core.pragma('dart2js:noInline')
  static OpenVPNStatusUpdate getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenVPNStatusUpdate>(
          OpenVPNStatusUpdate.$_createMessage);
  static OpenVPNStatusUpdate? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<OpenVPNEndpointStatus> get endpoints => $_getList(0);
}

class OpenVPNEndpointStatus extends $pb.GeneratedMessage {
  factory OpenVPNEndpointStatus({
    $core.String? endpointTag,
    $core.String? state,
    $core.String? stateText,
    OpenVPNChallenge? challenge,
    $core.String? error,
    OpenVPNTunnelInfo? tunnelInfo,
  }) {
    final result = OpenVPNEndpointStatus._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (state != null) result.state = state;
    if (stateText != null) result.stateText = stateText;
    if (challenge != null) result.challenge = challenge;
    if (error != null) result.error = error;
    if (tunnelInfo != null) result.tunnelInfo = tunnelInfo;
    return result;
  }

  OpenVPNEndpointStatus._();

  factory OpenVPNEndpointStatus.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNEndpointStatus()..mergeFromBuffer(data, registry);
  factory OpenVPNEndpointStatus.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNEndpointStatus()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenVPNEndpointStatus',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenVPNEndpointStatus.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'state')
    ..aOS(3, _omitFieldNames ? '' : 'stateText', protoName: 'stateText')
    ..aOM<OpenVPNChallenge>(4, _omitFieldNames ? '' : 'challenge',
        subBuilder: OpenVPNChallenge.$_createMessage)
    ..aOS(5, _omitFieldNames ? '' : 'error')
    ..aOM<OpenVPNTunnelInfo>(6, _omitFieldNames ? '' : 'tunnelInfo',
        protoName: 'tunnelInfo', subBuilder: OpenVPNTunnelInfo.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNEndpointStatus clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNEndpointStatus copyWith(
          void Function(OpenVPNEndpointStatus) updates) =>
      super.copyWith((message) => updates(message as OpenVPNEndpointStatus))
          as OpenVPNEndpointStatus;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenVPNEndpointStatus() / OpenVPNEndpointStatus.new instead')
  static OpenVPNEndpointStatus create() => OpenVPNEndpointStatus._();
  static $pb.GeneratedMessage $_createMessage() => OpenVPNEndpointStatus._();
  @$core.override
  OpenVPNEndpointStatus createEmptyInstance() => OpenVPNEndpointStatus._();
  @$core.pragma('dart2js:noInline')
  static OpenVPNEndpointStatus getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenVPNEndpointStatus>(
          OpenVPNEndpointStatus.$_createMessage);
  static OpenVPNEndpointStatus? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get state => $_getSZ(1);
  @$pb.TagNumber(2)
  set state($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasState() => $_has(1);
  @$pb.TagNumber(2)
  void clearState() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get stateText => $_getSZ(2);
  @$pb.TagNumber(3)
  set stateText($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasStateText() => $_has(2);
  @$pb.TagNumber(3)
  void clearStateText() => $_clearField(3);

  @$pb.TagNumber(4)
  OpenVPNChallenge get challenge => $_getN(3);
  @$pb.TagNumber(4)
  set challenge(OpenVPNChallenge value) => $_setField(4, value);
  @$pb.TagNumber(4)
  $core.bool hasChallenge() => $_has(3);
  @$pb.TagNumber(4)
  void clearChallenge() => $_clearField(4);
  @$pb.TagNumber(4)
  OpenVPNChallenge ensureChallenge() => $_ensure(3);

  @$pb.TagNumber(5)
  $core.String get error => $_getSZ(4);
  @$pb.TagNumber(5)
  set error($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasError() => $_has(4);
  @$pb.TagNumber(5)
  void clearError() => $_clearField(5);

  @$pb.TagNumber(6)
  OpenVPNTunnelInfo get tunnelInfo => $_getN(5);
  @$pb.TagNumber(6)
  set tunnelInfo(OpenVPNTunnelInfo value) => $_setField(6, value);
  @$pb.TagNumber(6)
  $core.bool hasTunnelInfo() => $_has(5);
  @$pb.TagNumber(6)
  void clearTunnelInfo() => $_clearField(6);
  @$pb.TagNumber(6)
  OpenVPNTunnelInfo ensureTunnelInfo() => $_ensure(5);
}

class OpenVPNTunnelInfo extends $pb.GeneratedMessage {
  factory OpenVPNTunnelInfo({
    $core.String? server,
    $core.String? network,
    $core.Iterable<$core.String>? ipv4,
    $core.Iterable<$core.String>? ipv6,
    $core.Iterable<$core.String>? dns,
    $core.int? mtu,
    $fixnum.Int64? connectedSince,
    $core.String? cipher,
  }) {
    final result = OpenVPNTunnelInfo._();
    if (server != null) result.server = server;
    if (network != null) result.network = network;
    if (ipv4 != null) result.ipv4.addAll(ipv4);
    if (ipv6 != null) result.ipv6.addAll(ipv6);
    if (dns != null) result.dns.addAll(dns);
    if (mtu != null) result.mtu = mtu;
    if (connectedSince != null) result.connectedSince = connectedSince;
    if (cipher != null) result.cipher = cipher;
    return result;
  }

  OpenVPNTunnelInfo._();

  factory OpenVPNTunnelInfo.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNTunnelInfo()..mergeFromBuffer(data, registry);
  factory OpenVPNTunnelInfo.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNTunnelInfo()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenVPNTunnelInfo',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenVPNTunnelInfo.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'server')
    ..aOS(3, _omitFieldNames ? '' : 'network')
    ..pPS(4, _omitFieldNames ? '' : 'ipv4')
    ..pPS(5, _omitFieldNames ? '' : 'ipv6')
    ..pPS(6, _omitFieldNames ? '' : 'dns')
    ..aI(7, _omitFieldNames ? '' : 'mtu', fieldType: $pb.PbFieldType.OU3)
    ..aInt64(8, _omitFieldNames ? '' : 'connectedSince',
        protoName: 'connectedSince')
    ..aOS(9, _omitFieldNames ? '' : 'cipher')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNTunnelInfo clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNTunnelInfo copyWith(void Function(OpenVPNTunnelInfo) updates) =>
      super.copyWith((message) => updates(message as OpenVPNTunnelInfo))
          as OpenVPNTunnelInfo;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OpenVPNTunnelInfo() / OpenVPNTunnelInfo.new instead')
  static OpenVPNTunnelInfo create() => OpenVPNTunnelInfo._();
  static $pb.GeneratedMessage $_createMessage() => OpenVPNTunnelInfo._();
  @$core.override
  OpenVPNTunnelInfo createEmptyInstance() => OpenVPNTunnelInfo._();
  @$core.pragma('dart2js:noInline')
  static OpenVPNTunnelInfo getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OpenVPNTunnelInfo>(
          OpenVPNTunnelInfo.$_createMessage);
  static OpenVPNTunnelInfo? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get server => $_getSZ(0);
  @$pb.TagNumber(1)
  set server($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasServer() => $_has(0);
  @$pb.TagNumber(1)
  void clearServer() => $_clearField(1);

  @$pb.TagNumber(3)
  $core.String get network => $_getSZ(1);
  @$pb.TagNumber(3)
  set network($core.String value) => $_setString(1, value);
  @$pb.TagNumber(3)
  $core.bool hasNetwork() => $_has(1);
  @$pb.TagNumber(3)
  void clearNetwork() => $_clearField(3);

  @$pb.TagNumber(4)
  $pb.PbList<$core.String> get ipv4 => $_getList(2);

  @$pb.TagNumber(5)
  $pb.PbList<$core.String> get ipv6 => $_getList(3);

  @$pb.TagNumber(6)
  $pb.PbList<$core.String> get dns => $_getList(4);

  @$pb.TagNumber(7)
  $core.int get mtu => $_getIZ(5);
  @$pb.TagNumber(7)
  set mtu($core.int value) => $_setUnsignedInt32(5, value);
  @$pb.TagNumber(7)
  $core.bool hasMtu() => $_has(5);
  @$pb.TagNumber(7)
  void clearMtu() => $_clearField(7);

  @$pb.TagNumber(8)
  $fixnum.Int64 get connectedSince => $_getI64(6);
  @$pb.TagNumber(8)
  set connectedSince($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(8)
  $core.bool hasConnectedSince() => $_has(6);
  @$pb.TagNumber(8)
  void clearConnectedSince() => $_clearField(8);

  @$pb.TagNumber(9)
  $core.String get cipher => $_getSZ(7);
  @$pb.TagNumber(9)
  set cipher($core.String value) => $_setString(7, value);
  @$pb.TagNumber(9)
  $core.bool hasCipher() => $_has(7);
  @$pb.TagNumber(9)
  void clearCipher() => $_clearField(9);
}

class OpenVPNChallenge extends $pb.GeneratedMessage {
  factory OpenVPNChallenge({
    $core.String? id,
    $core.String? kind,
    $core.String? username,
    $core.String? message,
    $core.String? url,
    $core.String? secretMessage,
    $core.bool? echo,
    $core.String? previousError,
    $fixnum.Int64? deadline,
  }) {
    final result = OpenVPNChallenge._();
    if (id != null) result.id = id;
    if (kind != null) result.kind = kind;
    if (username != null) result.username = username;
    if (message != null) result.message = message;
    if (url != null) result.url = url;
    if (secretMessage != null) result.secretMessage = secretMessage;
    if (echo != null) result.echo = echo;
    if (previousError != null) result.previousError = previousError;
    if (deadline != null) result.deadline = deadline;
    return result;
  }

  OpenVPNChallenge._();

  factory OpenVPNChallenge.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNChallenge()..mergeFromBuffer(data, registry);
  factory OpenVPNChallenge.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNChallenge()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenVPNChallenge',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenVPNChallenge.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'id')
    ..aOS(2, _omitFieldNames ? '' : 'kind')
    ..aOS(3, _omitFieldNames ? '' : 'username')
    ..aOS(4, _omitFieldNames ? '' : 'message')
    ..aOS(5, _omitFieldNames ? '' : 'url')
    ..aOS(6, _omitFieldNames ? '' : 'secretMessage', protoName: 'secretMessage')
    ..aOB(7, _omitFieldNames ? '' : 'echo')
    ..aOS(8, _omitFieldNames ? '' : 'previousError', protoName: 'previousError')
    ..aInt64(9, _omitFieldNames ? '' : 'deadline')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNChallenge clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNChallenge copyWith(void Function(OpenVPNChallenge) updates) =>
      super.copyWith((message) => updates(message as OpenVPNChallenge))
          as OpenVPNChallenge;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use OpenVPNChallenge() / OpenVPNChallenge.new instead')
  static OpenVPNChallenge create() => OpenVPNChallenge._();
  static $pb.GeneratedMessage $_createMessage() => OpenVPNChallenge._();
  @$core.override
  OpenVPNChallenge createEmptyInstance() => OpenVPNChallenge._();
  @$core.pragma('dart2js:noInline')
  static OpenVPNChallenge getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<OpenVPNChallenge>(
          OpenVPNChallenge.$_createMessage);
  static OpenVPNChallenge? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get id => $_getSZ(0);
  @$pb.TagNumber(1)
  set id($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasId() => $_has(0);
  @$pb.TagNumber(1)
  void clearId() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get kind => $_getSZ(1);
  @$pb.TagNumber(2)
  set kind($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasKind() => $_has(1);
  @$pb.TagNumber(2)
  void clearKind() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get username => $_getSZ(2);
  @$pb.TagNumber(3)
  set username($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasUsername() => $_has(2);
  @$pb.TagNumber(3)
  void clearUsername() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get message => $_getSZ(3);
  @$pb.TagNumber(4)
  set message($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasMessage() => $_has(3);
  @$pb.TagNumber(4)
  void clearMessage() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get url => $_getSZ(4);
  @$pb.TagNumber(5)
  set url($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasUrl() => $_has(4);
  @$pb.TagNumber(5)
  void clearUrl() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get secretMessage => $_getSZ(5);
  @$pb.TagNumber(6)
  set secretMessage($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasSecretMessage() => $_has(5);
  @$pb.TagNumber(6)
  void clearSecretMessage() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.bool get echo => $_getBF(6);
  @$pb.TagNumber(7)
  set echo($core.bool value) => $_setBool(6, value);
  @$pb.TagNumber(7)
  $core.bool hasEcho() => $_has(6);
  @$pb.TagNumber(7)
  void clearEcho() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.String get previousError => $_getSZ(7);
  @$pb.TagNumber(8)
  set previousError($core.String value) => $_setString(7, value);
  @$pb.TagNumber(8)
  $core.bool hasPreviousError() => $_has(7);
  @$pb.TagNumber(8)
  void clearPreviousError() => $_clearField(8);

  @$pb.TagNumber(9)
  $fixnum.Int64 get deadline => $_getI64(8);
  @$pb.TagNumber(9)
  set deadline($fixnum.Int64 value) => $_setInt64(8, value);
  @$pb.TagNumber(9)
  $core.bool hasDeadline() => $_has(8);
  @$pb.TagNumber(9)
  void clearDeadline() => $_clearField(9);
}

class OpenVPNChallengeSubmission extends $pb.GeneratedMessage {
  factory OpenVPNChallengeSubmission({
    $core.String? endpointTag,
    $core.String? challengeID,
    $core.String? username,
    $core.String? password,
    $core.String? secret,
  }) {
    final result = OpenVPNChallengeSubmission._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (challengeID != null) result.challengeID = challengeID;
    if (username != null) result.username = username;
    if (password != null) result.password = password;
    if (secret != null) result.secret = secret;
    return result;
  }

  OpenVPNChallengeSubmission._();

  factory OpenVPNChallengeSubmission.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNChallengeSubmission()..mergeFromBuffer(data, registry);
  factory OpenVPNChallengeSubmission.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNChallengeSubmission()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenVPNChallengeSubmission',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenVPNChallengeSubmission.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'challengeID', protoName: 'challengeID')
    ..aOS(3, _omitFieldNames ? '' : 'username')
    ..aOS(4, _omitFieldNames ? '' : 'password')
    ..aOS(5, _omitFieldNames ? '' : 'secret')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNChallengeSubmission clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNChallengeSubmission copyWith(
          void Function(OpenVPNChallengeSubmission) updates) =>
      super.copyWith(
              (message) => updates(message as OpenVPNChallengeSubmission))
          as OpenVPNChallengeSubmission;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenVPNChallengeSubmission() / OpenVPNChallengeSubmission.new instead')
  static OpenVPNChallengeSubmission create() => OpenVPNChallengeSubmission._();
  static $pb.GeneratedMessage $_createMessage() =>
      OpenVPNChallengeSubmission._();
  @$core.override
  OpenVPNChallengeSubmission createEmptyInstance() =>
      OpenVPNChallengeSubmission._();
  @$core.pragma('dart2js:noInline')
  static OpenVPNChallengeSubmission getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenVPNChallengeSubmission>(
          OpenVPNChallengeSubmission.$_createMessage);
  static OpenVPNChallengeSubmission? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get challengeID => $_getSZ(1);
  @$pb.TagNumber(2)
  set challengeID($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasChallengeID() => $_has(1);
  @$pb.TagNumber(2)
  void clearChallengeID() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get username => $_getSZ(2);
  @$pb.TagNumber(3)
  set username($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasUsername() => $_has(2);
  @$pb.TagNumber(3)
  void clearUsername() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get password => $_getSZ(3);
  @$pb.TagNumber(4)
  set password($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasPassword() => $_has(3);
  @$pb.TagNumber(4)
  void clearPassword() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get secret => $_getSZ(4);
  @$pb.TagNumber(5)
  set secret($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSecret() => $_has(4);
  @$pb.TagNumber(5)
  void clearSecret() => $_clearField(5);
}

class OpenVPNChallengeCancel extends $pb.GeneratedMessage {
  factory OpenVPNChallengeCancel({
    $core.String? endpointTag,
    $core.String? challengeID,
  }) {
    final result = OpenVPNChallengeCancel._();
    if (endpointTag != null) result.endpointTag = endpointTag;
    if (challengeID != null) result.challengeID = challengeID;
    return result;
  }

  OpenVPNChallengeCancel._();

  factory OpenVPNChallengeCancel.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNChallengeCancel()..mergeFromBuffer(data, registry);
  factory OpenVPNChallengeCancel.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      OpenVPNChallengeCancel()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'OpenVPNChallengeCancel',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: OpenVPNChallengeCancel.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'endpointTag', protoName: 'endpointTag')
    ..aOS(2, _omitFieldNames ? '' : 'challengeID', protoName: 'challengeID')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNChallengeCancel clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  OpenVPNChallengeCancel copyWith(
          void Function(OpenVPNChallengeCancel) updates) =>
      super.copyWith((message) => updates(message as OpenVPNChallengeCancel))
          as OpenVPNChallengeCancel;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated(
      'Use OpenVPNChallengeCancel() / OpenVPNChallengeCancel.new instead')
  static OpenVPNChallengeCancel create() => OpenVPNChallengeCancel._();
  static $pb.GeneratedMessage $_createMessage() => OpenVPNChallengeCancel._();
  @$core.override
  OpenVPNChallengeCancel createEmptyInstance() => OpenVPNChallengeCancel._();
  @$core.pragma('dart2js:noInline')
  static OpenVPNChallengeCancel getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<OpenVPNChallengeCancel>(
          OpenVPNChallengeCancel.$_createMessage);
  static OpenVPNChallengeCancel? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get endpointTag => $_getSZ(0);
  @$pb.TagNumber(1)
  set endpointTag($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasEndpointTag() => $_has(0);
  @$pb.TagNumber(1)
  void clearEndpointTag() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get challengeID => $_getSZ(1);
  @$pb.TagNumber(2)
  set challengeID($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasChallengeID() => $_has(1);
  @$pb.TagNumber(2)
  void clearChallengeID() => $_clearField(2);
}

enum NotificationEvent_Event { send, cancel, notSet }

class NotificationEvent extends $pb.GeneratedMessage {
  factory NotificationEvent({
    Notification? send,
    NotificationCancel? cancel,
  }) {
    final result = NotificationEvent._();
    if (send != null) result.send = send;
    if (cancel != null) result.cancel = cancel;
    return result;
  }

  NotificationEvent._();

  factory NotificationEvent.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      NotificationEvent()..mergeFromBuffer(data, registry);
  factory NotificationEvent.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      NotificationEvent()..mergeFromJson(json, registry);

  static const $core.Map<$core.int, NotificationEvent_Event>
      _NotificationEvent_EventByTag = {
    1: NotificationEvent_Event.send,
    2: NotificationEvent_Event.cancel,
    0: NotificationEvent_Event.notSet
  };
  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'NotificationEvent',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: NotificationEvent.$_createMessage)
    ..oo(0, [1, 2])
    ..aOM<Notification>(1, _omitFieldNames ? '' : 'send',
        subBuilder: Notification.$_createMessage)
    ..aOM<NotificationCancel>(2, _omitFieldNames ? '' : 'cancel',
        subBuilder: NotificationCancel.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NotificationEvent clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NotificationEvent copyWith(void Function(NotificationEvent) updates) =>
      super.copyWith((message) => updates(message as NotificationEvent))
          as NotificationEvent;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use NotificationEvent() / NotificationEvent.new instead')
  static NotificationEvent create() => NotificationEvent._();
  static $pb.GeneratedMessage $_createMessage() => NotificationEvent._();
  @$core.override
  NotificationEvent createEmptyInstance() => NotificationEvent._();
  @$core.pragma('dart2js:noInline')
  static NotificationEvent getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<NotificationEvent>(
          NotificationEvent.$_createMessage);
  static NotificationEvent? _defaultInstance;

  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  NotificationEvent_Event whichEvent() =>
      _NotificationEvent_EventByTag[$_whichOneof(0)]!;
  @$pb.TagNumber(1)
  @$pb.TagNumber(2)
  void clearEvent() => $_clearField($_whichOneof(0));

  @$pb.TagNumber(1)
  Notification get send => $_getN(0);
  @$pb.TagNumber(1)
  set send(Notification value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasSend() => $_has(0);
  @$pb.TagNumber(1)
  void clearSend() => $_clearField(1);
  @$pb.TagNumber(1)
  Notification ensureSend() => $_ensure(0);

  @$pb.TagNumber(2)
  NotificationCancel get cancel => $_getN(1);
  @$pb.TagNumber(2)
  set cancel(NotificationCancel value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasCancel() => $_has(1);
  @$pb.TagNumber(2)
  void clearCancel() => $_clearField(2);
  @$pb.TagNumber(2)
  NotificationCancel ensureCancel() => $_ensure(1);
}

class Notification extends $pb.GeneratedMessage {
  factory Notification({
    $core.String? identifier,
    $core.String? typeName,
    $core.int? typeID,
    $core.String? title,
    $core.String? subtitle,
    $core.String? body,
    $core.String? openURL,
  }) {
    final result = Notification._();
    if (identifier != null) result.identifier = identifier;
    if (typeName != null) result.typeName = typeName;
    if (typeID != null) result.typeID = typeID;
    if (title != null) result.title = title;
    if (subtitle != null) result.subtitle = subtitle;
    if (body != null) result.body = body;
    if (openURL != null) result.openURL = openURL;
    return result;
  }

  Notification._();

  factory Notification.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Notification()..mergeFromBuffer(data, registry);
  factory Notification.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Notification()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Notification',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: Notification.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'identifier')
    ..aOS(2, _omitFieldNames ? '' : 'typeName', protoName: 'typeName')
    ..aI(3, _omitFieldNames ? '' : 'typeID', protoName: 'typeID')
    ..aOS(4, _omitFieldNames ? '' : 'title')
    ..aOS(5, _omitFieldNames ? '' : 'subtitle')
    ..aOS(6, _omitFieldNames ? '' : 'body')
    ..aOS(7, _omitFieldNames ? '' : 'openURL', protoName: 'openURL')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Notification clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Notification copyWith(void Function(Notification) updates) =>
      super.copyWith((message) => updates(message as Notification))
          as Notification;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Notification() / Notification.new instead')
  static Notification create() => Notification._();
  static $pb.GeneratedMessage $_createMessage() => Notification._();
  @$core.override
  Notification createEmptyInstance() => Notification._();
  @$core.pragma('dart2js:noInline')
  static Notification getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<Notification>(
          Notification.$_createMessage);
  static Notification? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get identifier => $_getSZ(0);
  @$pb.TagNumber(1)
  set identifier($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIdentifier() => $_has(0);
  @$pb.TagNumber(1)
  void clearIdentifier() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get typeName => $_getSZ(1);
  @$pb.TagNumber(2)
  set typeName($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasTypeName() => $_has(1);
  @$pb.TagNumber(2)
  void clearTypeName() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get typeID => $_getIZ(2);
  @$pb.TagNumber(3)
  set typeID($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTypeID() => $_has(2);
  @$pb.TagNumber(3)
  void clearTypeID() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get title => $_getSZ(3);
  @$pb.TagNumber(4)
  set title($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasTitle() => $_has(3);
  @$pb.TagNumber(4)
  void clearTitle() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get subtitle => $_getSZ(4);
  @$pb.TagNumber(5)
  set subtitle($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasSubtitle() => $_has(4);
  @$pb.TagNumber(5)
  void clearSubtitle() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get body => $_getSZ(5);
  @$pb.TagNumber(6)
  set body($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasBody() => $_has(5);
  @$pb.TagNumber(6)
  void clearBody() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.String get openURL => $_getSZ(6);
  @$pb.TagNumber(7)
  set openURL($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasOpenURL() => $_has(6);
  @$pb.TagNumber(7)
  void clearOpenURL() => $_clearField(7);
}

class NotificationCancel extends $pb.GeneratedMessage {
  factory NotificationCancel({
    $core.String? identifier,
    $core.int? typeID,
  }) {
    final result = NotificationCancel._();
    if (identifier != null) result.identifier = identifier;
    if (typeID != null) result.typeID = typeID;
    return result;
  }

  NotificationCancel._();

  factory NotificationCancel.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      NotificationCancel()..mergeFromBuffer(data, registry);
  factory NotificationCancel.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      NotificationCancel()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'NotificationCancel',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'daemon'),
      createEmptyInstance: NotificationCancel.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'identifier')
    ..aI(2, _omitFieldNames ? '' : 'typeID', protoName: 'typeID')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NotificationCancel clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  NotificationCancel copyWith(void Function(NotificationCancel) updates) =>
      super.copyWith((message) => updates(message as NotificationCancel))
          as NotificationCancel;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use NotificationCancel() / NotificationCancel.new instead')
  static NotificationCancel create() => NotificationCancel._();
  static $pb.GeneratedMessage $_createMessage() => NotificationCancel._();
  @$core.override
  NotificationCancel createEmptyInstance() => NotificationCancel._();
  @$core.pragma('dart2js:noInline')
  static NotificationCancel getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<NotificationCancel>(
          NotificationCancel.$_createMessage);
  static NotificationCancel? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get identifier => $_getSZ(0);
  @$pb.TagNumber(1)
  set identifier($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIdentifier() => $_has(0);
  @$pb.TagNumber(1)
  void clearIdentifier() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.int get typeID => $_getIZ(1);
  @$pb.TagNumber(2)
  set typeID($core.int value) => $_setSignedInt32(1, value);
  @$pb.TagNumber(2)
  $core.bool hasTypeID() => $_has(1);
  @$pb.TagNumber(2)
  void clearTypeID() => $_clearField(2);
}

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
