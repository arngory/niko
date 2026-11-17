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

import 'package:protobuf/protobuf.dart' as $pb;

class DaemonOwnership extends $pb.ProtobufEnum {
  static const DaemonOwnership DAEMON_OWNERSHIP_UNSPECIFIED = DaemonOwnership._(
      0, _omitEnumNames ? '' : 'DAEMON_OWNERSHIP_UNSPECIFIED');
  static const DaemonOwnership DAEMON_OWNERSHIP_AVAILABLE =
      DaemonOwnership._(1, _omitEnumNames ? '' : 'DAEMON_OWNERSHIP_AVAILABLE');
  static const DaemonOwnership DAEMON_OWNERSHIP_CALLER =
      DaemonOwnership._(2, _omitEnumNames ? '' : 'DAEMON_OWNERSHIP_CALLER');
  static const DaemonOwnership DAEMON_OWNERSHIP_OTHER =
      DaemonOwnership._(3, _omitEnumNames ? '' : 'DAEMON_OWNERSHIP_OTHER');

  static const $core.List<DaemonOwnership> values = <DaemonOwnership>[
    DAEMON_OWNERSHIP_UNSPECIFIED,
    DAEMON_OWNERSHIP_AVAILABLE,
    DAEMON_OWNERSHIP_CALLER,
    DAEMON_OWNERSHIP_OTHER,
  ];

  static final $core.List<DaemonOwnership?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 3);
  static DaemonOwnership? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const DaemonOwnership._(super.value, super.name);
}

class InstallUpdateResult extends $pb.ProtobufEnum {
  static const InstallUpdateResult INSTALL_UPDATE_RESULT_UNSPECIFIED =
      InstallUpdateResult._(
          0, _omitEnumNames ? '' : 'INSTALL_UPDATE_RESULT_UNSPECIFIED');
  static const InstallUpdateResult INSTALL_UPDATE_RESULT_STARTED =
      InstallUpdateResult._(
          1, _omitEnumNames ? '' : 'INSTALL_UPDATE_RESULT_STARTED');
  static const InstallUpdateResult INSTALL_UPDATE_RESULT_SIGNER_MISMATCH =
      InstallUpdateResult._(
          2, _omitEnumNames ? '' : 'INSTALL_UPDATE_RESULT_SIGNER_MISMATCH');
  static const InstallUpdateResult INSTALL_UPDATE_RESULT_NOT_NEWER =
      InstallUpdateResult._(
          3, _omitEnumNames ? '' : 'INSTALL_UPDATE_RESULT_NOT_NEWER');

  static const $core.List<InstallUpdateResult> values = <InstallUpdateResult>[
    INSTALL_UPDATE_RESULT_UNSPECIFIED,
    INSTALL_UPDATE_RESULT_STARTED,
    INSTALL_UPDATE_RESULT_SIGNER_MISMATCH,
    INSTALL_UPDATE_RESULT_NOT_NEWER,
  ];

  static final $core.List<InstallUpdateResult?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 3);
  static InstallUpdateResult? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const InstallUpdateResult._(super.value, super.name);
}

class ProfileContent_Type extends $pb.ProtobufEnum {
  static const ProfileContent_Type LOCAL =
      ProfileContent_Type._(0, _omitEnumNames ? '' : 'LOCAL');
  static const ProfileContent_Type ICLOUD =
      ProfileContent_Type._(1, _omitEnumNames ? '' : 'ICLOUD');
  static const ProfileContent_Type REMOTE =
      ProfileContent_Type._(2, _omitEnumNames ? '' : 'REMOTE');

  static const $core.List<ProfileContent_Type> values = <ProfileContent_Type>[
    LOCAL,
    ICLOUD,
    REMOTE,
  ];

  static final $core.List<ProfileContent_Type?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 2);
  static ProfileContent_Type? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const ProfileContent_Type._(super.value, super.name);
}

const $core.bool _omitEnumNames =
    $core.bool.fromEnvironment('protobuf.omit_enum_names');
