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
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

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
    implementation("org.springframework.boot:spring-boot-flyway")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Resilience4j for Circuit Breaker (using core library directly, not Spring Cloud starter)
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")
    implementation("io.github.resilience4j:resilience4j-micrometer:2.4.0")

    // SendGrid for email
    implementation("com.sendgrid:sendgrid-java:4.10.3")

    // OGNL for Thymeleaf expression evaluation.
    //
    // Pinned to 3.3.x deliberately -- do NOT bump to 3.4.x. OGNL 3.4 changed the
    // OgnlContext constructor, and Thymeleaf 3.1.5's OGNLVariableExpressionEvaluator
    // still calls the 3.3 signature, so every template render dies with:
    //   NoSuchMethodError: 'void ognl.OgnlContext.<init>(
    //       ognl.ClassResolver, ognl.TypeConverter, ognl.MemberAccess)'
    // Verified against 3.4.13 on 2026-09-19: all 12 EmailTemplateServiceTest cases fail.
    // Revisit only when Thymeleaf itself moves to OGNL 3.4.
    //
    // Note this is only needed because EmailTemplateServiceTest builds a plain
    // TemplateEngine (StandardDialect -> OGNL). Production uses SpringTemplateEngine
    // (SpringStandardDialect -> SpEL), and thymeleaf-spring6 explicitly excludes OGNL.
    implementation("ognl:ognl:3.3.4")

    // Observability
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Testing - JUnit 6
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.vintage")
    }
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
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
