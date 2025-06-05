import 'package:equatable/equatable.dart';

class StockData extends Equatable {
  final String symbol;
  final double currentPrice;
  final int? volume;
  final double? volatility;
  final double? priceChange;
  final double? priceChangePercent;
  final double? volumeAverage;
  final double? riskScore;
  final String? trend;
  final double? support;
  final double? resistance;
  final DateTime timestamp;

  const StockData({
    required this.symbol,
    required this.currentPrice,
    this.volume,
    this.volatility,
    this.priceChange,
    this.priceChangePercent,
    this.volumeAverage,
    this.riskScore,
    this.trend,
    this.support,
    this.resistance,
    required this.timestamp,
  });

  factory StockData.fromJson(Map<String, dynamic> json) {
    return StockData(
      symbol: json['symbol'] ?? '',
      currentPrice: (json['currentPrice'] ?? 0.0).toDouble(),
      volume: json['volume']?.toInt(),
      volatility: json['volatility']?.toDouble(),
      priceChange: json['priceChange']?.toDouble(),
      priceChangePercent: json['priceChangePercent']?.toDouble(),
      volumeAverage: json['volumeAverage']?.toDouble(),
      riskScore: json['riskScore']?.toDouble(),
      trend: json['trend'],
      support: json['support']?.toDouble(),
      resistance: json['resistance']?.toDouble(),
      timestamp: DateTime.parse(json['timestamp']),
    );
  }

  @override
  List<Object?> get props => [
        symbol,
        currentPrice,
        volume,
        volatility,
        priceChange,
        priceChangePercent,
        volumeAverage,
        riskScore,
        trend,
        support,
        resistance,
        timestamp,
      ];
}

class StockMetrics extends Equatable {
  final String symbol;
  final List<MetricPoint> metrics;
  final TimeRange timeRange;
  final String aggregation;

  const StockMetrics({
    required this.symbol,
    required this.metrics,
    required this.timeRange,
    required this.aggregation,
  });

  factory StockMetrics.fromJson(Map<String, dynamic> json) {
    return StockMetrics(
      symbol: json['symbol'] ?? '',
      metrics: (json['metrics'] as List? ?? [])
          .map((item) => MetricPoint.fromJson(item))
          .toList(),
      timeRange: TimeRange.fromJson(json['timeRange'] ?? {}),
      aggregation: json['aggregation'] ?? '',
    );
  }

  @override
  List<Object?> get props => [symbol, metrics, timeRange, aggregation];
}

class MetricPoint extends Equatable {
  final DateTime timestamp;
  final double? price;
  final double? open;
  final double? high;
  final double? low;
  final int? volume;
  final double? volatility;
  final double? riskScore;

  const MetricPoint({
    required this.timestamp,
    this.price,
    this.open,
    this.high,
    this.low,
    this.volume,
    this.volatility,
    this.riskScore,
  });

  factory MetricPoint.fromJson(Map<String, dynamic> json) {
    return MetricPoint(
      timestamp: DateTime.parse(json['timestamp']),
      price: json['price']?.toDouble(),
      open: json['open']?.toDouble(),
      high: json['high']?.toDouble(),
      low: json['low']?.toDouble(),
      volume: json['volume']?.toInt(),
      volatility: json['volatility']?.toDouble(),
      riskScore: json['riskScore']?.toDouble(),
    );
  }

  @override
  List<Object?> get props => [
        timestamp,
        price,
        open,
        high,
        low,
        volume,
        volatility,
        riskScore,
      ];
}

class TimeRange extends Equatable {
  final DateTime start;
  final DateTime end;

  const TimeRange({
    required this.start,
    required this.end,
  });

  factory TimeRange.fromJson(Map<String, dynamic> json) {
    return TimeRange(
      start: DateTime.parse(json['start']),
      end: DateTime.parse(json['end']),
    );
  }

  @override
  List<Object?> get props => [start, end];
}