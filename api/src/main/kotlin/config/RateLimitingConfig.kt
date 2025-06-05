package com.harshsbajwa.stockifai.api.config

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Configuration
@ConditionalOnProperty(name = ["app.rate-limiting.enabled"], havingValue = "true")
class RateLimitingConfig

@Component
@ConditionalOnProperty(name = ["app.rate-limiting.enabled"], havingValue = "true")
class RateLimitingFilter(
    @Value("\${app.rate-limiting.requests-per-minute:100}")
    private val requestsPerMinute: Long,
) : OncePerRequestFilter() {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val clientId = getClientId(request)
        val bucket = buckets.computeIfAbsent(clientId) { createNewBucket() }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write(
                """
                {
                    "success": false,
                    "message": "Rate limit exceeded. Please try again later.",
                    "timestamp": "${java.time.Instant.now()}"
                }
                """.trimIndent(),
            )
        }
    }

    private fun getClientId(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        val realIp = request.getHeader("X-Real-IP")
        val remoteAddr = request.remoteAddr

        return forwarded?.split(",")?.first()?.trim() ?: realIp ?: remoteAddr ?: "unknown"
    }

    private fun createNewBucket(): Bucket {
        val bandwidth =
            Bandwidth.classic(
                requestsPerMinute,
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)),
            )
        return Bucket
            .builder()
            .addLimit(bandwidth)
            .build()
    }
}
