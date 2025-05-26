import React from 'react';
import { useNavigate } from 'react-router-dom';
import { StockAPI } from '../services/api';
import { usePolling } from '../hooks/useApi';
import StockList from '../components/stocks/StockList';

const Stocks: React.FC = () => {
  const navigate = useNavigate();

  const { 
    data: stocksData, 
    loading, 
    error,
    refetch
  } = usePolling(() => StockAPI.getAllStocks(0, 50), 30000);

  const handleStockClick = (symbol: string) => {
    navigate(`/stocks/${symbol}`);
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-gray-900">All Stocks</h1>
        <p className="text-gray-600 mt-2">Browse and monitor all available stocks</p>
      </div>

      <StockList
        stocks={stocksData?.data || []}
        loading={loading}
        error={error}
        onStockClick={handleStockClick}
        onRetry={refetch}
      />
    </div>
  );
};

export default Stocks;