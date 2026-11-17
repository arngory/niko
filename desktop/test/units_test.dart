import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/daemon/endpoint.dart';
import 'package:niko_desktop/ui/format.dart';
import 'package:niko_desktop/ui/icons.dart';

/// The box of the line itself: Path.getBounds takes an arc's control points, which lie outside it.
Rect drawnBounds(Path path) {
  var left = double.infinity, top = double.infinity, right = -double.infinity, bottom = -double.infinity;
  for (final metric in path.computeMetrics()) {
    for (var at = 0.0; at <= metric.length; at += 0.02) {
      final point = metric.getTangentForOffset(at)!.position;
      left = point.dx < left ? point.dx : left;
      top = point.dy < top ? point.dy : top;
      right = point.dx > right ? point.dx : right;
      bottom = point.dy > bottom ? point.dy : bottom;
    }
  }
  return Rect.fromLTRB(left, top, right, bottom);
}

void main() {
  group('the development endpoint', () {
    test('is 127.0.0.1 and a port', () {
      expect(DevEndpoint.parse('127.0.0.1:18080')?.port, 18080);
      expect(DevEndpoint.parse('127.0.0.1:18080').toString(), '127.0.0.1:18080');
    });
    test('is nothing else', () {
      for (final text in [
        '0.0.0.0:18080', ':18080', 'localhost:18080', '127.0.0.2:18080', '[::1]:18080', '192.168.1.5:18080',
        '127.0.0.1', '127.0.0.1:', '127.0.0.1:80', '127.0.0.1:1023', '127.0.0.1:65536', '127.0.0.1:018080',
        '127.0.0.1:18080 ', ' 127.0.0.1:18080', '127.0.0.1:18080/x', 'example.com:443', '',
        '127.0.0.1:18080\n', '127.0.0.1:18080\n0.0.0.0:1', '127.0.0.1:18080@evil.example',
      ]) {
        expect(DevEndpoint.parse(text), isNull, reason: '"$text"');
      }
    });
  });

  group('technical values', () {
    test('rates as the core writes them', () {
      expect(formatRate(0), '0 B/s');
      expect(formatRate(999), '999 B/s');
      expect(formatRate(312 * 1024), '312 KB/s');
      expect(formatRate((4.2 * 1024 * 1024).round()), '4.2 MB/s');
      expect(formatRate(42 * 1024 * 1024), '42 MB/s');
      expect(formatBytes(38 * 1024 * 1024 * 1024), '38 GB');
      expect(formatBytes(-5), '0 B');
    });
    test('the time since the start', () {
      expect(formatElapsed(Duration.zero), '00:00:00');
      expect(formatElapsed(const Duration(minutes: 42, seconds: 17)), '00:42:17');
      expect(formatElapsed(const Duration(hours: 101, minutes: 2, seconds: 3)), '101:02:03');
      expect(formatElapsed(const Duration(seconds: -4)), '00:00:00');
    });
  });

  group('icons', () {
    test('every icon is path data this reads, inside the 24 grid', () {
      for (final data in [NikoIcons.power, NikoIcons.alert, NikoIcons.refresh]) {
        final bounds = drawnBounds(parseSvgPath(data));
        expect(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= 24 && bounds.bottom <= 24, isTrue,
            reason: '$data: $bounds');
        expect(bounds.width > 8 || bounds.height > 8, isTrue, reason: data);
      }
    });
    test('the power icon: a stem and an open arc around it', () {
      final bounds = drawnBounds(parseSvgPath(NikoIcons.power));
      // the arc of radius 9 about (12, 13): from 3 to 21 across, down to 22
      expect(bounds.left, closeTo(3, 0.05));
      expect(bounds.right, closeTo(21, 0.05));
      expect(bounds.top, closeTo(2, 0.05));
      expect(bounds.bottom, closeTo(22, 0.05));
    });
    test('arc flags written together, and a command this does not know', () {
      expect(drawnBounds(parseSvgPath('M2 12a10 10 0 1020 0')).right, closeTo(22, 0.05));
      expect(() => parseSvgPath('M0 0q1 1 2 2'), throwsFormatException);
      expect(() => parseSvgPath('1 2 3'), throwsFormatException);
    });
  });
}
