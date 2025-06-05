import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:fl_chart/fl_chart.dart';
import '../providers/stock_provider.dart';
import '../utils/formatters.dart';

class StockChartModal extends StatefulWidget {
  final String symbol;

  const StockChartModal({
    super.key,
    required this.symbol,
  });

  @override
  State<StockChartModal> createState() => _StockChartModalState();
}

class _StockChartModalState extends State<StockChartModal> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<StockProvider>().selectStock(widget.symbol);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: Container(
        width: MediaQuery.of(context).size.width * 0.9,
        height: MediaQuery.of(context).size.height * 0.7,
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  widget.symbol,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () {
                    context.read<StockProvider>().clearSelection();
                    Navigator.of(context).pop();
                  },
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Chart Content
            Expanded(
              child: Consumer<StockProvider>(
                builder: (context, provider, child) {
                  if (provider.isLoadingMetrics) {
                    return const Center(child: CircularProgressIndicator());
                  }

                  if (provider.error != null) {
                    return Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Text('Error: ${provider.error}'),
                          const SizedBox(height: 16),
                          ElevatedButton(
                            onPressed: () => provider.selectStock(widget.symbol),
                            child: const Text('Retry'),
                          ),
                        ],
                      ),
                    );
                  }

                  final metrics = provider.selectedStockMetrics;
                  if (metrics == null || metrics.metrics.isEmpty) {
                    return const Center(
                      child: Text('No chart data available'),
                    );
                  }

                  return _buildChart(metrics);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildChart(stockMetrics) {
    final spots = stockMetrics.metrics
        .where((point) => point.price != null)
        .map<FlSpot>((point) {
          final index = stockMetrics.metrics.indexOf(point).toDouble();
          return FlSpot(index, point.price!);
        })
        .toList();

    if (spots.isEmpty) {
      return const Center(child: Text('No price data available'));
    }

    return LineChart(
      LineChartData(
        gridData: const FlGridData(show: true),
        titlesData: FlTitlesData(
          leftTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              getTitlesWidget: (value, meta) {
                return Text(
                  Formatters.formatCurrency(value),
                  style: const TextStyle(fontSize: 10),
                );
              },
              reservedSize: 60,
            ),
          ),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              getTitlesWidget: (value, meta) {
                final index = value.toInt();
                if (index >= 0 && index < stockMetrics.metrics.length) {
                  return Text(
                    Formatters.formatTimeShort(stockMetrics.metrics[index].timestamp),
                    style: const TextStyle(fontSize: 8),
                  );
                }
                return const Text('');
              },
              reservedSize: 30,
            ),
          ),
          topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
        ),
        borderData: FlBorderData(show: true),
        lineBarsData: [
          LineChartBarData(
            spots: spots,
            isCurved: false,
            color: Colors.blue,
            barWidth: 2,
            dotData: const FlDotData(show: false),
          ),
        ],
      ),
    );
  }
}