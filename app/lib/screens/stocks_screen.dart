import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/stock_provider.dart';
import '../widgets/stock_card.dart';
import '../widgets/stock_chart_modal.dart';


class StocksScreen extends StatefulWidget {
  const StocksScreen({super.key});

  @override
  State<StocksScreen> createState() => _StocksScreenState();
}

class _StocksScreenState extends State<StocksScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (context.read<StockProvider>().stocks.isEmpty && !context.read<StockProvider>().isLoading) {
        context.read<StockProvider>().fetchStocks();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Consumer<StockProvider>(
        builder: (context, provider, child) {
          if (provider.isLoading && provider.stocks.isEmpty) {
            return const Center(child: CircularProgressIndicator());
          }

          if (provider.error != null) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text('Error: ${provider.error}', textAlign: TextAlign.center),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      onPressed: provider.fetchStocks,
                      child: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            );
          }

          if (provider.stocks.isEmpty) {
            return const Center(
              child: Text('No stock data available'),
            );
          }

          return RefreshIndicator(
            onRefresh: provider.refresh,
            child: GridView.builder(
              padding: const EdgeInsets.all(16),
              gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: MediaQuery.of(context).size.width > 600 ? 3 : 2,
                childAspectRatio: 1.25,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
              ),
              itemCount: provider.stocks.length,
              itemBuilder: (context, index) {
                final stock = provider.stocks[index];
                return StockCard(
                  stock: stock,
                  onTap: () => _showStockDetailsOrChart(context, stock.symbol),
                );
              },
            ),
          );
        },
      ),
    );
  }

  void _showStockDetailsOrChart(BuildContext context, String symbol) {
    context.read<StockProvider>().selectStock(symbol);
    showDialog(context: context, builder: (_) => StockChartModal(symbol: symbol));
  }
}