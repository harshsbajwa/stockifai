plugins {
    kotlin("jvm")
}

description = "Processes real-time data streams using Apache Spark"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    val sparkVersion: String by rootProject.extra

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.apache.spark:spark-core_2.13:$sparkVersion")
    implementation("org.apache.spark:spark-sql_2.13:$sparkVersion")
    implementation("org.apache.spark:spark-streaming_2.13:$sparkVersion")
    implementation("org.apache.spark:spark-streaming-kafka-0-10_2.13:$sparkVersion")

    // Add InfluxDB and Cassandra client dependencies
    implementation("com.influxdb:influxdb-client-kotlin:7.3.0")
    implementation("com.datastax.oss:java-driver-core:4.19.0")

    testImplementation("junit:junit:5")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.harshsbajwa.stockifai.spark.app"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
