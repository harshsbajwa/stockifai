export interface ApiResponse<T> {
    success: boolean;
    data?: T;
    message?: string;
    timestamp: string;
    errors?: string[];
  }
  
  export interface PaginatedResponse<T> {
    data: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  }
  
  export interface StockData {
    symbol: string;
    currentPrice: number;
    volume: number;
    volatility: number;
    priceChange: number;
    priceChangePercent: number;
    volumeAverage: number;
    riskScore: number;
    trend: 'BULLISH' | 'BEARISH' | 'SIDEWAYS' | 'UNKNOWN';
    support?: number;
    resistance?: number;
    timestamp: string;
    lastUpdated: string;
  }
  
  export interface StockDataPoint {
    timestamp: string;
    price: number;
    volume: number;
    volatility: number;
    riskScore: number;
    trend: string;
  }
  
  export interface StockHistory {
    symbol: string;
    data: StockDataPoint[];
    count: number;
    timeRange: {
      start: string;
      end: string;
    };
  }
  
  export interface MetricPoint {
    timestamp: string;
    price?: number;
    volume?: number;
    volatility?: number;
    riskScore?: number;
  }
  
  export interface StockMetrics {
    symbol: string;
    metrics: MetricPoint[];
    timeRange: {
      start: string;
      end: string;
    };
    aggregation: string;
  }
  
  export interface MarketOverview {
    totalStocks: number;
    activeStocks: number;
    averageRiskScore: number;
    highRiskStocks: string[];
    topMovers: {
      gainers: StockMover[];
      losers: StockMover[];
      mostVolatile: StockMover[];
    };
    marketSentiment: 'BULLISH' | 'BEARISH' | 'NEUTRAL' | 'UNKNOWN';
    lastUpdated: string;
  }
  
  export interface StockMover {
    symbol: string;
    currentPrice: number;
    change: number;
    changePercent: number;
    volume: number;
  }
  
  export interface EconomicIndicator {
    indicator: string;
    value: number;
    country?: string;
    timestamp: string;
    description?: string;
  }