import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val springBootVersion = "3.5.0"
val kotlinVersion = "2.1.21"
val sparkVersion = "3.5.5"
val kafkaVersion = "4.0.0"
val libericaJdkVersion = "24.0.1"

plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("org.springframework.boot") version "3.5.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

repositories {
    group = "com.harshsbajwa.stockifai"
    version = "0.0.1-SNAPSHOT"
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "kotlin.jvm")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_24
        toolchain {
            languageVersion = JavaLanguageVersion.of(24)
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_24.toString()
            freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
}