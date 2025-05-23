plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "2.2.20"
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Spring Boot API server for the stock monitor"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator") // For Prometheus metrics
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // implementation("com.influxdb:influxdb-client-kotlin:7.3.0")
    // implementation("org.springframework.boot:spring-boot-starter-data-cassandra")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}

// For Kotlin Spring plugin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "24"
    }
}

tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
    enabled = true
}

tasks.withType<Test> {
    useJUnitPlatform()
}