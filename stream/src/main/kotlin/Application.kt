package com.yourusername.stockmonitor.stream;

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class Application
fun main(args: Array<String>) {
    println("Starting Collector...")
    runApplication<Application>(*args)
    println("Collector has started.")
}