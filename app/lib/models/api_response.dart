import 'package:equatable/equatable.dart';

class ApiResponse<T> extends Equatable {
  final bool success;
  final T? data;
  final String? message;
  final DateTime timestamp;
  final List<String>? errors;

  const ApiResponse({
    required this.success,
    this.data,
    this.message,
    required this.timestamp,
    this.errors,
  });

  factory ApiResponse.fromJson(
    Map<String, dynamic> json,
    T Function(dynamic)? fromJsonT,
  ) {
    return ApiResponse<T>(
      success: json['success'] ?? false,
      data: json['data'] != null && fromJsonT != null 
          ? fromJsonT(json['data']) 
          : json['data'],
      message: json['message'],
      timestamp: DateTime.parse(json['timestamp']),
      errors: json['errors'] != null 
          ? List<String>.from(json['errors']) 
          : null,
    );
  }

  @override
  List<Object?> get props => [success, data, message, timestamp, errors];
}

class PaginatedResponse<T> extends Equatable {
  final List<T> data;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool hasNext;
  final bool hasPrevious;

  const PaginatedResponse({
    required this.data,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.hasNext,
    required this.hasPrevious,
  });

  factory PaginatedResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic>) fromJsonT,
  ) {
    return PaginatedResponse<T>(
      data: (json['data'] as List)
          .map((item) => fromJsonT(item as Map<String, dynamic>))
          .toList(),
      page: json['page'] ?? 0,
      size: json['size'] ?? 0,
      totalElements: json['totalElements'] ?? 0,
      totalPages: json['totalPages'] ?? 0,
      hasNext: json['hasNext'] ?? false,
      hasPrevious: json['hasPrevious'] ?? false,
    );
  }

  @override
  List<Object?> get props => [
        data,
        page,
        size,
        totalElements,
        totalPages,
        hasNext,
        hasPrevious,
      ];
}