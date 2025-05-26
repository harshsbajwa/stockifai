import React from 'react';
import { useNavigate } from 'react-router-dom';
import { MarketAPI, StockAPI } from '../services/api';
import { usePolling } from '../hooks/useApi';
import MarketOverviewComponent from '../components/dashboard/MarketOverview';
import StockList from '../components/stocks/StockList';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { Card, CardHeader, CardBody } from '../components/common/Card';

const Dashboard: React.FC = () => {
  const navigate = useNavigate();

  const { 
    data: marketOverview, 
    loading: marketLoading, 
    error: marketError,
    refetch: refetchMarket
  } = usePolling(() => MarketAPI.getMarketOverview(), 30000);

  const { 
    data: stocksData, 
    loading: stocksLoading, 
    error: stocksError,
    refetch: refetchStocks
  } = usePolling(() => StockAPI.getAllStocks(0, 12), 30000);

  const { 
    data: topPerformers, 
    loading: performersLoading,
    refetch: refetchPerformers
  } = usePolling(() => StockAPI.getTopPerformers(10), 60000);

  const handleStockClick = (symbol: string) => {
    navigate(`/stocks/${symbol}`);
  };

  const handleRetry = () => {
    refetchMarket();
    refetchStocks();
    refetchPerformers();
  };

  if (marketLoading && stocksLoading) {
    return (
      <div className="flex justify-center items-center min-h-screen">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Market Dashboard</h1>
        <p className="text-gray-600 mt-2">Real-time market overview and stock performance</p>
      </div>

      {/* Market Overview */}
      {marketError ? (
        <ErrorMessage message={marketError} onRetry={refetchMarket} />
      ) : marketOverview ? (
        <MarketOverviewComponent overview={marketOverview} />
      ) : (
        <div className="flex justify-center py-8">
          <LoadingSpinner />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Top Performers */}
        <Card>
          <CardHeader>
            <h2 className="text-xl font-semibold text-gray-900">Top Performers</h2>
          </CardHeader>
          <CardBody>
            {performersLoading ? (
              <LoadingSpinner />
            ) : topPerformers ? (
              <div className="space-y-3">
                {topPerformers.slice(0, 8).map(([symbol, change]) => (
                  <div key={symbol} className="flex justify-between items-center">
                    <button 
                      onClick={() => handleStockClick(symbol)}
                      className="font-medium text-primary-600 hover:text-primary-800"
                    >
                      {symbol}
                    </button>
                    <span className={`font-semibold ${change >= 0 ? 'text-success-600' : 'text-danger-600'}`}>
                      {change >= 0 ? '+' : ''}{change.toFixed(2)}%
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-gray-600">Unable to load top performers</p>
            )}
          </CardBody>
        </Card>

        {/* Recent Stocks */}
        <div className="lg:col-span-2">
          <Card>
            <CardHeader>
              <div className="flex justify-between items-center">
                <h2 className="text-xl font-semibold text-gray-900">Recent Stocks</h2>
                <button 
                  onClick={() => navigate('/stocks')}
                  className="btn-primary"
                >
                  View All
                </button>
              </div>
            </CardHeader>
            <CardBody className="p-0">
              {stocksData ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 p-6">
                  {stocksData.data.slice(0, 6).map((stock) => (
                    <div key={stock.symbol} className="p-4 border rounded-lg hover:bg-gray-50 cursor-pointer"
                         onClick={() => handleStockClick(stock.symbol)}>
                      <div className="flex justify-between items-center">
                        <div>
                          <h3 className="font-bold text-gray-900">{stock.symbol}</h3>
                          <p className="text-lg font-semibold">${stock.currentPrice.toFixed(2)}</p>
                        </div>
                        <div className={`text-right ${stock.priceChange >= 0 ? 'text-success-600' : 'text-danger-600'}`}>
                          <p className="font-semibold">
                            {stock.priceChange >= 0 ? '+' : ''}{stock.priceChangePercent.toFixed(2)}%
                          </p>
                          <p className="text-sm">
                            {stock.priceChange >= 0 ? '+' : ''}${stock.priceChange.toFixed(2)}
                          </p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="p-6">
                  {stocksError ? (
                    <ErrorMessage message={stocksError} onRetry={refetchStocks} />
                  ) : (
                    <LoadingSpinner />
                  )}
                </div>
              )}
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;