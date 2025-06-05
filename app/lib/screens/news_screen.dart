import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/news_provider.dart';
import '../widgets/news_item_widget.dart';


class NewsScreen extends StatefulWidget {
  const NewsScreen({super.key});

  @override
  State<NewsScreen> createState() => _NewsScreenState();
}

class _NewsScreenState extends State<NewsScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (context.read<NewsProvider>().news.isEmpty && !context.read<NewsProvider>().isLoading) {
        context.read<NewsProvider>().fetchNews();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Consumer<NewsProvider>(
        builder: (context, provider, child) {
          if (provider.isLoading && provider.news.isEmpty) {
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
                      onPressed: provider.fetchNews,
                      child: const Text('Retry'),
                    ),
                  ],
                ),
              ),
            );
          }

          if (provider.news.isEmpty) {
            return const Center(
              child: Text('No news available'),
            );
          }

          return RefreshIndicator(
            onRefresh: provider.refresh,
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: provider.news.length,
              itemBuilder: (context, index) {
                final newsItem = provider.news[index];
                return NewsItemWidget(
                  newsItem: newsItem,
                  onTap: () {
                     ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Tapped on: ${newsItem.headline}')),
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