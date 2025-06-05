import 'package:equatable/equatable.dart';

class EconomicIndicator extends Equatable {
  final String seriesId;
  final List<EconomicObservation> observations;
  final EconomicIndicatorMetadata? metadata;

  const EconomicIndicator({
    required this.seriesId,
    required this.observations,
    this.metadata,
  });

  factory EconomicIndicator.fromJson(Map<String, dynamic> json) {
    return EconomicIndicator(
      seriesId: json['series_id'] ?? json['seriesId'] ?? '',
      observations: (json['observations'] as List? ?? [])
          .map((item) => EconomicObservation.fromJson(item))
          .toList(),
      metadata: json['metadata'] != null
          ? EconomicIndicatorMetadata.fromJson(json['metadata'])
          : null,
    );
  }

  @override
  List<Object?> get props => [seriesId, observations, metadata];
}

class EconomicObservation extends Equatable {
  final DateTime? date;
  final double? value;
  final DateTime? realTimeStart;
  final DateTime? realTimeEnd;

  const EconomicObservation({
    this.date,
    this.value,
    this.realTimeStart,
    this.realTimeEnd,
  });

  factory EconomicObservation.fromJson(Map<String, dynamic> json) {
    return EconomicObservation(
      date: json['date'] != null ? DateTime.parse(json['date']) : null,
      value: json['value']?.toDouble(),
      realTimeStart: json['realTimeStart'] != null
          ? DateTime.parse(json['realTimeStart'])
          : null,
      realTimeEnd: json['realTimeEnd'] != null
          ? DateTime.parse(json['realTimeEnd'])
          : null,
    );
  }

  @override
  List<Object?> get props => [date, value, realTimeStart, realTimeEnd];
}

class EconomicIndicatorMetadata extends Equatable {
  final String seriesId;
  final String? title;
  final String? frequency;
  final String? units;
  final String? notes;
  final String? source;

  const EconomicIndicatorMetadata({
    required this.seriesId,
    this.title,
    this.frequency,
    this.units,
    this.notes,
    this.source,
  });

  factory EconomicIndicatorMetadata.fromJson(Map<String, dynamic> json) {
    return EconomicIndicatorMetadata(
      seriesId: json['series_id'] ?? json['seriesId'] ?? '',
      title: json['title'],
      frequency: json['frequency'],
      units: json['units'],
      notes: json['notes'],
      source: json['source'],
    );
  }

  @override
  List<Object?> get props => [seriesId, title, frequency, units, notes, source];
}

class EconomicDataPoint extends Equatable {
  final String seriesId;
  final double value;
  final DateTime timestamp;

  const EconomicDataPoint({
    required this.seriesId,
    required this.value,
    required this.timestamp,
  });

  factory EconomicDataPoint.fromJson(Map<String, dynamic> json) {
    return EconomicDataPoint(
      seriesId: json['seriesId'] ?? json['series_id'] ?? '',
      value: (json['value'] ?? 0.0).toDouble(),
      timestamp: DateTime.parse(json['timestamp']),
    );
  }

  @override
  List<Object?> get props => [seriesId, value, timestamp];
}