plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Spring Boot API server for the stock monitor"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Database clients
    implementation("com.influxdb:influxdb-client-kotlin:7.3.0")
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = true
    archiveFileName.set("api.jar")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
