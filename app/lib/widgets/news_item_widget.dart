import 'package:flutter/material.dart';
import '../models/news_item.dart';
import '../utils/formatters.dart';

class NewsItemWidget extends StatelessWidget {
  final NewsItem newsItem;
  final VoidCallback? onTap;

  const NewsItemWidget({
    super.key,
    required this.newsItem,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 6),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                newsItem.headline,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 8),
              if (newsItem.summary.isNotEmpty)
                Text(
                  newsItem.summary,
                  style: TextStyle(
                    fontSize: 13,
                    color: Colors.grey[700],
                  ),
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                ),
              const SizedBox(height: 10),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: _getSentimentColor(newsItem.sentiment).withOpacity(0.15),
                      borderRadius: BorderRadius.circular(6)
                    ),
                    child: Text(
                      newsItem.sentiment.toUpperCase(),
                      style: TextStyle(
                        color: _getSentimentColor(newsItem.sentiment),
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  Flexible(
                    child: Text(
                      '${newsItem.source ?? 'Unknown'} • ${Formatters.formatDateShort(newsItem.timestamp)}',
                      style: TextStyle(
                        fontSize: 10,
                        color: Colors.grey[600],
                      ),
                      textAlign: TextAlign.right,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Color _getSentimentColor(String sentiment) {
    switch (sentiment.toUpperCase()) {
      case 'BULLISH':
      case 'POSITIVE':
        return Colors.green;
      case 'BEARISH':
      case 'NEGATIVE':
        return Colors.red;
      case 'NEUTRAL':
      default:
        return Colors.orange;
    }
  }
}