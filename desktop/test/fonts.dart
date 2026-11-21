import 'dart:io';

import 'package:flutter/services.dart';

/// The window's fonts for the pictures: the tests draw with Geist, as the window does.
Future<void> loadFonts() async {
  for (final MapEntry(key: family, value: files) in {
    'Geist': ['Geist-Regular', 'Geist-Medium', 'Geist-SemiBold'],
    'Geist Mono': ['GeistMono-Regular', 'GeistMono-Medium'],
  }.entries) {
    final loader = FontLoader(family);
    for (final file in files) {
      loader.addFont(File('fonts/$file.ttf').readAsBytes().then((bytes) => ByteData.sublistView(bytes)));
    }
    await loader.load();
  }
}
