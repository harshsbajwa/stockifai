import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
// For navigation
import '../providers/economic_provider.dart';
import '../widgets/economic_indicator_widget.dart';


class EconomicScreen extends StatefulWidget {
  const EconomicScreen({super.key});

  @override
  State<EconomicScreen> createState() => _EconomicScreenState();
}

class _EconomicScreenState extends State<EconomicScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (context.read<EconomicProvider>().indicators.isEmpty && !context.read<EconomicProvider>().isLoading) {
        context.read<EconomicProvider>().fetchIndicators();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Consumer<EconomicProvider>(
        builder: (context, provider, child) {
          if (provider.isLoading && provider.indicators.isEmpty) {
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
                      onPressed: provider.fetchIndicators,
                      child: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            );
          }

          if (provider.indicators.isEmpty) {
            return const Center(
              child: Text('No economic indicators available'),
            );
          }

          return RefreshIndicator(
            onRefresh: provider.refresh,
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: provider.indicators.length,
              itemBuilder: (context, index) {
                final indicator = provider.indicators[index];
                return EconomicIndicatorWidget(
                  indicator: indicator,
                  onTap: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Tapped on ${indicator.metadata?.title ?? indicator.seriesId}')),
                    );
                  },
                );
              },
            ),
          );
        },
      ),
    );
  }
}