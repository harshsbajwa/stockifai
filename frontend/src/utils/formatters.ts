export const formatCurrency = (value: number, currency: string = 'USD'): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  };
  
  export const formatPercentage = (value: number, decimals: number = 2): string => {
    return `${value >= 0 ? '+' : ''}${value.toFixed(decimals)}%`;
  };
  
  export const formatNumber = (value: number, decimals: number = 0): string => {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(value);
  };
  
  export const formatVolume = (volume: number): string => {
    if (volume >= 1_000_000_000) {
      return `${(volume / 1_000_000_000).toFixed(1)}B`;
    } else if (volume >= 1_000_000) {
      return `${(volume / 1_000_000).toFixed(1)}M`;
    } else if (volume >= 1_000) {
      return `${(volume / 1_000).toFixed(1)}K`;
    }
    return volume.toString();
  };
  
  export const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  };
  
  export const formatDateShort = (dateString: string): string => {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date);
  };
  
  export const getChangeColor = (change: number): string => {
    if (change > 0) return 'text-success-600';
    if (change < 0) return 'text-danger-600';
    return 'text-gray-600';
  };
  
  export const getChangeBgColor = (change: number): string => {
    if (change > 0) return 'bg-success-50';
    if (change < 0) return 'bg-danger-50';
    return 'bg-gray-50';
  };
  
  export const getTrendColor = (trend: string): string => {
    switch (trend) {
      case 'BULLISH': return 'text-success-600';
      case 'BEARISH': return 'text-danger-600';
      case 'SIDEWAYS': return 'text-warning-600';
      default: return 'text-gray-600';
    }
  };
  
  export const getTrendBadgeColor = (trend: string): string => {
    switch (trend) {
      case 'BULLISH': return 'badge-success';
      case 'BEARISH': return 'badge-danger';
      case 'SIDEWAYS': return 'badge-warning';
      default: return 'badge-neutral';
    }
  };
  
  export const getRiskColor = (riskScore: number): string => {
    if (riskScore >= 70) return 'text-danger-600';
    if (riskScore >= 40) return 'text-warning-600';
    return 'text-success-600';
  };
  
  export const getRiskBadgeColor = (riskScore: number): string => {
    if (riskScore >= 70) return 'badge-danger';
    if (riskScore >= 40) return 'badge-warning';
    return 'badge-success';
  };