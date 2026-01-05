package com.acme.customer.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

/**
 * MongoDB configuration for the Customer Service.
 *
 * Configures MongoDB for storing the read model (query side of CQRS).
 * The actual connection settings are provided via application.yml.
 */
@Configuration
@EnableMongoRepositories(basePackages = ["com.acme.customer.infrastructure.projection"])
@EnableMongoAuditing
class MongoConfig
