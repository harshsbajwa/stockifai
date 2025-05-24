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

group = "com.harshsbajwa.stockifai"
version = "0.0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")
    
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
    
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn"))
        }
    }
    
    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs = listOf(
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED"
        )
    }
    
    // Set extra properties for subprojects
    extra["springBootVersion"] = springBootVersion
    extra["sparkVersion"] = sparkVersion
    extra["kafkaVersion"] = kafkaVersion
    extra["libericaJdkVersion"] = libericaJdkVersion
}