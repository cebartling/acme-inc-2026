package com.acme.customer.infrastructure.projection

import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Projects customer data to MongoDB read model.
 *
 * This component implements the CQRS pattern by maintaining a
 * denormalized read model in MongoDB that is optimized for queries.
 * The projection runs asynchronously to avoid blocking the main
 * transaction flow.
 */
@Component
class CustomerReadModelProjector(
    private val mongoTemplate: MongoTemplate,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(CustomerReadModelProjector::class.java)

    private val collectionName = "customers"

    private val projectionTimer: Timer = Timer.builder("customer.read_model.projection.duration")
        .register(meterRegistry)

    /**
     * Projects a customer and their preferences to MongoDB.
     *
     * This method runs asynchronously and creates/updates the
     * customer document in MongoDB. The document structure is
     * denormalized for efficient querying.
     *
     * Exceptions are propagated back to the caller so that the
     * main transaction can be rolled back if projection fails.
     *
     * @param customer The customer entity to project.
     * @param preferences The customer's preferences.
     * @return A CompletableFuture that completes when projection is done.
     */
    @Async
    fun projectCustomer(
        customer: Customer,
        preferences: CustomerPreferences
    ): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            val startTime = System.nanoTime()
            try {
                val document = buildDocument(customer, preferences)
                mongoTemplate.save(document, collectionName)

                logger.info(
                    "Projected customer {} to MongoDB read model",
                    customer.id
                )
                projectionTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
            } catch (e: Exception) {
                projectionTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                logger.error(
                    "Failed to project customer {} to MongoDB: {}",
                    customer.id,
                    e.message,
                    e
                )
                throw e
            }
        }
    }

    /**
     * Builds the MongoDB document from customer and preferences.
     *
     * The document structure follows the schema defined in US-0002-03
     * and is optimized for common query patterns.
     */
    private fun buildDocument(
        customer: Customer,
        preferences: CustomerPreferences
    ): Document {
        return Document().apply {
            put("_id", customer.id.toString())
            put("userId", customer.userId.toString())
            put("customerNumber", customer.customerNumber)

            put("name", Document().apply {
                put("firstName", customer.firstName)
                put("lastName", customer.lastName)
                put("displayName", customer.displayName)
            })

            put("email", Document().apply {
                put("address", customer.email)
                put("verified", customer.emailVerified)
            })

            put("phone", customer.phoneNumber?.let {
                Document().apply {
                    put("countryCode", customer.phoneCountryCode)
                    put("number", it)
                    put("verified", customer.phoneVerified ?: false)
                }
            })

            put("status", customer.status.name)
            put("type", customer.type.name)

            put("profile", Document().apply {
                put("dateOfBirth", customer.dateOfBirth?.toString())
                put("gender", customer.gender)
                put("preferredLocale", customer.preferredLocale)
                put("timezone", customer.timezone)
                put("preferredCurrency", customer.preferredCurrency)
            })

            put("preferences", Document().apply {
                put("communication", Document().apply {
                    put("email", preferences.emailNotifications)
                    put("sms", preferences.smsNotifications)
                    put("push", preferences.pushNotifications)
                    put("marketing", preferences.marketingCommunications)
                })
                put("privacy", Document().apply {
                    put("shareDataWithPartners", preferences.shareDataWithPartners)
                    put("allowAnalytics", preferences.allowAnalytics)
                })
            })

            put("addresses", listOf<Document>())
            put("segments", listOf<String>())

            put("registeredAt", customer.registeredAt.toString())
            put("lastActivityAt", customer.lastActivityAt.toString())
            put("profileCompleteness", customer.profileCompleteness)
            put("_version", 1)
        }
    }

    /**
     * Deletes a customer from the read model.
     *
     * Exceptions are propagated back to the caller so that the
     * main transaction can be rolled back if deletion fails.
     *
     * @param customerId The customer ID to delete.
     */
    @Async
    fun deleteCustomer(customerId: String): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            val startTime = System.nanoTime()
            try {
                mongoTemplate.remove(
                    org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").`is`(customerId)
                    ),
                    collectionName
                )
                logger.info("Deleted customer {} from MongoDB read model", customerId)
                projectionTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
            } catch (e: Exception) {
                projectionTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS)
                logger.error(
                    "Failed to delete customer {} from MongoDB: {}",
                    customerId,
                    e.message,
                    e
                )
                throw e
            }
        }
    }
}
