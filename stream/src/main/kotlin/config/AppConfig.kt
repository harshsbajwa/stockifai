package com.harshsbajwa.stockifai.stream.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app")
data class AppConfig(
    var stocks: String = "PANW,NVDA,AAPL,MSFT,RBLX,NFLX,PYPL,AB,GOOGL,DIS,TSLA,CRM,V,MU,AXP",
    var finnhub: FinnhubProperties = FinnhubProperties(),
    var fred: FredProperties = FredProperties(),
    var alphavantage: AlphavantageProperties = AlphavantageProperties(),
    var collection: CollectionProperties = CollectionProperties(),
    var market: MarketProperties = MarketProperties(),
)

data class FinnhubProperties(
    var api: ApiProperties = ApiProperties(),
    var baseUrl: String = "https://finnhub.io/api/v1",
)

data class FredProperties(
    var api: ApiProperties = ApiProperties(),
    var baseUrl: String = "https://api.stlouisfed.org",
)

data class AlphavantageProperties(
    var api: ApiProperties = ApiProperties(),
    var baseUrl: String = "https://www.alphavantage.co",
)

data class ApiProperties(
    var key: String? = null,
)

data class CollectionProperties(
    var enabled: Boolean = true,
)

data class MarketProperties(
    var hours: MarketHoursProperties = MarketHoursProperties(),
)

data class MarketHoursProperties(
    var only: Boolean = false,
)
