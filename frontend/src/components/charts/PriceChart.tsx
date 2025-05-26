import React from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ReferenceLine
} from 'recharts';
import { StockDataPoint } from '../../types';
import { formatCurrency, formatDateShort } from '../../utils/formatters';

interface PriceChartProps {
  data: StockDataPoint[];
  symbol: string;
  support?: number;
  resistance?: number;
  height?: number;
}

const PriceChart: React.FC<PriceChartProps> = ({ 
  data, 
  symbol, 
  support, 
  resistance, 
  height = 300 
}) => {
  const chartData = data.map(point => ({
    ...point,
    timestamp: new Date(point.timestamp).getTime(),
    formattedTime: formatDateShort(point.timestamp)
  })).sort((a, b) => a.timestamp - b.timestamp);

  const minPrice = Math.min(...chartData.map(d => d.price));
  const maxPrice = Math.max(...chartData.map(d => d.price));
  const padding = (maxPrice - minPrice) * 0.05;

  return (
    <div className="w-full">
      <div className="mb-4">
        <h3 className="text-lg font-semibold text-gray-900">
          {symbol} Price Chart
        </h3>
      </div>
      
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e0e7ff" />
          <XAxis 
            dataKey="formattedTime"
            tick={{ fontSize: 12 }}
            stroke="#6b7280"
          />
          <YAxis 
            domain={[minPrice - padding, maxPrice + padding]}
            tickFormatter={(value) => `$${value.toFixed(2)}`}
            tick={{ fontSize: 12 }}
            stroke="#6b7280"
          />
          <Tooltip 
            formatter={(value: number) => [formatCurrency(value), 'Price']}
            labelFormatter={(label) => `Time: ${label}`}
            contentStyle={{
              backgroundColor: 'white',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'
            }}
          />
          
          {/* Support and Resistance Lines */}
          {support && (
            <ReferenceLine 
              y={support} 
              stroke="#10b981" 
              strokeDasharray="5 5" 
              label="Support"
            />
          )}
          {resistance && (
            <ReferenceLine 
              y={resistance} 
              stroke="#ef4444" 
              strokeDasharray="5 5" 
              label="Resistance"
            />
          )}
          
          <Line 
            type="monotone" 
            dataKey="price" 
            stroke="#3b82f6" 
            strokeWidth={2}
            dot={{ fill: '#3b82f6', strokeWidth: 2, r: 3 }}
            activeDot={{ r: 5, stroke: '#3b82f6', strokeWidth: 2 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
};

export default PriceChart;