import 'dart:async';
import 'package:flutter/foundation.dart';
import '../config/app_config.dart';
import '../models/news_item.dart';
import '../services/api_service.dart';

class NewsProvider extends ChangeNotifier {
  final ApiService _apiService = ApiService();
  Timer? _pollingTimer;

  List<NewsItem> _news = [];
  bool _isLoading = false;
  String? _error;

  List<NewsItem> get news => _news;
  bool get isLoading => _isLoading;
  String? get error => _error;

  NewsProvider() {
    _startPolling();
  }

  void _startPolling() {
    fetchNews();
    _pollingTimer = Timer.periodic(AppConfig.newsPollingInterval, (_) {
      fetchNews();
    });
  }

  Future<void> fetchNews() async {
    _setLoading(true);
    _setError(null);

    try {
      final news = await _apiService.getRecentNews(hours: 48, limit: 15);
      _news = news;
      notifyListeners();
    } catch (e) {
      _setError(e.toString());
    } finally {
      _setLoading(false);
    }
  }

  Future<void> refresh() async {
    await fetchNews();
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