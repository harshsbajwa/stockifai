plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "2.0.21"
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

description = "Data ingestion services for Finnhub and FRED APIs"

repositories {
    mavenCentral()
    // Confluent repository for Kafka dependencies
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Avro dependencies
    implementation("org.apache.avro:avro:1.11.3")
    implementation("io.confluent:kafka-avro-serializer:7.9.1")

    // For @EnableRetry support
    implementation("org.springframework:spring-aspects")
    implementation("org.aspectj:aspectjweaver:1.9.19")

    // Test dependencies
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.3"))
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.wiremock:wiremock-standalone:3.4.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
}

// Configure Avro code generation
avro {
    isCreateSetters.set(false)
    stringType.set("Utf8")
    outputCharacterEncoding.set("UTF-8")
    isEnableDecimalLogicalType.set(true)
}
tasks.configureEach {
    if (name.contains("ktlint", ignoreCase = true) || name.contains("KtLint")) {
        dependsOn("generateAvroJava")
        dependsOn("generateTestAvroJava")
    }
}

tasks.whenTaskAdded {
    if (name.startsWith("runKtlintCheckOver") || name.startsWith("runKtlintFormatOver")) {
        dependsOn("generateAvroJava", "generateTestAvroJava")
    }
}

ktlint {
    filter {
        exclude("**/generated/**")
        exclude("**/build/generated/**")
    }
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = true
    archiveFileName.set("stream.jar")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

afterEvaluate {
    tasks.findByName("runKtlintCheckOverTestSourceSet")?.let { ktlintTask ->
        ktlintTask.dependsOn("generateTestAvroJava")
    }

    tasks.findByName("runKtlintCheckOverMainSourceSet")?.let { ktlintTask ->
        ktlintTask.dependsOn("generateAvroJava")
    }
}
