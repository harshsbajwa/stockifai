import axios, { AxiosResponse } from 'axios';
import { 
  ApiResponse, 
  PaginatedResponse, 
  StockData, 
  // StockHistory,
  StockMetrics, 
  MarketOverview, 
  EconomicIndicator,
  MetricPoint,
  EconomicDataPoint,
  NewsItem
} from '../types';

const API_BASE_URL = process.env.REACT_APP_API_URL || '';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    // console.log(`API Request: ${config.method?.toUpperCase()} ${config.url} PARAMS: ${JSON.stringify(config.params)}`);
    // const token = localStorage.getItem('authToken'); // Example for auth
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    let errorMessage = 'An unexpected error occurred while fetching data.';
    if (error.response) {
      errorMessage = error.response.data?.message || error.response.data?.error || error.message;
      console.error('API Error Response:', error.response.status, error.response.data);
    } else if (error.request) {
      errorMessage = 'No response from server. Please check your network connection.';
      console.error('API No Response:', error.request);
    } else {
      errorMessage = error.message;
      console.error('API Request Setup Error:', error.message);
    }
    return Promise.reject(new Error(errorMessage));
  }
);

export class StockAPI {
  static async getStockSummary(symbol: string): Promise<StockData> {
    const response: AxiosResponse<ApiResponse<StockData>> = 
      await apiClient.get(`/api/v1/stocks/${symbol}`);
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || `Failed to fetch stock summary for ${symbol}`);
    }
    return response.data.data;
  }

  static async getStockTimeSeries(
    symbol: string, 
    hours: number = 24, 
    aggregation: string = "5m"
  ): Promise<StockMetrics> {
    const response: AxiosResponse<ApiResponse<StockMetrics>> = 
      await apiClient.get(`/api/v1/stocks/${symbol}/timeseries`, { 
        params: { hours, aggregation } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || `Failed to fetch stock time-series for ${symbol}`);
    }
    return response.data.data;
  }

  static async getAllStockSummaries(page: number = 0, size: number = 50): Promise<PaginatedResponse<StockData>> {
    const response: AxiosResponse<ApiResponse<PaginatedResponse<StockData>>> = 
      await apiClient.get('/api/v1/stocks', { 
        params: { page, size } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch stock summaries');
    }
    return response.data.data;
  }

  static async getTopPerformingSymbols(limit: number = 10): Promise<Array<{ first: string; second: number }>> {
    const response: AxiosResponse<ApiResponse<Array<{ first: string; second: number }>>> = 
      await apiClient.get('/api/v1/stocks/top-performers', { 
        params: { limit } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch top performing symbols');
    }
    return response.data.data;
  }
}

export class MarketAPI {
  static async getMarketOverview(): Promise<MarketOverview> {
    const response: AxiosResponse<ApiResponse<MarketOverview>> = 
      await apiClient.get('/api/v1/market/overview');
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch market overview');
    }
    return response.data.data;
  }

  static async getMarketVolatility(hours: number = 24): Promise<MetricPoint[]> {
    const response: AxiosResponse<ApiResponse<MetricPoint[]>> = 
      await apiClient.get('/api/v1/market/volatility', { 
        params: { hours } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch market volatility');
    }
    return response.data.data;
  }
}

export class EconomicAPI {
  static async getIndicatorSummary(indicatorId: string): Promise<EconomicIndicator> {
    const response: AxiosResponse<ApiResponse<EconomicIndicator>> = 
      await apiClient.get(`/api/v1/economic/indicators/${indicatorId}`);
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || `Failed to fetch economic indicator summary for ${indicatorId}`);
    }
    return response.data.data;
  }

  static async getAllIndicatorSummaries(page: number = 0, size: number = 20): Promise<PaginatedResponse<EconomicIndicator>> {
    const response: AxiosResponse<ApiResponse<PaginatedResponse<EconomicIndicator>>> = 
      await apiClient.get('/api/v1/economic/indicators', { 
        params: { page, size } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch all economic indicator summaries');
    }
    return response.data.data;
  }

  static async getEconomicIndicatorTimeSeries(
    indicatorId: string, 
    days: number = 30
  ): Promise<EconomicDataPoint[]> {
    const response: AxiosResponse<ApiResponse<EconomicDataPoint[]>> = 
      await apiClient.get(`/api/v1/economic/indicators/${indicatorId}/timeseries`, { 
        params: { days } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || `Failed to fetch economic indicator time-series for ${indicatorId}`);
    }
    return response.data.data;
  }
}

export class NewsAPI {
  static async getRecentNews(hours: number = 24, limit: number = 50): Promise<NewsItem[]> {
    const response: AxiosResponse<ApiResponse<NewsItem[]>> = 
      await apiClient.get('/api/v1/news', { 
        params: { hours, limit } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch recent news');
    }
    return response.data.data;
  }

  static async getNewsBySentiment(sentiment: string, hours: number = 24, limit: number = 20): Promise<NewsItem[]> {
    const response: AxiosResponse<ApiResponse<NewsItem[]>> = 
      await apiClient.get(`/api/v1/news/sentiment/${sentiment}`, { 
        params: { hours, limit } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || `Failed to fetch news by sentiment: ${sentiment}`);
    }
    return response.data.data;
  }

  static async getNewsForSymbol(symbol: string, hours: number = 24, limit: number = 20): Promise<NewsItem[]> {
    const response: AxiosResponse<ApiResponse<NewsItem[]>> = 
      await apiClient.get(`/api/v1/news/symbol/${symbol}`, { 
        params: { hours, limit } 
      });
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || `Failed to fetch news for symbol: ${symbol}`);
    }
    return response.data.data;
  }
}

export class HealthAPI {
  static async checkApiHealth(): Promise<any> {
    const response = await apiClient.get('/api/v1/health');
    return response.data;
  }
}