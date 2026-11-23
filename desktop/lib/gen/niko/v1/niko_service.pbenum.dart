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

class Change_Kind extends $pb.ProtobufEnum {
  static const Change_Kind KIND_UNSPECIFIED =
      Change_Kind._(0, _omitEnumNames ? '' : 'KIND_UNSPECIFIED');
  static const Change_Kind PROFILES =
      Change_Kind._(1, _omitEnumNames ? '' : 'PROFILES');
  static const Change_Kind GROUPS =
      Change_Kind._(2, _omitEnumNames ? '' : 'GROUPS');
  static const Change_Kind NODES =
      Change_Kind._(3, _omitEnumNames ? '' : 'NODES');
  static const Change_Kind RULES =
      Change_Kind._(4, _omitEnumNames ? '' : 'RULES');
  static const Change_Kind RULE_SETS =
      Change_Kind._(5, _omitEnumNames ? '' : 'RULE_SETS');
  static const Change_Kind SETTINGS =
      Change_Kind._(6, _omitEnumNames ? '' : 'SETTINGS');
  static const Change_Kind TESTS =
      Change_Kind._(7, _omitEnumNames ? '' : 'TESTS');
  static const Change_Kind SELECTED =
      Change_Kind._(8, _omitEnumNames ? '' : 'SELECTED');
  static const Change_Kind TASK =
      Change_Kind._(9, _omitEnumNames ? '' : 'TASK');

  static const $core.List<Change_Kind> values = <Change_Kind>[
    KIND_UNSPECIFIED,
    PROFILES,
    GROUPS,
    NODES,
    RULES,
    RULE_SETS,
    SETTINGS,
    TESTS,
    SELECTED,
    TASK,
  ];

  static final $core.List<Change_Kind?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 9);
  static Change_Kind? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const Change_Kind._(super.value, super.name);
}

class Group_Type extends $pb.ProtobufEnum {
  static const Group_Type BASIC =
      Group_Type._(0, _omitEnumNames ? '' : 'BASIC');
  static const Group_Type SUBSCRIPTION =
      Group_Type._(1, _omitEnumNames ? '' : 'SUBSCRIPTION');

  static const $core.List<Group_Type> values = <Group_Type>[
    BASIC,
    SUBSCRIPTION,
  ];

  static final $core.List<Group_Type?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 1);
  static Group_Type? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const Group_Type._(super.value, super.name);
}

/// How the computer's traffic reaches the core (D-NAV-02).
class ConnectRequest_Mode extends $pb.ProtobufEnum {
  /// the local proxy; the default
  static const ConnectRequest_Mode PROXY =
      ConnectRequest_Mode._(0, _omitEnumNames ? '' : 'PROXY');

  /// the TUN: the system routes all traffic into it
  static const ConnectRequest_Mode VPN =
      ConnectRequest_Mode._(1, _omitEnumNames ? '' : 'VPN');

  static const $core.List<ConnectRequest_Mode> values = <ConnectRequest_Mode>[
    PROXY,
    VPN,
  ];

  static final $core.List<ConnectRequest_Mode?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 1);
  static ConnectRequest_Mode? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const ConnectRequest_Mode._(super.value, super.name);
}

class ImportPreview_Kind extends $pb.ProtobufEnum {
  static const ImportPreview_Kind NOTHING =
      ImportPreview_Kind._(0, _omitEnumNames ? '' : 'NOTHING');
  static const ImportPreview_Kind PROFILES =
      ImportPreview_Kind._(1, _omitEnumNames ? '' : 'PROFILES');
  static const ImportPreview_Kind SUBSCRIPTION =
      ImportPreview_Kind._(2, _omitEnumNames ? '' : 'SUBSCRIPTION');
  static const ImportPreview_Kind NODE =
      ImportPreview_Kind._(3, _omitEnumNames ? '' : 'NODE');

  static const $core.List<ImportPreview_Kind> values = <ImportPreview_Kind>[
    NOTHING,
    PROFILES,
    SUBSCRIPTION,
    NODE,
  ];

  static final $core.List<ImportPreview_Kind?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 3);
  static ImportPreview_Kind? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const ImportPreview_Kind._(super.value, super.name);
}

class PresetRequest_Preset extends $pb.ProtobufEnum {
  static const PresetRequest_Preset PRESET_UNSPECIFIED =
      PresetRequest_Preset._(0, _omitEnumNames ? '' : 'PRESET_UNSPECIFIED');

  /// blocked in Russia through the proxy, Russian services directly (runetfreedom's lists)
  static const PresetRequest_Preset RUSSIA =
      PresetRequest_Preset._(1, _omitEnumNames ? '' : 'RUSSIA');

  static const $core.List<PresetRequest_Preset> values = <PresetRequest_Preset>[
    PRESET_UNSPECIFIED,
    RUSSIA,
  ];

  static final $core.List<PresetRequest_Preset?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 1);
  static PresetRequest_Preset? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const PresetRequest_Preset._(super.value, super.name);
}

const $core.bool _omitEnumNames =
    $core.bool.fromEnvironment('protobuf.omit_enum_names');
