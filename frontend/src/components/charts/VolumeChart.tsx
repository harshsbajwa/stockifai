import React from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer
} from 'recharts';
import { StockDataPoint } from '../../types';
import { formatVolume, formatDateShort } from '../../utils/formatters';

interface VolumeChartProps {
  data: StockDataPoint[];
  symbol: string;
  height?: number;
}

const VolumeChart: React.FC<VolumeChartProps> = ({ data, symbol, height = 200 }) => {
  const chartData = data.map(point => ({
    ...point,
    timestamp: new Date(point.timestamp).getTime(),
    formattedTime: formatDateShort(point.timestamp)
  })).sort((a, b) => a.timestamp - b.timestamp);

  return (
    <div className="w-full">
      <div className="mb-4">
        <h3 className="text-lg font-semibold text-gray-900">
          {symbol} Volume Chart
        </h3>
      </div>
      
      <ResponsiveContainer width="100%" height={height}>
        <BarChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#e0e7ff" />
          <XAxis 
            dataKey="formattedTime"
            tick={{ fontSize: 12 }}
            stroke="#6b7280"
          />
          <YAxis 
            tickFormatter={(value) => formatVolume(value)}
            tick={{ fontSize: 12 }}
            stroke="#6b7280"
          />
          <Tooltip 
            formatter={(value: number) => [formatVolume(value), 'Volume']}
            labelFormatter={(label) => `Time: ${label}`}
            contentStyle={{
              backgroundColor: 'white',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'
            }}
          />
          <Bar 
            dataKey="volume" 
            fill="#8b5cf6"
            radius={[2, 2, 0, 0]}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};

export default VolumeChart;