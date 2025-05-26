import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, RefreshCw } from 'lucide-react';
import { StockAPI } from '../services/api';
import { usePolling, useApi } from '../hooks/useApi';
import PriceChart from '../components/charts/PriceChart';
import VolumeChart from '../components/charts/VolumeChart';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { Card, CardHeader, CardBody } from '../components/common/Card';
import { formatCurrency, formatPercentage, formatVolume, formatDate, getChangeColor, getTrendBadgeColor, getRiskBadgeColor } from '../utils/formatters';

const StockDetail: React.FC = () => {
  const { symbol } = useParams<{ symbol: string }>();
  const navigate = useNavigate();
  const [timeRange, setTimeRange] = useState<number>(24);

  if (!symbol) {
    return <ErrorMessage message="Stock symbol not provided" />;
  }

  const { 
    data: stockData, 
    loading: stockLoading, 
    error: stockError,
    refetch: refetchStock
  } = usePolling(() => StockAPI.getStock(symbol), 30000);

  const { 
    data: historyData, 
    loading: historyLoading, 
    error: historyError,
    refetch: refetchHistory
  } = useApi(() => StockAPI.getStockHistory(symbol, timeRange), [symbol, timeRange]);

  const { 
    data: metricsData, 
    loading: metricsLoading,
    refetch: refetchMetrics
  } = useApi(() => StockAPI.getStockMetrics(symbol, timeRange), [symbol, timeRange]);

  const handleRefresh = () => {
    refetchStock();
    refetchHistory();
    refetchMetrics();
  };

  if (stockLoading) {
    return (
      <div className="flex justify-center items-center min-h-screen">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (stockError || !stockData) {
    return (
      <ErrorMessage 
        message={stockError || "Stock not found"} 
        onRetry={refetchStock}
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center space-x-4">
          <button 
            onClick={() => navigate(-1)}
            className="btn-secondary"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <h1 className="text-3xl font-bold text-gray-900">{stockData.symbol}</h1>
            <p className="text-gray-600">Last updated: {formatDate(stockData.lastUpdated)}</p>
          </div>
        </div>
        
        <div className="flex items-center space-x-4">
          <select 
            value={timeRange} 
            onChange={(e) => setTimeRange(Number(e.target.value))}
            className="input w-auto"
          >
            <option value={1}>1 Hour</option>
            <option value={6}>6 Hours</option>
            <option value={24}>24 Hours</option>
            <option value={72}>3 Days</option>
            <option value={168}>1 Week</option>
          </select>
          
          <button onClick={handleRefresh} className="btn-primary">
            <RefreshCw className="w-4 h-4 mr-2" />
            Refresh
          </button>
        </div>
      </div>

      {/* Stock Overview */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <Card>
          <CardBody>
            <div className="text-center">
              <p className="text-sm text-gray-600">Current Price</p>
              <p className="text-2xl font-bold text-gray-900">
                {formatCurrency(stockData.currentPrice)}
              </p>
              <p className={`text-sm font-medium ${getChangeColor(stockData.priceChange)}`}>
                {formatPercentage(stockData.priceChangePercent)} 
                ({stockData.priceChange >= 0 ? '+' : ''}{formatCurrency(stockData.priceChange)})
              </p>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <div className="text-center">
              <p className="text-sm text-gray-600">Volume</p>
              <p className="text-xl font-bold text-gray-900">
                {formatVolume(stockData.volume)}
              </p>
              <p className="text-sm text-gray-600">
                Avg: {formatVolume(stockData.volumeAverage)}
              </p>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <div className="text-center">
              <p className="text-sm text-gray-600">Volatility</p>
              <p className="text-xl font-bold text-gray-900">
                {(stockData.volatility * 100).toFixed(2)}%
              </p>
              <span className={`badge ${getTrendBadgeColor(stockData.trend)} mt-2`}>
                {stockData.trend}
              </span>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            <div className="text-center">
              <p className="text-sm text-gray-600">Risk Score</p>
              <p className="text-xl font-bold text-gray-900">
                {stockData.riskScore.toFixed(1)}
              </p>
              <span className={`badge ${getRiskBadgeColor(stockData.riskScore)} mt-2`}>
                {stockData.riskScore >= 70 ? 'High Risk' : 
                 stockData.riskScore >= 40 ? 'Medium Risk' : 'Low Risk'}
              </span>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Support & Resistance */}
      {(stockData.support || stockData.resistance) && (
        <Card>
          <CardHeader>
            <h2 className="text-xl font-semibold text-gray-900">Support & Resistance</h2>
          </CardHeader>
          <CardBody>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {stockData.support && (
                <div className="text-center">
                  <p className="text-sm text-gray-600">Support Level</p>
                  <p className="text-xl font-bold text-success-600">
                    {formatCurrency(stockData.support)}
                  </p>
                </div>
              )}
              {stockData.resistance && (
                <div className="text-center">
                  <p className="text-sm text-gray-600">Resistance Level</p>
                  <p className="text-xl font-bold text-danger-600">
                    {formatCurrency(stockData.resistance)}
                  </p>
                </div>
              )}
            </div>
          </CardBody>
        </Card>
      )}

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <CardBody>
            {historyLoading ? (
              <LoadingSpinner />
            ) : historyError ? (
              <ErrorMessage message={historyError} onRetry={refetchHistory} />
            ) : historyData ? (
              <PriceChart 
                data={historyData.data} 
                symbol={symbol}
                support={stockData.support}
                resistance={stockData.resistance}
                height={400}
              />
            ) : (
              <p className="text-gray-600 text-center py-8">No price data available</p>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardBody>
            {historyLoading ? (
              <LoadingSpinner />
            ) : historyError ? (
              <ErrorMessage message={historyError} onRetry={refetchHistory} />
            ) : historyData ? (
              <VolumeChart 
                data={historyData.data} 
                symbol={symbol}
                height={400}
              />
            ) : (
              <p className="text-gray-600 text-center py-8">No volume data available</p>
            )}
          </CardBody>
        </Card>
      </div>

      {/* Metrics Data */}
      {metricsData && (
        <Card>
          <CardHeader>
            <h2 className="text-xl font-semibold text-gray-900">Detailed Metrics</h2>
          </CardHeader>
          <CardBody>
            {metricsLoading ? (
              <LoadingSpinner />
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Time
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Price
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Volume
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Volatility
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Risk Score
                      </th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {metricsData.metrics.slice(0, 10).map((metric, index) => (
                      <tr key={index}>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {formatDate(metric.timestamp)}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {metric.price ? formatCurrency(metric.price) : '-'}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {metric.volume ? formatVolume(metric.volume) : '-'}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {metric.volatility ? `${(metric.volatility * 100).toFixed(2)}%` : '-'}
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                          {metric.riskScore ? metric.riskScore.toFixed(1) : '-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardBody>
        </Card>
      )}
    </div>
  );
};

export default StockDetail;