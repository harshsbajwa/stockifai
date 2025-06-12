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
  priceChange: number | null;
  priceChangePercent: number | null;
  volumeAverage: number;
  riskScore: number;
  trend: 'BULLISH' | 'BEARISH' | 'SIDEWAYS' | 'UNKNOWN';
  support?: number;
  resistance?: number;
  timestamp: string;
  lastUpdated: string;
  calculationDate?: string;
}
  
export interface StockDataPoint {
  timestamp: string;
  price: number;
  volume: number;
  volatility: number;
  riskScore: number | null;
  trend: string | null;
  open?: number | null;
  high?: number | null;
  low?: number | null;
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
  open?: number | null;
  high?: number | null;
  low?: number | null;
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
  series_id: string;
  observations: EconomicObservation[];
  metadata?: EconomicIndicatorMetadata | null;
}

export interface EconomicObservation {
  date: string | null;
  value: number | null;
  realTimeStart?: string | null;
  realTimeEnd?: string | null;
}

export interface EconomicIndicatorMetadata {
  series_id: string;
  title?: string | null;
  frequency?: string | null;
  units?: string | null;
  notes?: string | null;
  source?: string | null;
}

export interface EconomicDataPoint {
  series_id: string;
  value: number;
  timestamp: string;
}

export interface NewsItem {
  id: string;
  headline: string;
  summary: string;
  sentiment: string;
  timestamp: string;
  source?: string | null;
  relatedSymbol?: string | null;
}
