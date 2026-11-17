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

class DebugCrashRequest_Type extends $pb.ProtobufEnum {
  static const DebugCrashRequest_Type GO =
      DebugCrashRequest_Type._(0, _omitEnumNames ? '' : 'GO');
  static const DebugCrashRequest_Type NATIVE =
      DebugCrashRequest_Type._(1, _omitEnumNames ? '' : 'NATIVE');

  static const $core.List<DebugCrashRequest_Type> values =
      <DebugCrashRequest_Type>[
    GO,
    NATIVE,
  ];

  static final $core.List<DebugCrashRequest_Type?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 1);
  static DebugCrashRequest_Type? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const DebugCrashRequest_Type._(super.value, super.name);
}

const $core.bool _omitEnumNames =
    $core.bool.fromEnvironment('protobuf.omit_enum_names');
