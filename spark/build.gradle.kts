plugins {
    kotlin("jvm")
    application
}

description = "Processes real-time data streams using Apache Spark Structured Streaming"

application {
    mainClass.set("com.harshsbajwa.stockifai.processing.Processing")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(listOf("-Xjsr305=strict"))
    }
}

dependencies {
    val sparkVersion: String by project.extra

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Spark Core and SQL - Use implementation for runtime
    implementation("org.apache.spark:spark-core_2.12:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    implementation("org.apache.spark:spark-sql_2.12:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    // Spark Structured Streaming with Kafka
    implementation("org.apache.spark:spark-sql-kafka-0-10_2.12:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // InfluxDB client
    implementation("com.influxdb:influxdb-client-kotlin:7.3.0")

    // Cassandra driver
    implementation("org.apache.cassandra:java-driver-core:4.19.0")
    implementation("org.apache.cassandra:java-driver-query-builder:4.19.0")
    implementation("org.apache.cassandra:java-driver-mapper-runtime:4.19.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Configuration
    implementation("com.typesafe:config:1.4.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    
    // TestContainers
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:cassandra")
    testImplementation("org.testcontainers:influxdb")
    
    // Kotlin test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // Spark testing - need full spark jars for tests
    testImplementation("org.apache.spark:spark-core_2.12:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    testImplementation("org.apache.spark:spark-sql_2.12:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    testImplementation("org.apache.spark:spark-sql-kafka-0-10_2.12:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    
    // Additional database clients for testing
    testImplementation("com.influxdb:influxdb-client-kotlin:7.3.0")
    testImplementation("org.apache.cassandra:java-driver-core:4.19.0")
    
    // Kafka clients for test data production
    testImplementation("org.apache.kafka:kafka-clients:3.8.1")
}

tasks.test {
    useJUnitPlatform()
    
    // Increase memory for Spark tests
    minHeapSize = "1g"
    maxHeapSize = "4g"
    
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED", 
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/sun.security.action=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "java.base/java.net=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.fs=ALL-UNNAMED",
        
        // Spark specific JVM args
        "-Djava.security.manager=default",
        "-Dio.netty.tryReflectionSetAccessible=true"
    )
    
    // System properties for tests
    systemProperties = mapOf(
        "spark.master" to "local[2]",
        "spark.app.name" to "SparkProcessorTest",
        "spark.sql.adaptive.enabled" to "true",
        "spark.sql.adaptive.coalescePartitions.enabled" to "true",
        "spark.sql.streaming.forceDeleteTempCheckpointLocation" to "true"
    )
    
    // Parallel test execution
    maxParallelForks = 1 // Keep at 1 for integration tests to avoid resource conflicts
    
    // Test logging
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

// Create a fat JAR for deployment
tasks.jar {
    enabled = true
    archiveFileName.set("spark-processor.jar")
    isZip64 = true

    manifest {
        attributes(
            "Main-Class" to "com.harshsbajwa.stockifai.processing.Processing",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }

    // Create fat JAR with all dependencies
    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        },
    ) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        // Exclude signature files to avoid issues
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/versions/**")
    }
}

// Task to run the application locally with environment variables
tasks.register<JavaExec>("runLocal") {
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.harshsbajwa.stockifai.processing.Processing")

    // Set local Spark configuration
    systemProperties = mapOf(
        "spark.master" to "local[*]",
        "spark.app.name" to "StockifAI-StructuredStreaming-Local",
        "spark.sql.adaptive.enabled" to "true",
        "spark.sql.adaptive.coalescePartitions.enabled" to "true",
        "spark.sql.streaming.forceDeleteTempCheckpointLocation" to "true",
    )

    // Set environment variables for local testing
    environment =
        mapOf(
            // Kafka Configuration
            "KAFKA_BOOTSTRAP_SERVERS" to "localhost:9092",
            "KAFKA_TOPICS" to "stock_prices,economic_indicators",
            // InfluxDB Configuration
            "INFLUXDB_URL" to "http://localhost:8086",
            "INFLUXDB_TOKEN" to "your-influxdb-token",
            "INFLUXDB_ORG" to "stockifai",
            "INFLUXDB_BUCKET" to "stocks",
            // Cassandra Configuration
            "CASSANDRA_HOST" to "localhost",
            "CASSANDRA_PORT" to "9042",
            "CASSANDRA_KEYSPACE" to "stockifai",
        )
}
