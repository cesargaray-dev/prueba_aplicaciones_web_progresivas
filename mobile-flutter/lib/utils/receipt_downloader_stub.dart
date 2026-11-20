import 'dart:io';
import 'dart:typed_data';

Future<String> savePdfReceipt(Uint8List bytes, String fileName) async {
  final safeName = fileName.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_');
  final file = File('${Directory.systemTemp.path}${Platform.pathSeparator}$safeName');
  await file.writeAsBytes(bytes, flush: true);
  return file.path;
}
