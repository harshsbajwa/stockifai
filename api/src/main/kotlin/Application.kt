package com.yourusername.stockmonitor.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    println("Starting Backend API Server...")
    runApplication<Application>(*args)
    println("Backend API Server has started.")
}
