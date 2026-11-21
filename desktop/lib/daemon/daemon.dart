import 'dart:async';

import 'package:fixnum/fixnum.dart';
import 'package:grpc/grpc.dart';
import 'package:protobuf/well_known_types/google/protobuf/empty.pb.dart';

import '../gen/daemon/managed_service.pbgrpc.dart';
import '../gen/daemon/started_service.pbgrpc.dart';
import '../gen/experimental/boxdd/desktop_service.pbgrpc.dart';
import '../gen/niko/v1/niko_service.pbgrpc.dart' as niko;
import 'endpoint.dart';

/// What the core is doing (the daemon's ServiceStatus).
enum ServicePhase { idle, starting, started, stopping, fatal }

class ServiceState {
  const ServiceState(this.phase, [this.error = '']);

  final ServicePhase phase;

  /// The core's own words for a start that failed ([ServicePhase.fatal]).
  final String error;
}

/// The traffic of the running service: bytes a second over the last interval, and since the start.
class Traffic {
  const Traffic({required this.up, required this.down, required this.upTotal, required this.downTotal});

  final int up;
  final int down;
  final int upTotal;
  final int downTotal;
}

/// How grave a line of the core's log is (the daemon's LogLevel, in its order).
enum LogSeverity { panic, fatal, error, warn, info, debug, trace }

/// A line of the core's log, as the core wrote it but for the terminal colours.
class LogLine {
  const LogLine(this.level, this.text);

  final LogSeverity level;
  final String text;
}

/// What the log stream brings at a time. [reset]: what was shown before is gone (the first batch, which carries
/// the lines the daemon keeps, and a clearing), and [lines] is all there is.
class LogBatch {
  const LogBatch(this.lines, {this.reset = false});

  final List<LogLine> lines;
  final bool reset;
}

/// A connection the core carries: where to, for which program, by which rule and through what.
class ConnectionInfo {
  const ConnectionInfo({
    required this.id,
    required this.network,
    required this.destination,
    this.domain = '',
    this.protocol = '',
    this.process = '',
    this.rule = '',
    this.outbound = '',
    this.outboundType = '',
    required this.createdAt,
    this.up = 0,
    this.down = 0,
  });

  final String id;

  /// `tcp` or `udp`.
  final String network;

  /// `address:port`, as the core dials it.
  final String destination;

  /// The name, where the program asked by name or the core sniffed it.
  final String domain;

  /// What the core sniffed in the stream: `tls`, `quic`, `http`, `ssh`...
  final String protocol;

  /// The program's file, where the core could tell.
  final String process;
  final String rule;
  final String outbound;
  final String outboundType;
  final DateTime createdAt;

  /// Bytes so far.
  final int up;
  final int down;

  ConnectionInfo moved(int upDelta, int downDelta) => ConnectionInfo(
    id: id,
    network: network,
    destination: destination,
    domain: domain,
    protocol: protocol,
    process: process,
    rule: rule,
    outbound: outbound,
    outboundType: outboundType,
    createdAt: createdAt,
    up: up + upDelta,
    down: down + downDelta,
  );
}

/// A change in the connections: [opened] is set for a new one, the deltas for one that moved bytes.
class ConnectionChange {
  const ConnectionChange.opened(ConnectionInfo this.opened) : id = '', closed = false, upDelta = 0, downDelta = 0;
  const ConnectionChange.moved(this.id, this.upDelta, this.downDelta) : opened = null, closed = false;
  const ConnectionChange.closed(this.id) : opened = null, closed = true, upDelta = 0, downDelta = 0;

  final ConnectionInfo? opened;
  final String id;
  final bool closed;
  final int upDelta;
  final int downDelta;
}

/// What the connections stream brings at a time. [reset]: the first batch, which is every open connection.
class ConnectionBatch {
  const ConnectionBatch(this.changes, {this.reset = false});

  final List<ConnectionChange> changes;
  final bool reset;
}

/// What a report the daemon kept is of: the core's crash, or the memory it ran out of (D-LOG-02).
enum ReportKind { crash, outOfMemory }

/// A report the daemon kept: the core crashed, or ran out of memory.
class ReportEntry {
  const ReportEntry(this.kind, this.name, this.at, {this.read = false});

  final ReportKind kind;

  /// The daemon's name of it, which the other calls take.
  final String name;
  final DateTime at;

  /// Exported or looked at already.
  final bool read;
}

/// A report packed by the daemon to be saved: a zip of what it kept, the core's log with it, never its config.
class ReportArchive {
  const ReportArchive(this.fileName, this.data);

  final String fileName;
  final List<int> data;
}

/// How a NAT maps a host's port, or lets packets in (RFC 4787), as STUN measured it.
enum NatBehaviour { unknown, endpointIndependent, addressDependent, addressAndPortDependent }

/// A step of the STUN test, or its result ([isFinal]): what the network looks like from the core's default way out.
class StunProgress {
  const StunProgress({
    this.externalAddress = '',
    this.latencyMs = 0,
    this.mapping = NatBehaviour.unknown,
    this.filtering = NatBehaviour.unknown,
    this.natTypeSupported = false,
    this.isFinal = false,
    this.error = '',
  });

  /// The address and port the STUN server saw.
  final String externalAddress;
  final int latencyMs;
  final NatBehaviour mapping;
  final NatBehaviour filtering;

  /// The server answers the tests that tell the NAT's behaviour (RFC 5780), not the binding alone.
  final bool natTypeSupported;
  final bool isFinal;
  final String error;
}

/// What Niko's daemon, nikod, is (NikoService.GetInfo).
class NikoInfo {
  const NikoInfo({required this.nikoVersion, required this.coreVersion, required this.storeSchema, required this.api});

  final String nikoVersion;
  final String coreVersion;

  /// The schema of the daemon's store; 0: no store yet.
  final int storeSchema;

  /// The version of NikoService: what the window may ask of this daemon.
  final int api;
}

/// A rule a connection matched on its way (NikoService.RouteTest).
class RouteStep {
  const RouteStep({
    required this.index,
    required this.rule,
    required this.action,
    this.resolved = const [],
    this.note = '',
  });

  final int index;
  final String rule;
  final String action;

  /// A resolve action's addresses.
  final List<String> resolved;

  /// What the action did, or what of it was taken on trust.
  final String note;
}

/// Where a connection would go, and which rule of the running core sent it there (NikoService.RouteTest).
class RouteVerdict {
  const RouteVerdict({
    required this.index,
    required this.rule,
    required this.action,
    required this.outbound,
    this.error = '',
    this.destination = '',
    this.fakeIp = false,
    this.resolved = const [],
    this.notes = const [],
    this.steps = const [],
  });

  /// The rule that decided; -1: none, the final outbound took it.
  final int index;
  final String rule;
  final String action;
  final String outbound;

  /// A lookup the connection cannot do without failed, and it goes nowhere.
  final String error;

  /// The connection as the rules first saw it.
  final String destination;
  final bool fakeIp;

  /// What the core's DNS answered the app's own query through the VPN.
  final List<String> resolved;

  /// What the rules did not see as they would see a real connection.
  final List<String> notes;
  final List<RouteStep> steps;
}

/// What a change of the daemon's store is of (NikoService.Watch).
enum StoreKind { other, profiles, groups, selected, task }

/// A change of the daemon's store (NikoService.Watch): what it is of and the ids it touched, added, changed or
/// removed (none: all of that kind). [reset]: the first change, and one after changes were lost: all that is shown is
/// read again.
class StoreChange {
  const StoreChange({required this.revision, this.reset = false, this.kind = StoreKind.other, this.ids = const []});

  /// Grows with each change from the daemon's start; a reset's is the revision then.
  final int revision;
  final bool reset;
  final StoreKind kind;
  final List<int> ids;
}

/// A group of profiles (NikoService.ListGroups).
class GroupRow {
  const GroupRow({
    required this.id,
    required this.name,
    this.ungrouped = false,
    this.subscription = false,
    this.profiles = 0,
  });

  final int id;

  /// Empty for the ungrouped group, which the window names itself.
  final String name;

  /// The default group: an import with no group goes there.
  final bool ungrouped;
  final bool subscription;
  final int profiles;
}

/// A profile as a list shows it, without its bean (NikoService.ListProfiles).
class ProfileRow {
  const ProfileRow({
    required this.id,
    required this.groupId,
    required this.order,
    required this.type,
    required this.name,
    required this.address,
  });

  final int id;
  final int groupId;
  final int order;

  /// The app's name of the bean's type: `naive`, `hysteria`, `vmess` (VLESS too)...
  final String type;
  final String name;
  final String address;
}

/// What an input is (NikoService.Recognize).
enum ImportKind { nothing, profiles, subscription }

/// What an import of an input would add: the profiles as rows without ids, or a subscription's link.
class ImportPreview {
  const ImportPreview({this.kind = ImportKind.nothing, this.profiles = const [], this.subscription = ''});

  final ImportKind kind;
  final List<ProfileRow> profiles;
  final String subscription;
}

/// A profile with its bean (NikoService.GetProfile): what it takes to put it back after a deletion.
class SavedProfile {
  const SavedProfile({required this.row, required this.beanJson});

  final ProfileRow row;
  final String beanJson;
}

/// The daemon as the window uses it. One implementation talks gRPC ([GrpcDaemon]); tests put a server behind it.
abstract interface class Daemon {
  /// The daemon's version: the first call, which also says whether it is there at all.
  Future<String> version();

  /// The service's state now, then every change. Ends or fails when the daemon goes away.
  Stream<ServiceState> serviceStatus();

  /// The traffic every [interval] while the service runs.
  Stream<Traffic> traffic(Duration interval);

  /// When the running service started.
  Future<DateTime> startedAt();

  /// Starts the core with a ready JSON config (D-MAIN-04).
  Future<void> start(String config);

  /// Stops it (D-MAIN-04).
  Future<void> stop();

  /// The core's log: what the daemon keeps of it, then every new line (D-LOG-01).
  Stream<LogBatch> logs();

  /// Empties the daemon's log, for every window that reads it (D-LOG-01).
  Future<void> clearLogs();

  /// The open connections, then their changes; the traffic every [interval]. Fails at once when the core is not
  /// running, and ends when it stops (D-CONN-01).
  Stream<ConnectionBatch> connections(Duration interval);

  Future<void> closeConnection(String id);

  Future<void> closeAllConnections();

  /// The reports of crashes and of the memory run out, newest first (D-LOG-02).
  Future<List<ReportEntry>> reports();

  /// The report as an archive: the core's log in it, the config the core ran left out (it holds the keys).
  Future<ReportArchive> exportReport(ReportEntry report);

  Future<void> markReportRead(ReportEntry report);

  /// The STUN test through the core's default outbound (`host:port`, empty: the daemon's own server): its steps, the
  /// last [StunProgress.isFinal]. Fails at once when the core is not running.
  Stream<StunProgress> stunTest(String server);

  /// What Niko's daemon is (nikod's NikoService). Fails with UNIMPLEMENTED against the core's daemon alone.
  Future<NikoInfo> nikoInfo();

  /// Which rule of the running core a connection to [host]:[port] would match and where it would go; nothing is
  /// connected (D-CHECK-03…05). [inbound] empty: the TUN's; [resolve] lets the core's DNS be asked, as an app's query
  /// would. Fails when the core is not running, and for the daemon's owner alone outside development mode.
  Future<RouteVerdict> routeTest(
    String host,
    int port, {
    String network = 'tcp',
    String inbound = '',
    bool resolve = false,
  });

  /// What changes in the daemon's store: a reset first, then each change (NikoService.Watch). Ends or fails when the
  /// daemon goes away; fails with UNIMPLEMENTED against a daemon without the store.
  Stream<StoreChange> storeChanges();

  /// The profiles of a group ([groupId] 0: of all), in the list's order.
  Future<List<ProfileRow>> profiles({int groupId = 0});

  /// The groups, in their order (NikoService.ListGroups).
  Future<List<GroupRow>> groups();

  /// The selected profile's id; 0: none (NikoService.GetSelected).
  Future<int> selected();

  /// Selects a profile, or none with 0 (NikoService.Select): what the window connects with, D-MAIN-07.
  Future<void> select(int profileId);

  /// What an input is and what its import would add; nothing is written (NikoService.Recognize).
  Future<ImportPreview> recognize(String text);

  /// A profile with its bean (NikoService.GetProfile).
  Future<SavedProfile> profile(int id);

  /// A new profile at the end of its group, from its type and bean (NikoService.SaveProfile): its id.
  Future<int> addProfile({required int groupId, required String type, required String beanJson});

  /// Deletes the profiles of those ids (NikoService.DeleteProfiles).
  Future<void> deleteProfiles(List<int> ids);

  /// Puts profiles into a group before one of its profiles, or at its end with [before] 0 (NikoService.MoveProfiles).
  Future<void> moveProfiles(List<int> ids, {required int groupId, int before = 0});

  /// Imports the share links in [text] into a group ([groupId] 0: the ungrouped one), D-ADD-03, -04: the ids of the
  /// profiles added. Fails with the daemon's code and words where it imported nothing: no link it reads, or a
  /// subscription's link, whose import comes later.
  Future<List<int>> importLinks(String text, {int groupId = 0});

  Future<void> close();
}

/// A failure of a call as a sentence for the screen: the daemon's own message where it gave one.
String daemonError(Object error) {
  if (error is GrpcError) {
    final message = error.message ?? '';
    if (error.code == StatusCode.unavailable) return message.isEmpty ? 'daemon unavailable' : message;
    return message.isEmpty ? error.codeName : message;
  }
  return error.toString();
}

class GrpcDaemon implements Daemon {
  GrpcDaemon(this._channel)
    : _desktop = DesktopServiceClient(_channel),
      _started = StartedServiceClient(_channel),
      _managed = ManagedServiceClient(_channel),
      _niko = niko.NikoServiceClient(_channel);

  /// The development link: plain TCP to 127.0.0.1, which [DevEndpoint] alone can name.
  factory GrpcDaemon.dev(DevEndpoint endpoint) => GrpcDaemon(
    ClientChannel(
      DevEndpoint.host,
      port: endpoint.port,
      options: const ChannelOptions(credentials: ChannelCredentials.insecure(), connectTimeout: Duration(seconds: 3)),
    ),
  );

  final ClientChannel _channel;
  final DesktopServiceClient _desktop;
  final StartedServiceClient _started;
  final ManagedServiceClient _managed;
  final niko.NikoServiceClient _niko;

  static final _short = CallOptions(timeout: const Duration(seconds: 5));
  // a start downloads rule sets and opens a TUN: the daemon answers when the core is up or has failed
  static final _long = CallOptions(timeout: const Duration(seconds: 90));

  @override
  Future<String> version() async => (await _desktop.getDaemonInfo(Empty(), options: _short)).version;

  @override
  Stream<ServiceState> serviceStatus() => _started
      .subscribeServiceStatus(Empty())
      .map(
        (status) => ServiceState(switch (status.status) {
          ServiceStatus_Type.STARTING => ServicePhase.starting,
          ServiceStatus_Type.STARTED => ServicePhase.started,
          ServiceStatus_Type.STOPPING => ServicePhase.stopping,
          ServiceStatus_Type.FATAL => ServicePhase.fatal,
          _ => ServicePhase.idle,
        }, status.errorMessage),
      );

  @override
  Stream<Traffic> traffic(Duration interval) {
    // the interval is a Go duration: nanoseconds. The rates are bytes per interval
    final request = SubscribeStatusRequest(interval: Int64(interval.inMicroseconds) * 1000);
    final perSecond = Duration.microsecondsPerSecond / interval.inMicroseconds;
    return _started
        .subscribeStatus(request)
        .map(
          (status) => Traffic(
            up: (status.uplink.toInt() * perSecond).round(),
            down: (status.downlink.toInt() * perSecond).round(),
            upTotal: status.uplinkTotal.toInt(),
            downTotal: status.downlinkTotal.toInt(),
          ),
        );
  }

  @override
  Future<DateTime> startedAt() async =>
      DateTime.fromMillisecondsSinceEpoch((await _started.getStartedAt(Empty(), options: _short)).startedAt.toInt());

  @override
  Future<void> start(String config) =>
      _desktop.startService(StartServiceRequest(configContent: config), options: _long);

  @override
  Future<void> stop() => _managed.stopService(Empty(), options: _long);

  @override
  Stream<LogBatch> logs() => _started
      .subscribeLog(Empty())
      .map(
        (batch) => LogBatch([
          for (final message in batch.messages)
            LogLine(
              LogSeverity.values[message.level.value.clamp(0, LogSeverity.values.length - 1)],
              plainLogText(message.message),
            ),
        ], reset: batch.reset),
      );

  @override
  Future<void> clearLogs() => _started.clearLogs(Empty(), options: _short);

  @override
  Stream<ConnectionBatch> connections(Duration interval) {
    final request = SubscribeConnectionsRequest(interval: Int64(interval.inMicroseconds) * 1000);
    return _started
        .subscribeConnections(request)
        .map(
          (batch) => ConnectionBatch([
            for (final event in batch.events)
              switch (event.type) {
                ConnectionEventType.CONNECTION_EVENT_NEW when event.hasConnection() => ConnectionChange.opened(
                  _connection(event.connection),
                ),
                ConnectionEventType.CONNECTION_EVENT_UPDATE => ConnectionChange.moved(
                  event.id,
                  event.uplinkDelta.toInt(),
                  event.downlinkDelta.toInt(),
                ),
                ConnectionEventType.CONNECTION_EVENT_CLOSED => ConnectionChange.closed(event.id),
                // a new connection without its description says nothing to show
                _ => ConnectionChange.closed(event.id),
              },
          ], reset: batch.reset),
        );
  }

  static ConnectionInfo _connection(Connection connection) => ConnectionInfo(
    id: connection.id,
    network: connection.network,
    destination: connection.destination,
    domain: connection.domain,
    protocol: connection.protocol,
    process: connection.hasProcessInfo() ? connection.processInfo.processPath : '',
    rule: connection.rule,
    outbound: connection.outbound,
    outboundType: connection.outboundType,
    createdAt: DateTime.fromMillisecondsSinceEpoch(connection.createdAt.toInt()),
    up: connection.uplinkTotal.toInt(),
    down: connection.downlinkTotal.toInt(),
  );

  @override
  Future<void> closeConnection(String id) => _started.closeConnection(CloseConnectionRequest(id: id), options: _short);

  @override
  Future<void> closeAllConnections() => _started.closeAllConnections(Empty(), options: _short);

  @override
  Future<List<ReportEntry>> reports() async {
    final crashes = await _desktop.listCrashReports(Empty(), options: _short);
    final memory = await _desktop.listOOMReports(Empty(), options: _short);
    return [
      for (final report in crashes.reports)
        ReportEntry(
          ReportKind.crash,
          report.name,
          DateTime.fromMillisecondsSinceEpoch(report.crashedAt.toInt()),
          read: report.isRead,
        ),
      for (final report in memory.reports)
        ReportEntry(
          ReportKind.outOfMemory,
          report.name,
          DateTime.fromMillisecondsSinceEpoch(report.recordedAt.toInt()),
          read: report.isRead,
        ),
    ]..sort((a, b) => b.at.compareTo(a.at));
  }

  @override
  Future<ReportArchive> exportReport(ReportEntry report) async {
    final archive = switch (report.kind) {
      ReportKind.crash => await _desktop.exportCrashReport(
        CrashReportExportRequest(name: report.name, withLog: true, withConfiguration: false),
        options: _long,
      ),
      ReportKind.outOfMemory => await _desktop.exportOOMReport(
        OOMReportExportRequest(name: report.name, withLog: true, withConfiguration: false),
        options: _long,
      ),
    };
    return ReportArchive(archive.fileName, archive.data);
  }

  @override
  Future<void> markReportRead(ReportEntry report) => switch (report.kind) {
    ReportKind.crash => _desktop.markCrashReportRead(CrashReportRequest(name: report.name), options: _short),
    ReportKind.outOfMemory => _desktop.markOOMReportRead(OOMReportRequest(name: report.name), options: _short),
  };

  @override
  Stream<StunProgress> stunTest(String server) => _started
      .startSTUNTest(STUNTestRequest(server: server))
      .map(
        (progress) => StunProgress(
          externalAddress: progress.externalAddr,
          latencyMs: progress.latencyMs,
          mapping: natMapping(progress.natMapping),
          filtering: natFiltering(progress.natFiltering),
          natTypeSupported: progress.natTypeSupported,
          isFinal: progress.isFinal,
          error: progress.error,
        ),
      );

  /// The core's numbers (common/stun): the mapping keeps 1 reserved, the filtering does not.
  static NatBehaviour natMapping(int value) => switch (value) {
    2 => NatBehaviour.endpointIndependent,
    3 => NatBehaviour.addressDependent,
    4 => NatBehaviour.addressAndPortDependent,
    _ => NatBehaviour.unknown,
  };

  static NatBehaviour natFiltering(int value) => switch (value) {
    1 => NatBehaviour.endpointIndependent,
    2 => NatBehaviour.addressDependent,
    3 => NatBehaviour.addressAndPortDependent,
    _ => NatBehaviour.unknown,
  };

  @override
  Future<NikoInfo> nikoInfo() async {
    final info = await _niko.getInfo(niko.GetInfoRequest(), options: _short);
    return NikoInfo(
      nikoVersion: info.nikoVersion,
      coreVersion: info.coreVersion,
      storeSchema: info.storeSchema,
      api: info.api,
    );
  }

  // a resolve asks the core's DNS: its rules' servers, up to the tester's own 20 s
  static final _routeTest = CallOptions(timeout: const Duration(seconds: 25));

  @override
  Future<RouteVerdict> routeTest(
    String host,
    int port, {
    String network = 'tcp',
    String inbound = '',
    bool resolve = false,
  }) async {
    final r = await _niko.routeTest(
      niko.RouteTestRequest(host: host, port: port, network: network, inbound: inbound, resolve: resolve),
      options: _routeTest,
    );
    return RouteVerdict(
      index: r.index,
      rule: r.rule,
      action: r.action,
      outbound: r.outbound,
      error: r.error,
      destination: r.destination,
      fakeIp: r.fakeIp,
      resolved: List.unmodifiable(r.resolved),
      notes: List.unmodifiable(r.notes),
      steps: List.unmodifiable(
        r.steps.map(
          (s) => RouteStep(
            index: s.index,
            rule: s.rule,
            action: s.action,
            resolved: List.unmodifiable(s.resolved),
            note: s.note,
          ),
        ),
      ),
    );
  }

  @override
  Stream<StoreChange> storeChanges() => _niko
      .watch(niko.WatchRequest())
      .map(
        (change) => StoreChange(
          revision: change.revision.toInt(),
          reset: change.reset,
          kind: switch (change.kind) {
            niko.Change_Kind.PROFILES => StoreKind.profiles,
            niko.Change_Kind.GROUPS => StoreKind.groups,
            niko.Change_Kind.SELECTED => StoreKind.selected,
            niko.Change_Kind.TASK => StoreKind.task,
            _ => StoreKind.other,
          },
          ids: List.unmodifiable(change.ids.map((id) => id.toInt())),
        ),
      );

  @override
  Future<List<ProfileRow>> profiles({int groupId = 0}) async {
    final list = await _niko.listProfiles(niko.ListProfilesRequest(groupId: Int64(groupId)), options: _short);
    return List.unmodifiable(
      list.profiles.map(
        (p) => ProfileRow(
          id: p.id.toInt(),
          groupId: p.groupId.toInt(),
          order: p.order.toInt(),
          type: p.type,
          name: p.name,
          address: p.address,
        ),
      ),
    );
  }

  @override
  Future<List<GroupRow>> groups() async {
    final list = await _niko.listGroups(niko.Empty(), options: _short);
    return List.unmodifiable(
      list.groups.map(
        (g) => GroupRow(
          id: g.id.toInt(),
          name: g.name,
          ungrouped: g.ungrouped,
          subscription: g.type == niko.Group_Type.SUBSCRIPTION,
          profiles: g.profiles,
        ),
      ),
    );
  }

  @override
  Future<int> selected() async => (await _niko.getSelected(niko.Empty(), options: _short)).id.toInt();

  @override
  Future<void> select(int profileId) => _niko.select(niko.ProfileId(id: Int64(profileId)), options: _short);

  ProfileRow _row(niko.Profile p) => ProfileRow(
        id: p.id.toInt(),
        groupId: p.groupId.toInt(),
        order: p.order.toInt(),
        type: p.type,
        name: p.name,
        address: p.address,
      );

  @override
  Future<ImportPreview> recognize(String text) async {
    final preview = await _niko.recognize(niko.ImportInput(text: text), options: _short);
    return ImportPreview(
      kind: switch (preview.kind) {
        niko.ImportPreview_Kind.PROFILES => ImportKind.profiles,
        niko.ImportPreview_Kind.SUBSCRIPTION => ImportKind.subscription,
        _ => ImportKind.nothing,
      },
      profiles: List.unmodifiable(preview.profiles.map(_row)),
      subscription: preview.subscription,
    );
  }

  @override
  Future<SavedProfile> profile(int id) async {
    final p = await _niko.getProfile(niko.ProfileId(id: Int64(id)), options: _short);
    return SavedProfile(row: _row(p), beanJson: p.beanJson);
  }

  @override
  Future<int> addProfile({required int groupId, required String type, required String beanJson}) async =>
      (await _niko.saveProfile(niko.Profile(groupId: Int64(groupId), type: type, beanJson: beanJson), options: _short))
          .id
          .toInt();

  @override
  Future<void> deleteProfiles(List<int> ids) =>
      _niko.deleteProfiles(niko.ProfileIds(ids: ids.map(Int64.new)), options: _short);

  @override
  Future<void> moveProfiles(List<int> ids, {required int groupId, int before = 0}) => _niko.moveProfiles(
        niko.MoveRequest(ids: ids.map(Int64.new), groupId: Int64(groupId), before: Int64(before)),
        options: _short,
      );

  @override
  Future<List<int>> importLinks(String text, {int groupId = 0}) async {
    final request = niko.ImportRequest(
      input: niko.ImportInput(text: text),
      groupId: Int64(groupId),
    );
    // the task's steps, then its result: the last, done
    niko.TaskProgress? result;
    await for (final progress in _niko.import(request, options: _long)) {
      if (progress.done) result = progress;
    }
    if (result == null) throw GrpcError.unknown('the import ended without its result');
    if (result.code != StatusCode.ok) throw GrpcError.custom(result.code, result.error);
    return List.unmodifiable(result.ids.map((id) => id.toInt()));
  }

  @override
  Future<void> close() => _channel.shutdown();
}

final _terminalColours = RegExp('\x1b\\[[0-9;]*m');

/// A log line without the colour codes the core writes for a terminal.
String plainLogText(String line) => line.replaceAll(_terminalColours, '');
