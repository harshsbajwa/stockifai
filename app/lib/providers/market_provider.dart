import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/market_overview.dart';
import '../services/api_service.dart';

class MarketProvider extends ChangeNotifier {
  final ApiService _apiService = ApiService();
  Timer? _pollingTimer;

  MarketOverview? _marketOverview;
  bool _isLoading = false;
  String? _error;

  MarketOverview? get marketOverview => _marketOverview;
  bool get isLoading => _isLoading;
  String? get error => _error;

  MarketProvider() {
    _startPolling();
  }

  void _startPolling() {
    fetchMarketOverview();
    _pollingTimer = Timer.periodic(const Duration(minutes: 5), (_) {
      fetchMarketOverview();
    });
  }

  Future<void> fetchMarketOverview() async {
    _setLoading(true);
    _setError(null);

    try {
      final overview = await _apiService.getMarketOverview();
      _marketOverview = overview;
      notifyListeners();
    } catch (e) {
      _setError(e.toString());
    } finally {
      _setLoading(false);
    }
  }

  Future<void> refresh() async {
    await fetchMarketOverview();
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