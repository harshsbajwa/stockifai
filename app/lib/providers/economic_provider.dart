import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/economic_indicator.dart';
import '../services/api_service.dart';

class EconomicProvider extends ChangeNotifier {
  final ApiService _apiService = ApiService();
  Timer? _pollingTimer;

  List<EconomicIndicator> _indicators = [];
  bool _isLoading = false;
  String? _error;

  List<EconomicIndicator> get indicators => _indicators;
  bool get isLoading => _isLoading;
  String? get error => _error;

  EconomicProvider() {
    _startPolling();
  }

  void _startPolling() {
    fetchIndicators();
    _pollingTimer = Timer.periodic(const Duration(minutes: 5), (_) {
      fetchIndicators();
    });
  }

  Future<void> fetchIndicators() async {
    _setLoading(true);
    _setError(null);

    try {
      final response = await _apiService.getAllIndicatorSummaries(size: 10);
      _indicators = response.data;
      notifyListeners();
    } catch (e) {
      _setError(e.toString());
    } finally {
      _setLoading(false);
    }
  }

  Future<void> refresh() async {
    await fetchIndicators();
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