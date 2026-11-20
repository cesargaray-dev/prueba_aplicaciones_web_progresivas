import 'dart:typed_data';

import 'receipt_downloader_stub.dart'
    if (dart.library.js_interop) 'receipt_downloader_web.dart' as impl;

Future<String> savePdfReceipt(Uint8List bytes, String fileName) {
  return impl.savePdfReceipt(bytes, fileName);
}
