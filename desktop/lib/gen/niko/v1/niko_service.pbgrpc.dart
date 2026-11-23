// This is a generated file - do not edit.
//
// Generated from niko/v1/niko_service.proto.

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

import 'niko_service.pb.dart' as $0;

export 'niko_service.pb.dart';

@$pb.GrpcServiceName('niko.v1.NikoService')
class NikoServiceClient extends $grpc.Client {
  /// The hostname for this service.
  static const $core.String defaultHost = '';

  /// OAuth scopes needed for the client.
  static const $core.List<$core.String> oauthScopes = [
    '',
  ];

  NikoServiceClient(super.channel, {super.options, super.interceptors});

  /// What this daemon is. Any caller the daemon verified.
  $grpc.ResponseFuture<$0.Info> getInfo(
    $0.GetInfoRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getInfo, request, options: options);
  }

  /// Which rule of the running core a connection would match and where it would go; no connection is made. The
  /// daemon's owner only: it reads the owner's core; it runs outside the daemon's lifecycle lock, so a stop does not
  /// wait for its lookups. FAILED_PRECONDITION when no core runs; ABORTED when the core stopped during the test (its
  /// answer would be a closing core's, not the running one's).
  $grpc.ResponseFuture<$0.RouteTestResult> routeTest(
    $0.RouteTestRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$routeTest, request, options: options);
  }

  /// What changes in the store, and the results of its tasks: first a reset with the revision now, then each change;
  /// a reset again where some were lost. A change says what changed: the window reads it again.
  $grpc.ResponseStream<$0.Change> watch(
    $0.WatchRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(_$watch, $async.Stream.fromIterable([request]),
        options: options);
  }

  /// The groups, in their order.
  $grpc.ResponseFuture<$0.GroupList> listGroups(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$listGroups, request, options: options);
  }

  /// The profiles of a group, or of all, in the list's order: rows, without their beans. NOT_FOUND for no such group.
  $grpc.ResponseFuture<$0.ProfileList> listProfiles(
    $0.ListProfilesRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$listProfiles, request, options: options);
  }

  /// A profile with its bean. NOT_FOUND; FAILED_PRECONDITION where its secrets cannot be opened on this machine.
  $grpc.ResponseFuture<$0.Profile> getProfile(
    $0.ProfileId request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getProfile, request, options: options);
  }

  /// A profile from its type and bean: a new one (id 0) at the end of its group, or the one of its id with a new bean of
  /// the same type, where it is. INVALID_ARGUMENT for a bean that is not one of its type.
  $grpc.ResponseFuture<$0.Profile> saveProfile(
    $0.Profile request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$saveProfile, request, options: options);
  }

  /// Deletes the profiles of those ids that are there.
  $grpc.ResponseFuture<$0.Empty> deleteProfiles(
    $0.ProfileIds request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteProfiles, request, options: options);
  }

  /// Puts profiles, in their order, into a group before one of its profiles or at its end.
  $grpc.ResponseFuture<$0.Empty> moveProfiles(
    $0.MoveRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$moveProfiles, request, options: options);
  }

  /// A group: of the user's own (BASIC), a new one (id 0) or a new name; of a subscription (SUBSCRIPTION, D-SUB-01,
  /// -02, -08), a new one with its subscription's address and settings (its name the app's default, "Subscription
  /// #…", where none is given, which its first update replaces by the provider's), or the name and settings of one;
  /// what its updates set is kept. Saving makes no update: Refresh does. INVALID_ARGUMENT for an address that is no
  /// http(s) URL, an interval under 15 minutes; FAILED_PRECONDITION for a group of the other type.
  $grpc.ResponseFuture<$0.Group> saveGroup(
    $0.Group request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$saveGroup, request, options: options);
  }

  /// Deletes a group and its profiles; the ungrouped group stays (FAILED_PRECONDITION).
  $grpc.ResponseFuture<$0.Empty> deleteGroup(
    $0.GroupId request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteGroup, request, options: options);
  }

  /// The profile the window connects with (D-MAIN-07): kept in state.json, none (0) once deleted. Watch says SELECTED
  /// with its id, or no id for none. Id 0 selects none; NOT_FOUND for no such profile; FAILED_PRECONDITION for one whose
  /// secrets cannot be opened on this machine.
  $grpc.ResponseFuture<$0.Empty> select(
    $0.ProfileId request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$select, request, options: options);
  }

  /// The selected profile; id 0: none.
  $grpc.ResponseFuture<$0.ProfileId> getSelected(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getSelected, request, options: options);
  }

  /// The link to share a profile by (D-PROFILE-07), as the app writes it: a link of its type's own, else sn://.
  /// NOT_FOUND; FAILED_PRECONDITION where its secrets cannot be opened on this machine; UNIMPLEMENTED for a kind with
  /// no link (a plugin's profile).
  $grpc.ResponseFuture<$0.ShareLink> shareProfile(
    $0.ProfileId request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$shareProfile, request, options: options);
  }

  /// Builds the config of a profile in the daemon, as the app builds it, for this computer (Windows or Linux) with the
  /// computer's settings (GetSettings) in the mode asked, else theirs, and starts the core with it (reloads the one
  /// running), as DesktopService.StartService starts a config with no options: the caller becomes the daemon's owner.
  /// By default proxy mode: the local proxy on 127.0.0.1:2080 with no login, on Windows the caller's system proxy too
  /// (system_proxy), set by the daemon for the caller; VPN mode is the TUN, which the core opens only with the daemon's
  /// privileges. FAILED_PRECONDITION for no profile selected, one
  /// whose secrets cannot be opened on this machine, or settings whose secrets cannot; NOT_FOUND; INVALID_ARGUMENT for
  /// a profile the build refuses, or no such mode; UNIMPLEMENTED for a plugin's profile; PERMISSION_DENIED where another
  /// user owns the daemon; the core's own error for a config it refuses or cannot start.
  $grpc.ResponseFuture<$0.Empty> connect(
    $0.ConnectRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$connect, request, options: options);
  }

  /// The computer's settings (D-SET-05, -16, -18): what Connect builds the config with. FAILED_PRECONDITION where
  /// their secrets were sealed by another machine or account: they are to be set again.
  $grpc.ResponseFuture<$0.Settings> getSettings(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$getSettings, request, options: options);
  }

  /// Sets the settings named to their values in the request, the others as they were, in one write: the settings
  /// then. Watch says SETTINGS. INVALID_ARGUMENT for a name that is no field of Settings, or a value it may not have,
  /// and nothing is changed; FAILED_PRECONDITION where the secrets cannot be opened on this machine and the change
  /// does not set them again.
  $grpc.ResponseFuture<$0.Settings> updateSettings(
    $0.SettingsUpdate request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$updateSettings, request, options: options);
  }

  /// What an input is and what its import would add; nothing is written, and no store is needed.
  $grpc.ResponseFuture<$0.ImportPreview> recognize(
    $0.ImportInput request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$recognize, request, options: options);
  }

  /// Imports an input: the task's progress, then its result, the last message (done). Share links and sn://; a
  /// subscription's link (http(s), clash://install-config, another client's "add subscription" link) makes a group of
  /// that subscription and updates it, as the app's import does: the result names the group (group_id). A raycontrol
  /// node's invite or device package is recognized, its import comes with the nodes (UNIMPLEMENTED as the result's
  /// code), and so is a whole group shared as sn://subscription.
  $grpc.ResponseStream<$0.TaskProgress> import(
    $0.ImportRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(_$import, $async.Stream.fromIterable([request]),
        options: options);
  }

  /// Updates a subscription's group (D-SUB-03), or every one (group_id 0), as the app's update does: the answer
  /// fetched, its profiles merged into the group's, its traffic and expiry kept (Subscription). A task, as Import is:
  /// its progress, then its result; the result in Watch too, and the group's change. The daemon updates the groups
  /// that say so on their own, by their interval. NOT_FOUND; FAILED_PRECONDITION for a group of no subscription or
  /// one whose address cannot be opened on this machine (to be given again); ABORTED where an update of the group runs
  /// already; UNAVAILABLE where the provider did not answer, INVALID_ARGUMENT where its answer has no profiles,
  /// PERMISSION_DENIED where its panel refused this device: as the result's code.
  $grpc.ResponseStream<$0.TaskProgress> refresh(
    $0.RefreshRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$refresh, $async.Stream.fromIterable([request]),
        options: options);
  }

  /// The route rules (D-RULES-01), in the order the build takes them: what Connect builds the config with, the
  /// enabled ones. Watch says RULES.
  $grpc.ResponseFuture<$0.RuleList> listRules(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$listRules, request, options: options);
  }

  /// Saves a rule: a new one (id 0) at the end, or the one of that id with these fields where it is (its order and
  /// its apps stay). The rule as saved. INVALID_ARGUMENT for what a rule may not be (an outbound that is no profile, a
  /// network other than tcp or udp, its own JSON not an object, a list over 1 MiB); NOT_FOUND.
  $grpc.ResponseFuture<$0.Rule> saveRule(
    $0.Rule request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$saveRule, request, options: options);
  }

  $grpc.ResponseFuture<$0.Empty> deleteRules(
    $0.RuleIds request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$deleteRules, request, options: options);
  }

  /// Puts rules, in the order given, before another one (0: at the end); the rules are numbered again from 1.
  /// NOT_FOUND; INVALID_ARGUMENT for no rule, or one to put them before that is not there or among them.
  $grpc.ResponseFuture<$0.Empty> moveRules(
    $0.MoveRulesRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$moveRules, request, options: options);
  }

  /// A ready-made set of rules (D-RULES-05): Russia's two replace its rules there are, at the end. The rules added;
  /// their rule-sets are downloaded by UpdateRuleSets (rule_sets).
  $grpc.ResponseFuture<$0.PresetResult> applyPreset(
    $0.PresetRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$applyPreset, request, options: options);
  }

  /// The rule-sets the rules name that the daemon downloads (BG-09, D-RULES-03): each with its file, when it was
  /// fetched, what it holds and its last download's error. Watch says RULE_SETS.
  $grpc.ResponseFuture<$0.RuleSetList> listRuleSets(
    $0.Empty request, {
    $grpc.CallOptions? options,
  }) {
    return $createUnaryCall(_$listRuleSets, request, options: options);
  }

  /// Downloads rule-sets (D-RULES-03): those asked, or those of the enabled rules, each missing or older than the
  /// update interval (rule_set_update_hours), or each with force. Directly, with the limits of the app's (https alone,
  /// 32 MiB, 120 s in all, 30 s without a byte); a list replaces its file only once the core's reader takes it
  /// (libcore's CheckRuleSet). A task, as Refresh is; the result's code the first failure's (UNAVAILABLE where a
  /// server did not answer, INVALID_ARGUMENT for what is not a rule-set or not https, ABORTED where an update runs
  /// already). The core takes a list it started without when it starts again. While the core runs, the daemon updates
  /// the enabled rules' lists on its own, by the interval.
  $grpc.ResponseStream<$0.TaskProgress> updateRuleSets(
    $0.RuleSetRequest request, {
    $grpc.CallOptions? options,
  }) {
    return $createStreamingCall(
        _$updateRuleSets, $async.Stream.fromIterable([request]),
        options: options);
  }

  // method descriptors

  static final _$getInfo = $grpc.ClientMethod<$0.GetInfoRequest, $0.Info>(
      '/niko.v1.NikoService/GetInfo',
      ($0.GetInfoRequest value) => value.writeToBuffer(),
      $0.Info.fromBuffer);
  static final _$routeTest =
      $grpc.ClientMethod<$0.RouteTestRequest, $0.RouteTestResult>(
          '/niko.v1.NikoService/RouteTest',
          ($0.RouteTestRequest value) => value.writeToBuffer(),
          $0.RouteTestResult.fromBuffer);
  static final _$watch = $grpc.ClientMethod<$0.WatchRequest, $0.Change>(
      '/niko.v1.NikoService/Watch',
      ($0.WatchRequest value) => value.writeToBuffer(),
      $0.Change.fromBuffer);
  static final _$listGroups = $grpc.ClientMethod<$0.Empty, $0.GroupList>(
      '/niko.v1.NikoService/ListGroups',
      ($0.Empty value) => value.writeToBuffer(),
      $0.GroupList.fromBuffer);
  static final _$listProfiles =
      $grpc.ClientMethod<$0.ListProfilesRequest, $0.ProfileList>(
          '/niko.v1.NikoService/ListProfiles',
          ($0.ListProfilesRequest value) => value.writeToBuffer(),
          $0.ProfileList.fromBuffer);
  static final _$getProfile = $grpc.ClientMethod<$0.ProfileId, $0.Profile>(
      '/niko.v1.NikoService/GetProfile',
      ($0.ProfileId value) => value.writeToBuffer(),
      $0.Profile.fromBuffer);
  static final _$saveProfile = $grpc.ClientMethod<$0.Profile, $0.Profile>(
      '/niko.v1.NikoService/SaveProfile',
      ($0.Profile value) => value.writeToBuffer(),
      $0.Profile.fromBuffer);
  static final _$deleteProfiles = $grpc.ClientMethod<$0.ProfileIds, $0.Empty>(
      '/niko.v1.NikoService/DeleteProfiles',
      ($0.ProfileIds value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$moveProfiles = $grpc.ClientMethod<$0.MoveRequest, $0.Empty>(
      '/niko.v1.NikoService/MoveProfiles',
      ($0.MoveRequest value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$saveGroup = $grpc.ClientMethod<$0.Group, $0.Group>(
      '/niko.v1.NikoService/SaveGroup',
      ($0.Group value) => value.writeToBuffer(),
      $0.Group.fromBuffer);
  static final _$deleteGroup = $grpc.ClientMethod<$0.GroupId, $0.Empty>(
      '/niko.v1.NikoService/DeleteGroup',
      ($0.GroupId value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$select = $grpc.ClientMethod<$0.ProfileId, $0.Empty>(
      '/niko.v1.NikoService/Select',
      ($0.ProfileId value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$getSelected = $grpc.ClientMethod<$0.Empty, $0.ProfileId>(
      '/niko.v1.NikoService/GetSelected',
      ($0.Empty value) => value.writeToBuffer(),
      $0.ProfileId.fromBuffer);
  static final _$shareProfile = $grpc.ClientMethod<$0.ProfileId, $0.ShareLink>(
      '/niko.v1.NikoService/ShareProfile',
      ($0.ProfileId value) => value.writeToBuffer(),
      $0.ShareLink.fromBuffer);
  static final _$connect = $grpc.ClientMethod<$0.ConnectRequest, $0.Empty>(
      '/niko.v1.NikoService/Connect',
      ($0.ConnectRequest value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$getSettings = $grpc.ClientMethod<$0.Empty, $0.Settings>(
      '/niko.v1.NikoService/GetSettings',
      ($0.Empty value) => value.writeToBuffer(),
      $0.Settings.fromBuffer);
  static final _$updateSettings =
      $grpc.ClientMethod<$0.SettingsUpdate, $0.Settings>(
          '/niko.v1.NikoService/UpdateSettings',
          ($0.SettingsUpdate value) => value.writeToBuffer(),
          $0.Settings.fromBuffer);
  static final _$recognize =
      $grpc.ClientMethod<$0.ImportInput, $0.ImportPreview>(
          '/niko.v1.NikoService/Recognize',
          ($0.ImportInput value) => value.writeToBuffer(),
          $0.ImportPreview.fromBuffer);
  static final _$import = $grpc.ClientMethod<$0.ImportRequest, $0.TaskProgress>(
      '/niko.v1.NikoService/Import',
      ($0.ImportRequest value) => value.writeToBuffer(),
      $0.TaskProgress.fromBuffer);
  static final _$refresh =
      $grpc.ClientMethod<$0.RefreshRequest, $0.TaskProgress>(
          '/niko.v1.NikoService/Refresh',
          ($0.RefreshRequest value) => value.writeToBuffer(),
          $0.TaskProgress.fromBuffer);
  static final _$listRules = $grpc.ClientMethod<$0.Empty, $0.RuleList>(
      '/niko.v1.NikoService/ListRules',
      ($0.Empty value) => value.writeToBuffer(),
      $0.RuleList.fromBuffer);
  static final _$saveRule = $grpc.ClientMethod<$0.Rule, $0.Rule>(
      '/niko.v1.NikoService/SaveRule',
      ($0.Rule value) => value.writeToBuffer(),
      $0.Rule.fromBuffer);
  static final _$deleteRules = $grpc.ClientMethod<$0.RuleIds, $0.Empty>(
      '/niko.v1.NikoService/DeleteRules',
      ($0.RuleIds value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$moveRules = $grpc.ClientMethod<$0.MoveRulesRequest, $0.Empty>(
      '/niko.v1.NikoService/MoveRules',
      ($0.MoveRulesRequest value) => value.writeToBuffer(),
      $0.Empty.fromBuffer);
  static final _$applyPreset =
      $grpc.ClientMethod<$0.PresetRequest, $0.PresetResult>(
          '/niko.v1.NikoService/ApplyPreset',
          ($0.PresetRequest value) => value.writeToBuffer(),
          $0.PresetResult.fromBuffer);
  static final _$listRuleSets = $grpc.ClientMethod<$0.Empty, $0.RuleSetList>(
      '/niko.v1.NikoService/ListRuleSets',
      ($0.Empty value) => value.writeToBuffer(),
      $0.RuleSetList.fromBuffer);
  static final _$updateRuleSets =
      $grpc.ClientMethod<$0.RuleSetRequest, $0.TaskProgress>(
          '/niko.v1.NikoService/UpdateRuleSets',
          ($0.RuleSetRequest value) => value.writeToBuffer(),
          $0.TaskProgress.fromBuffer);
}

@$pb.GrpcServiceName('niko.v1.NikoService')
abstract class NikoServiceBase extends $grpc.Service {
  $core.String get $name => 'niko.v1.NikoService';

  NikoServiceBase() {
    $addMethod($grpc.ServiceMethod<$0.GetInfoRequest, $0.Info>(
        'GetInfo',
        getInfo_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.GetInfoRequest.fromBuffer(value),
        ($0.Info value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.RouteTestRequest, $0.RouteTestResult>(
        'RouteTest',
        routeTest_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.RouteTestRequest.fromBuffer(value),
        ($0.RouteTestResult value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.WatchRequest, $0.Change>(
        'Watch',
        watch_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.WatchRequest.fromBuffer(value),
        ($0.Change value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.GroupList>(
        'ListGroups',
        listGroups_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.GroupList value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.ListProfilesRequest, $0.ProfileList>(
        'ListProfiles',
        listProfiles_Pre,
        false,
        false,
        ($core.List<$core.int> value) =>
            $0.ListProfilesRequest.fromBuffer(value),
        ($0.ProfileList value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.ProfileId, $0.Profile>(
        'GetProfile',
        getProfile_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.ProfileId.fromBuffer(value),
        ($0.Profile value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Profile, $0.Profile>(
        'SaveProfile',
        saveProfile_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Profile.fromBuffer(value),
        ($0.Profile value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.ProfileIds, $0.Empty>(
        'DeleteProfiles',
        deleteProfiles_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.ProfileIds.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.MoveRequest, $0.Empty>(
        'MoveProfiles',
        moveProfiles_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.MoveRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Group, $0.Group>(
        'SaveGroup',
        saveGroup_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Group.fromBuffer(value),
        ($0.Group value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.GroupId, $0.Empty>(
        'DeleteGroup',
        deleteGroup_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.GroupId.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.ProfileId, $0.Empty>(
        'Select',
        select_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.ProfileId.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.ProfileId>(
        'GetSelected',
        getSelected_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.ProfileId value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.ProfileId, $0.ShareLink>(
        'ShareProfile',
        shareProfile_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.ProfileId.fromBuffer(value),
        ($0.ShareLink value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.ConnectRequest, $0.Empty>(
        'Connect',
        connect_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.ConnectRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.Settings>(
        'GetSettings',
        getSettings_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.Settings value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.SettingsUpdate, $0.Settings>(
        'UpdateSettings',
        updateSettings_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.SettingsUpdate.fromBuffer(value),
        ($0.Settings value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.ImportInput, $0.ImportPreview>(
        'Recognize',
        recognize_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.ImportInput.fromBuffer(value),
        ($0.ImportPreview value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.ImportRequest, $0.TaskProgress>(
        'Import',
        import_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.ImportRequest.fromBuffer(value),
        ($0.TaskProgress value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.RefreshRequest, $0.TaskProgress>(
        'Refresh',
        refresh_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.RefreshRequest.fromBuffer(value),
        ($0.TaskProgress value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.RuleList>(
        'ListRules',
        listRules_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.RuleList value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Rule, $0.Rule>(
        'SaveRule',
        saveRule_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Rule.fromBuffer(value),
        ($0.Rule value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.RuleIds, $0.Empty>(
        'DeleteRules',
        deleteRules_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.RuleIds.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.MoveRulesRequest, $0.Empty>(
        'MoveRules',
        moveRules_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.MoveRulesRequest.fromBuffer(value),
        ($0.Empty value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.PresetRequest, $0.PresetResult>(
        'ApplyPreset',
        applyPreset_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.PresetRequest.fromBuffer(value),
        ($0.PresetResult value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.Empty, $0.RuleSetList>(
        'ListRuleSets',
        listRuleSets_Pre,
        false,
        false,
        ($core.List<$core.int> value) => $0.Empty.fromBuffer(value),
        ($0.RuleSetList value) => value.writeToBuffer()));
    $addMethod($grpc.ServiceMethod<$0.RuleSetRequest, $0.TaskProgress>(
        'UpdateRuleSets',
        updateRuleSets_Pre,
        false,
        true,
        ($core.List<$core.int> value) => $0.RuleSetRequest.fromBuffer(value),
        ($0.TaskProgress value) => value.writeToBuffer()));
  }

  $async.Future<$0.Info> getInfo_Pre($grpc.ServiceCall $call,
      $async.Future<$0.GetInfoRequest> $request) async {
    return getInfo($call, await $request);
  }

  $async.Future<$0.Info> getInfo(
      $grpc.ServiceCall call, $0.GetInfoRequest request);

  $async.Future<$0.RouteTestResult> routeTest_Pre($grpc.ServiceCall $call,
      $async.Future<$0.RouteTestRequest> $request) async {
    return routeTest($call, await $request);
  }

  $async.Future<$0.RouteTestResult> routeTest(
      $grpc.ServiceCall call, $0.RouteTestRequest request);

  $async.Stream<$0.Change> watch_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.WatchRequest> $request) async* {
    yield* watch($call, await $request);
  }

  $async.Stream<$0.Change> watch(
      $grpc.ServiceCall call, $0.WatchRequest request);

  $async.Future<$0.GroupList> listGroups_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return listGroups($call, await $request);
  }

  $async.Future<$0.GroupList> listGroups(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.ProfileList> listProfiles_Pre($grpc.ServiceCall $call,
      $async.Future<$0.ListProfilesRequest> $request) async {
    return listProfiles($call, await $request);
  }

  $async.Future<$0.ProfileList> listProfiles(
      $grpc.ServiceCall call, $0.ListProfilesRequest request);

  $async.Future<$0.Profile> getProfile_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.ProfileId> $request) async {
    return getProfile($call, await $request);
  }

  $async.Future<$0.Profile> getProfile(
      $grpc.ServiceCall call, $0.ProfileId request);

  $async.Future<$0.Profile> saveProfile_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Profile> $request) async {
    return saveProfile($call, await $request);
  }

  $async.Future<$0.Profile> saveProfile(
      $grpc.ServiceCall call, $0.Profile request);

  $async.Future<$0.Empty> deleteProfiles_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.ProfileIds> $request) async {
    return deleteProfiles($call, await $request);
  }

  $async.Future<$0.Empty> deleteProfiles(
      $grpc.ServiceCall call, $0.ProfileIds request);

  $async.Future<$0.Empty> moveProfiles_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.MoveRequest> $request) async {
    return moveProfiles($call, await $request);
  }

  $async.Future<$0.Empty> moveProfiles(
      $grpc.ServiceCall call, $0.MoveRequest request);

  $async.Future<$0.Group> saveGroup_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Group> $request) async {
    return saveGroup($call, await $request);
  }

  $async.Future<$0.Group> saveGroup($grpc.ServiceCall call, $0.Group request);

  $async.Future<$0.Empty> deleteGroup_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.GroupId> $request) async {
    return deleteGroup($call, await $request);
  }

  $async.Future<$0.Empty> deleteGroup(
      $grpc.ServiceCall call, $0.GroupId request);

  $async.Future<$0.Empty> select_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.ProfileId> $request) async {
    return select($call, await $request);
  }

  $async.Future<$0.Empty> select($grpc.ServiceCall call, $0.ProfileId request);

  $async.Future<$0.ProfileId> getSelected_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getSelected($call, await $request);
  }

  $async.Future<$0.ProfileId> getSelected(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.ShareLink> shareProfile_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.ProfileId> $request) async {
    return shareProfile($call, await $request);
  }

  $async.Future<$0.ShareLink> shareProfile(
      $grpc.ServiceCall call, $0.ProfileId request);

  $async.Future<$0.Empty> connect_Pre($grpc.ServiceCall $call,
      $async.Future<$0.ConnectRequest> $request) async {
    return connect($call, await $request);
  }

  $async.Future<$0.Empty> connect(
      $grpc.ServiceCall call, $0.ConnectRequest request);

  $async.Future<$0.Settings> getSettings_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return getSettings($call, await $request);
  }

  $async.Future<$0.Settings> getSettings(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Settings> updateSettings_Pre($grpc.ServiceCall $call,
      $async.Future<$0.SettingsUpdate> $request) async {
    return updateSettings($call, await $request);
  }

  $async.Future<$0.Settings> updateSettings(
      $grpc.ServiceCall call, $0.SettingsUpdate request);

  $async.Future<$0.ImportPreview> recognize_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.ImportInput> $request) async {
    return recognize($call, await $request);
  }

  $async.Future<$0.ImportPreview> recognize(
      $grpc.ServiceCall call, $0.ImportInput request);

  $async.Stream<$0.TaskProgress> import_Pre($grpc.ServiceCall $call,
      $async.Future<$0.ImportRequest> $request) async* {
    yield* import($call, await $request);
  }

  $async.Stream<$0.TaskProgress> import(
      $grpc.ServiceCall call, $0.ImportRequest request);

  $async.Stream<$0.TaskProgress> refresh_Pre($grpc.ServiceCall $call,
      $async.Future<$0.RefreshRequest> $request) async* {
    yield* refresh($call, await $request);
  }

  $async.Stream<$0.TaskProgress> refresh(
      $grpc.ServiceCall call, $0.RefreshRequest request);

  $async.Future<$0.RuleList> listRules_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return listRules($call, await $request);
  }

  $async.Future<$0.RuleList> listRules(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Future<$0.Rule> saveRule_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Rule> $request) async {
    return saveRule($call, await $request);
  }

  $async.Future<$0.Rule> saveRule($grpc.ServiceCall call, $0.Rule request);

  $async.Future<$0.Empty> deleteRules_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.RuleIds> $request) async {
    return deleteRules($call, await $request);
  }

  $async.Future<$0.Empty> deleteRules(
      $grpc.ServiceCall call, $0.RuleIds request);

  $async.Future<$0.Empty> moveRules_Pre($grpc.ServiceCall $call,
      $async.Future<$0.MoveRulesRequest> $request) async {
    return moveRules($call, await $request);
  }

  $async.Future<$0.Empty> moveRules(
      $grpc.ServiceCall call, $0.MoveRulesRequest request);

  $async.Future<$0.PresetResult> applyPreset_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.PresetRequest> $request) async {
    return applyPreset($call, await $request);
  }

  $async.Future<$0.PresetResult> applyPreset(
      $grpc.ServiceCall call, $0.PresetRequest request);

  $async.Future<$0.RuleSetList> listRuleSets_Pre(
      $grpc.ServiceCall $call, $async.Future<$0.Empty> $request) async {
    return listRuleSets($call, await $request);
  }

  $async.Future<$0.RuleSetList> listRuleSets(
      $grpc.ServiceCall call, $0.Empty request);

  $async.Stream<$0.TaskProgress> updateRuleSets_Pre($grpc.ServiceCall $call,
      $async.Future<$0.RuleSetRequest> $request) async* {
    yield* updateRuleSets($call, await $request);
  }

  $async.Stream<$0.TaskProgress> updateRuleSets(
      $grpc.ServiceCall call, $0.RuleSetRequest request);
}
