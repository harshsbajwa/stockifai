package com.harshsbajwa.stockifai.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AnalysisApiApplication

fun main(args: Array<String>) {
    println("Starting StockifAI Analysis API...")
    runApplication<AnalysisApiApplication>(*args)
    println("StockifAI Analysis API started successfully.")
}