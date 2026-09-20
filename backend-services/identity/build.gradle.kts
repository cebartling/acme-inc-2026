import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    kotlin("plugin.jpa") version "2.4.20"
}

group = "com.acme"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}


dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin
    // Jackson 3 Kotlin module: Spring MVC (Boot 4) binds request/response bodies with Jackson 3
    implementation("tools.jackson.module:jackson-module-kotlin")
    // Jackson 2 modules: used by JacksonConfig's ObjectMapper (Kafka, event store, Redis)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Arrow - Functional Programming
    implementation("io.arrow-kt:arrow-core:2.2.3")
    implementation("io.arrow-kt:arrow-fx-coroutines:2.2.3")
    implementation("io.arrow-kt:arrow-core-serialization:2.2.3")

    // Database & Migrations
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core:12.6.2")
    implementation("org.flywaydb:flyway-database-postgresql:12.6.2")

    // Password hashing - Argon2id via Password4j
    implementation("com.password4j:password4j:1.8.4")

    // UUID v7 support
    implementation("com.fasterxml.uuid:java-uuid-generator:5.2.0")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Observability
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Rate limiting
    implementation("com.bucket4j:bucket4j_jdk17-core:8.20.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    // TOTP (Time-based One-Time Password) for MFA
    implementation("dev.samstevens.totp:totp:1.7.1")

    // Twilio SDK for SMS MFA
    implementation("com.twilio.sdk:twilio:12.1.1")

    // JWT - Nimbus JOSE JWT for RS256 token generation
    implementation("com.nimbusds:nimbus-jose-jwt:10.10")

    // Redis for session storage (lettuce-core is transitively included)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Testing - JUnit 6
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.vintage")
    }
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-kafka:2.0.5")
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
