import axios, { AxiosResponse } from 'axios';
import { 
  ApiResponse, 
  PaginatedResponse, 
  StockData, 
  StockHistory, 
  StockMetrics, 
  MarketOverview, 
  EconomicIndicator,
  MetricPoint
} from '../types';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8000';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor for logging
apiClient.interceptors.request.use(
  (config) => {
    console.log(`API Request: ${config.method?.toUpperCase()} ${config.url}`);
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error.response?.data || error.message);
    return Promise.reject(error);
  }
);

export class StockAPI {
  static async getStock(symbol: string): Promise<StockData> {
    const response: AxiosResponse<ApiResponse<StockData>> = await apiClient.get(`/api/v1/stocks/${symbol}`);
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch stock data');
    }
    return response.data.data;
  }

  static async getStockHistory(
    symbol: string, 
    hours: number = 24, 
    page: number = 0, 
    size: number = 100
  ): Promise<StockHistory> {
    const response: AxiosResponse<ApiResponse<StockHistory>> = await apiClient.get(
      `/api/v1/stocks/${symbol}/history`,
      { params: { hours, page, size } }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch stock history');
    }
    return response.data.data;
  }

  static async getStockMetrics(
    symbol: string, 
    hours: number = 24, 
    aggregation: string = '5m'
  ): Promise<StockMetrics> {
    const response: AxiosResponse<ApiResponse<StockMetrics>> = await apiClient.get(
      `/api/v1/stocks/${symbol}/metrics`,
      { params: { hours, aggregation } }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch stock metrics');
    }
    return response.data.data;
  }

  static async getAllStocks(page: number = 0, size: number = 50): Promise<PaginatedResponse<StockData>> {
    const response: AxiosResponse<ApiResponse<PaginatedResponse<StockData>>> = await apiClient.get(
      '/api/v1/stocks',
      { params: { page, size } }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch stocks');
    }
    return response.data.data;
  }

  static async getTopPerformers(limit: number = 10): Promise<Array<[string, number]>> {
    const response: AxiosResponse<ApiResponse<Array<[string, number]>>> = await apiClient.get(
      '/api/v1/stocks/top-performers',
      { params: { limit } }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch top performers');
    }
    return response.data.data;
  }
}

export class MarketAPI {
  static async getMarketOverview(): Promise<MarketOverview> {
    const response: AxiosResponse<ApiResponse<MarketOverview>> = await apiClient.get('/api/v1/market/overview');
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch market overview');
    }
    return response.data.data;
  }

  static async getMarketVolatility(hours: number = 24): Promise<MetricPoint[]> {
    const response: AxiosResponse<ApiResponse<MetricPoint[]>> = await apiClient.get(
      '/api/v1/market/volatility',
      { params: { hours } }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch market volatility');
    }
    return response.data.data;
  }
}

export class EconomicAPI {
  static async getIndicator(indicator: string): Promise<EconomicIndicator> {
    const response: AxiosResponse<ApiResponse<EconomicIndicator>> = await apiClient.get(
      `/api/v1/economic/indicators/${indicator}`
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch economic indicator');
    }
    return response.data.data;
  }

  static async getAllIndicators(page: number = 0, size: number = 20): Promise<PaginatedResponse<EconomicIndicator>> {
    const response: AxiosResponse<ApiResponse<PaginatedResponse<EconomicIndicator>>> = await apiClient.get(
      '/api/v1/economic/indicators',
      { params: { page, size } }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch economic indicators');
    }
    return response.data.data;
  }

  static async getIndicatorHistory(
    indicator: string, 
    page: number = 0, 
    size: number = 100
  ): Promise<PaginatedResponse<EconomicIndicator>> {
    const response: AxiosResponse<ApiResponse<PaginatedResponse<EconomicIndicator>>> = await apiClient.get(
      `/api/v1/economic/indicators/${indicator}/history`,
      { params: { page, size } }
    );
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || 'Failed to fetch indicator history');
    }
    return response.data.data;
  }
}

export class HealthAPI {
  static async checkHealth(): Promise<any> {
    const response = await apiClient.get('/api/v1/health');
    return response.data;
  }
}