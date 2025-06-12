import React, { useState, useMemo, useCallback } from 'react';
import { StockAPI, MarketAPI, NewsAPI, EconomicAPI } from '../services/api';
import { usePolling } from '../hooks/useApi';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { Card, CardHeader, CardBody } from '../components/common/Card';
import { 
  TrendingUp, TrendingDown, AlertTriangle, Activity, X, BarChart3, DollarSign, Newspaper
} from 'lucide-react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend
} from 'recharts';
import { formatCurrency, formatPercentage, formatDateShort, getChangeColor, getTrendBadgeColor, getRiskBadgeColor, getRiskColor } from '../utils/formatters';
import { StockData, EconomicIndicator } from '../types';
import clsx from 'clsx';

const POLLING_INTERVAL_PRIMARY = 30000;
const POLLING_INTERVAL_SECONDARY = 300000;
const POLLING_INTERVAL_NEWS = 900000;

const Dashboard: React.FC = () => {
  const [selectedStockSymbol, setSelectedStockSymbol] = useState<string | null>(null);
  const [selectedEconomicIndicatorId, setSelectedEconomicIndicatorId] = useState<string | null>(null);
  
  const [stockTimeSeriesRangeHours, setStockTimeSeriesRangeHours] = useState<number>(24);
  const [economicTimeSeriesRangeDays, setEconomicTimeSeriesRangeDays] = useState<number>(30);

  const fetchStockSummaries = useCallback(() => StockAPI.getAllStockSummaries(0, 100), []);
  const { data: stockSummariesResponse, loading: stocksLoading, error: stocksError, refetch: refetchStocks } = 
    usePolling(fetchStockSummaries, POLLING_INTERVAL_PRIMARY);

  const fetchMarketOverview = useCallback(() => MarketAPI.getMarketOverview(), []);
  const { data: marketOverview, loading: marketLoading, error: marketError } = 
    usePolling(fetchMarketOverview, POLLING_INTERVAL_SECONDARY);

  const fetchNews = useCallback(() => NewsAPI.getRecentNews(48, 15), []);
  const { data: newsData, loading: newsLoading, error: newsError } = 
    usePolling(fetchNews, POLLING_INTERVAL_NEWS);

  const fetchEconomicIndicatorSummaries = useCallback(() => EconomicAPI.getAllIndicatorSummaries(0, 10), []);
  const { data: economicIndicatorSummariesResponse, loading: economicLoading, error: economicError } = 
    usePolling(fetchEconomicIndicatorSummaries, POLLING_INTERVAL_SECONDARY);

  const fetchStockTimeSeries = useCallback(() => {
    if (!selectedStockSymbol) return Promise.resolve(null);
    return StockAPI.getStockTimeSeries(selectedStockSymbol, stockTimeSeriesRangeHours);
  }, [selectedStockSymbol, stockTimeSeriesRangeHours]);
  const { data: stockTimeSeriesData, loading: stockTimeSeriesLoading, error: stockTimeSeriesError, refetch: refetchStockTimeSeries } = 
    usePolling(
      fetchStockTimeSeries,
      POLLING_INTERVAL_PRIMARY,
      [selectedStockSymbol, stockTimeSeriesRangeHours]
    );
  
  const fetchCurrentSelectedStockSummary = useCallback(() => {
    if (!selectedStockSymbol) return Promise.resolve(null);
    return StockAPI.getStockSummary(selectedStockSymbol);
  }, [selectedStockSymbol]);
  const { data: currentSelectedStockSummary, refetch: refetchCurrentSelectedStockSummary } =
    usePolling(
      fetchCurrentSelectedStockSummary,
      POLLING_INTERVAL_PRIMARY,
      [selectedStockSymbol]
    );

  const fetchEconomicTimeSeries = useCallback(() => {
    if (!selectedEconomicIndicatorId) return Promise.resolve(null);
    return EconomicAPI.getEconomicIndicatorTimeSeries(selectedEconomicIndicatorId, economicTimeSeriesRangeDays);
  }, [selectedEconomicIndicatorId, economicTimeSeriesRangeDays]);
  const { data: economicTimeSeriesData, loading: economicTimeSeriesLoading, error: economicTimeSeriesError, refetch: refetchEconomicTimeSeries } = 
    usePolling(
      fetchEconomicTimeSeries,
      POLLING_INTERVAL_SECONDARY,
      [selectedEconomicIndicatorId, economicTimeSeriesRangeDays]
    );

  const handleStockCardClick = (stock: StockData) => {
    setSelectedStockSymbol(stock.symbol);
    refetchStockTimeSeries();
    refetchCurrentSelectedStockSummary();
  };

  const closeStockTimeSeriesPanel = () => setSelectedStockSymbol(null);

  const handleEconomicIndicatorClick = (indicator: EconomicIndicator) => {
    setSelectedEconomicIndicatorId(indicator.series_id);
    refetchEconomicTimeSeries();
  };
  const closeEconomicTimeSeriesPanel = () => setSelectedEconomicIndicatorId(null);

  const displayedStocks = useMemo(() => stockSummariesResponse?.data?.slice(0, 12) || [], [stockSummariesResponse]);
  const displayedEconomicIndicators = useMemo(() => economicIndicatorSummariesResponse?.data?.slice(0, 6) || [], [economicIndicatorSummariesResponse]);
  const displayedNews = useMemo(() => newsData?.slice(0, 9) || [], [newsData]);

  const getSentimentColor = (sentiment?: string) => {
    switch (sentiment?.toUpperCase()) {
      case 'POSITIVE': return 'text-success-700 bg-success-100';
      case 'NEGATIVE': return 'text-danger-700 bg-danger-100';
      default: return 'text-gray-700 bg-gray-100';
    }
  };
  
  const renderMarketOverviewCard = (title: string, value: string | number | undefined, icon: React.ReactNode, valueColor?: string) => (
    <Card>
      <CardBody>
        <div className="flex items-center">
          {icon}
          <div className="ml-3">
            <p className="text-sm font-medium text-gray-500 truncate">{title}</p>
            <p className={clsx("text-xl font-semibold text-gray-900", valueColor)}>{value ?? 'N/A'}</p>
          </div>
        </div>
      </CardBody>
    </Card>
  );

  if (stocksLoading && marketLoading && newsLoading && economicLoading && !stockSummariesResponse && !marketOverview && !newsData && !economicIndicatorSummariesResponse) {
    return <div className="flex justify-center items-center min-h-screen"><LoadingSpinner size="lg" /></div>;
  }

  return (
    <div className="min-h-screen bg-gray-100">
      {/* Header */}
      <header className="bg-white shadow-md sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center">
              <TrendingUp className="h-8 w-8 text-primary-600" />
              <h1 className="ml-3 text-2xl font-bold text-gray-900">StockiFAI Dashboard</h1>
            </div>
            <div className="text-sm text-gray-500">
              Live Data Feed
            </div>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto p-4 sm:p-6 lg:p-8 space-y-8">
        {/* Market Overview Section */}
        {marketError && <ErrorMessage message={`Market Overview Error: ${marketError}`} onRetry={() => window.location.reload()} />}
        {marketOverview && !marketError && (
          <section aria-labelledby="market-overview-title">
            <h2 id="market-overview-title" className="text-xl font-semibold text-gray-900 mb-4">Market Pulse</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {renderMarketOverviewCard("Sentiment", marketOverview.marketSentiment, 
                marketOverview.marketSentiment === "BULLISH" ? <TrendingUp className="h-7 w-7 text-success-500"/> : 
                marketOverview.marketSentiment === "BEARISH" ? <TrendingDown className="h-7 w-7 text-danger-500"/> : 
                <Activity className="h-7 w-7 text-yellow-500"/>,
                marketOverview.marketSentiment === "BULLISH" ? 'text-success-600' : 
                marketOverview.marketSentiment === "BEARISH" ? 'text-danger-600' : 'text-yellow-600'
              )}
              {renderMarketOverviewCard("Avg. Risk Score", marketOverview.averageRiskScore?.toFixed(1), <AlertTriangle className="h-7 w-7 text-orange-500"/>, getRiskColor(marketOverview.averageRiskScore ?? 0))}
              {renderMarketOverviewCard("High Risk Count", marketOverview.highRiskStocks?.length, <AlertTriangle className="h-7 w-7 text-red-500"/>, 'text-danger-600')}
            </div>
          </section>
        )}

        {/* Main Content Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Stocks Panel */}
          <section className="lg:col-span-2 space-y-8">
            <div>
              <Card>
                <CardHeader>
                  <div className="flex items-center">
                    <BarChart3 className="w-5 h-5 mr-2 text-primary-700" />
                    <h2 className="text-lg font-semibold text-gray-900">Monitored Stocks</h2>
                  </div>
                </CardHeader>
                <CardBody className="p-0">
                  {stocksLoading && <div className="p-6 text-center"><LoadingSpinner /></div>}
                  {stocksError && <div className="p-6"><ErrorMessage message={`Stocks Error: ${stocksError}`} onRetry={refetchStocks} /></div>}
                  {displayedStocks.length > 0 && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 p-4 max-h-[40rem] overflow-y-auto">
                      {displayedStocks.map((stock) => (
                        <div 
                          key={stock.symbol} 
                          className="p-3 border rounded-lg hover:shadow-lg cursor-pointer transition-all duration-200 bg-white hover:border-primary-500"
                          onClick={() => handleStockCardClick(stock)}
                        >
                          <div className="flex justify-between items-start mb-1">
                            <h3 className="font-bold text-gray-800 text-md">{stock.symbol}</h3>
                            <div className={`text-sm font-semibold ${getChangeColor(stock.priceChangePercent ?? 0)}`}>
                              {formatPercentage(stock.priceChangePercent ?? 0)}
                            </div>
                          </div>
                          <p className="text-xl font-semibold text-gray-900 mb-1">
                            {formatCurrency(stock.currentPrice)}
                          </p>
                          <div className="flex justify-between items-center text-xs text-gray-500">
                            <span className={`px-2 py-0.5 rounded-full text-xs ${getTrendBadgeColor(stock.trend ?? "UNKNOWN")}`}>
                              {stock.trend ?? "N/A"}
                            </span>
                            <span className={`px-2 py-0.5 rounded-full text-xs ${getRiskBadgeColor(stock.riskScore ?? 0)}`}>
                              Risk: {stock.riskScore?.toFixed(0) ?? "N/A"}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                  {!stocksLoading && displayedStocks.length === 0 && !stocksError && (
                     <p className="p-6 text-center text-gray-500">No stock data currently available.</p>
                  )}
                </CardBody>
              </Card>
            </div>
          </section>

          {/* Economic Indicators & News Panel */}
          <aside className="space-y-8">
            <Card>
              <CardHeader>
                <div className="flex items-center">
                  <DollarSign className="w-5 h-5 mr-2 text-green-700" />
                  <h2 className="text-lg font-semibold text-gray-900">Economic Indicators</h2>
                </div>
              </CardHeader>
              <CardBody>
                {economicLoading && <div className="text-center"><LoadingSpinner /></div>}
                {economicError && <ErrorMessage message={`Economic Data Error: ${economicError}`} />}
                {displayedEconomicIndicators.length > 0 && (
                  <div className="space-y-3 max-h-[18rem] overflow-y-auto">
                    {displayedEconomicIndicators.map((indicator) => (
                      <div 
                        key={indicator.series_id} 
                        className="p-2.5 bg-gray-50 rounded-md hover:bg-gray-100 cursor-pointer"
                        onClick={() => handleEconomicIndicatorClick(indicator)}
                      >
                        <div className="flex justify-between items-center">
                          <span className="text-sm font-medium text-gray-700 truncate pr-2" title={indicator.metadata?.title || indicator.series_id}>
                            {indicator.metadata?.title || indicator.series_id}
                          </span>
                          <span className="text-sm font-semibold text-gray-900">
                            {indicator.observations[0]?.value?.toFixed(2) ?? 'N/A'}
                          </span>
                        </div>
                        <p className="text-xs text-gray-500">{indicator.metadata?.units || ''}</p>
                      </div>
                    ))}
                  </div>
                )}
                {!economicLoading && displayedEconomicIndicators.length === 0 && !economicError && (
                  <p className="text-center text-gray-500">No economic indicators available.</p>
                )}
              </CardBody>
            </Card>

            <Card>
              <CardHeader>
                <div className="flex items-center">
                  <Newspaper className="w-5 h-5 mr-2 text-indigo-700" />
                  <h2 className="text-lg font-semibold text-gray-900">Market News</h2>
                </div>
              </CardHeader>
              <CardBody>
                {newsLoading && <div className="text-center"><LoadingSpinner /></div>}
                {newsError && <ErrorMessage message={`News Error: ${newsError}`} />}
                {displayedNews.length > 0 && (
                  <div className="space-y-3 max-h-[25rem] overflow-y-auto">
                    {displayedNews.map((item) => (
                      <div key={item.id} className="p-2.5 border-b border-gray-200 last:border-b-0">
                        <h4 className="text-sm font-medium text-gray-800 mb-0.5 line-clamp-2" title={item.headline}>{item.headline}</h4>
                        <div className="flex justify-between items-center text-xs">
                          <span className={`px-1.5 py-0.5 rounded text-xs ${getSentimentColor(item.sentiment)}`}>
                            {item.sentiment}
                          </span>
                          <span className="text-gray-500">{item.source} - {formatDateShort(item.timestamp)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
                {!newsLoading && displayedNews.length === 0 && !newsError && (
                  <p className="text-center text-gray-500">No news available.</p>
                )}
              </CardBody>
            </Card>
          </aside>
        </div>

        {/* Stock Time Series Modal */}
        {selectedStockSymbol && (
          <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-50 transition-opacity duration-300 ease-in-out">
            <Card className="max-w-3xl w-full max-h-[90vh] flex flex-col">
              <CardHeader>
                <div className="flex justify-between items-center">
                  <div>
                    <h3 className="text-lg font-semibold text-gray-900">
                      {selectedStockSymbol} - {currentSelectedStockSummary?.currentPrice ? formatCurrency(currentSelectedStockSummary.currentPrice) : 'Loading...'}
                    </h3>
                    <p className="text-xs text-gray-500">
                      Risk: {currentSelectedStockSummary?.riskScore?.toFixed(1) ?? 'N/A'} | Trend: {currentSelectedStockSummary?.trend ?? 'N/A'}
                    </p>
                  </div>
                  <div className="flex items-center space-x-2">
                    <select
                      value={stockTimeSeriesRangeHours}
                      onChange={(e) => setStockTimeSeriesRangeHours(Number(e.target.value))}
                      className="input py-1 px-2 text-sm"
                    >
                      <option value={1}>1H</option><option value={6}>6H</option>
                      <option value={24}>24H</option><option value={7*24}>7D</option>
                      <option value={30*24}>30D</option>
                    </select>
                    <button onClick={closeStockTimeSeriesPanel} className="p-1 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100">
                      <X className="w-5 h-5" />
                    </button>
                  </div>
                </div>
              </CardHeader>
              <CardBody className="flex-grow overflow-y-auto">
                {stockTimeSeriesLoading && <div className="h-72 flex justify-center items-center"><LoadingSpinner size="lg" /></div>}
                {stockTimeSeriesError && <ErrorMessage message={`Time Series Error: ${stockTimeSeriesError}`} onRetry={refetchStockTimeSeries}/>}
                {stockTimeSeriesData && stockTimeSeriesData.metrics.length > 0 && (
                  <div className="h-72 md:h-80">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={stockTimeSeriesData.metrics.map(p => ({ 
                          time: formatDateShort(p.timestamp), 
                          price: p.price, 
                          volume: p.volume,
                          risk: p.riskScore
                        }))}
                        margin={{ top: 5, right: 20, left: -20, bottom: 5 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb"/>
                        <XAxis dataKey="time" tick={{ fontSize: 10 }} stroke="#6b7280"/>
                        <YAxis yAxisId="price" orientation="left" tickFormatter={(v) => `$${Number(v).toFixed(2)}`} tick={{ fontSize: 10 }} stroke="#3b82f6" />
                        <YAxis yAxisId="volume" orientation="right" tickFormatter={(v) => Number(v) > 1000 ? `${(Number(v)/1000).toFixed(0)}k` : Number(v).toFixed(0) } tick={{ fontSize: 10 }} stroke="#8b5cf6"/>
                        <Tooltip 
                          contentStyle={{ backgroundColor: 'rgba(255,255,255,0.9)', borderRadius: '0.5rem', border: '1px solid #e5e7eb' }}
                          formatter={(value: number, name: string) => {
                            if (name === 'price') return [formatCurrency(value), 'Price'];
                            if (name === 'volume') return [value.toLocaleString(), 'Volume'];
                            if (name === 'risk') return [value.toFixed(1), 'Risk Score'];
                            return [value, name];
                          }}
                        />
                        <Legend verticalAlign="top" height={36}/>
                        <Line yAxisId="price" type="monotone" dataKey="price" stroke="#3b82f6" strokeWidth={2} dot={false} name="Price" />
                        <Line yAxisId="volume" type="monotone" dataKey="volume" stroke="#8b5cf6" strokeWidth={1} dot={false} name="Volume" strokeDasharray="3 3" />
                        {/* <Line yAxisId="left" type="monotone" dataKey="risk" stroke="#f59e0b" strokeWidth={1} dot={false} name="Risk" /> */}
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                )}
                {!stockTimeSeriesLoading && (!stockTimeSeriesData || stockTimeSeriesData.metrics.length === 0) && !stockTimeSeriesError && (
                  <p className="text-center text-gray-500 py-10">No time-series data available for {selectedStockSymbol} in the selected range.</p>
                )}
              </CardBody>
            </Card>
          </div>
        )}

        {/* Economic Indicator Time Series Modal */}
        {selectedEconomicIndicatorId && (
           <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 z-50 transition-opacity duration-300 ease-in-out">
            <Card className="max-w-2xl w-full max-h-[80vh] flex flex-col">
              <CardHeader>
                <div className="flex justify-between items-center">
                  <h3 className="text-lg font-semibold text-gray-900">{selectedEconomicIndicatorId} - Time Series</h3>
                  <div className="flex items-center space-x-2">
                    <select
                      value={economicTimeSeriesRangeDays}
                      onChange={(e) => setEconomicTimeSeriesRangeDays(Number(e.target.value))}
                      className="input py-1 px-2 text-sm"
                    >
                      <option value={30}>30D</option><option value={90}>90D</option>
                      <option value={180}>180D</option><option value={365}>1Y</option>
                    </select>
                    <button onClick={closeEconomicTimeSeriesPanel} className="p-1 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100">
                      <X className="w-5 h-5" />
                    </button>
                  </div>
                </div>
              </CardHeader>
              <CardBody className="flex-grow overflow-y-auto">
                {economicTimeSeriesLoading && (
                  <div className="h-72 flex justify-center items-center"><LoadingSpinner size="lg" /></div>
                )}
                {economicTimeSeriesError && <ErrorMessage message={`Economic Time Series Error: ${economicTimeSeriesError}`} onRetry={refetchEconomicTimeSeries}/>}
                {economicTimeSeriesData && economicTimeSeriesData.length > 0 && (
                  <div className="h-72 md:h-80">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={economicTimeSeriesData.map(p => ({ time: formatDateShort(p.timestamp), value: p.value }))}
                        margin={{ top: 5, right: 20, left: -20, bottom: 5 }}
                      >
                        <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb"/>
                        <XAxis dataKey="time" tick={{ fontSize: 10 }} stroke="#6b7280"/>
                        <YAxis tickFormatter={(v) => Number(v).toFixed(2)} tick={{ fontSize: 10 }} stroke="#8884d8"/>
                        <Tooltip 
                          contentStyle={{ backgroundColor: 'rgba(255,255,255,0.9)', borderRadius: '0.5rem', border: '1px solid #e5e7eb' }}
                          formatter={(value: number) => [Number(value).toFixed(2), selectedEconomicIndicatorId]}
                        />
                        <Line type="monotone" dataKey="value" stroke="#8884d8" strokeWidth={2} dot={false} name={selectedEconomicIndicatorId || "Value"}/>
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                )}
                {!economicTimeSeriesLoading && (!economicTimeSeriesData || economicTimeSeriesData.length === 0) && !economicTimeSeriesError && (
                  <p className="text-center text-gray-500 py-10">No time-series data available for {selectedEconomicIndicatorId}.</p>
                )}
              </CardBody>
            </Card>
          </div>
        )}
      </main>
    </div>
  );
};

export default Dashboard;
