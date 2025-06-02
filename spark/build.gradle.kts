import java.time.Duration

plugins {
    kotlin("jvm")
    application
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

description = "Processes real-time data streams using Apache Spark Structured Streaming"

repositories {
    mavenCentral()
    // Confluent repository for Kafka dependencies
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

application {
    mainClass.set("com.harshsbajwa.stockifai.processing.RiskCalculationEngineKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(listOf("-Xjsr305=strict"))
    }
}

// Configure Avro code generation
avro {
    isCreateSetters.set(false)
    stringType.set("Utf8")
    outputCharacterEncoding.set("UTF-8")
    isEnableDecimalLogicalType.set(true)
}

// Fix dependency issues with generated code
tasks.matching { it.name.contains("ktlint") }.configureEach {
    dependsOn("generateAvroJava", "generateTestAvroJava")
    mustRunAfter("generateAvroJava", "generateTestAvroJava")
}

tasks.matching { it.name.contains("detekt") }.configureEach {
    dependsOn("generateAvroJava", "generateTestAvroJava")
    mustRunAfter("generateAvroJava", "generateTestAvroJava")
}

dependencies {
    val sparkVersion: String by project
    val scalaVersion: String by project

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Scala library (required for Spark)
    implementation("org.scala-lang:scala-library:2.12.18")

    // Spark Core and SQL
    implementation("org.apache.spark:spark-core_${scalaVersion}:${sparkVersion}") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    implementation("org.apache.spark:spark-sql_${scalaVersion}:${sparkVersion}") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    // Spark Structured Streaming with Kafka
    implementation("org.apache.spark:spark-sql-kafka-0-10_${scalaVersion}:${sparkVersion}") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }
    
    // Avro support for Spark
    implementation("org.apache.spark:spark-avro_${scalaVersion}:${sparkVersion}") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
    }

    // Avro dependencies
    implementation("org.apache.avro:avro:1.11.3")
    implementation("io.confluent:kafka-avro-serializer:7.9.1")
    implementation("io.confluent:kafka-schema-registry-client:7.9.1")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

    // DataStax Java Driver for Apache Cassandra (for AstraDB native protocol)
    implementation("com.datastax.oss:java-driver-core:4.17.0")
    implementation("com.datastax.oss:java-driver-query-builder:4.17.0")

    // InfluxDB client
    implementation("com.influxdb:influxdb-client-java:7.3.0")

    // Cassandra driver
    implementation("org.apache.cassandra:java-driver-core:4.19.0")
    implementation("org.apache.cassandra:java-driver-query-builder:4.19.0")
    implementation("org.apache.cassandra:java-driver-mapper-runtime:4.19.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1")
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")

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
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens", "java.base/sun.util.calendar=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio.channels=ALL-UNNAMED",
        "--add-opens", "java.management/sun.management=ALL-UNNAMED",
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
            "Class-Path" to configurations.runtimeClasspath.get().joinToString(" ") { it.name }
        )
    }

    from(
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        },
    ) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
    }
}

tasks.register<JavaExec>("runLocal") {
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.harshsbajwa.stockifai.processing.RiskCalculationEngineKt")

    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED", 
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/sun.security.action=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "java.base/java.net=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.fs=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens", "java.base/sun.util.calendar=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio.channels=ALL-UNNAMED",
        "--add-opens", "java.management/sun.management=ALL-UNNAMED",
        "-Dio.netty.tryReflectionSetAccessible=true",
        "-Dlog4j.configurationFile=src/main/resources/log4j2.xml"
    )

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
        "CASSANDRA_KEYSPACE" to "stockdata"
    )
}