package com.acme.notification.application.eventhandlers

import com.acme.notification.application.SendWelcomeEmailResult
import com.acme.notification.application.SendWelcomeEmailUseCase
import com.acme.notification.infrastructure.client.CustomerQueryResult
import com.acme.notification.infrastructure.client.CustomerServiceClient
import com.acme.notification.infrastructure.client.dto.CustomerDto
import com.acme.notification.infrastructure.messaging.dto.CustomerActivatedEvent
import com.acme.notification.infrastructure.persistence.ProcessedEvent
import com.acme.notification.infrastructure.persistence.ProcessedEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Handler for CustomerActivated events from the Customer Service.
 *
 * This component processes incoming CustomerActivated events and
 * triggers the sending of welcome emails by fetching customer details
 * and delegating to the SendWelcomeEmailUseCase.
 */
@Component
class CustomerActivatedHandler(
    private val sendWelcomeEmailUseCase: SendWelcomeEmailUseCase,
    private val customerServiceClient: CustomerServiceClient,
    private val processedEventRepository: ProcessedEventRepository
) {
    private val logger = LoggerFactory.getLogger(CustomerActivatedHandler::class.java)

    /**
     * Handles a CustomerActivated event by sending a welcome email.
     *
     * This method first checks for idempotency, then fetches customer details from
     * the Customer Service (outside of transaction) to avoid holding database connections
     * during remote HTTP calls. The actual email sending and event marking happen in a
     * separate transactional method with a re-check for idempotency to handle race conditions.
     *
     * @param event The CustomerActivated event from Kafka.
     * @throws RuntimeException If email sending fails.
     */
    fun handle(event: CustomerActivatedEvent) {
        logger.info(
            "Handling CustomerActivated event {} for customer {}",
            event.eventId,
            event.payload.customerId
        )

        // Quick idempotency check to avoid unnecessary work
        if (isEventAlreadyProcessed(event.eventId)) {
            logger.info(
                "Event {} already processed, skipping (customer: {})",
                event.eventId,
                event.payload.customerId
            )
            return
        }

        // Fetch customer details from Customer Service OUTSIDE of transaction
        // to avoid holding database connections during remote HTTP call
        val customerResult = customerServiceClient.getCustomerById(event.payload.customerId)

        when (customerResult) {
            is CustomerQueryResult.Success -> {
                val customer = customerResult.customer
                
                // Process event within transaction (with idempotency re-check)
                processEventTransactionally(event, customer)
            }

            is CustomerQueryResult.NotFound -> {
                logger.error(
                    "Customer {} not found in Customer Service for event {}",
                    event.payload.customerId,
                    event.eventId
                )
                throw IllegalStateException(
                    "Customer ${event.payload.customerId} not found for CustomerActivated event ${event.eventId}"
                )
            }

            is CustomerQueryResult.Error -> {
                logger.error(
                    "Error fetching customer {} for event {}: {}",
                    event.payload.customerId,
                    event.eventId,
                    customerResult.message,
                    customerResult.cause
                )
                throw RuntimeException(customerResult.message, customerResult.cause)
            }
        }
    }

    /**
     * Checks if an event has already been processed.
     * 
     * This is a quick check to avoid unnecessary HTTP calls to Customer Service.
     * The check is performed again within the `processEventTransactionally` method
     * to handle race conditions where another thread might process the same event
     * between this check and the actual transaction.
     *
     * @param eventId The event ID to check.
     * @return true if the event was already processed, false otherwise.
     */
    @Transactional(readOnly = true)
    private fun isEventAlreadyProcessed(eventId: UUID): Boolean {
        return processedEventRepository.existsByEventId(eventId)
    }

    /**
     * Processes the event transactionally after customer data has been fetched.
     * 
     * This method performs the idempotency check again (within the transaction) to handle
     * the race condition where another thread might have processed the same event between
     * the initial check in `isEventAlreadyProcessed` and this transaction. All database
     * operations (idempotency check, sending email, marking event as processed) are
     * performed within the same transaction to ensure consistency.
     *
     * @param event The CustomerActivated event from Kafka.
     * @param customer The customer data fetched from Customer Service.
     * @throws RuntimeException If email sending fails.
     */
    @Transactional
    private fun processEventTransactionally(
        event: CustomerActivatedEvent,
        customer: CustomerDto
    ) {
        // Re-check idempotency within transaction to handle race conditions
        if (processedEventRepository.existsByEventId(event.eventId)) {
            logger.info(
                "Event {} already processed (detected in transaction), skipping (customer: {})",
                event.eventId,
                event.payload.customerId
            )
            return
        }

        val result = sendWelcomeEmailUseCase.execute(
            customerId = event.payload.customerId,
            email = customer.email.address,
            firstName = customer.name.firstName,
            displayName = customer.name.displayName,
            customerNumber = customer.customerNumber,
            marketingOptIn = customer.preferences.communication.marketing,
            profileCompleteness = customer.profileCompleteness,
            correlationId = event.correlationId
        )

        when (result) {
            is SendWelcomeEmailResult.Success -> {
                logger.info(
                    "Sent welcome email to {} for customer {}, notification ID: {}",
                    customer.email.address,
                    event.payload.customerId,
                    result.notificationId
                )
            }

            is SendWelcomeEmailResult.AlreadySent -> {
                logger.info(
                    "Welcome email already sent for customer {}, notification ID: {}",
                    event.payload.customerId,
                    result.notificationId
                )
            }

            is SendWelcomeEmailResult.Failure -> {
                logger.error(
                    "Failed to send welcome email to {} for customer {}: {}",
                    customer.email.address,
                    event.payload.customerId,
                    result.message,
                    result.cause
                )
                throw RuntimeException(result.message, result.cause)
            }
        }

        // Mark event as processed within same transaction
        processedEventRepository.save(
            ProcessedEvent(
                eventId = event.eventId,
                eventType = event.eventType
            )
        )
    }
}
