import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val springBootVersion = "3.5.0"
val kotlinVersion = "2.0.21"
val sparkVersion = "3.5.5"
val kafkaVersion = "4.0.0"
val libericaJdkVersion = "21"
val testcontainersVersion = "1.21.0"

plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.springframework.boot") version "3.5.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

group = "com.harshsbajwa.stockifai"

version = "0.0.1-SNAPSHOT"

allprojects { repositories { mavenCentral() } }

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.6.0")
        verbose.set(false)
        android.set(false)
        ignoreFailures.set(true)
        outputToConsole.set(true)
        outputColorName.set("RED")
        enableExperimentalRules.set(true)
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        toolVersion = "1.23.8"
        buildUponDefaultConfig = true
        allRules = false
        ignoreFailures = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn"))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs =
                listOf(
                        "--add-opens",
                        "java.base/java.lang=ALL-UNNAMED",
                        "--add-opens",
                        "java.base/java.util=ALL-UNNAMED"
                )
    }

    extra["springBootVersion"] = springBootVersion
    extra["sparkVersion"] = sparkVersion
    extra["kafkaVersion"] = kafkaVersion
    extra["libericaJdkVersion"] = libericaJdkVersion
}
