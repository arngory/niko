// This is a generated file - do not edit.
//
// Generated from niko/v1/niko_service.proto.

// @dart = 3.3

// ignore_for_file: annotate_overrides, camel_case_types, comment_references
// ignore_for_file: constant_identifier_names
// ignore_for_file: curly_braces_in_flow_control_structures
// ignore_for_file: deprecated_member_use_from_same_package, library_prefixes
// ignore_for_file: non_constant_identifier_names, prefer_relative_imports

import 'dart:core' as $core;

import 'package:fixnum/fixnum.dart' as $fixnum;
import 'package:protobuf/protobuf.dart' as $pb;

import 'niko_service.pbenum.dart';

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

export 'niko_service.pbenum.dart';

class Empty extends $pb.GeneratedMessage {
  factory Empty() => Empty._();

  Empty._();

  factory Empty.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Empty()..mergeFromBuffer(data, registry);
  factory Empty.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Empty()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Empty',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: Empty.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Empty clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Empty copyWith(void Function(Empty) updates) =>
      super.copyWith((message) => updates(message as Empty)) as Empty;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Empty() / Empty.new instead')
  static Empty create() => Empty._();
  static $pb.GeneratedMessage $_createMessage() => Empty._();
  @$core.override
  Empty createEmptyInstance() => Empty._();
  @$core.pragma('dart2js:noInline')
  static Empty getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Empty>(Empty.$_createMessage);
  static Empty? _defaultInstance;
}

class GetInfoRequest extends $pb.GeneratedMessage {
  factory GetInfoRequest() => GetInfoRequest._();

  GetInfoRequest._();

  factory GetInfoRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GetInfoRequest()..mergeFromBuffer(data, registry);
  factory GetInfoRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GetInfoRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GetInfoRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: GetInfoRequest.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GetInfoRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GetInfoRequest copyWith(void Function(GetInfoRequest) updates) =>
      super.copyWith((message) => updates(message as GetInfoRequest))
          as GetInfoRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use GetInfoRequest() / GetInfoRequest.new instead')
  static GetInfoRequest create() => GetInfoRequest._();
  static $pb.GeneratedMessage $_createMessage() => GetInfoRequest._();
  @$core.override
  GetInfoRequest createEmptyInstance() => GetInfoRequest._();
  @$core.pragma('dart2js:noInline')
  static GetInfoRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<GetInfoRequest>(
          GetInfoRequest.$_createMessage);
  static GetInfoRequest? _defaultInstance;
}

class Info extends $pb.GeneratedMessage {
  factory Info({
    $core.String? nikoVersion,
    $core.String? coreVersion,
    $core.int? storeSchema,
    $core.int? api,
  }) {
    final result = Info._();
    if (nikoVersion != null) result.nikoVersion = nikoVersion;
    if (coreVersion != null) result.coreVersion = coreVersion;
    if (storeSchema != null) result.storeSchema = storeSchema;
    if (api != null) result.api = api;
    return result;
  }

  Info._();

  factory Info.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Info()..mergeFromBuffer(data, registry);
  factory Info.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Info()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Info',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: Info.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'nikoVersion')
    ..aOS(2, _omitFieldNames ? '' : 'coreVersion')
    ..aI(3, _omitFieldNames ? '' : 'storeSchema')
    ..aI(4, _omitFieldNames ? '' : 'api')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Info clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Info copyWith(void Function(Info) updates) =>
      super.copyWith((message) => updates(message as Info)) as Info;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Info() / Info.new instead')
  static Info create() => Info._();
  static $pb.GeneratedMessage $_createMessage() => Info._();
  @$core.override
  Info createEmptyInstance() => Info._();
  @$core.pragma('dart2js:noInline')
  static Info getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Info>(Info.$_createMessage);
  static Info? _defaultInstance;

  /// Niko's version, as its build names it
  @$pb.TagNumber(1)
  $core.String get nikoVersion => $_getSZ(0);
  @$pb.TagNumber(1)
  set nikoVersion($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasNikoVersion() => $_has(0);
  @$pb.TagNumber(1)
  void clearNikoVersion() => $_clearField(1);

  /// the core's version
  @$pb.TagNumber(2)
  $core.String get coreVersion => $_getSZ(1);
  @$pb.TagNumber(2)
  set coreVersion($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasCoreVersion() => $_has(1);
  @$pb.TagNumber(2)
  void clearCoreVersion() => $_clearField(2);

  /// the schema of the daemon's store (nikostore's); 0: a daemon without one
  @$pb.TagNumber(3)
  $core.int get storeSchema => $_getIZ(2);
  @$pb.TagNumber(3)
  set storeSchema($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasStoreSchema() => $_has(2);
  @$pb.TagNumber(3)
  void clearStoreSchema() => $_clearField(3);

  /// the version of this API: what the window may ask. 2: the store, Watch and the import; 3: Select and GetSelected;
  /// 4: ShareProfile; 5: Connect; 6: Connect's mode, the config a computer's; 7: the computer's settings, Connect
  /// with them; 8: the programs through Niko; 9: subscriptions (Group.subscription, Refresh, their import)
  @$pb.TagNumber(4)
  $core.int get api => $_getIZ(3);
  @$pb.TagNumber(4)
  set api($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasApi() => $_has(3);
  @$pb.TagNumber(4)
  void clearApi() => $_clearField(4);
}

class RouteTestRequest extends $pb.GeneratedMessage {
  factory RouteTestRequest({
    $core.String? network,
    $core.String? host,
    $core.int? port,
    $core.String? inbound,
    $core.bool? resolve,
  }) {
    final result = RouteTestRequest._();
    if (network != null) result.network = network;
    if (host != null) result.host = host;
    if (port != null) result.port = port;
    if (inbound != null) result.inbound = inbound;
    if (resolve != null) result.resolve = resolve;
    return result;
  }

  RouteTestRequest._();

  factory RouteTestRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      RouteTestRequest()..mergeFromBuffer(data, registry);
  factory RouteTestRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      RouteTestRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RouteTestRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: RouteTestRequest.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'network')
    ..aOS(2, _omitFieldNames ? '' : 'host')
    ..aI(3, _omitFieldNames ? '' : 'port', fieldType: $pb.PbFieldType.OU3)
    ..aOS(4, _omitFieldNames ? '' : 'inbound')
    ..aOB(5, _omitFieldNames ? '' : 'resolve')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RouteTestRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RouteTestRequest copyWith(void Function(RouteTestRequest) updates) =>
      super.copyWith((message) => updates(message as RouteTestRequest))
          as RouteTestRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use RouteTestRequest() / RouteTestRequest.new instead')
  static RouteTestRequest create() => RouteTestRequest._();
  static $pb.GeneratedMessage $_createMessage() => RouteTestRequest._();
  @$core.override
  RouteTestRequest createEmptyInstance() => RouteTestRequest._();
  @$core.pragma('dart2js:noInline')
  static RouteTestRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<RouteTestRequest>(
          RouteTestRequest.$_createMessage);
  static RouteTestRequest? _defaultInstance;

  /// tcp (the default) or udp
  @$pb.TagNumber(1)
  $core.String get network => $_getSZ(0);
  @$pb.TagNumber(1)
  set network($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasNetwork() => $_has(0);
  @$pb.TagNumber(1)
  void clearNetwork() => $_clearField(1);

  /// a domain or an address
  @$pb.TagNumber(2)
  $core.String get host => $_getSZ(1);
  @$pb.TagNumber(2)
  set host($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasHost() => $_has(1);
  @$pb.TagNumber(2)
  void clearHost() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.int get port => $_getIZ(2);
  @$pb.TagNumber(3)
  set port($core.int value) => $_setUnsignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasPort() => $_has(2);
  @$pb.TagNumber(3)
  void clearPort() => $_clearField(3);

  /// the inbound's tag: tun-in (the default), mixed-in or another of the running core's
  @$pb.TagNumber(4)
  $core.String get inbound => $_getSZ(3);
  @$pb.TagNumber(4)
  set inbound($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasInbound() => $_has(3);
  @$pb.TagNumber(4)
  void clearInbound() => $_clearField(4);

  /// DNS queries allowed, through the core's DNS: an app's own query for a domain through the VPN, a resolve
  /// action's; they leave what a real query would (a fake address, a cache entry)
  @$pb.TagNumber(5)
  $core.bool get resolve => $_getBF(4);
  @$pb.TagNumber(5)
  set resolve($core.bool value) => $_setBool(4, value);
  @$pb.TagNumber(5)
  $core.bool hasResolve() => $_has(4);
  @$pb.TagNumber(5)
  void clearResolve() => $_clearField(5);
}

/// A rule the connection matched on its way.
class RouteTestStep extends $pb.GeneratedMessage {
  factory RouteTestStep({
    $core.int? index,
    $core.String? rule,
    $core.String? action,
    $core.Iterable<$core.String>? resolved,
    $core.String? note,
  }) {
    final result = RouteTestStep._();
    if (index != null) result.index = index;
    if (rule != null) result.rule = rule;
    if (action != null) result.action = action;
    if (resolved != null) result.resolved.addAll(resolved);
    if (note != null) result.note = note;
    return result;
  }

  RouteTestStep._();

  factory RouteTestStep.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      RouteTestStep()..mergeFromBuffer(data, registry);
  factory RouteTestStep.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      RouteTestStep()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RouteTestStep',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: RouteTestStep.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'index')
    ..aOS(2, _omitFieldNames ? '' : 'rule')
    ..aOS(3, _omitFieldNames ? '' : 'action')
    ..pPS(4, _omitFieldNames ? '' : 'resolved')
    ..aOS(5, _omitFieldNames ? '' : 'note')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RouteTestStep clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RouteTestStep copyWith(void Function(RouteTestStep) updates) =>
      super.copyWith((message) => updates(message as RouteTestStep))
          as RouteTestStep;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use RouteTestStep() / RouteTestStep.new instead')
  static RouteTestStep create() => RouteTestStep._();
  static $pb.GeneratedMessage $_createMessage() => RouteTestStep._();
  @$core.override
  RouteTestStep createEmptyInstance() => RouteTestStep._();
  @$core.pragma('dart2js:noInline')
  static RouteTestStep getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<RouteTestStep>(
          RouteTestStep.$_createMessage);
  static RouteTestStep? _defaultInstance;

  @$pb.TagNumber(1)
  $core.int get index => $_getIZ(0);
  @$pb.TagNumber(1)
  set index($core.int value) => $_setSignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIndex() => $_has(0);
  @$pb.TagNumber(1)
  void clearIndex() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get rule => $_getSZ(1);
  @$pb.TagNumber(2)
  set rule($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasRule() => $_has(1);
  @$pb.TagNumber(2)
  void clearRule() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get action => $_getSZ(2);
  @$pb.TagNumber(3)
  set action($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasAction() => $_has(2);
  @$pb.TagNumber(3)
  void clearAction() => $_clearField(3);

  /// a resolve action's addresses
  @$pb.TagNumber(4)
  $pb.PbList<$core.String> get resolved => $_getList(3);

  /// what the action did, or what of it was taken on trust
  @$pb.TagNumber(5)
  $core.String get note => $_getSZ(4);
  @$pb.TagNumber(5)
  set note($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasNote() => $_has(4);
  @$pb.TagNumber(5)
  void clearNote() => $_clearField(5);
}

class RouteTestResult extends $pb.GeneratedMessage {
  factory RouteTestResult({
    $core.int? index,
    $core.String? rule,
    $core.String? action,
    $core.String? outbound,
    $core.String? error,
    $core.String? destination,
    $core.bool? fakeIp,
    $core.Iterable<$core.String>? resolved,
    $core.Iterable<$core.String>? notes,
    $core.Iterable<RouteTestStep>? steps,
  }) {
    final result = RouteTestResult._();
    if (index != null) result.index = index;
    if (rule != null) result.rule = rule;
    if (action != null) result.action = action;
    if (outbound != null) result.outbound = outbound;
    if (error != null) result.error = error;
    if (destination != null) result.destination = destination;
    if (fakeIp != null) result.fakeIp = fakeIp;
    if (resolved != null) result.resolved.addAll(resolved);
    if (notes != null) result.notes.addAll(notes);
    if (steps != null) result.steps.addAll(steps);
    return result;
  }

  RouteTestResult._();

  factory RouteTestResult.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      RouteTestResult()..mergeFromBuffer(data, registry);
  factory RouteTestResult.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      RouteTestResult()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RouteTestResult',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: RouteTestResult.$_createMessage)
    ..aI(1, _omitFieldNames ? '' : 'index')
    ..aOS(2, _omitFieldNames ? '' : 'rule')
    ..aOS(3, _omitFieldNames ? '' : 'action')
    ..aOS(4, _omitFieldNames ? '' : 'outbound')
    ..aOS(5, _omitFieldNames ? '' : 'error')
    ..aOS(6, _omitFieldNames ? '' : 'destination')
    ..aOB(7, _omitFieldNames ? '' : 'fakeIp')
    ..pPS(8, _omitFieldNames ? '' : 'resolved')
    ..pPS(9, _omitFieldNames ? '' : 'notes')
    ..pPM<RouteTestStep>(10, _omitFieldNames ? '' : 'steps',
        subBuilder: RouteTestStep.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RouteTestResult clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RouteTestResult copyWith(void Function(RouteTestResult) updates) =>
      super.copyWith((message) => updates(message as RouteTestResult))
          as RouteTestResult;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use RouteTestResult() / RouteTestResult.new instead')
  static RouteTestResult create() => RouteTestResult._();
  static $pb.GeneratedMessage $_createMessage() => RouteTestResult._();
  @$core.override
  RouteTestResult createEmptyInstance() => RouteTestResult._();
  @$core.pragma('dart2js:noInline')
  static RouteTestResult getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<RouteTestResult>(
          RouteTestResult.$_createMessage);
  static RouteTestResult? _defaultInstance;

  /// the rule that decided; -1: none, the final outbound took it (or a lookup before the rules failed)
  @$pb.TagNumber(1)
  $core.int get index => $_getIZ(0);
  @$pb.TagNumber(1)
  set index($core.int value) => $_setSignedInt32(0, value);
  @$pb.TagNumber(1)
  $core.bool hasIndex() => $_has(0);
  @$pb.TagNumber(1)
  void clearIndex() => $_clearField(1);

  @$pb.TagNumber(2)
  $core.String get rule => $_getSZ(1);
  @$pb.TagNumber(2)
  set rule($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasRule() => $_has(1);
  @$pb.TagNumber(2)
  void clearRule() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get action => $_getSZ(2);
  @$pb.TagNumber(3)
  set action($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasAction() => $_has(2);
  @$pb.TagNumber(3)
  void clearAction() => $_clearField(3);

  @$pb.TagNumber(4)
  $core.String get outbound => $_getSZ(3);
  @$pb.TagNumber(4)
  set outbound($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasOutbound() => $_has(3);
  @$pb.TagNumber(4)
  void clearOutbound() => $_clearField(4);

  /// a lookup the connection cannot do without failed, and it goes nowhere
  @$pb.TagNumber(5)
  $core.String get error => $_getSZ(4);
  @$pb.TagNumber(5)
  set error($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasError() => $_has(4);
  @$pb.TagNumber(5)
  void clearError() => $_clearField(5);

  /// the connection as the rules first saw it
  @$pb.TagNumber(6)
  $core.String get destination => $_getSZ(5);
  @$pb.TagNumber(6)
  set destination($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasDestination() => $_has(5);
  @$pb.TagNumber(6)
  void clearDestination() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.bool get fakeIp => $_getBF(6);
  @$pb.TagNumber(7)
  set fakeIp($core.bool value) => $_setBool(6, value);
  @$pb.TagNumber(7)
  $core.bool hasFakeIp() => $_has(6);
  @$pb.TagNumber(7)
  void clearFakeIp() => $_clearField(7);

  /// what the core's DNS answered the app's own query through the VPN
  @$pb.TagNumber(8)
  $pb.PbList<$core.String> get resolved => $_getList(7);

  /// what the rules did not see as they would see a real connection
  @$pb.TagNumber(9)
  $pb.PbList<$core.String> get notes => $_getList(8);

  @$pb.TagNumber(10)
  $pb.PbList<RouteTestStep> get steps => $_getList(9);
}

class WatchRequest extends $pb.GeneratedMessage {
  factory WatchRequest() => WatchRequest._();

  WatchRequest._();

  factory WatchRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WatchRequest()..mergeFromBuffer(data, registry);
  factory WatchRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      WatchRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'WatchRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: WatchRequest.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WatchRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  WatchRequest copyWith(void Function(WatchRequest) updates) =>
      super.copyWith((message) => updates(message as WatchRequest))
          as WatchRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use WatchRequest() / WatchRequest.new instead')
  static WatchRequest create() => WatchRequest._();
  static $pb.GeneratedMessage $_createMessage() => WatchRequest._();
  @$core.override
  WatchRequest createEmptyInstance() => WatchRequest._();
  @$core.pragma('dart2js:noInline')
  static WatchRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<WatchRequest>(
          WatchRequest.$_createMessage);
  static WatchRequest? _defaultInstance;
}

/// What one write changed, or a task's result.
class Change extends $pb.GeneratedMessage {
  factory Change({
    $fixnum.Int64? revision,
    $core.bool? reset,
    Change_Kind? kind,
    $core.Iterable<$fixnum.Int64>? ids,
    TaskProgress? task,
  }) {
    final result = Change._();
    if (revision != null) result.revision = revision;
    if (reset != null) result.reset = reset;
    if (kind != null) result.kind = kind;
    if (ids != null) result.ids.addAll(ids);
    if (task != null) result.task = task;
    return result;
  }

  Change._();

  factory Change.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Change()..mergeFromBuffer(data, registry);
  factory Change.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Change()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Change',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: Change.$_createMessage)
    ..a<$fixnum.Int64>(
        1, _omitFieldNames ? '' : 'revision', $pb.PbFieldType.OU6,
        defaultOrMaker: $fixnum.Int64.ZERO)
    ..aOB(2, _omitFieldNames ? '' : 'reset')
    ..aE<Change_Kind>(3, _omitFieldNames ? '' : 'kind',
        enumValues: Change_Kind.values)
    ..p<$fixnum.Int64>(4, _omitFieldNames ? '' : 'ids', $pb.PbFieldType.K6)
    ..aOM<TaskProgress>(5, _omitFieldNames ? '' : 'task',
        subBuilder: TaskProgress.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Change clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Change copyWith(void Function(Change) updates) =>
      super.copyWith((message) => updates(message as Change)) as Change;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Change() / Change.new instead')
  static Change create() => Change._();
  static $pb.GeneratedMessage $_createMessage() => Change._();
  @$core.override
  Change createEmptyInstance() => Change._();
  @$core.pragma('dart2js:noInline')
  static Change getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Change>(Change.$_createMessage);
  static Change? _defaultInstance;

  /// grows with each change from the daemon's start; a reset's is the revision now
  @$pb.TagNumber(1)
  $fixnum.Int64 get revision => $_getI64(0);
  @$pb.TagNumber(1)
  set revision($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasRevision() => $_has(0);
  @$pb.TagNumber(1)
  void clearRevision() => $_clearField(1);

  /// read again all that is shown: the first message, and where changes were lost
  @$pb.TagNumber(2)
  $core.bool get reset => $_getBF(1);
  @$pb.TagNumber(2)
  set reset($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasReset() => $_has(1);
  @$pb.TagNumber(2)
  void clearReset() => $_clearField(2);

  @$pb.TagNumber(3)
  Change_Kind get kind => $_getN(2);
  @$pb.TagNumber(3)
  set kind(Change_Kind value) => $_setField(3, value);
  @$pb.TagNumber(3)
  $core.bool hasKind() => $_has(2);
  @$pb.TagNumber(3)
  void clearKind() => $_clearField(3);

  /// what changed: added, changed or removed; empty: the whole kind
  @$pb.TagNumber(4)
  $pb.PbList<$fixnum.Int64> get ids => $_getList(3);

  /// the task's result, with kind TASK
  @$pb.TagNumber(5)
  TaskProgress get task => $_getN(4);
  @$pb.TagNumber(5)
  set task(TaskProgress value) => $_setField(5, value);
  @$pb.TagNumber(5)
  $core.bool hasTask() => $_has(4);
  @$pb.TagNumber(5)
  void clearTask() => $_clearField(5);
  @$pb.TagNumber(5)
  TaskProgress ensureTask() => $_ensure(4);
}

/// A task of the daemon's (an import): its steps, then its result. A task goes on to its end without the stream that
/// asked for it; its result comes in Watch too.
class TaskProgress extends $pb.GeneratedMessage {
  factory TaskProgress({
    $core.String? taskId,
    $core.String? line,
    $core.bool? done,
    $core.int? code,
    $core.String? error,
    $core.Iterable<$fixnum.Int64>? ids,
    $fixnum.Int64? groupId,
  }) {
    final result = TaskProgress._();
    if (taskId != null) result.taskId = taskId;
    if (line != null) result.line = line;
    if (done != null) result.done = done;
    if (code != null) result.code = code;
    if (error != null) result.error = error;
    if (ids != null) result.ids.addAll(ids);
    if (groupId != null) result.groupId = groupId;
    return result;
  }

  TaskProgress._();

  factory TaskProgress.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaskProgress()..mergeFromBuffer(data, registry);
  factory TaskProgress.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      TaskProgress()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'TaskProgress',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: TaskProgress.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'taskId')
    ..aOS(2, _omitFieldNames ? '' : 'line')
    ..aOB(3, _omitFieldNames ? '' : 'done')
    ..aI(4, _omitFieldNames ? '' : 'code')
    ..aOS(5, _omitFieldNames ? '' : 'error')
    ..p<$fixnum.Int64>(7, _omitFieldNames ? '' : 'ids', $pb.PbFieldType.K6)
    ..aInt64(8, _omitFieldNames ? '' : 'groupId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaskProgress clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  TaskProgress copyWith(void Function(TaskProgress) updates) =>
      super.copyWith((message) => updates(message as TaskProgress))
          as TaskProgress;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use TaskProgress() / TaskProgress.new instead')
  static TaskProgress create() => TaskProgress._();
  static $pb.GeneratedMessage $_createMessage() => TaskProgress._();
  @$core.override
  TaskProgress createEmptyInstance() => TaskProgress._();
  @$core.pragma('dart2js:noInline')
  static TaskProgress getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<TaskProgress>(
          TaskProgress.$_createMessage);
  static TaskProgress? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get taskId => $_getSZ(0);
  @$pb.TagNumber(1)
  set taskId($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasTaskId() => $_has(0);
  @$pb.TagNumber(1)
  void clearTaskId() => $_clearField(1);

  /// a line for the user
  @$pb.TagNumber(2)
  $core.String get line => $_getSZ(1);
  @$pb.TagNumber(2)
  set line($core.String value) => $_setString(1, value);
  @$pb.TagNumber(2)
  $core.bool hasLine() => $_has(1);
  @$pb.TagNumber(2)
  void clearLine() => $_clearField(2);

  /// the result
  @$pb.TagNumber(3)
  $core.bool get done => $_getBF(2);
  @$pb.TagNumber(3)
  set done($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasDone() => $_has(2);
  @$pb.TagNumber(3)
  void clearDone() => $_clearField(3);

  /// the result's gRPC code: 0 done, else what error says
  @$pb.TagNumber(4)
  $core.int get code => $_getIZ(3);
  @$pb.TagNumber(4)
  set code($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasCode() => $_has(3);
  @$pb.TagNumber(4)
  void clearCode() => $_clearField(4);

  @$pb.TagNumber(5)
  $core.String get error => $_getSZ(4);
  @$pb.TagNumber(5)
  set error($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasError() => $_has(4);
  @$pb.TagNumber(5)
  void clearError() => $_clearField(5);

  /// what the task added or changed: the profiles an import or an update added
  @$pb.TagNumber(7)
  $pb.PbList<$fixnum.Int64> get ids => $_getList(5);

  /// the subscription's group the task made or updated
  @$pb.TagNumber(8)
  $fixnum.Int64 get groupId => $_getI64(6);
  @$pb.TagNumber(8)
  set groupId($fixnum.Int64 value) => $_setInt64(6, value);
  @$pb.TagNumber(8)
  $core.bool hasGroupId() => $_has(6);
  @$pb.TagNumber(8)
  void clearGroupId() => $_clearField(8);
}

class Group extends $pb.GeneratedMessage {
  factory Group({
    $fixnum.Int64? id,
    $fixnum.Int64? order,
    $core.String? name,
    $core.bool? ungrouped,
    Group_Type? type,
    $core.int? profiles,
    Subscription? subscription,
  }) {
    final result = Group._();
    if (id != null) result.id = id;
    if (order != null) result.order = order;
    if (name != null) result.name = name;
    if (ungrouped != null) result.ungrouped = ungrouped;
    if (type != null) result.type = type;
    if (profiles != null) result.profiles = profiles;
    if (subscription != null) result.subscription = subscription;
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
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: Group.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'id')
    ..aInt64(2, _omitFieldNames ? '' : 'order')
    ..aOS(3, _omitFieldNames ? '' : 'name')
    ..aOB(4, _omitFieldNames ? '' : 'ungrouped')
    ..aE<Group_Type>(5, _omitFieldNames ? '' : 'type',
        enumValues: Group_Type.values)
    ..aI(6, _omitFieldNames ? '' : 'profiles')
    ..aOM<Subscription>(7, _omitFieldNames ? '' : 'subscription',
        subBuilder: Subscription.$_createMessage)
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
  $fixnum.Int64 get id => $_getI64(0);
  @$pb.TagNumber(1)
  set id($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasId() => $_has(0);
  @$pb.TagNumber(1)
  void clearId() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get order => $_getI64(1);
  @$pb.TagNumber(2)
  set order($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasOrder() => $_has(1);
  @$pb.TagNumber(2)
  void clearOrder() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get name => $_getSZ(2);
  @$pb.TagNumber(3)
  set name($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasName() => $_has(2);
  @$pb.TagNumber(3)
  void clearName() => $_clearField(3);

  /// the default group, made with the store: an import with no group goes there; it is not deleted
  @$pb.TagNumber(4)
  $core.bool get ungrouped => $_getBF(3);
  @$pb.TagNumber(4)
  set ungrouped($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasUngrouped() => $_has(3);
  @$pb.TagNumber(4)
  void clearUngrouped() => $_clearField(4);

  @$pb.TagNumber(5)
  Group_Type get type => $_getN(4);
  @$pb.TagNumber(5)
  set type(Group_Type value) => $_setField(5, value);
  @$pb.TagNumber(5)
  $core.bool hasType() => $_has(4);
  @$pb.TagNumber(5)
  void clearType() => $_clearField(5);

  /// how many profiles it has; not saved
  @$pb.TagNumber(6)
  $core.int get profiles => $_getIZ(5);
  @$pb.TagNumber(6)
  set profiles($core.int value) => $_setSignedInt32(5, value);
  @$pb.TagNumber(6)
  $core.bool hasProfiles() => $_has(5);
  @$pb.TagNumber(6)
  void clearProfiles() => $_clearField(6);

  /// a subscription's group's
  @$pb.TagNumber(7)
  Subscription get subscription => $_getN(6);
  @$pb.TagNumber(7)
  set subscription(Subscription value) => $_setField(7, value);
  @$pb.TagNumber(7)
  $core.bool hasSubscription() => $_has(6);
  @$pb.TagNumber(7)
  void clearSubscription() => $_clearField(7);
  @$pb.TagNumber(7)
  Subscription ensureSubscription() => $_ensure(6);
}

/// A group's subscription (the app's SubscriptionBean). The settings are the user's; what an update says is read only.
class Subscription extends $pb.GeneratedMessage {
  factory Subscription({
    $core.String? link,
    $core.bool? deduplication,
    $core.bool? forceResolve,
    $core.bool? updateWhenConnectedOnly,
    $core.String? customUserAgent,
    $core.bool? autoUpdate,
    $core.int? autoUpdateDelay,
    $fixnum.Int64? lastUpdated,
    $fixnum.Int64? used,
    $fixnum.Int64? total,
    $fixnum.Int64? expire,
    $core.String? announce,
    $core.bool? updating,
  }) {
    final result = Subscription._();
    if (link != null) result.link = link;
    if (deduplication != null) result.deduplication = deduplication;
    if (forceResolve != null) result.forceResolve = forceResolve;
    if (updateWhenConnectedOnly != null)
      result.updateWhenConnectedOnly = updateWhenConnectedOnly;
    if (customUserAgent != null) result.customUserAgent = customUserAgent;
    if (autoUpdate != null) result.autoUpdate = autoUpdate;
    if (autoUpdateDelay != null) result.autoUpdateDelay = autoUpdateDelay;
    if (lastUpdated != null) result.lastUpdated = lastUpdated;
    if (used != null) result.used = used;
    if (total != null) result.total = total;
    if (expire != null) result.expire = expire;
    if (announce != null) result.announce = announce;
    if (updating != null) result.updating = updating;
    return result;
  }

  Subscription._();

  factory Subscription.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Subscription()..mergeFromBuffer(data, registry);
  factory Subscription.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Subscription()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Subscription',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: Subscription.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'link')
    ..aOB(2, _omitFieldNames ? '' : 'deduplication')
    ..aOB(3, _omitFieldNames ? '' : 'forceResolve')
    ..aOB(4, _omitFieldNames ? '' : 'updateWhenConnectedOnly')
    ..aOS(5, _omitFieldNames ? '' : 'customUserAgent')
    ..aOB(6, _omitFieldNames ? '' : 'autoUpdate')
    ..aI(7, _omitFieldNames ? '' : 'autoUpdateDelay')
    ..aInt64(8, _omitFieldNames ? '' : 'lastUpdated')
    ..aInt64(9, _omitFieldNames ? '' : 'used')
    ..aInt64(10, _omitFieldNames ? '' : 'total')
    ..aInt64(11, _omitFieldNames ? '' : 'expire')
    ..aOS(12, _omitFieldNames ? '' : 'announce')
    ..aOB(13, _omitFieldNames ? '' : 'updating')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Subscription clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Subscription copyWith(void Function(Subscription) updates) =>
      super.copyWith((message) => updates(message as Subscription))
          as Subscription;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Subscription() / Subscription.new instead')
  static Subscription create() => Subscription._();
  static $pb.GeneratedMessage $_createMessage() => Subscription._();
  @$core.override
  Subscription createEmptyInstance() => Subscription._();
  @$core.pragma('dart2js:noInline')
  static Subscription getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<Subscription>(
          Subscription.$_createMessage);
  static Subscription? _defaultInstance;

  /// the address the profiles are fetched from: http or https; empty where it was sealed on another machine and is
  /// to be given again
  @$pb.TagNumber(1)
  $core.String get link => $_getSZ(0);
  @$pb.TagNumber(1)
  set link($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasLink() => $_has(0);
  @$pb.TagNumber(1)
  void clearLink() => $_clearField(1);

  /// one profile of each server
  @$pb.TagNumber(2)
  $core.bool get deduplication => $_getBF(1);
  @$pb.TagNumber(2)
  set deduplication($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasDeduplication() => $_has(1);
  @$pb.TagNumber(2)
  void clearDeduplication() => $_clearField(2);

  /// the servers' names resolved at each update
  @$pb.TagNumber(3)
  $core.bool get forceResolve => $_getBF(2);
  @$pb.TagNumber(3)
  set forceResolve($core.bool value) => $_setBool(2, value);
  @$pb.TagNumber(3)
  $core.bool hasForceResolve() => $_has(2);
  @$pb.TagNumber(3)
  void clearForceResolve() => $_clearField(3);

  /// the timed update waits for a connection
  @$pb.TagNumber(4)
  $core.bool get updateWhenConnectedOnly => $_getBF(3);
  @$pb.TagNumber(4)
  set updateWhenConnectedOnly($core.bool value) => $_setBool(3, value);
  @$pb.TagNumber(4)
  $core.bool hasUpdateWhenConnectedOnly() => $_has(3);
  @$pb.TagNumber(4)
  void clearUpdateWhenConnectedOnly() => $_clearField(4);

  /// the User-Agent the provider is asked with; empty: the app's
  @$pb.TagNumber(5)
  $core.String get customUserAgent => $_getSZ(4);
  @$pb.TagNumber(5)
  set customUserAgent($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasCustomUserAgent() => $_has(4);
  @$pb.TagNumber(5)
  void clearCustomUserAgent() => $_clearField(5);

  /// the timed update, every auto_update_delay minutes (at least 15; a day by default); the provider's interval turns
  /// it on at the first update
  @$pb.TagNumber(6)
  $core.bool get autoUpdate => $_getBF(5);
  @$pb.TagNumber(6)
  set autoUpdate($core.bool value) => $_setBool(5, value);
  @$pb.TagNumber(6)
  $core.bool hasAutoUpdate() => $_has(5);
  @$pb.TagNumber(6)
  void clearAutoUpdate() => $_clearField(6);

  @$pb.TagNumber(7)
  $core.int get autoUpdateDelay => $_getIZ(6);
  @$pb.TagNumber(7)
  set autoUpdateDelay($core.int value) => $_setSignedInt32(6, value);
  @$pb.TagNumber(7)
  $core.bool hasAutoUpdateDelay() => $_has(6);
  @$pb.TagNumber(7)
  void clearAutoUpdateDelay() => $_clearField(7);

  /// read only, from the updates: the last one's time (unix seconds, 0 for none); the traffic used and allowed
  /// (bytes) and the expiry (unix seconds) the provider said, 0 for unknown; the provider's announcement
  @$pb.TagNumber(8)
  $fixnum.Int64 get lastUpdated => $_getI64(7);
  @$pb.TagNumber(8)
  set lastUpdated($fixnum.Int64 value) => $_setInt64(7, value);
  @$pb.TagNumber(8)
  $core.bool hasLastUpdated() => $_has(7);
  @$pb.TagNumber(8)
  void clearLastUpdated() => $_clearField(8);

  @$pb.TagNumber(9)
  $fixnum.Int64 get used => $_getI64(8);
  @$pb.TagNumber(9)
  set used($fixnum.Int64 value) => $_setInt64(8, value);
  @$pb.TagNumber(9)
  $core.bool hasUsed() => $_has(8);
  @$pb.TagNumber(9)
  void clearUsed() => $_clearField(9);

  @$pb.TagNumber(10)
  $fixnum.Int64 get total => $_getI64(9);
  @$pb.TagNumber(10)
  set total($fixnum.Int64 value) => $_setInt64(9, value);
  @$pb.TagNumber(10)
  $core.bool hasTotal() => $_has(9);
  @$pb.TagNumber(10)
  void clearTotal() => $_clearField(10);

  @$pb.TagNumber(11)
  $fixnum.Int64 get expire => $_getI64(10);
  @$pb.TagNumber(11)
  set expire($fixnum.Int64 value) => $_setInt64(10, value);
  @$pb.TagNumber(11)
  $core.bool hasExpire() => $_has(10);
  @$pb.TagNumber(11)
  void clearExpire() => $_clearField(11);

  @$pb.TagNumber(12)
  $core.String get announce => $_getSZ(11);
  @$pb.TagNumber(12)
  set announce($core.String value) => $_setString(11, value);
  @$pb.TagNumber(12)
  $core.bool hasAnnounce() => $_has(11);
  @$pb.TagNumber(12)
  void clearAnnounce() => $_clearField(12);

  /// an update of it runs now
  @$pb.TagNumber(13)
  $core.bool get updating => $_getBF(12);
  @$pb.TagNumber(13)
  set updating($core.bool value) => $_setBool(12, value);
  @$pb.TagNumber(13)
  $core.bool hasUpdating() => $_has(12);
  @$pb.TagNumber(13)
  void clearUpdating() => $_clearField(13);
}

class RefreshRequest extends $pb.GeneratedMessage {
  factory RefreshRequest({
    $fixnum.Int64? groupId,
  }) {
    final result = RefreshRequest._();
    if (groupId != null) result.groupId = groupId;
    return result;
  }

  RefreshRequest._();

  factory RefreshRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      RefreshRequest()..mergeFromBuffer(data, registry);
  factory RefreshRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      RefreshRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'RefreshRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: RefreshRequest.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'groupId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RefreshRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  RefreshRequest copyWith(void Function(RefreshRequest) updates) =>
      super.copyWith((message) => updates(message as RefreshRequest))
          as RefreshRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use RefreshRequest() / RefreshRequest.new instead')
  static RefreshRequest create() => RefreshRequest._();
  static $pb.GeneratedMessage $_createMessage() => RefreshRequest._();
  @$core.override
  RefreshRequest createEmptyInstance() => RefreshRequest._();
  @$core.pragma('dart2js:noInline')
  static RefreshRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<RefreshRequest>(
          RefreshRequest.$_createMessage);
  static RefreshRequest? _defaultInstance;

  /// the subscription's group; 0: every one
  @$pb.TagNumber(1)
  $fixnum.Int64 get groupId => $_getI64(0);
  @$pb.TagNumber(1)
  set groupId($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasGroupId() => $_has(0);
  @$pb.TagNumber(1)
  void clearGroupId() => $_clearField(1);
}

class GroupId extends $pb.GeneratedMessage {
  factory GroupId({
    $fixnum.Int64? id,
  }) {
    final result = GroupId._();
    if (id != null) result.id = id;
    return result;
  }

  GroupId._();

  factory GroupId.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GroupId()..mergeFromBuffer(data, registry);
  factory GroupId.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GroupId()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GroupId',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: GroupId.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'id')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GroupId clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GroupId copyWith(void Function(GroupId) updates) =>
      super.copyWith((message) => updates(message as GroupId)) as GroupId;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use GroupId() / GroupId.new instead')
  static GroupId create() => GroupId._();
  static $pb.GeneratedMessage $_createMessage() => GroupId._();
  @$core.override
  GroupId createEmptyInstance() => GroupId._();
  @$core.pragma('dart2js:noInline')
  static GroupId getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<GroupId>(GroupId.$_createMessage);
  static GroupId? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get id => $_getI64(0);
  @$pb.TagNumber(1)
  set id($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasId() => $_has(0);
  @$pb.TagNumber(1)
  void clearId() => $_clearField(1);
}

class GroupList extends $pb.GeneratedMessage {
  factory GroupList({
    $core.Iterable<Group>? groups,
  }) {
    final result = GroupList._();
    if (groups != null) result.groups.addAll(groups);
    return result;
  }

  GroupList._();

  factory GroupList.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GroupList()..mergeFromBuffer(data, registry);
  factory GroupList.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      GroupList()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'GroupList',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: GroupList.$_createMessage)
    ..pPM<Group>(1, _omitFieldNames ? '' : 'groups',
        subBuilder: Group.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GroupList clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  GroupList copyWith(void Function(GroupList) updates) =>
      super.copyWith((message) => updates(message as GroupList)) as GroupList;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use GroupList() / GroupList.new instead')
  static GroupList create() => GroupList._();
  static $pb.GeneratedMessage $_createMessage() => GroupList._();
  @$core.override
  GroupList createEmptyInstance() => GroupList._();
  @$core.pragma('dart2js:noInline')
  static GroupList getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<GroupList>(GroupList.$_createMessage);
  static GroupList? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<Group> get groups => $_getList(0);
}

class ListProfilesRequest extends $pb.GeneratedMessage {
  factory ListProfilesRequest({
    $fixnum.Int64? groupId,
  }) {
    final result = ListProfilesRequest._();
    if (groupId != null) result.groupId = groupId;
    return result;
  }

  ListProfilesRequest._();

  factory ListProfilesRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ListProfilesRequest()..mergeFromBuffer(data, registry);
  factory ListProfilesRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ListProfilesRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ListProfilesRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ListProfilesRequest.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'groupId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ListProfilesRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ListProfilesRequest copyWith(void Function(ListProfilesRequest) updates) =>
      super.copyWith((message) => updates(message as ListProfilesRequest))
          as ListProfilesRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core
      .Deprecated('Use ListProfilesRequest() / ListProfilesRequest.new instead')
  static ListProfilesRequest create() => ListProfilesRequest._();
  static $pb.GeneratedMessage $_createMessage() => ListProfilesRequest._();
  @$core.override
  ListProfilesRequest createEmptyInstance() => ListProfilesRequest._();
  @$core.pragma('dart2js:noInline')
  static ListProfilesRequest getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ListProfilesRequest>(
          ListProfilesRequest.$_createMessage);
  static ListProfilesRequest? _defaultInstance;

  /// 0: every group's
  @$pb.TagNumber(1)
  $fixnum.Int64 get groupId => $_getI64(0);
  @$pb.TagNumber(1)
  set groupId($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasGroupId() => $_has(0);
  @$pb.TagNumber(1)
  void clearGroupId() => $_clearField(1);
}

class Profile extends $pb.GeneratedMessage {
  factory Profile({
    $fixnum.Int64? id,
    $fixnum.Int64? groupId,
    $fixnum.Int64? order,
    $core.String? type,
    $core.String? name,
    $core.String? address,
    $core.String? beanJson,
  }) {
    final result = Profile._();
    if (id != null) result.id = id;
    if (groupId != null) result.groupId = groupId;
    if (order != null) result.order = order;
    if (type != null) result.type = type;
    if (name != null) result.name = name;
    if (address != null) result.address = address;
    if (beanJson != null) result.beanJson = beanJson;
    return result;
  }

  Profile._();

  factory Profile.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Profile()..mergeFromBuffer(data, registry);
  factory Profile.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Profile()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Profile',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: Profile.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'id')
    ..aInt64(2, _omitFieldNames ? '' : 'groupId')
    ..aInt64(3, _omitFieldNames ? '' : 'order')
    ..aOS(4, _omitFieldNames ? '' : 'type')
    ..aOS(5, _omitFieldNames ? '' : 'name')
    ..aOS(6, _omitFieldNames ? '' : 'address')
    ..aOS(7, _omitFieldNames ? '' : 'beanJson')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Profile clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Profile copyWith(void Function(Profile) updates) =>
      super.copyWith((message) => updates(message as Profile)) as Profile;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Profile() / Profile.new instead')
  static Profile create() => Profile._();
  static $pb.GeneratedMessage $_createMessage() => Profile._();
  @$core.override
  Profile createEmptyInstance() => Profile._();
  @$core.pragma('dart2js:noInline')
  static Profile getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Profile>(Profile.$_createMessage);
  static Profile? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get id => $_getI64(0);
  @$pb.TagNumber(1)
  set id($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasId() => $_has(0);
  @$pb.TagNumber(1)
  void clearId() => $_clearField(1);

  @$pb.TagNumber(2)
  $fixnum.Int64 get groupId => $_getI64(1);
  @$pb.TagNumber(2)
  set groupId($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasGroupId() => $_has(1);
  @$pb.TagNumber(2)
  void clearGroupId() => $_clearField(2);

  @$pb.TagNumber(3)
  $fixnum.Int64 get order => $_getI64(2);
  @$pb.TagNumber(3)
  set order($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasOrder() => $_has(2);
  @$pb.TagNumber(3)
  void clearOrder() => $_clearField(3);

  /// the bean's type, as the app's TypeMap names it: naive, hysteria, vmess (VLESS too), socks...
  @$pb.TagNumber(4)
  $core.String get type => $_getSZ(3);
  @$pb.TagNumber(4)
  set type($core.String value) => $_setString(3, value);
  @$pb.TagNumber(4)
  $core.bool hasType() => $_has(3);
  @$pb.TagNumber(4)
  void clearType() => $_clearField(4);

  /// the bean's display name and address; not saved, the bean's own are
  @$pb.TagNumber(5)
  $core.String get name => $_getSZ(4);
  @$pb.TagNumber(5)
  set name($core.String value) => $_setString(4, value);
  @$pb.TagNumber(5)
  $core.bool hasName() => $_has(4);
  @$pb.TagNumber(5)
  void clearName() => $_clearField(5);

  @$pb.TagNumber(6)
  $core.String get address => $_getSZ(5);
  @$pb.TagNumber(6)
  set address($core.String value) => $_setString(5, value);
  @$pb.TagNumber(6)
  $core.bool hasAddress() => $_has(5);
  @$pb.TagNumber(6)
  void clearAddress() => $_clearField(6);

  /// the whole bean: JSON with the app's field names, as the golden fixtures have it; empty in a list's rows
  @$pb.TagNumber(7)
  $core.String get beanJson => $_getSZ(6);
  @$pb.TagNumber(7)
  set beanJson($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasBeanJson() => $_has(6);
  @$pb.TagNumber(7)
  void clearBeanJson() => $_clearField(7);
}

class ProfileList extends $pb.GeneratedMessage {
  factory ProfileList({
    $core.Iterable<Profile>? profiles,
  }) {
    final result = ProfileList._();
    if (profiles != null) result.profiles.addAll(profiles);
    return result;
  }

  ProfileList._();

  factory ProfileList.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileList()..mergeFromBuffer(data, registry);
  factory ProfileList.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileList()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ProfileList',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ProfileList.$_createMessage)
    ..pPM<Profile>(1, _omitFieldNames ? '' : 'profiles',
        subBuilder: Profile.$_createMessage)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileList clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileList copyWith(void Function(ProfileList) updates) =>
      super.copyWith((message) => updates(message as ProfileList))
          as ProfileList;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ProfileList() / ProfileList.new instead')
  static ProfileList create() => ProfileList._();
  static $pb.GeneratedMessage $_createMessage() => ProfileList._();
  @$core.override
  ProfileList createEmptyInstance() => ProfileList._();
  @$core.pragma('dart2js:noInline')
  static ProfileList getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ProfileList>(
          ProfileList.$_createMessage);
  static ProfileList? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<Profile> get profiles => $_getList(0);
}

class ProfileId extends $pb.GeneratedMessage {
  factory ProfileId({
    $fixnum.Int64? id,
  }) {
    final result = ProfileId._();
    if (id != null) result.id = id;
    return result;
  }

  ProfileId._();

  factory ProfileId.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileId()..mergeFromBuffer(data, registry);
  factory ProfileId.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileId()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ProfileId',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ProfileId.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'id')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileId clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileId copyWith(void Function(ProfileId) updates) =>
      super.copyWith((message) => updates(message as ProfileId)) as ProfileId;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ProfileId() / ProfileId.new instead')
  static ProfileId create() => ProfileId._();
  static $pb.GeneratedMessage $_createMessage() => ProfileId._();
  @$core.override
  ProfileId createEmptyInstance() => ProfileId._();
  @$core.pragma('dart2js:noInline')
  static ProfileId getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ProfileId>(ProfileId.$_createMessage);
  static ProfileId? _defaultInstance;

  @$pb.TagNumber(1)
  $fixnum.Int64 get id => $_getI64(0);
  @$pb.TagNumber(1)
  set id($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasId() => $_has(0);
  @$pb.TagNumber(1)
  void clearId() => $_clearField(1);
}

class ShareLink extends $pb.GeneratedMessage {
  factory ShareLink({
    $core.String? link,
  }) {
    final result = ShareLink._();
    if (link != null) result.link = link;
    return result;
  }

  ShareLink._();

  factory ShareLink.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ShareLink()..mergeFromBuffer(data, registry);
  factory ShareLink.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ShareLink()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ShareLink',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ShareLink.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'link')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ShareLink clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ShareLink copyWith(void Function(ShareLink) updates) =>
      super.copyWith((message) => updates(message as ShareLink)) as ShareLink;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ShareLink() / ShareLink.new instead')
  static ShareLink create() => ShareLink._();
  static $pb.GeneratedMessage $_createMessage() => ShareLink._();
  @$core.override
  ShareLink createEmptyInstance() => ShareLink._();
  @$core.pragma('dart2js:noInline')
  static ShareLink getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ShareLink>(ShareLink.$_createMessage);
  static ShareLink? _defaultInstance;

  @$pb.TagNumber(1)
  $core.String get link => $_getSZ(0);
  @$pb.TagNumber(1)
  set link($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasLink() => $_has(0);
  @$pb.TagNumber(1)
  void clearLink() => $_clearField(1);
}

class ConnectRequest extends $pb.GeneratedMessage {
  factory ConnectRequest({
    $fixnum.Int64? profileId,
    ConnectRequest_Mode? mode,
  }) {
    final result = ConnectRequest._();
    if (profileId != null) result.profileId = profileId;
    if (mode != null) result.mode = mode;
    return result;
  }

  ConnectRequest._();

  factory ConnectRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ConnectRequest()..mergeFromBuffer(data, registry);
  factory ConnectRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ConnectRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ConnectRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ConnectRequest.$_createMessage)
    ..aInt64(1, _omitFieldNames ? '' : 'profileId')
    ..aE<ConnectRequest_Mode>(2, _omitFieldNames ? '' : 'mode',
        enumValues: ConnectRequest_Mode.values)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ConnectRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ConnectRequest copyWith(void Function(ConnectRequest) updates) =>
      super.copyWith((message) => updates(message as ConnectRequest))
          as ConnectRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ConnectRequest() / ConnectRequest.new instead')
  static ConnectRequest create() => ConnectRequest._();
  static $pb.GeneratedMessage $_createMessage() => ConnectRequest._();
  @$core.override
  ConnectRequest createEmptyInstance() => ConnectRequest._();
  @$core.pragma('dart2js:noInline')
  static ConnectRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ConnectRequest>(
          ConnectRequest.$_createMessage);
  static ConnectRequest? _defaultInstance;

  /// the profile; 0: the selected one
  @$pb.TagNumber(1)
  $fixnum.Int64 get profileId => $_getI64(0);
  @$pb.TagNumber(1)
  set profileId($fixnum.Int64 value) => $_setInt64(0, value);
  @$pb.TagNumber(1)
  $core.bool hasProfileId() => $_has(0);
  @$pb.TagNumber(1)
  void clearProfileId() => $_clearField(1);

  /// none: the settings' (service_mode)
  @$pb.TagNumber(2)
  ConnectRequest_Mode get mode => $_getN(1);
  @$pb.TagNumber(2)
  set mode(ConnectRequest_Mode value) => $_setField(2, value);
  @$pb.TagNumber(2)
  $core.bool hasMode() => $_has(1);
  @$pb.TagNumber(2)
  void clearMode() => $_clearField(2);
}

/// A computer's settings: those its config's build takes (nikoconf.Settings, the app's ConfigSettings), with the app's
/// values; as JSON their names are the app's (mixedPort). The window's own (its view) are the window's.
class Settings extends $pb.GeneratedMessage {
  factory Settings({
    $core.String? serviceMode,
    $core.bool? systemProxy,
    $core.int? tunImplementation,
    $core.int? mtu,
    $core.int? ipv6Mode,
    $core.bool? globalAllowInsecure,
    $core.String? remoteDns,
    $core.String? directDns,
    $core.bool? enableFakeDns,
    $core.String? domainStrategyForRemote,
    $core.String? domainStrategyForDirect,
    $core.String? domainStrategyForServer,
    $core.bool? dnsOptimisticCache,
    $core.bool? bypassLanInCore,
    $core.int? mixedPort,
    $core.String? mixedUsername,
    $core.String? mixedPassword,
    $core.bool? allowAccess,
    $core.int? trafficSniffing,
    $core.bool? resolveDestination,
    $core.bool? refuseQuicOverStreams,
    $core.bool? icmpNoLocalReply,
    $core.int? logLevel,
    $core.int? programsMode,
    $core.String? programs,
  }) {
    final result = Settings._();
    if (serviceMode != null) result.serviceMode = serviceMode;
    if (systemProxy != null) result.systemProxy = systemProxy;
    if (tunImplementation != null) result.tunImplementation = tunImplementation;
    if (mtu != null) result.mtu = mtu;
    if (ipv6Mode != null) result.ipv6Mode = ipv6Mode;
    if (globalAllowInsecure != null)
      result.globalAllowInsecure = globalAllowInsecure;
    if (remoteDns != null) result.remoteDns = remoteDns;
    if (directDns != null) result.directDns = directDns;
    if (enableFakeDns != null) result.enableFakeDns = enableFakeDns;
    if (domainStrategyForRemote != null)
      result.domainStrategyForRemote = domainStrategyForRemote;
    if (domainStrategyForDirect != null)
      result.domainStrategyForDirect = domainStrategyForDirect;
    if (domainStrategyForServer != null)
      result.domainStrategyForServer = domainStrategyForServer;
    if (dnsOptimisticCache != null)
      result.dnsOptimisticCache = dnsOptimisticCache;
    if (bypassLanInCore != null) result.bypassLanInCore = bypassLanInCore;
    if (mixedPort != null) result.mixedPort = mixedPort;
    if (mixedUsername != null) result.mixedUsername = mixedUsername;
    if (mixedPassword != null) result.mixedPassword = mixedPassword;
    if (allowAccess != null) result.allowAccess = allowAccess;
    if (trafficSniffing != null) result.trafficSniffing = trafficSniffing;
    if (resolveDestination != null)
      result.resolveDestination = resolveDestination;
    if (refuseQuicOverStreams != null)
      result.refuseQuicOverStreams = refuseQuicOverStreams;
    if (icmpNoLocalReply != null) result.icmpNoLocalReply = icmpNoLocalReply;
    if (logLevel != null) result.logLevel = logLevel;
    if (programsMode != null) result.programsMode = programsMode;
    if (programs != null) result.programs = programs;
    return result;
  }

  Settings._();

  factory Settings.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Settings()..mergeFromBuffer(data, registry);
  factory Settings.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      Settings()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'Settings',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: Settings.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'serviceMode')
    ..aOB(2, _omitFieldNames ? '' : 'systemProxy')
    ..aI(3, _omitFieldNames ? '' : 'tunImplementation')
    ..aI(4, _omitFieldNames ? '' : 'mtu')
    ..aI(5, _omitFieldNames ? '' : 'ipv6Mode')
    ..aOB(6, _omitFieldNames ? '' : 'globalAllowInsecure')
    ..aOS(7, _omitFieldNames ? '' : 'remoteDns')
    ..aOS(8, _omitFieldNames ? '' : 'directDns')
    ..aOB(9, _omitFieldNames ? '' : 'enableFakeDns')
    ..aOS(10, _omitFieldNames ? '' : 'domainStrategyForRemote')
    ..aOS(11, _omitFieldNames ? '' : 'domainStrategyForDirect')
    ..aOS(12, _omitFieldNames ? '' : 'domainStrategyForServer')
    ..aOB(13, _omitFieldNames ? '' : 'dnsOptimisticCache')
    ..aOB(14, _omitFieldNames ? '' : 'bypassLanInCore')
    ..aI(15, _omitFieldNames ? '' : 'mixedPort')
    ..aOS(16, _omitFieldNames ? '' : 'mixedUsername')
    ..aOS(17, _omitFieldNames ? '' : 'mixedPassword')
    ..aOB(18, _omitFieldNames ? '' : 'allowAccess')
    ..aI(19, _omitFieldNames ? '' : 'trafficSniffing')
    ..aOB(20, _omitFieldNames ? '' : 'resolveDestination')
    ..aOB(21, _omitFieldNames ? '' : 'refuseQuicOverStreams')
    ..aOB(22, _omitFieldNames ? '' : 'icmpNoLocalReply')
    ..aI(23, _omitFieldNames ? '' : 'logLevel')
    ..aI(24, _omitFieldNames ? '' : 'programsMode')
    ..aOS(25, _omitFieldNames ? '' : 'programs')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Settings clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  Settings copyWith(void Function(Settings) updates) =>
      super.copyWith((message) => updates(message as Settings)) as Settings;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use Settings() / Settings.new instead')
  static Settings create() => Settings._();
  static $pb.GeneratedMessage $_createMessage() => Settings._();
  @$core.override
  Settings createEmptyInstance() => Settings._();
  @$core.pragma('dart2js:noInline')
  static Settings getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<Settings>(Settings.$_createMessage);
  static Settings? _defaultInstance;

  /// proxy or vpn: how the computer's traffic reaches the core (D-NAV-02) where Connect asks no mode; proxy by default
  @$pb.TagNumber(1)
  $core.String get serviceMode => $_getSZ(0);
  @$pb.TagNumber(1)
  set serviceMode($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasServiceMode() => $_has(0);
  @$pb.TagNumber(1)
  void clearServiceMode() => $_clearField(1);

  /// in proxy mode the local proxy is the system's while the core runs: the daemon sets it for the user who connected,
  /// and ManagedService.SetSystemProxyEnabled turns it off and on; on by default on Windows
  @$pb.TagNumber(2)
  $core.bool get systemProxy => $_getBF(1);
  @$pb.TagNumber(2)
  set systemProxy($core.bool value) => $_setBool(1, value);
  @$pb.TagNumber(2)
  $core.bool hasSystemProxy() => $_has(1);
  @$pb.TagNumber(2)
  void clearSystemProxy() => $_clearField(2);

  /// the TUN's stack: 0 gVisor, 1 system, 2 mixed
  @$pb.TagNumber(3)
  $core.int get tunImplementation => $_getIZ(2);
  @$pb.TagNumber(3)
  set tunImplementation($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasTunImplementation() => $_has(2);
  @$pb.TagNumber(3)
  void clearTunImplementation() => $_clearField(3);

  /// the TUN's, from 1000 to 10000
  @$pb.TagNumber(4)
  $core.int get mtu => $_getIZ(3);
  @$pb.TagNumber(4)
  set mtu($core.int value) => $_setSignedInt32(3, value);
  @$pb.TagNumber(4)
  $core.bool hasMtu() => $_has(3);
  @$pb.TagNumber(4)
  void clearMtu() => $_clearField(4);

  /// 0 off, 1 on, 2 first, 3 only
  @$pb.TagNumber(5)
  $core.int get ipv6Mode => $_getIZ(4);
  @$pb.TagNumber(5)
  set ipv6Mode($core.int value) => $_setSignedInt32(4, value);
  @$pb.TagNumber(5)
  $core.bool hasIpv6Mode() => $_has(4);
  @$pb.TagNumber(5)
  void clearIpv6Mode() => $_clearField(5);

  /// no profile checks its server's certificate
  @$pb.TagNumber(6)
  $core.bool get globalAllowInsecure => $_getBF(5);
  @$pb.TagNumber(6)
  set globalAllowInsecure($core.bool value) => $_setBool(5, value);
  @$pb.TagNumber(6)
  $core.bool hasGlobalAllowInsecure() => $_has(5);
  @$pb.TagNumber(6)
  void clearGlobalAllowInsecure() => $_clearField(6);

  /// DNS servers, a line each, "#" a comment, as the app reads them: the build takes the first (D-SET-18). "local"
  /// is the system's
  @$pb.TagNumber(7)
  $core.String get remoteDns => $_getSZ(6);
  @$pb.TagNumber(7)
  set remoteDns($core.String value) => $_setString(6, value);
  @$pb.TagNumber(7)
  $core.bool hasRemoteDns() => $_has(6);
  @$pb.TagNumber(7)
  void clearRemoteDns() => $_clearField(7);

  @$pb.TagNumber(8)
  $core.String get directDns => $_getSZ(7);
  @$pb.TagNumber(8)
  set directDns($core.String value) => $_setString(7, value);
  @$pb.TagNumber(8)
  $core.bool hasDirectDns() => $_has(7);
  @$pb.TagNumber(8)
  void clearDirectDns() => $_clearField(8);

  /// fake addresses for the TUN's queries
  @$pb.TagNumber(9)
  $core.bool get enableFakeDns => $_getBF(8);
  @$pb.TagNumber(9)
  set enableFakeDns($core.bool value) => $_setBool(8, value);
  @$pb.TagNumber(9)
  $core.bool hasEnableFakeDns() => $_has(8);
  @$pb.TagNumber(9)
  void clearEnableFakeDns() => $_clearField(9);

  /// "" for the core's own, else auto, prefer_ipv4, prefer_ipv6, ipv4_only or ipv6_only
  @$pb.TagNumber(10)
  $core.String get domainStrategyForRemote => $_getSZ(9);
  @$pb.TagNumber(10)
  set domainStrategyForRemote($core.String value) => $_setString(9, value);
  @$pb.TagNumber(10)
  $core.bool hasDomainStrategyForRemote() => $_has(9);
  @$pb.TagNumber(10)
  void clearDomainStrategyForRemote() => $_clearField(10);

  @$pb.TagNumber(11)
  $core.String get domainStrategyForDirect => $_getSZ(10);
  @$pb.TagNumber(11)
  set domainStrategyForDirect($core.String value) => $_setString(10, value);
  @$pb.TagNumber(11)
  $core.bool hasDomainStrategyForDirect() => $_has(10);
  @$pb.TagNumber(11)
  void clearDomainStrategyForDirect() => $_clearField(11);

  @$pb.TagNumber(12)
  $core.String get domainStrategyForServer => $_getSZ(11);
  @$pb.TagNumber(12)
  set domainStrategyForServer($core.String value) => $_setString(11, value);
  @$pb.TagNumber(12)
  $core.bool hasDomainStrategyForServer() => $_has(11);
  @$pb.TagNumber(12)
  void clearDomainStrategyForServer() => $_clearField(12);

  /// an expired cache entry answered at once and refreshed behind
  @$pb.TagNumber(13)
  $core.bool get dnsOptimisticCache => $_getBF(12);
  @$pb.TagNumber(13)
  set dnsOptimisticCache($core.bool value) => $_setBool(12, value);
  @$pb.TagNumber(13)
  $core.bool hasDnsOptimisticCache() => $_has(12);
  @$pb.TagNumber(13)
  void clearDnsOptimisticCache() => $_clearField(13);

  /// private addresses go direct (D-SET-16)
  @$pb.TagNumber(14)
  $core.bool get bypassLanInCore => $_getBF(13);
  @$pb.TagNumber(14)
  set bypassLanInCore($core.bool value) => $_setBool(13, value);
  @$pb.TagNumber(14)
  $core.bool hasBypassLanInCore() => $_has(13);
  @$pb.TagNumber(14)
  void clearBypassLanInCore() => $_clearField(14);

  /// the local proxy: its port, from 1 to 65535
  @$pb.TagNumber(15)
  $core.int get mixedPort => $_getIZ(14);
  @$pb.TagNumber(15)
  set mixedPort($core.int value) => $_setSignedInt32(14, value);
  @$pb.TagNumber(15)
  $core.bool hasMixedPort() => $_has(14);
  @$pb.TagNumber(15)
  void clearMixedPort() => $_clearField(15);

  /// its login, none where the password is empty: the user's name (no colon; empty: "user") and password, printable
  /// characters, 255 bytes at most
  @$pb.TagNumber(16)
  $core.String get mixedUsername => $_getSZ(15);
  @$pb.TagNumber(16)
  set mixedUsername($core.String value) => $_setString(15, value);
  @$pb.TagNumber(16)
  $core.bool hasMixedUsername() => $_has(15);
  @$pb.TagNumber(16)
  void clearMixedUsername() => $_clearField(16);

  @$pb.TagNumber(17)
  $core.String get mixedPassword => $_getSZ(16);
  @$pb.TagNumber(17)
  set mixedPassword($core.String value) => $_setString(16, value);
  @$pb.TagNumber(17)
  $core.bool hasMixedPassword() => $_has(16);
  @$pb.TagNumber(17)
  void clearMixedPassword() => $_clearField(17);

  /// it listens on every address, not 127.0.0.1 alone: other devices of the LAN may use it
  @$pb.TagNumber(18)
  $core.bool get allowAccess => $_getBF(17);
  @$pb.TagNumber(18)
  set allowAccess($core.bool value) => $_setBool(17, value);
  @$pb.TagNumber(18)
  $core.bool hasAllowAccess() => $_has(17);
  @$pb.TagNumber(18)
  void clearAllowAccess() => $_clearField(18);

  /// 0 off, 1 on
  @$pb.TagNumber(19)
  $core.int get trafficSniffing => $_getIZ(18);
  @$pb.TagNumber(19)
  set trafficSniffing($core.int value) => $_setSignedInt32(18, value);
  @$pb.TagNumber(19)
  $core.bool hasTrafficSniffing() => $_has(18);
  @$pb.TagNumber(19)
  void clearTrafficSniffing() => $_clearField(19);

  /// domains resolved before routing
  @$pb.TagNumber(20)
  $core.bool get resolveDestination => $_getBF(19);
  @$pb.TagNumber(20)
  set resolveDestination($core.bool value) => $_setBool(19, value);
  @$pb.TagNumber(20)
  $core.bool hasResolveDestination() => $_has(19);
  @$pb.TagNumber(20)
  void clearResolveDestination() => $_clearField(20);

  /// QUIC refused on a profile that carries UDP inside a stream, so browsers go to TCP at once
  @$pb.TagNumber(21)
  $core.bool get refuseQuicOverStreams => $_getBF(20);
  @$pb.TagNumber(21)
  set refuseQuicOverStreams($core.bool value) => $_setBool(20, value);
  @$pb.TagNumber(21)
  $core.bool hasRefuseQuicOverStreams() => $_has(20);
  @$pb.TagNumber(21)
  void clearRefuseQuicOverStreams() => $_clearField(21);

  /// an echo no outbound carries refused, not answered by the TUN
  @$pb.TagNumber(22)
  $core.bool get icmpNoLocalReply => $_getBF(21);
  @$pb.TagNumber(22)
  set icmpNoLocalReply($core.bool value) => $_setBool(21, value);
  @$pb.TagNumber(22)
  $core.bool hasIcmpNoLocalReply() => $_has(21);
  @$pb.TagNumber(22)
  void clearIcmpNoLocalReply() => $_clearField(22);

  /// the core's log: 0 none (panic alone), 1 warn, 2 info, 3 debug, 4 trace
  @$pb.TagNumber(23)
  $core.int get logLevel => $_getIZ(22);
  @$pb.TagNumber(23)
  set logLevel($core.int value) => $_setSignedInt32(22, value);
  @$pb.TagNumber(23)
  $core.bool hasLogLevel() => $_has(22);
  @$pb.TagNumber(23)
  void clearLogLevel() => $_clearField(23);

  /// which programs go through Niko (D-RULES-06), as the phone's per-app proxy: 0 all, 1 only those of programs (the
  /// rest direct), 2 all but those; a rule by program ahead of the rules
  @$pb.TagNumber(24)
  $core.int get programsMode => $_getIZ(23);
  @$pb.TagNumber(24)
  set programsMode($core.int value) => $_setSignedInt32(23, value);
  @$pb.TagNumber(24)
  $core.bool hasProgramsMode() => $_has(23);
  @$pb.TagNumber(24)
  void clearProgramsMode() => $_clearField(24);

  /// the programs, a line each: a name ("firefox.exe") or a full path, as the system names the process; empty lines
  /// are none
  @$pb.TagNumber(25)
  $core.String get programs => $_getSZ(24);
  @$pb.TagNumber(25)
  set programs($core.String value) => $_setString(24, value);
  @$pb.TagNumber(25)
  $core.bool hasPrograms() => $_has(24);
  @$pb.TagNumber(25)
  void clearPrograms() => $_clearField(25);
}

class SettingsUpdate extends $pb.GeneratedMessage {
  factory SettingsUpdate({
    Settings? settings,
    $core.Iterable<$core.String>? fields,
  }) {
    final result = SettingsUpdate._();
    if (settings != null) result.settings = settings;
    if (fields != null) result.fields.addAll(fields);
    return result;
  }

  SettingsUpdate._();

  factory SettingsUpdate.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SettingsUpdate()..mergeFromBuffer(data, registry);
  factory SettingsUpdate.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      SettingsUpdate()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'SettingsUpdate',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: SettingsUpdate.$_createMessage)
    ..aOM<Settings>(1, _omitFieldNames ? '' : 'settings',
        subBuilder: Settings.$_createMessage)
    ..pPS(2, _omitFieldNames ? '' : 'fields')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SettingsUpdate clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  SettingsUpdate copyWith(void Function(SettingsUpdate) updates) =>
      super.copyWith((message) => updates(message as SettingsUpdate))
          as SettingsUpdate;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use SettingsUpdate() / SettingsUpdate.new instead')
  static SettingsUpdate create() => SettingsUpdate._();
  static $pb.GeneratedMessage $_createMessage() => SettingsUpdate._();
  @$core.override
  SettingsUpdate createEmptyInstance() => SettingsUpdate._();
  @$core.pragma('dart2js:noInline')
  static SettingsUpdate getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<SettingsUpdate>(
          SettingsUpdate.$_createMessage);
  static SettingsUpdate? _defaultInstance;

  @$pb.TagNumber(1)
  Settings get settings => $_getN(0);
  @$pb.TagNumber(1)
  set settings(Settings value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasSettings() => $_has(0);
  @$pb.TagNumber(1)
  void clearSettings() => $_clearField(1);
  @$pb.TagNumber(1)
  Settings ensureSettings() => $_ensure(0);

  /// the names of the fields that change, as this file has them (mixed_port); the others' values are not read
  @$pb.TagNumber(2)
  $pb.PbList<$core.String> get fields => $_getList(1);
}

class ProfileIds extends $pb.GeneratedMessage {
  factory ProfileIds({
    $core.Iterable<$fixnum.Int64>? ids,
  }) {
    final result = ProfileIds._();
    if (ids != null) result.ids.addAll(ids);
    return result;
  }

  ProfileIds._();

  factory ProfileIds.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileIds()..mergeFromBuffer(data, registry);
  factory ProfileIds.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ProfileIds()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ProfileIds',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ProfileIds.$_createMessage)
    ..p<$fixnum.Int64>(1, _omitFieldNames ? '' : 'ids', $pb.PbFieldType.K6)
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileIds clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ProfileIds copyWith(void Function(ProfileIds) updates) =>
      super.copyWith((message) => updates(message as ProfileIds)) as ProfileIds;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ProfileIds() / ProfileIds.new instead')
  static ProfileIds create() => ProfileIds._();
  static $pb.GeneratedMessage $_createMessage() => ProfileIds._();
  @$core.override
  ProfileIds createEmptyInstance() => ProfileIds._();
  @$core.pragma('dart2js:noInline')
  static ProfileIds getDefault() => _defaultInstance ??=
      $pb.GeneratedMessage.$_defaultFor<ProfileIds>(ProfileIds.$_createMessage);
  static ProfileIds? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<$fixnum.Int64> get ids => $_getList(0);
}

class MoveRequest extends $pb.GeneratedMessage {
  factory MoveRequest({
    $core.Iterable<$fixnum.Int64>? ids,
    $fixnum.Int64? groupId,
    $fixnum.Int64? before,
  }) {
    final result = MoveRequest._();
    if (ids != null) result.ids.addAll(ids);
    if (groupId != null) result.groupId = groupId;
    if (before != null) result.before = before;
    return result;
  }

  MoveRequest._();

  factory MoveRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      MoveRequest()..mergeFromBuffer(data, registry);
  factory MoveRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      MoveRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'MoveRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: MoveRequest.$_createMessage)
    ..p<$fixnum.Int64>(1, _omitFieldNames ? '' : 'ids', $pb.PbFieldType.K6)
    ..aInt64(2, _omitFieldNames ? '' : 'groupId')
    ..aInt64(3, _omitFieldNames ? '' : 'before')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  MoveRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  MoveRequest copyWith(void Function(MoveRequest) updates) =>
      super.copyWith((message) => updates(message as MoveRequest))
          as MoveRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use MoveRequest() / MoveRequest.new instead')
  static MoveRequest create() => MoveRequest._();
  static $pb.GeneratedMessage $_createMessage() => MoveRequest._();
  @$core.override
  MoveRequest createEmptyInstance() => MoveRequest._();
  @$core.pragma('dart2js:noInline')
  static MoveRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<MoveRequest>(
          MoveRequest.$_createMessage);
  static MoveRequest? _defaultInstance;

  @$pb.TagNumber(1)
  $pb.PbList<$fixnum.Int64> get ids => $_getList(0);

  /// the group they go into
  @$pb.TagNumber(2)
  $fixnum.Int64 get groupId => $_getI64(1);
  @$pb.TagNumber(2)
  set groupId($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasGroupId() => $_has(1);
  @$pb.TagNumber(2)
  void clearGroupId() => $_clearField(2);

  /// the profile of that group they go before; 0: at its end
  @$pb.TagNumber(3)
  $fixnum.Int64 get before => $_getI64(2);
  @$pb.TagNumber(3)
  set before($fixnum.Int64 value) => $_setInt64(2, value);
  @$pb.TagNumber(3)
  $core.bool hasBefore() => $_has(2);
  @$pb.TagNumber(3)
  void clearBefore() => $_clearField(3);
}

class ImportInput extends $pb.GeneratedMessage {
  factory ImportInput({
    $core.String? text,
    $core.List<$core.int>? file,
    $core.String? fileName,
  }) {
    final result = ImportInput._();
    if (text != null) result.text = text;
    if (file != null) result.file = file;
    if (fileName != null) result.fileName = fileName;
    return result;
  }

  ImportInput._();

  factory ImportInput.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ImportInput()..mergeFromBuffer(data, registry);
  factory ImportInput.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ImportInput()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ImportInput',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ImportInput.$_createMessage)
    ..aOS(1, _omitFieldNames ? '' : 'text')
    ..a<$core.List<$core.int>>(
        2, _omitFieldNames ? '' : 'file', $pb.PbFieldType.OY)
    ..aOS(3, _omitFieldNames ? '' : 'fileName')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ImportInput clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ImportInput copyWith(void Function(ImportInput) updates) =>
      super.copyWith((message) => updates(message as ImportInput))
          as ImportInput;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ImportInput() / ImportInput.new instead')
  static ImportInput create() => ImportInput._();
  static $pb.GeneratedMessage $_createMessage() => ImportInput._();
  @$core.override
  ImportInput createEmptyInstance() => ImportInput._();
  @$core.pragma('dart2js:noInline')
  static ImportInput getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ImportInput>(
          ImportInput.$_createMessage);
  static ImportInput? _defaultInstance;

  /// a paste, the clipboard, a QR code's text
  @$pb.TagNumber(1)
  $core.String get text => $_getSZ(0);
  @$pb.TagNumber(1)
  set text($core.String value) => $_setString(0, value);
  @$pb.TagNumber(1)
  $core.bool hasText() => $_has(0);
  @$pb.TagNumber(1)
  void clearText() => $_clearField(1);

  /// a file's content, which the window read (the daemon reads no path); taken as text where text is empty
  @$pb.TagNumber(2)
  $core.List<$core.int> get file => $_getN(1);
  @$pb.TagNumber(2)
  set file($core.List<$core.int> value) => $_setBytes(1, value);
  @$pb.TagNumber(2)
  $core.bool hasFile() => $_has(1);
  @$pb.TagNumber(2)
  void clearFile() => $_clearField(2);

  @$pb.TagNumber(3)
  $core.String get fileName => $_getSZ(2);
  @$pb.TagNumber(3)
  set fileName($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasFileName() => $_has(2);
  @$pb.TagNumber(3)
  void clearFileName() => $_clearField(3);
}

class ImportPreview extends $pb.GeneratedMessage {
  factory ImportPreview({
    ImportPreview_Kind? kind,
    $core.Iterable<Profile>? profiles,
    $core.String? subscription,
  }) {
    final result = ImportPreview._();
    if (kind != null) result.kind = kind;
    if (profiles != null) result.profiles.addAll(profiles);
    if (subscription != null) result.subscription = subscription;
    return result;
  }

  ImportPreview._();

  factory ImportPreview.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ImportPreview()..mergeFromBuffer(data, registry);
  factory ImportPreview.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ImportPreview()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ImportPreview',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ImportPreview.$_createMessage)
    ..aE<ImportPreview_Kind>(1, _omitFieldNames ? '' : 'kind',
        enumValues: ImportPreview_Kind.values)
    ..pPM<Profile>(2, _omitFieldNames ? '' : 'profiles',
        subBuilder: Profile.$_createMessage)
    ..aOS(3, _omitFieldNames ? '' : 'subscription')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ImportPreview clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ImportPreview copyWith(void Function(ImportPreview) updates) =>
      super.copyWith((message) => updates(message as ImportPreview))
          as ImportPreview;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ImportPreview() / ImportPreview.new instead')
  static ImportPreview create() => ImportPreview._();
  static $pb.GeneratedMessage $_createMessage() => ImportPreview._();
  @$core.override
  ImportPreview createEmptyInstance() => ImportPreview._();
  @$core.pragma('dart2js:noInline')
  static ImportPreview getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ImportPreview>(
          ImportPreview.$_createMessage);
  static ImportPreview? _defaultInstance;

  /// NODE: a raycontrol node's invite or device package, whose import comes with the nodes
  @$pb.TagNumber(1)
  ImportPreview_Kind get kind => $_getN(0);
  @$pb.TagNumber(1)
  set kind(ImportPreview_Kind value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasKind() => $_has(0);
  @$pb.TagNumber(1)
  void clearKind() => $_clearField(1);

  /// the profiles an import would add, as rows without ids
  @$pb.TagNumber(2)
  $pb.PbList<Profile> get profiles => $_getList(1);

  /// the subscription's link, with kind SUBSCRIPTION: its import makes a group of it
  @$pb.TagNumber(3)
  $core.String get subscription => $_getSZ(2);
  @$pb.TagNumber(3)
  set subscription($core.String value) => $_setString(2, value);
  @$pb.TagNumber(3)
  $core.bool hasSubscription() => $_has(2);
  @$pb.TagNumber(3)
  void clearSubscription() => $_clearField(3);
}

class ImportRequest extends $pb.GeneratedMessage {
  factory ImportRequest({
    ImportInput? input,
    $fixnum.Int64? groupId,
  }) {
    final result = ImportRequest._();
    if (input != null) result.input = input;
    if (groupId != null) result.groupId = groupId;
    return result;
  }

  ImportRequest._();

  factory ImportRequest.fromBuffer($core.List<$core.int> data,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ImportRequest()..mergeFromBuffer(data, registry);
  factory ImportRequest.fromJson($core.String json,
          [$pb.ExtensionRegistry registry = $pb.ExtensionRegistry.EMPTY]) =>
      ImportRequest()..mergeFromJson(json, registry);

  static final $pb.BuilderInfo _i = $pb.BuilderInfo(
      _omitMessageNames ? '' : 'ImportRequest',
      package: const $pb.PackageName(_omitMessageNames ? '' : 'niko.v1'),
      createEmptyInstance: ImportRequest.$_createMessage)
    ..aOM<ImportInput>(1, _omitFieldNames ? '' : 'input',
        subBuilder: ImportInput.$_createMessage)
    ..aInt64(2, _omitFieldNames ? '' : 'groupId')
    ..hasRequiredFields = false;

  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ImportRequest clone() => deepCopy();
  @$core.Deprecated('See https://github.com/google/protobuf.dart/issues/998.')
  ImportRequest copyWith(void Function(ImportRequest) updates) =>
      super.copyWith((message) => updates(message as ImportRequest))
          as ImportRequest;

  @$core.override
  $pb.BuilderInfo get info_ => _i;

  @$core.pragma('dart2js:noInline')
  @$core.Deprecated('Use ImportRequest() / ImportRequest.new instead')
  static ImportRequest create() => ImportRequest._();
  static $pb.GeneratedMessage $_createMessage() => ImportRequest._();
  @$core.override
  ImportRequest createEmptyInstance() => ImportRequest._();
  @$core.pragma('dart2js:noInline')
  static ImportRequest getDefault() =>
      _defaultInstance ??= $pb.GeneratedMessage.$_defaultFor<ImportRequest>(
          ImportRequest.$_createMessage);
  static ImportRequest? _defaultInstance;

  @$pb.TagNumber(1)
  ImportInput get input => $_getN(0);
  @$pb.TagNumber(1)
  set input(ImportInput value) => $_setField(1, value);
  @$pb.TagNumber(1)
  $core.bool hasInput() => $_has(0);
  @$pb.TagNumber(1)
  void clearInput() => $_clearField(1);
  @$pb.TagNumber(1)
  ImportInput ensureInput() => $_ensure(0);

  /// the group the profiles go into; 0: the ungrouped one
  @$pb.TagNumber(2)
  $fixnum.Int64 get groupId => $_getI64(1);
  @$pb.TagNumber(2)
  set groupId($fixnum.Int64 value) => $_setInt64(1, value);
  @$pb.TagNumber(2)
  $core.bool hasGroupId() => $_has(1);
  @$pb.TagNumber(2)
  void clearGroupId() => $_clearField(2);
}

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
