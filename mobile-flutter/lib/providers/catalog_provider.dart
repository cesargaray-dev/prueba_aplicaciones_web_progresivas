import 'package:flutter/foundation.dart';
import '../models/product_item.dart';
import '../services/api_client.dart';
import '../services/catalog_service.dart';
import '../utils/validation_utils.dart';

class CatalogProvider with ChangeNotifier {
  final CatalogService _catalogService;

  List<ProductItem> _products = [];
  bool _isLoading = false;
  String? _errorMessage;
  String _searchQuery = '';
  String _selectedCategory = 'Todos';
  String _selectedFrontType = 'ALL'; // 'ALL', '1' (Recargas), '2' (Servicios)

  CatalogProvider({CatalogService? catalogService})
      : _catalogService = catalogService ?? CatalogService();

  List<ProductItem> get products => _products;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  String get searchQuery => _searchQuery;
  String get selectedCategory => _selectedCategory;
  String get selectedFrontType => _selectedFrontType;

  List<ProductItem> get _frontTypeProducts {
    return _products.where((item) {
      if (item.isConsultaSaldo) return false;
      if (_selectedFrontType == '1' && !item.isRecarga) return false;
      if (_selectedFrontType == '2' && item.isRecarga) return false;
      return true;
    }).toList();
  }

  Map<String, int> get categoryCounts {
    final base = _frontTypeProducts;
    final counts = <String, int>{'Todos': base.length};
    for (final item in base) {
      final cat = item.categoryName;
      counts[cat] = (counts[cat] ?? 0) + 1;
    }
    return counts;
  }

  List<String> get availableCategories {
    final cats = _frontTypeProducts.map((e) => e.categoryName).toSet().toList();
    cats.sort();
    return ['Todos', ...cats];
  }

  List<ProductItem> get filteredProducts {
    return _frontTypeProducts.where((item) {
      if (_selectedCategory != 'Todos' && item.categoryName != _selectedCategory) {
        return false;
      }

      if (_searchQuery.trim().isNotEmpty) {
        final q = _searchQuery.toLowerCase().trim();
        final qNorm = ValidationUtils.normalizarBusquedaCatalogo(_searchQuery);
        final matchService = item.service.toLowerCase().contains(q);
        final matchProduct = item.name.toLowerCase().contains(q);
        final matchCat = item.categoryName.toLowerCase().contains(q);
        final matchServiceId = item.serviceId.toLowerCase().contains(q);
        final matchProductId = item.id.toLowerCase().contains(q);
        final matchNorm = qNorm.isNotEmpty &&
            (ValidationUtils.normalizarBusquedaCatalogo(item.service).contains(qNorm) ||
                ValidationUtils.normalizarBusquedaCatalogo(item.name).contains(qNorm) ||
                ValidationUtils.normalizarBusquedaCatalogo(item.categoryName).contains(qNorm));
        if (!matchService &&
            !matchProduct &&
            !matchCat &&
            !matchServiceId &&
            !matchProductId &&
            !matchNorm) {
          return false;
        }
      }

      return true;
    }).toList();
  }

  void setSearchQuery(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  void clearSearch() {
    _searchQuery = '';
    notifyListeners();
  }

  void setCategory(String category) {
    _selectedCategory = category;
    notifyListeners();
  }

  void setFrontType(String frontType) {
    _selectedFrontType = frontType;
    if (_selectedCategory != 'Todos' &&
        !availableCategories.contains(_selectedCategory)) {
      _selectedCategory = 'Todos';
    }
    notifyListeners();
  }

  Future<void> loadProducts() async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final fetched = await _catalogService.obtenerProductos();
      _products = fetched.where((p) => !p.isConsultaSaldo).toList();
      _isLoading = false;
      notifyListeners();
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _isLoading = false;
      notifyListeners();
    } catch (_) {
      _errorMessage = 'No se pudo cargar el catálogo de productos GestoPago.';
      _isLoading = false;
      notifyListeners();
    }
  }

  void reset() {
    _products = [];
    _isLoading = false;
    _errorMessage = null;
    _searchQuery = '';
    _selectedCategory = 'Todos';
    _selectedFrontType = 'ALL';
    notifyListeners();
  }
}
