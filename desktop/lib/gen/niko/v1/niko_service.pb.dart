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

import 'package:protobuf/protobuf.dart' as $pb;

export 'package:protobuf/protobuf.dart' show GeneratedMessageGenericExtensions;

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

  /// the schema of the daemon's store; 0: no store yet
  @$pb.TagNumber(3)
  $core.int get storeSchema => $_getIZ(2);
  @$pb.TagNumber(3)
  set storeSchema($core.int value) => $_setSignedInt32(2, value);
  @$pb.TagNumber(3)
  $core.bool hasStoreSchema() => $_has(2);
  @$pb.TagNumber(3)
  void clearStoreSchema() => $_clearField(3);

  /// the version of this API: what the window may ask
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

const $core.bool _omitFieldNames =
    $core.bool.fromEnvironment('protobuf.omit_field_names');
const $core.bool _omitMessageNames =
    $core.bool.fromEnvironment('protobuf.omit_message_names');
