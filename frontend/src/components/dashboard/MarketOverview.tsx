import React from 'react';
import { TrendingUp, TrendingDown, AlertTriangle, Activity } from 'lucide-react';
import { MarketOverview } from '../../types';
import { formatNumber, getTrendColor } from '../../utils/formatters';
import { Card, CardHeader, CardBody } from '../common/Card';

interface MarketOverviewProps {
  overview: MarketOverview;
}

const MarketOverviewComponent: React.FC<MarketOverviewProps> = ({ overview }) => {
  const getSentimentIcon = () => {
    switch (overview.marketSentiment) {
      case 'BULLISH': return <TrendingUp className="w-6 h-6 text-success-600" />;
      case 'BEARISH': return <TrendingDown className="w-6 h-6 text-danger-600" />;
      case 'NEUTRAL': return <Activity className="w-6 h-6 text-warning-600" />;
      default: return <AlertTriangle className="w-6 h-6 text-gray-600" />;
    }
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
      {/* Market Sentiment */}
      <Card>
        <CardBody>
          <div className="flex items-center">
            {getSentimentIcon()}
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-600">Market Sentiment</p>
              <p className={`text-lg font-bold ${getTrendColor(overview.marketSentiment)}`}>
                {overview.marketSentiment}
              </p>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Active Stocks */}
      <Card>
        <CardBody>
          <div className="flex items-center">
            <Activity className="w-6 h-6 text-primary-600" />
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-600">Active Stocks</p>
              <p className="text-lg font-bold text-gray-900">
                {formatNumber(overview.activeStocks)} / {formatNumber(overview.totalStocks)}
              </p>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Average Risk Score */}
      <Card>
        <CardBody>
          <div className="flex items-center">
            <AlertTriangle className="w-6 h-6 text-warning-600" />
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-600">Avg Risk Score</p>
              <p className="text-lg font-bold text-gray-900">
                {overview.averageRiskScore.toFixed(1)}
              </p>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* High Risk Stocks */}
      <Card>
        <CardBody>
          <div className="flex items-center">
            <AlertTriangle className="w-6 h-6 text-danger-600" />
            <div className="ml-4">
              <p className="text-sm font-medium text-gray-600">High Risk Stocks</p>
              <p className="text-lg font-bold text-danger-600">
                {overview.highRiskStocks.length}
              </p>
            </div>
          </div>
        </CardBody>
      </Card>

      {/* Top Movers */}
      <Card className="md:col-span-2 lg:col-span-4">
        <CardHeader>
          <h3 className="text-lg font-semibold text-gray-900">Top Movers</h3>
        </CardHeader>
        <CardBody>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Top Gainers */}
            <div>
              <h4 className="font-medium text-success-600 mb-3 flex items-center">
                <TrendingUp className="w-4 h-4 mr-2" />
                Top Gainers
              </h4>
              <div className="space-y-2">
                {overview.topMovers.gainers.slice(0, 5).map((stock) => (
                  <div key={stock.symbol} className="flex justify-between items-center">
                    <span className="font-medium">{stock.symbol}</span>
                    <span className="text-success-600 font-semibold">
                      +{stock.changePercent.toFixed(2)}%
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* Top Losers */}
            <div>
              <h4 className="font-medium text-danger-600 mb-3 flex items-center">
                <TrendingDown className="w-4 h-4 mr-2" />
                Top Losers
              </h4>
              <div className="space-y-2">
                {overview.topMovers.losers.slice(0, 5).map((stock) => (
                  <div key={stock.symbol} className="flex justify-between items-center">
                    <span className="font-medium">{stock.symbol}</span>
                    <span className="text-danger-600 font-semibold">
                      {stock.changePercent.toFixed(2)}%
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* Most Volatile */}
            <div>
              <h4 className="font-medium text-warning-600 mb-3 flex items-center">
                <Activity className="w-4 h-4 mr-2" />
                Most Volatile
              </h4>
              <div className="space-y-2">
                {overview.topMovers.mostVolatile.slice(0, 5).map((stock) => (
                  <div key={stock.symbol} className="flex justify-between items-center">
                    <span className="font-medium">{stock.symbol}</span>
                    <span className="text-warning-600 font-semibold">
                      {stock.changePercent.toFixed(2)}%
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
};

export default MarketOverviewComponent;