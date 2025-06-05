import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';

import '../providers/stock_provider.dart';
import '../providers/market_provider.dart';
import '../providers/news_provider.dart';
import '../providers/economic_provider.dart';
import '../widgets/stock_card.dart';
import '../widgets/market_overview_card.dart';
import '../widgets/news_item_widget.dart';
import '../widgets/economic_indicator_widget.dart';


class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _fetchAllData();
    });
  }

  Future<void> _fetchAllData() async {
    if (mounted) {
      if (context.read<StockProvider>().stocks.isEmpty && !context.read<StockProvider>().isLoading) {
        context.read<StockProvider>().fetchStocks();
      }
      if (context.read<MarketProvider>().marketOverview == null && !context.read<MarketProvider>().isLoading) {
        context.read<MarketProvider>().fetchMarketOverview();
      }
      if (context.read<NewsProvider>().news.isEmpty && !context.read<NewsProvider>().isLoading) {
        context.read<NewsProvider>().fetchNews();
      }
      if (context.read<EconomicProvider>().indicators.isEmpty && !context.read<EconomicProvider>().isLoading) {
        context.read<EconomicProvider>().fetchIndicators();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[100],
      body: RefreshIndicator(
        onRefresh: _refreshAll,
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildMarketOverview(),
              const SizedBox(height: 24),
              _buildStocksPanel(),
              const SizedBox(height: 24),
              _buildEconomicIndicators(),
              const SizedBox(height: 24),
              _buildNewsPanel(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMarketOverview() {
    return Consumer<MarketProvider>(
      builder: (context, provider, child) {
        if (provider.isLoading && provider.marketOverview == null) {
          return const Card(
            child: Padding(
              padding: EdgeInsets.all(24.0),
              child: Center(child: CircularProgressIndicator()),
            ),
          );
        }

        if (provider.error != null) {
          return Card(
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text('Error: ${provider.error}'),
            ),
          );
        }

        final overview = provider.marketOverview;
        if (overview == null) return const SizedBox.shrink();

        return Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Market Pulse',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: MarketOverviewCard(
                        title: 'Sentiment',
                        value: overview.marketSentiment.toUpperCase(),
                        icon: _getSentimentIcon(overview.marketSentiment),
                        color: _getSentimentColor(overview.marketSentiment),
                        onTap: () => context.go('/'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: MarketOverviewCard(
                        title: 'Avg Risk Score',
                        value: overview.averageRiskScore.toStringAsFixed(1),
                        icon: Icons.shield_outlined,
                        color: _getRiskColor(overview.averageRiskScore),
                        onTap: () => context.go('/'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: MarketOverviewCard(
                        title: 'High Risk Count',
                        value: overview.highRiskStocks.length.toString(),
                        icon: Icons.warning_amber_rounded,
                        color: Colors.red,
                        onTap: () => context.go('/'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildStocksPanel() {
    return Consumer<StockProvider>(
      builder: (context, provider, child) {
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.bar_chart, color: Colors.blue),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        'Monitored Stocks',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        overflow: TextOverflow.ellipsis,
                        maxLines: 1,
                      ),
                    ),
                    TextButton(
                      onPressed: () => context.go('/stocks'),
                      child: const Text('View All'),
                    )
                  ],
                ),
                const SizedBox(height: 16),
                if (provider.isLoading && provider.stocks.isEmpty)
                  const Center(
                    child: Padding(
                      padding: EdgeInsets.all(24.0),
                      child: CircularProgressIndicator(),
                    ),
                  )
                else if (provider.error != null)
                  Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24.0),
                      child: Column(
                        children: [
                          Text('Error: ${provider.error}'),
                          const SizedBox(height: 8),
                          ElevatedButton(
                            onPressed: provider.fetchStocks,
                            child: const Text('Retry'),
                          ),
                        ],
                      ),
                    ),
                  )
                else if (provider.stocks.isEmpty)
                  const Center(
                    child: Padding(
                      padding: EdgeInsets.all(24.0),
                      child: Text('No stock data available'),
                    ),
                  )
                else
                  GridView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: 2,
                      childAspectRatio: 1.3,
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 12,
                    ),
                    itemCount: provider.stocks.take(6).length,
                    itemBuilder: (context, index) {
                      final stock = provider.stocks[index];
                      return StockCard(
                        stock: stock,
                        onTap: () => context.go('/stocks'),
                      );
                    },
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildEconomicIndicators() {
    return Consumer<EconomicProvider>(
      builder: (context, provider, child) {
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.account_balance, color: Colors.green),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        'Economic Indicators',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        overflow: TextOverflow.ellipsis,
                        maxLines: 1,
                      ),
                    ),
                    TextButton(
                      onPressed: () => context.go('/economic'),
                      child: const Text('View All'),
                    )
                  ],
                ),
                const SizedBox(height: 16),
                if (provider.isLoading && provider.indicators.isEmpty)
                  const Center(child: Padding(padding: EdgeInsets.all(24.0), child: CircularProgressIndicator()))
                else if (provider.error != null)
                  Center(child: Padding(padding: EdgeInsets.all(24.0), child: Text('Error: ${provider.error}')))
                else if (provider.indicators.isEmpty)
                  const Center(child: Padding(padding: EdgeInsets.all(24.0), child: Text('No indicators available')))
                else
                  ListView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: provider.indicators.take(3).length,
                    itemBuilder: (context, index) {
                      final indicator = provider.indicators[index];
                      return EconomicIndicatorWidget(
                        indicator: indicator,
                        onTap: () => context.go('/economic'),
                      );
                    },
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildNewsPanel() {
    return Consumer<NewsProvider>(
      builder: (context, provider, child) {
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.article, color: Colors.indigo),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        'Market News',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        overflow: TextOverflow.ellipsis,
                        maxLines: 1,
                      ),
                    ),
                     TextButton(
                      onPressed: () => context.go('/news'),
                      child: const Text('View All'),
                    )
                  ],
                ),
                const SizedBox(height: 16),
                if (provider.isLoading && provider.news.isEmpty)
                  const Center(child: Padding(padding: EdgeInsets.all(24.0), child: CircularProgressIndicator()))
                else if (provider.error != null)
                  Center(child: Padding(padding: EdgeInsets.all(24.0), child: Text('Error: ${provider.error}')))
                else if (provider.news.isEmpty)
                  const Center(child: Padding(padding: EdgeInsets.all(24.0), child:Text('No news available')))
                else
                  ListView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: provider.news.take(3).length,
                    itemBuilder: (context, index) {
                      final newsItem = provider.news[index];
                      return NewsItemWidget(
                        newsItem: newsItem,
                        onTap: () => context.go('/news'),
                      );
                    },
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _refreshAll() async {
    if (!mounted) return;
    await Future.wait([
      context.read<StockProvider>().refresh(),
      context.read<MarketProvider>().refresh(),
      context.read<NewsProvider>().refresh(),
      context.read<EconomicProvider>().refresh(),
    ]);
  }

  IconData _getSentimentIcon(String sentiment) {
    switch (sentiment.toUpperCase()) {
      case 'BULLISH':
        return Icons.trending_up;
      case 'BEARISH':
        return Icons.trending_down;
      default:
        return Icons.remove;
    }
  }

  Color _getSentimentColor(String sentiment) {
    switch (sentiment.toUpperCase()) {
      case 'BULLISH':
        return Colors.green;
      case 'BEARISH':
        return Colors.red;
      default:
        return Colors.orange;
    }
  }

  Color _getRiskColor(double risk) {
    if (risk >= 7) return Colors.red;
    if (risk >= 4) return Colors.orange;
    return Colors.green;
  }
}