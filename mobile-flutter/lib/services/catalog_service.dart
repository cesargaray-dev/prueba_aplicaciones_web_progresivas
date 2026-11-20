import '../models/product_item.dart';
import 'api_client.dart';

class CatalogService {
  final ApiClient _client;

  CatalogService({ApiClient? client}) : _client = client ?? ApiClient();

  Future<List<ProductItem>> getProducts() async {
    final response = await _client.get('/api/v1/gestopago/productos');

    List<dynamic> itemsList = [];
    if (response is Map<String, dynamic>) {
      if (response['data'] is List) {
        itemsList = response['data'] as List<dynamic>;
      } else if (response['PRODUCTOS'] is List) {
        itemsList = response['PRODUCTOS'] as List<dynamic>;
      }
    } else if (response is List) {
      itemsList = response;
    }

    return itemsList
        .map((e) => ProductItem.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<ProductItem>> obtenerProductos() => getProducts();
}
