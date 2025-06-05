import 'package:dio/dio.dart';
import '../config/app_config.dart';
import '../models/api_response.dart';
import '../models/stock_data.dart';
import '../models/market_overview.dart';
import '../models/news_item.dart';
import '../models/economic_indicator.dart';

class ApiService {
  late final Dio _dio;

  ApiService() {
    _dio = Dio(BaseOptions(
      baseUrl: AppConfig.baseUrl,
      connectTimeout: AppConfig.requestTimeout,
      receiveTimeout: AppConfig.requestTimeout,
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    ));

    _dio.interceptors.add(LogInterceptor(
      requestBody: false,
      responseBody: false,
      logPrint: (obj) => print('[API] $obj'),
    ));
  }

  // Stock API
  Future<StockData> getStockSummary(String symbol) async {
    try {
      final response = await _dio.get('/api/v1/stocks/$symbol');
      final apiResponse = ApiResponse.fromJson(
        response.data,
        (data) => StockData.fromJson(data),
      );
      
      if (!apiResponse.success || apiResponse.data == null) {
        throw Exception(apiResponse.message ?? 'Failed to fetch stock data');
      }
      
      return apiResponse.data!;
    } catch (e) {
      throw _handleError(e);
    }
  }

  Future<PaginatedResponse<StockData>> getAllStockSummaries({
    int page = 0,
    int size = 50,
  }) async {
    try {
      final response = await _dio.get('/api/v1/stocks', queryParameters: {
        'page': page,
        'size': size,
      });
      
      final apiResponse = ApiResponse.fromJson(
        response.data,
        (data) => PaginatedResponse.fromJson(data, StockData.fromJson),
      );
      
      if (!apiResponse.success || apiResponse.data == null) {
        throw Exception(apiResponse.message ?? 'Failed to fetch stocks');
      }
      
      return apiResponse.data!;
    } catch (e) {
      throw _handleError(e);
    }
  }

  Future<StockMetrics> getStockTimeSeries(
    String symbol, {
    int hours = 24,
    String aggregation = '5m',
  }) async {
    try {
      final response = await _dio.get('/api/v1/stocks/$symbol/timeseries', 
        queryParameters: {
          'hours': hours,
          'aggregation': aggregation,
        });
      
      final apiResponse = ApiResponse.fromJson(
        response.data,
        (data) => StockMetrics.fromJson(data),
      );
      
      if (!apiResponse.success || apiResponse.data == null) {
        throw Exception(apiResponse.message ?? 'Failed to fetch time series');
      }
      
      return apiResponse.data!;
    } catch (e) {
      throw _handleError(e);
    }
  }

  // Market API
  Future<MarketOverview> getMarketOverview() async {
    try {
      final response = await _dio.get('/api/v1/market/overview');
      final apiResponse = ApiResponse.fromJson(
        response.data,
        (data) => MarketOverview.fromJson(data),
      );
      
      if (!apiResponse.success || apiResponse.data == null) {
        throw Exception(apiResponse.message ?? 'Failed to fetch market overview');
      }
      
      return apiResponse.data!;
    } catch (e) {
      throw _handleError(e);
    }
  }

  // News API
  Future<List<NewsItem>> getRecentNews({
    int hours = 24,
    int limit = 50,
  }) async {
    try {
      final response = await _dio.get('/api/v1/news', queryParameters: {
        'hours': hours,
        'limit': limit,
      });
      
      final apiResponse = ApiResponse.fromJson(
        response.data,
        (data) => (data as List).map((item) => NewsItem.fromJson(item)).toList(),
      );
      
      if (!apiResponse.success || apiResponse.data == null) {
        throw Exception(apiResponse.message ?? 'Failed to fetch news');
      }
      
      return apiResponse.data!;
    } catch (e) {
      throw _handleError(e);
    }
  }

  // Economic API
  Future<PaginatedResponse<EconomicIndicator>> getAllIndicatorSummaries({
    int page = 0,
    int size = 20,
  }) async {
    try {
      final response = await _dio.get('/api/v1/economic/indicators', 
        queryParameters: {
          'page': page,
          'size': size,
        });
      
      final apiResponse = ApiResponse.fromJson(
        response.data,
        (data) => PaginatedResponse.fromJson(data, EconomicIndicator.fromJson),
      );
      
      if (!apiResponse.success || apiResponse.data == null) {
        throw Exception(apiResponse.message ?? 'Failed to fetch economic indicators');
      }
      
      return apiResponse.data!;
    } catch (e) {
      throw _handleError(e);
    }
  }

  Exception _handleError(dynamic error) {
    if (error is DioException) {
      switch (error.type) {
        case DioExceptionType.connectionTimeout:
        case DioExceptionType.sendTimeout:
        case DioExceptionType.receiveTimeout:
          return Exception('Connection timeout. Please try again.');
        case DioExceptionType.connectionError:
          return Exception('No internet connection.');
        case DioExceptionType.badResponse:
          final message = error.response?.data?['message'] ?? 
                         'Server error ${error.response?.statusCode}';
          return Exception(message);
        default:
          return Exception('Network error occurred.');
      }
    }
    return Exception(error.toString());
  }
}