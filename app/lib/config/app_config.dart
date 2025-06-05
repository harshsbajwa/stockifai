import 'package:flutter/foundation.dart' show kIsWeb;
import 'dart:io' show Platform;

class AppConfig {
  static String get baseUrl {
    const String envApiBaseUrl = String.fromEnvironment('API_BASE_URL');

    if (envApiBaseUrl.isNotEmpty) {
      return envApiBaseUrl;
    }

    if (kIsWeb) {
      return 'http://localhost:8000';
    } else if (Platform.isAndroid) {
      return 'http://10.0.2.2:8000';
    } else if (Platform.isIOS) {
      return 'http://localhost:8000';
    }
    return 'http://localhost:8000';
  }

  static const Duration requestTimeout = Duration(seconds: 15);
  static const Duration pollingInterval = Duration(seconds: 30);
  static const Duration newsPollingInterval = Duration(minutes: 15);
}
