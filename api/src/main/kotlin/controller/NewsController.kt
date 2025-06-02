package com.harshsbajwa.stockifai.api.controller

import com.harshsbajwa.stockifai.api.dto.ApiResponse
import com.harshsbajwa.stockifai.api.dto.NewsItem
import com.harshsbajwa.stockifai.api.service.NewsService 
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api/v1/news")
@CrossOrigin(origins = ["*"])
class NewsController(
    private val newsService: NewsService
) {

    @GetMapping
    fun getMarketNews(
        @RequestParam(defaultValue = "24") hours: Long,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<ApiResponse<List<NewsItem>>> {
        val newsData = newsService.getRecentNews(hours, limit)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = newsData,
            message = if (newsData.isEmpty()) "No news data available" else "News data retrieved successfully"
        ))
    }

    @GetMapping("/sentiment/{sentiment}")
    fun getNewsBySentiment(
        @PathVariable sentiment: String,
        @RequestParam(defaultValue = "24") hours: Long,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<List<NewsItem>>> {
        val newsData = newsService.getNewsBySentiment(sentiment, hours, limit)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = newsData,
            message = if (newsData.isEmpty()) "No news data available for sentiment $sentiment" else "News data retrieved successfully"
        ))
    }

    @GetMapping("/symbol/{symbol}")
    fun getNewsForSymbol(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "24") hours: Long,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<ApiResponse<List<NewsItem>>> {
        val newsData = newsService.getNewsForSymbol(symbol, hours, limit)
        
        return ResponseEntity.ok(ApiResponse(
            success = true,
            data = newsData,
            message = if (newsData.isEmpty()) "No news data available for symbol $symbol" else "News data retrieved successfully"
        ))
    }
}