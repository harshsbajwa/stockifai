plugins {
    kotlin("jvm")
    application
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

description = "Processes real-time data streams using Apache Spark Structured Streaming"

application {
    mainClass.set("com.harshsbajwa.stockifai.processing.RiskCalculationEngineKt")
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

// Configure Avro code generation
avro {
    createSetters.set(false)
    stringType.set("String")
    outputCharacterEncoding.set("UTF-8")
    enableDecimalLogicalType.set(true)
}

dependencies {
    val sparkVersion: String by project.extra

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Kotlin Spark API
    implementation("org.jetbrains.kotlinx:kotlin-spark-api_3.5_2.12:1.4.1")

    // Spark Core and SQL
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
    
    // Avro support for Spark
    implementation("org.apache.spark:spark-avro_2.12:$sparkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    // Avro dependencies
    implementation("org.apache.avro:avro:1.11.3")
    implementation("io.confluent:kafka-avro-serializer:7.5.0")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // InfluxDB client v3
    implementation("com.influxdb:influxdb3-java:0.8.0")

    // Cassandra driver
    implementation("org.apache.cassandra:java-driver-core:4.19.0")
    implementation("org.apache.cassandra:java-driver-query-builder:4.19.0")
    implementation("org.apache.cassandra:java-driver-mapper-runtime:4.19.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Configuration
    implementation("com.typesafe:config:1.4.3")

    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:cassandra")
    testImplementation("org.testcontainers:influxdb")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.test {
    useJUnitPlatform()
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
        "-Djava.security.manager=default",
        "-Dio.netty.tryReflectionSetAccessible=true",
        "-Dsun.net.useExclusiveBind=false",
        "-Djava.net.preferIPv4Stack=true"
    )
    
    systemProperties = mapOf(
        "spark.master" to "local[2]",
        "spark.app.name" to "SparkProcessorTest",
        "spark.sql.adaptive.enabled" to "true",
        "spark.sql.adaptive.coalescePartitions.enabled" to "true",
        "spark.sql.streaming.forceDeleteTempCheckpointLocation" to "true",
        "java.net.useSystemProxies" to "true"
    )
    
    timeout.set(Duration.ofMinutes(10))
    maxParallelForks = 1
    
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

tasks.jar {
    enabled = true
    archiveFileName.set("spark-processor.jar")
    isZip64 = true

    manifest {
        attributes(
            "Main-Class" to "com.harshsbajwa.stockifai.processing.RiskCalculationEngineKt",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
        )
    }

    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        },
    ) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
        exclude("META-INF/versions/**")
    }
}

tasks.register<JavaExec>("runLocal") {
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.harshsbajwa.stockifai.processing.RiskCalculationEngineKt")

    systemProperties = mapOf(
        "spark.master" to "local[*]",
        "spark.app.name" to "StockifAI-RiskCalculationEngine-Local",
        "spark.sql.adaptive.enabled" to "true",
        "spark.sql.adaptive.coalescePartitions.enabled" to "true",
        "spark.sql.streaming.forceDeleteTempCheckpointLocation" to "true",
    )

    environment = mapOf(
        "KAFKA_BOOTSTRAP_SERVERS" to "localhost:9092",
        "SCHEMA_REGISTRY_URL" to "http://localhost:8081",
        "INFLUXDB_URL" to "http://localhost:8086",
        "INFLUXDB_TOKEN" to "your-influxdb-token",
        "INFLUXDB_ORG" to "stockifai",
        "INFLUXDB_BUCKET" to "stocks",
        "CASSANDRA_HOST" to "localhost",
        "CASSANDRA_PORT" to "9042",
        "CASSANDRA_KEYSPACE" to "finrisk_reference_data"
    )
}
