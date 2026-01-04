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
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
    // Note: Spring milestone repository required for Spring Boot 4.x
    // which is not yet GA. Remove this once Spring Boot 4 is released to Maven Central.
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://packages.confluent.io/maven/") }
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

    // Password hashing - Argon2id via Password4j
    implementation("com.password4j:password4j:1.8.2")

    // UUID v7 support
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

    // Kafka with Avro
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.confluent:kafka-avro-serializer:7.7.1")
    implementation("org.apache.avro:avro:1.12.0")

    // Observability
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Rate limiting
    implementation("com.bucket4j:bucket4j_jdk17-core:8.16.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

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
