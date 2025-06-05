import 'package:flutter/material.dart';
import '../models/economic_indicator.dart';
import '../utils/formatters.dart';


class EconomicIndicatorWidget extends StatelessWidget {
  final EconomicIndicator indicator;
  final VoidCallback? onTap;

  const EconomicIndicatorWidget({
    super.key,
    required this.indicator,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final latestObservation = indicator.observations.isNotEmpty
        ? indicator.observations.first
        : null;
    final latestValue = latestObservation?.value;
    final latestDate = latestObservation?.date;

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
                indicator.metadata?.title ?? indicator.seriesId,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 8),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    latestValue?.toStringAsFixed(2) ?? 'N/A',
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Colors.black87,
                    ),
                  ),
                  if (indicator.metadata?.units != null)
                    Text(
                      indicator.metadata!.units!,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[700],
                      ),
                    ),
                ],
              ),
              if (latestDate != null) ...[
                const SizedBox(height: 4),
                Text(
                  'As of: ${Formatters.formatDateShort(latestDate)}',
                  style: TextStyle(
                    fontSize: 10,
                    color: Colors.grey[600],
                  ),
                ),
              ]
            ],
          ),
        ),
      ),
    );
  }
}