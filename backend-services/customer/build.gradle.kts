import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"
}

group = "com.acme"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    // Note: Spring milestone repository required for Spring Boot 4.x
    // which is not yet GA. Remove this once Spring Boot 4 is released to Maven Central.
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springCloudVersion"] = "2024.0.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database & Migrations
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core:11.5.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.5.0")

    // UUID v7 support
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Observability
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Testing - JUnit 6
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.vintage")
    }
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.3")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.3")
    testImplementation("org.testcontainers:testcontainers-kafka:2.0.3")
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
