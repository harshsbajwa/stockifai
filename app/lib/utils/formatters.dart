import 'package:intl/intl.dart';

class Formatters {
  static String formatCurrency(double value, {String currency = 'USD'}) {
    final formatter = NumberFormat.currency(
      symbol: '\$',
      decimalDigits: 2,
    );
    return formatter.format(value);
  }

  static String formatPercentage(double value, {int decimals = 2}) {
    final sign = value >= 0 ? '+' : '';
    return '$sign${value.toStringAsFixed(decimals)}%';
  }

  static String formatNumber(double value, {int decimals = 0}) {
    return value.toStringAsFixed(decimals);
  }

  static String formatVolume(int? volume) {
    if (volume == null) return 'N/A';
    
    if (volume >= 1000000000) {
      return '${(volume / 1000000000).toStringAsFixed(1)}B';
    } else if (volume >= 1000000) {
      return '${(volume / 1000000).toStringAsFixed(1)}M';
    } else if (volume >= 1000) {
      return '${(volume / 1000).toStringAsFixed(1)}K';
    }
    return volume.toString();
  }

  static String formatDate(DateTime date) {
    return DateFormat('MMM dd, yyyy HH:mm').format(date);
  }

  static String formatDateShort(DateTime date) {
    return DateFormat('MMM dd HH:mm').format(date);
  }

  static String formatTimeShort(DateTime date) {
    return DateFormat('HH:mm').format(date);
  }
}