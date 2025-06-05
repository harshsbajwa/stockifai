import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';

import 'providers/stock_provider.dart';
import 'providers/market_provider.dart';
import 'providers/news_provider.dart';
import 'providers/economic_provider.dart';
import 'screens/dashboard_screen.dart';
import 'screens/stocks_screen.dart';
import 'screens/economic_screen.dart';
import 'screens/news_screen.dart';
import 'theme/app_theme.dart';


void main() {
  runApp(const StockiFAIApp());
}

class StockiFAIApp extends StatelessWidget {
  const StockiFAIApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => StockProvider()),
        ChangeNotifierProvider(create: (_) => MarketProvider()),
        ChangeNotifierProvider(create: (_) => NewsProvider()),
        ChangeNotifierProvider(create: (_) => EconomicProvider()),
      ],
      child: MaterialApp.router(
        title: 'StockiFAI',
        theme: AppTheme.lightTheme,
        routerConfig: _router,
        debugShowCheckedModeBanner: false,
      ),
    );
  }

  static final GoRouter _router = GoRouter(
    initialLocation: '/',
    routes: [
      ShellRoute(
        builder: (context, state, child) => MainLayout(child: child),
        routes: [
          GoRoute(
            path: '/',
            name: 'dashboard',
            builder: (context, state) => const DashboardScreen(),
          ),
          GoRoute(
            path: '/stocks',
            name: 'stocks',
            builder: (context, state) => const StocksScreen(),
          ),
          GoRoute(
            path: '/economic',
            name: 'economic',
            builder: (context, state) => const EconomicScreen(),
          ),
          GoRoute(
            path: '/news',
            name: 'news',
            builder: (context, state) => const NewsScreen(),
          ),
        ],
      ),
    ],
  );
}

class MainLayout extends StatefulWidget {
  final Widget child;

  const MainLayout({super.key, required this.child});

  @override
  State<MainLayout> createState() => _MainLayoutState();
}

class _MainLayoutState extends State<MainLayout> {
  int _calculateSelectedIndex(BuildContext context) {
    final String location = GoRouterState.of(context).uri.toString();
    if (location.startsWith('/stocks')) {
      return 1;
    }
    if (location.startsWith('/economic')) {
      return 2;
    }
    if (location.startsWith('/news')) {
      return 3;
    }
    if (location == '/') {
      return 0;
    }
    return 0;
  }

  String _getTitleForIndex(int index) {
    switch (index) {
      case 0:
        return 'Dashboard';
      case 1:
        return 'Stocks';
      case 2:
        return 'Economic Indicators';
      case 3:
        return 'Market News';
      default:
        return 'StockiFAI';
    }
  }

  @override
  Widget build(BuildContext context) {
    final int currentIndex = _calculateSelectedIndex(context);
    final String title = _getTitleForIndex(currentIndex);

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            if (currentIndex == 0)
              const Icon(Icons.trending_up, color: Colors.blue),
            if (currentIndex == 0)
              const SizedBox(width: 8),
            Text(title),
          ],
        ),
        backgroundColor: Colors.white,
        elevation: 1,
      ),
      body: widget.child,
      bottomNavigationBar: BottomNavigationBar(
        type: BottomNavigationBarType.fixed,
        currentIndex: currentIndex,
        onTap: (index) {
          switch (index) {
            case 0:
              context.go('/');
              break;
            case 1:
              context.go('/stocks');
              break;
            case 2:
              context.go('/economic');
              break;
            case 3:
              context.go('/news');
              break;
          }
        },
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.dashboard_outlined),
            activeIcon: Icon(Icons.dashboard),
            label: 'Dashboard',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.trending_up_outlined),
            activeIcon: Icon(Icons.trending_up),
            label: 'Stocks',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.account_balance_outlined),
            activeIcon: Icon(Icons.account_balance),
            label: 'Economic',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.article_outlined),
            activeIcon: Icon(Icons.article),
            label: 'News',
          ),
        ],
      ),
    );
  }
}