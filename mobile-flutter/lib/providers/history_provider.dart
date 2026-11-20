import 'package:flutter/foundation.dart';
import '../models/history_item.dart';
import '../services/payment_service.dart';

class HistoryProvider extends ChangeNotifier {
  final PaymentService _paymentService;

  List<HistoryItem> _items = [];
  bool _isLoading = false;
  String? _errorMessage;
  String _searchQuery = '';
  String _typeFilter = 'ALL'; // ALL, 'RECARGAS', 'SERVICIOS'

  HistoryProvider({PaymentService? paymentService})
      : _paymentService = paymentService ?? PaymentService();

  List<HistoryItem> get items => _items;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  String get searchQuery => _searchQuery;
  String get typeFilter => _typeFilter;

  List<HistoryItem> get filteredItems {
    return _items.where((item) {
      if (_typeFilter == 'RECARGAS' && !item.isRecarga) {
        return false;
      }
      if (_typeFilter == 'SERVICIOS' && item.isRecarga) {
        return false;
      }

      if (_searchQuery.isNotEmpty) {
        final q = _searchQuery.toLowerCase();
        final matchesService = item.servicio.toLowerCase().contains(q);
        final matchesProduct = item.producto.toLowerCase().contains(q);
        final matchesFolio = item.folioTicket.toLowerCase().contains(q);
        final matchesRef = item.referencia.toLowerCase().contains(q) ||
            item.telefono.toLowerCase().contains(q);

        if (!matchesService && !matchesProduct && !matchesFolio && !matchesRef) {
          return false;
        }
      }

      return true;
    }).toList();
  }

  void setSearchQuery(String q) {
    _searchQuery = q;
    notifyListeners();
  }

  void setTypeFilter(String filter) {
    _typeFilter = filter;
    notifyListeners();
  }

  Future<void> loadHistory(String numeroCuenta) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final response = await _paymentService.getHistorial(numeroCuenta);
      _items = response.pagos;
      _isLoading = false;
      notifyListeners();
    } catch (e) {
      _isLoading = false;
      _errorMessage = 'No se pudo obtener el historial: $e';
      notifyListeners();
    }
  }

  void reset() {
    _items = [];
    _isLoading = false;
    _errorMessage = null;
    _searchQuery = '';
    _typeFilter = 'ALL';
    notifyListeners();
  }
}
