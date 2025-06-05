import 'dart:async';
import 'package:flutter/foundation.dart';
import '../config/app_config.dart';
import '../models/stock_data.dart';
import '../services/api_service.dart';

class StockProvider extends ChangeNotifier {
  final ApiService _apiService = ApiService();
  Timer? _pollingTimer;

  List<StockData> _stocks = [];
  StockData? _selectedStock;
  StockMetrics? _selectedStockMetrics;
  bool _isLoading = false;
  bool _isLoadingMetrics = false;
  String? _error;

  List<StockData> get stocks => _stocks;
  StockData? get selectedStock => _selectedStock;
  StockMetrics? get selectedStockMetrics => _selectedStockMetrics;
  bool get isLoading => _isLoading;
  bool get isLoadingMetrics => _isLoadingMetrics;
  String? get error => _error;

  StockProvider() {
    _startPolling();
  }

  void _startPolling() {
    fetchStocks();
    _pollingTimer = Timer.periodic(AppConfig.pollingInterval, (_) {
      fetchStocks();
    });
  }

  Future<void> fetchStocks() async {
    _setLoading(true);
    _setError(null);

    try {
      final response = await _apiService.getAllStockSummaries(size: 100);
      _stocks = response.data;
      notifyListeners();
    } catch (e) {
      _setError(e.toString());
    } finally {
      _setLoading(false);
    }
  }

  Future<void> selectStock(String symbol) async {
    _isLoadingMetrics = true;
    notifyListeners();

    try {
      // Get stock summary
      final stock = await _apiService.getStockSummary(symbol);
      _selectedStock = stock;

      // Get time series data
      final metrics = await _apiService.getStockTimeSeries(symbol);
      _selectedStockMetrics = metrics;

      notifyListeners();
    } catch (e) {
      _setError(e.toString());
    } finally {
      _isLoadingMetrics = false;
      notifyListeners();
    }
  }

  void clearSelection() {
    _selectedStock = null;
    _selectedStockMetrics = null;
    notifyListeners();
  }

  Future<void> refresh() async {
    await fetchStocks();
    if (_selectedStock != null) {
      await selectStock(_selectedStock!.symbol);
    }
  }

  void _setLoading(bool loading) {
    _isLoading = loading;
    notifyListeners();
  }

  void _setError(String? error) {
    _error = error;
    notifyListeners();
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    super.dispose();
  }
}