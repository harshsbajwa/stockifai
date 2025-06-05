import 'package:equatable/equatable.dart';

class NewsItem extends Equatable {
  final String id;
  final String headline;
  final String summary;
  final String sentiment;
  final DateTime timestamp;
  final String? source;
  final String? relatedSymbol;

  const NewsItem({
    required this.id,
    required this.headline,
    required this.summary,
    required this.sentiment,
    required this.timestamp,
    this.source,
    this.relatedSymbol,
  });

  factory NewsItem.fromJson(Map<String, dynamic> json) {
    return NewsItem(
      id: json['id'] ?? '',
      headline: json['headline'] ?? '',
      summary: json['summary'] ?? '',
      sentiment: json['sentiment'] ?? 'NEUTRAL',
      timestamp: DateTime.parse(json['timestamp']),
      source: json['source'],
      relatedSymbol: json['relatedSymbol'],
    );
  }

  @override
  List<Object?> get props => [
        id,
        headline,
        summary,
        sentiment,
        timestamp,
        source,
        relatedSymbol,
      ];
}