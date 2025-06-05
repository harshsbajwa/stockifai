import 'package:flutter/material.dart';
import '../../models/stock_data.dart';
import '../../utils/formatters.dart';

class StockCard extends StatelessWidget {
  final StockData stock;
  final VoidCallback? onTap;

  const StockCard({
    super.key,
    required this.stock,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isPositive = (stock.priceChangePercent ?? 0) >= 0;
    final changeColor = isPositive ? Colors.green : Colors.red;

    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      stock.symbol,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (stock.trend != null && stock.trend!.isNotEmpty)
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 6,
                        vertical: 3,
                      ),
                      decoration: BoxDecoration(
                        color: _getTrendColor(stock.trend).withOpacity(0.15),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        stock.trend!.toUpperCase(),
                        style: TextStyle(
                          color: _getTrendColor(stock.trend),
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    Formatters.formatCurrency(stock.currentPrice),
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Icon(
                        isPositive ? Icons.arrow_upward : Icons.arrow_downward,
                        size: 14,
                        color: changeColor,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        Formatters.formatPercentage(stock.priceChangePercent ?? 0),
                        style: TextStyle(
                          color: changeColor,
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              if (stock.riskScore != null)
                Align(
                  alignment: Alignment.bottomRight,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: _getRiskColor(stock.riskScore!).withOpacity(0.15),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      'Risk: ${stock.riskScore!.toStringAsFixed(0)}',
                      style: TextStyle(
                        color: _getRiskColor(stock.riskScore!),
                        fontSize: 9,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Color _getTrendColor(String? trend) {
    switch (trend?.toUpperCase()) {
      case 'BULLISH':
        return Colors.green;
      case 'BEARISH':
        return Colors.red;
      case 'SIDEWAYS':
      case 'NEUTRAL':
        return Colors.orange;
      default:
        return Colors.grey;
    }
  }

  Color _getRiskColor(double risk) {
    if (risk >= 70) return Colors.red;
    if (risk >= 40) return Colors.orange;
    return Colors.green;
  }
}