package com.harshsbajwa.stockifai.api.controller

import org.springframework.web.bind.annotation.*
import java.time.Instant


@RestController
@RequestMapping("/api/v1/health")
class HealthController {

    @GetMapping
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "timestamp" to Instant.now().toString(),
            "service" to "stockifai-api",
            "version" to "1.0.0"
        )
    }
}