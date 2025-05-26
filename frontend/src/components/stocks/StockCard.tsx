import React from 'react';
import { ArrowUpRight, ArrowDownRight, TrendingUp, TrendingDown, Minus } from 'lucide-react';
import { StockData } from '../../types';
import { formatCurrency, formatPercentage, formatVolume, getChangeColor, getTrendBadgeColor, getRiskBadgeColor } from '../../utils/formatters';
import { Card, CardBody } from '../common/Card';

interface StockCardProps {
  stock: StockData;
  onClick?: () => void;
}

const StockCard: React.FC<StockCardProps> = ({ stock, onClick }) => {
  const isPositive = stock.priceChange >= 0;
  const changeIcon = isPositive ? ArrowUpRight : ArrowDownRight;
  const ChangeIcon = changeIcon;

  const getTrendIcon = () => {
    switch (stock.trend) {
      case 'BULLISH': return <TrendingUp className="w-4 h-4" />;
      case 'BEARISH': return <TrendingDown className="w-4 h-4" />;
      default: return <Minus className="w-4 h-4" />;
    }
  };

  return (
    <Card className={`transition-all duration-200 hover:shadow-lg ${onClick ? 'cursor-pointer' : ''}`}>
      <CardBody>
        <div onClick={onClick}>
          <div className="flex justify-between items-start mb-4">
            <div>
              <h3 className="text-xl font-bold text-gray-900">{stock.symbol}</h3>
              <p className="text-2xl font-semibold text-gray-900 mt-1">
                {formatCurrency(stock.currentPrice)}
              </p>
            </div>
            <div className="text-right">
              <div className={`flex items-center ${getChangeColor(stock.priceChange)}`}>
                <ChangeIcon className="w-4 h-4 mr-1" />
                <span className="font-medium">
                  {formatPercentage(stock.priceChangePercent)}
                </span>
              </div>
              <p className={`text-sm ${getChangeColor(stock.priceChange)}`}>
                {isPositive ? '+' : ''}{formatCurrency(stock.priceChange)}
              </p>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 mb-4">
            <div>
              <p className="text-sm text-gray-600">Volume</p>
              <p className="font-semibold">{formatVolume(stock.volume)}</p>
            </div>
            <div>
              <p className="text-sm text-gray-600">Volatility</p>
              <p className="font-semibold">{(stock.volatility * 100).toFixed(2)}%</p>
            </div>
          </div>

          <div className="flex justify-between items-center">
            <div className="flex items-center space-x-2">
              <span className={`badge ${getTrendBadgeColor(stock.trend)} flex items-center`}>
                {getTrendIcon()}
                <span className="ml-1">{stock.trend}</span>
              </span>
            </div>
            <div>
              <span className={`badge ${getRiskBadgeColor(stock.riskScore)}`}>
                Risk: {stock.riskScore.toFixed(0)}
              </span>
            </div>
          </div>

          {(stock.support || stock.resistance) && (
            <div className="mt-4 pt-4 border-t border-gray-200">
              <div className="grid grid-cols-2 gap-4 text-sm">
                {stock.support && (
                  <div>
                    <p className="text-gray-600">Support</p>
                    <p className="font-semibold text-success-600">
                      {formatCurrency(stock.support)}
                    </p>
                  </div>
                )}
                {stock.resistance && (
                  <div>
                    <p className="text-gray-600">Resistance</p>
                    <p className="font-semibold text-danger-600">
                      {formatCurrency(stock.resistance)}
                    </p>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </CardBody>
    </Card>
  );
};

export default StockCard;