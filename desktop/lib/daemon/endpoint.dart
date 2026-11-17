/// Where the daemon listens in development: `boxdd run --listen 127.0.0.1:<port>`, gRPC over TCP with no check of
/// the client at all (docs/desktop/README.md, «Что решить», п. 1). So the address is 127.0.0.1 and nothing else: not
/// a name, not another address of this machine, not another machine.
class DevEndpoint {
  const DevEndpoint._(this.port);

  static const host = '127.0.0.1';

  final int port;

  /// `127.0.0.1:<port>` with a port an ordinary user can listen on; null for anything else.
  static DevEndpoint? parse(String text) {
    final match = RegExp(r'^127\.0\.0\.1:([1-9][0-9]{3,4})$').firstMatch(text);
    if (match == null) return null;
    final port = int.parse(match.group(1)!);
    if (port < 1024 || port > 65535) return null;
    return DevEndpoint._(port);
  }

  @override
  String toString() => '$host:$port';
}
