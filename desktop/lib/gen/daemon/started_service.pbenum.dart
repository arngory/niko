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

import 'package:protobuf/protobuf.dart' as $pb;

class LogLevel extends $pb.ProtobufEnum {
  static const LogLevel PANIC = LogLevel._(0, _omitEnumNames ? '' : 'PANIC');
  static const LogLevel FATAL = LogLevel._(1, _omitEnumNames ? '' : 'FATAL');
  static const LogLevel ERROR = LogLevel._(2, _omitEnumNames ? '' : 'ERROR');
  static const LogLevel WARN = LogLevel._(3, _omitEnumNames ? '' : 'WARN');
  static const LogLevel INFO = LogLevel._(4, _omitEnumNames ? '' : 'INFO');
  static const LogLevel DEBUG = LogLevel._(5, _omitEnumNames ? '' : 'DEBUG');
  static const LogLevel TRACE = LogLevel._(6, _omitEnumNames ? '' : 'TRACE');

  static const $core.List<LogLevel> values = <LogLevel>[
    PANIC,
    FATAL,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE,
  ];

  static final $core.List<LogLevel?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 6);
  static LogLevel? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const LogLevel._(super.value, super.name);
}

class ConnectionEventType extends $pb.ProtobufEnum {
  static const ConnectionEventType CONNECTION_EVENT_NEW =
      ConnectionEventType._(0, _omitEnumNames ? '' : 'CONNECTION_EVENT_NEW');
  static const ConnectionEventType CONNECTION_EVENT_UPDATE =
      ConnectionEventType._(1, _omitEnumNames ? '' : 'CONNECTION_EVENT_UPDATE');
  static const ConnectionEventType CONNECTION_EVENT_CLOSED =
      ConnectionEventType._(2, _omitEnumNames ? '' : 'CONNECTION_EVENT_CLOSED');

  static const $core.List<ConnectionEventType> values = <ConnectionEventType>[
    CONNECTION_EVENT_NEW,
    CONNECTION_EVENT_UPDATE,
    CONNECTION_EVENT_CLOSED,
  ];

  static final $core.List<ConnectionEventType?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 2);
  static ConnectionEventType? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const ConnectionEventType._(super.value, super.name);
}

class USBDeviceState extends $pb.ProtobufEnum {
  static const USBDeviceState USB_DEVICE_STATE_IDLE =
      USBDeviceState._(0, _omitEnumNames ? '' : 'USB_DEVICE_STATE_IDLE');
  static const USBDeviceState USB_DEVICE_STATE_ATTACHED =
      USBDeviceState._(1, _omitEnumNames ? '' : 'USB_DEVICE_STATE_ATTACHED');
  static const USBDeviceState USB_DEVICE_STATE_UNAVAILABLE =
      USBDeviceState._(2, _omitEnumNames ? '' : 'USB_DEVICE_STATE_UNAVAILABLE');

  static const $core.List<USBDeviceState> values = <USBDeviceState>[
    USB_DEVICE_STATE_IDLE,
    USB_DEVICE_STATE_ATTACHED,
    USB_DEVICE_STATE_UNAVAILABLE,
  ];

  static final $core.List<USBDeviceState?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 2);
  static USBDeviceState? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const USBDeviceState._(super.value, super.name);
}

class USBBackend extends $pb.ProtobufEnum {
  static const USBBackend USB_BACKEND_UNSPECIFIED =
      USBBackend._(0, _omitEnumNames ? '' : 'USB_BACKEND_UNSPECIFIED');
  static const USBBackend USB_BACKEND_LINUX_SYSFS =
      USBBackend._(1, _omitEnumNames ? '' : 'USB_BACKEND_LINUX_SYSFS');
  static const USBBackend USB_BACKEND_DYNAMIC =
      USBBackend._(2, _omitEnumNames ? '' : 'USB_BACKEND_DYNAMIC');
  static const USBBackend USB_BACKEND_DARWIN_IOKIT =
      USBBackend._(3, _omitEnumNames ? '' : 'USB_BACKEND_DARWIN_IOKIT');
  static const USBBackend USB_BACKEND_WINDOWS_VBOXUSB =
      USBBackend._(4, _omitEnumNames ? '' : 'USB_BACKEND_WINDOWS_VBOXUSB');

  static const $core.List<USBBackend> values = <USBBackend>[
    USB_BACKEND_UNSPECIFIED,
    USB_BACKEND_LINUX_SYSFS,
    USB_BACKEND_DYNAMIC,
    USB_BACKEND_DARWIN_IOKIT,
    USB_BACKEND_WINDOWS_VBOXUSB,
  ];

  static final $core.List<USBBackend?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 4);
  static USBBackend? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const USBBackend._(super.value, super.name);
}

class ServiceStatus_Type extends $pb.ProtobufEnum {
  static const ServiceStatus_Type IDLE =
      ServiceStatus_Type._(0, _omitEnumNames ? '' : 'IDLE');
  static const ServiceStatus_Type STARTING =
      ServiceStatus_Type._(1, _omitEnumNames ? '' : 'STARTING');
  static const ServiceStatus_Type STARTED =
      ServiceStatus_Type._(2, _omitEnumNames ? '' : 'STARTED');
  static const ServiceStatus_Type STOPPING =
      ServiceStatus_Type._(3, _omitEnumNames ? '' : 'STOPPING');
  static const ServiceStatus_Type FATAL =
      ServiceStatus_Type._(4, _omitEnumNames ? '' : 'FATAL');

  static const $core.List<ServiceStatus_Type> values = <ServiceStatus_Type>[
    IDLE,
    STARTING,
    STARTED,
    STOPPING,
    FATAL,
  ];

  static final $core.List<ServiceStatus_Type?> _byValue =
      $pb.ProtobufEnum.$_initByValueList(values, 4);
  static ServiceStatus_Type? valueOf($core.int value) =>
      value < 0 || value >= _byValue.length ? null : _byValue[value];

  const ServiceStatus_Type._(super.value, super.name);
}

const $core.bool _omitEnumNames =
    $core.bool.fromEnvironment('protobuf.omit_enum_names');
