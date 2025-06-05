import 'package:equatable/equatable.dart';

class MarketOverview extends Equatable {
  final int totalStocks;
  final int activeStocks;
  final double averageRiskScore;
  final List<String> highRiskStocks;
  final TopMovers topMovers;
  final String marketSentiment;
  final DateTime lastUpdated;

  const MarketOverview({
    required this.totalStocks,
    required this.activeStocks,
    required this.averageRiskScore,
    required this.highRiskStocks,
    required this.topMovers,
    required this.marketSentiment,
    required this.lastUpdated,
  });

  factory MarketOverview.fromJson(Map<String, dynamic> json) {
    return MarketOverview(
      totalStocks: json['totalStocks'] ?? 0,
      activeStocks: json['activeStocks'] ?? 0,
      averageRiskScore: (json['averageRiskScore'] ?? 0.0).toDouble(),
      highRiskStocks: List<String>.from(json['highRiskStocks'] ?? []),
      topMovers: TopMovers.fromJson(json['topMovers'] ?? {}),
      marketSentiment: json['marketSentiment'] ?? 'UNKNOWN',
      lastUpdated: DateTime.parse(json['lastUpdated']),
    );
  }

  @override
  List<Object?> get props => [
        totalStocks,
        activeStocks,
        averageRiskScore,
        highRiskStocks,
        topMovers,
        marketSentiment,
        lastUpdated,
      ];
}

class TopMovers extends Equatable {
  final List<StockMover> gainers;
  final List<StockMover> losers;
  final List<StockMover> mostVolatile;

  const TopMovers({
    required this.gainers,
    required this.losers,
    required this.mostVolatile,
  });

  factory TopMovers.fromJson(Map<String, dynamic> json) {
    return TopMovers(
      gainers: (json['gainers'] as List? ?? [])
          .map((item) => StockMover.fromJson(item))
          .toList(),
      losers: (json['losers'] as List? ?? [])
          .map((item) => StockMover.fromJson(item))
          .toList(),
      mostVolatile: (json['mostVolatile'] as List? ?? [])
          .map((item) => StockMover.fromJson(item))
          .toList(),
    );
  }

  @override
  List<Object?> get props => [gainers, losers, mostVolatile];
}

class StockMover extends Equatable {
  final String symbol;
  final double? currentPrice;
  final double? change;
  final double? changePercent;
  final int? volume;

  const StockMover({
    required this.symbol,
    this.currentPrice,
    this.change,
    this.changePercent,
    this.volume,
  });

  factory StockMover.fromJson(Map<String, dynamic> json) {
    return StockMover(
      symbol: json['symbol'] ?? '',
      currentPrice: json['currentPrice']?.toDouble(),
      change: json['change']?.toDouble(),
      changePercent: json['changePercent']?.toDouble(),
      volume: json['volume']?.toInt(),
    );
  }

  @override
  List<Object?> get props => [symbol, currentPrice, change, changePercent, volume];
}