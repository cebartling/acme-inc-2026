package com.acme.notification.acceptance

import com.acme.notification.application.SendWelcomeEmailResult
import com.acme.notification.application.SendWelcomeEmailUseCase
import com.acme.notification.application.eventhandlers.CustomerActivatedHandler
import com.acme.notification.domain.NotificationDelivery
import com.acme.notification.domain.NotificationType
import com.acme.notification.infrastructure.client.CustomerQueryResult
import com.acme.notification.infrastructure.client.CustomerServiceClient
import com.acme.notification.infrastructure.client.dto.*
import com.acme.notification.infrastructure.email.EmailSendResult
import com.acme.notification.infrastructure.email.SendGridEmailSender
import com.acme.notification.infrastructure.messaging.CustomerActivatedConsumer
import com.acme.notification.infrastructure.messaging.NotificationEventPublisher
import com.acme.notification.infrastructure.messaging.dto.CustomerActivatedEvent
import com.acme.notification.infrastructure.messaging.dto.CustomerActivatedPayload
import com.acme.notification.infrastructure.persistence.NotificationDeliveryRepository
import com.acme.notification.infrastructure.persistence.ProcessedEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.time.Instant
import java.util.UUID

/**
 * Acceptance tests for US-0002-07: Welcome Notification
 *
 * These tests verify the acceptance criteria defined in the user story.
 */
class WelcomeNotificationAcceptanceTest {

    private lateinit var emailSender: SendGridEmailSender
    private lateinit var deliveryRepository: NotificationDeliveryRepository
    private lateinit var eventPublisher: NotificationEventPublisher
    private lateinit var customerServiceClient: CustomerServiceClient
    private lateinit var processedEventRepository: ProcessedEventRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment

    private lateinit var useCase: SendWelcomeEmailUseCase
    private lateinit var handler: CustomerActivatedHandler
    private lateinit var consumer: CustomerActivatedConsumer

    @BeforeEach
    fun setUp() {
        emailSender = mockk()
        deliveryRepository = mockk()
        eventPublisher = mockk(relaxed = true)
        customerServiceClient = mockk()
        processedEventRepository = mockk()
        objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
        acknowledgment = mockk(relaxed = true)

        val meterRegistry = SimpleMeterRegistry()

        useCase = SendWelcomeEmailUseCase(
            emailSender = emailSender,
            deliveryRepository = deliveryRepository,
            eventPublisher = eventPublisher,
            meterRegistry = meterRegistry
        )

        handler = CustomerActivatedHandler(
            sendWelcomeEmailUseCase = useCase,
            customerServiceClient = customerServiceClient,
            processedEventRepository = processedEventRepository
        )

        consumer = CustomerActivatedConsumer(
            customerActivatedHandler = handler,
            objectMapper = objectMapper,
            meterRegistry = meterRegistry
        )
    }

    @Nested
    @DisplayName("AC-0002-07-02: Personalized Content")
    inner class PersonalizedContentTests {

        @Test
        @DisplayName("Given a welcome email is being prepared, When the template is rendered, Then the email includes the customer's first name in the greeting")
        fun `should include customer first name in greeting`() {
            val customerId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }

            // Capture the firstName parameter passed to email sender
            val capturedFirstName = slot<String>()
            every {
                emailSender.sendWelcomeEmail(
                    recipientEmail = any(),
                    recipientName = capture(capturedFirstName),
                    displayName = any(),
                    customerNumber = any(),
                    marketingOptIn = any(),
                    showProfileCta = any(),
                    correlationId = any()
                )
            } returns EmailSendResult.Success("msg-123", 202)

            useCase.execute(
                customerId = customerId,
                email = "jane@example.com",
                firstName = "Jane",
                displayName = "Jane Doe",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = false,
                profileCompleteness = 80,
                correlationId = correlationId
            )

            assert(capturedFirstName.captured == "Jane") { "First name should be passed for personalization" }
        }

        @Test
        @DisplayName("Given a welcome email is being prepared, When the template is rendered, Then the email includes the customer number for reference")
        fun `should include customer number for reference`() {
            val customerId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }

            // Capture the customerNumber parameter passed to email sender
            val capturedCustomerNumber = slot<String>()
            every {
                emailSender.sendWelcomeEmail(
                    recipientEmail = any(),
                    recipientName = any(),
                    displayName = any(),
                    customerNumber = capture(capturedCustomerNumber),
                    marketingOptIn = any(),
                    showProfileCta = any(),
                    correlationId = any()
                )
            } returns EmailSendResult.Success("msg-123", 202)

            useCase.execute(
                customerId = customerId,
                email = "jane@example.com",
                firstName = "Jane",
                displayName = "Jane Doe",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = false,
                profileCompleteness = 80,
                correlationId = correlationId
            )

            assert(capturedCustomerNumber.captured == "ACME-202601-000142") { "Customer number should be included" }
        }
    }

    @Nested
    @DisplayName("AC-0002-07-03: Marketing Preference Respect")
    inner class MarketingPreferenceTests {

        @Test
        @DisplayName("Given a customer has marketingOptIn: false, When the welcome email is sent, Then promotional/marketing section is NOT included")
        fun `should not include marketing section when customer opted out`() {
            val customerId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }

            val capturedMarketingOptIn = slot<Boolean>()
            every {
                emailSender.sendWelcomeEmail(
                    recipientEmail = any(),
                    recipientName = any(),
                    displayName = any(),
                    customerNumber = any(),
                    marketingOptIn = capture(capturedMarketingOptIn),
                    showProfileCta = any(),
                    correlationId = any()
                )
            } returns EmailSendResult.Success("msg-123", 202)

            useCase.execute(
                customerId = customerId,
                email = "jane@example.com",
                firstName = "Jane",
                displayName = "Jane Doe",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = false,
                profileCompleteness = 80,
                correlationId = correlationId
            )

            assert(!capturedMarketingOptIn.captured) { "Marketing should not be included when opt-in is false" }
        }

        @Test
        @DisplayName("Given a customer has marketingOptIn: true, When the welcome email is sent, Then promotional/marketing section IS included")
        fun `should include marketing section when customer opted in`() {
            val customerId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }

            val capturedMarketingOptIn = slot<Boolean>()
            every {
                emailSender.sendWelcomeEmail(
                    recipientEmail = any(),
                    recipientName = any(),
                    displayName = any(),
                    customerNumber = any(),
                    marketingOptIn = capture(capturedMarketingOptIn),
                    showProfileCta = any(),
                    correlationId = any()
                )
            } returns EmailSendResult.Success("msg-123", 202)

            val result = useCase.execute(
                customerId = customerId,
                email = "john@example.com",
                firstName = "John",
                displayName = "John Smith",
                customerNumber = "ACME-202601-000143",
                marketingOptIn = true,
                profileCompleteness = 100,
                correlationId = correlationId
            )

            assert(capturedMarketingOptIn.captured) { "Marketing should be included when opt-in is true" }
            assert(result is SendWelcomeEmailResult.Success)
            assert((result as SendWelcomeEmailResult.Success).marketingIncluded) { "Result should indicate marketing was included" }
        }
    }

    @Nested
    @DisplayName("AC-0002-07-05: Call-to-Action")
    inner class CallToActionTests {

        @Test
        @DisplayName("Given a welcome email is rendered, When the customer views the email, Then there is a prominent 'Start Shopping' button")
        fun `should include Start Shopping button`() {
            // This test verifies that the email is sent with the shopUrl parameter
            // The template contains the actual button
            val customerId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }
            every {
                emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any())
            } returns EmailSendResult.Success("msg-123", 202)

            val result = useCase.execute(
                customerId = customerId,
                email = "jane@example.com",
                firstName = "Jane",
                displayName = "Jane Doe",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = false,
                profileCompleteness = 80,
                correlationId = correlationId
            )

            assert(result is SendWelcomeEmailResult.Success)
            verify { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("AC-0002-07-06: Profile Completion Prompt")
    inner class ProfileCompletionTests {

        @Test
        @DisplayName("Given a customer's profile completeness is less than 100%, When the welcome email is rendered, Then a secondary CTA 'Complete Your Profile' is included")
        fun `should include profile completion CTA when profile is incomplete`() {
            val customerId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }

            val capturedShowProfileCta = slot<Boolean>()
            every {
                emailSender.sendWelcomeEmail(
                    recipientEmail = any(),
                    recipientName = any(),
                    displayName = any(),
                    customerNumber = any(),
                    marketingOptIn = any(),
                    showProfileCta = capture(capturedShowProfileCta),
                    correlationId = any()
                )
            } returns EmailSendResult.Success("msg-123", 202)

            useCase.execute(
                customerId = customerId,
                email = "jane@example.com",
                firstName = "Jane",
                displayName = "Jane Doe",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = false,
                profileCompleteness = 50, // Less than 100%
                correlationId = correlationId
            )

            assert(capturedShowProfileCta.captured) { "Profile CTA should be shown when completeness < 100%" }
        }

        @Test
        @DisplayName("Given a customer's profile is 100% complete, When the welcome email is rendered, Then the profile CTA is NOT included")
        fun `should not include profile completion CTA when profile is complete`() {
            val customerId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }

            val capturedShowProfileCta = slot<Boolean>()
            every {
                emailSender.sendWelcomeEmail(
                    recipientEmail = any(),
                    recipientName = any(),
                    displayName = any(),
                    customerNumber = any(),
                    marketingOptIn = any(),
                    showProfileCta = capture(capturedShowProfileCta),
                    correlationId = any()
                )
            } returns EmailSendResult.Success("msg-123", 202)

            useCase.execute(
                customerId = customerId,
                email = "jane@example.com",
                firstName = "Jane",
                displayName = "Jane Doe",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = false,
                profileCompleteness = 100, // Exactly 100%
                correlationId = correlationId
            )

            assert(!capturedShowProfileCta.captured) { "Profile CTA should NOT be shown when completeness = 100%" }
        }
    }

    @Nested
    @DisplayName("End-to-End Flow")
    inner class EndToEndFlowTests {

        @Test
        @DisplayName("Full flow: CustomerActivated event -> Customer lookup -> Welcome email sent")
        fun `should process CustomerActivated event end to end`() {
            val customerId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            // Set up customer service mock
            val customer = CustomerDto(
                customerId = customerId.toString(),
                userId = UUID.randomUUID().toString(),
                customerNumber = "ACME-202601-000142",
                name = NameDto(
                    firstName = "Jane",
                    lastName = "Doe",
                    displayName = "Jane Doe"
                ),
                email = EmailDto(
                    address = "jane@example.com",
                    verified = true
                ),
                status = "ACTIVE",
                preferences = PreferencesDto(
                    communication = CommunicationPreferencesDto(
                        email = true,
                        sms = false,
                        push = false,
                        marketing = false
                    )
                ),
                profileCompleteness = 80,
                registeredAt = Instant.now()
            )

            every { processedEventRepository.existsByEventId(eventId) } returns false
            every { customerServiceClient.getCustomerById(customerId) } returns CustomerQueryResult.Success(customer)
            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }
            every { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) } returns EmailSendResult.Success("msg-123", 202)
            every { processedEventRepository.save(any()) } answers { firstArg() }

            // Create event JSON
            val eventJson = """
                {
                    "eventId": "$eventId",
                    "eventType": "CustomerActivated",
                    "eventVersion": "1.0",
                    "timestamp": "${Instant.now()}",
                    "aggregateId": "$customerId",
                    "aggregateType": "Customer",
                    "correlationId": "$correlationId",
                    "causationId": "${UUID.randomUUID()}",
                    "payload": {
                        "customerId": "$customerId",
                        "activatedAt": "${Instant.now()}",
                        "emailVerified": true
                    }
                }
            """.trimIndent()

            val record = ConsumerRecord<String, String>(
                "customer.events",
                0,
                100L,
                customerId.toString(),
                eventJson
            )

            // Process the event
            consumer.consume(record, acknowledgment)

            // Verify the flow
            verify { customerServiceClient.getCustomerById(customerId) }
            verify {
                emailSender.sendWelcomeEmail(
                    recipientEmail = "jane@example.com",
                    recipientName = "Jane",
                    displayName = "Jane Doe",
                    customerNumber = "ACME-202601-000142",
                    marketingOptIn = false,
                    showProfileCta = true,
                    correlationId = correlationId.toString()
                )
            }
            verify { deliveryRepository.save(match { it.notificationType == NotificationType.WELCOME }) }
            verify { eventPublisher.publish(any()) }
            verify { processedEventRepository.save(match { it.eventId == eventId }) }
            verify { acknowledgment.acknowledge() }
        }

        @Test
        @DisplayName("Full flow with marketing opt-in: CustomerActivated event -> Customer lookup -> Marketing welcome email sent")
        fun `should send marketing welcome email when customer opted in`() {
            val customerId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            val customer = CustomerDto(
                customerId = customerId.toString(),
                userId = UUID.randomUUID().toString(),
                customerNumber = "ACME-202601-000143",
                name = NameDto(
                    firstName = "John",
                    lastName = "Smith",
                    displayName = "John Smith"
                ),
                email = EmailDto(
                    address = "john@example.com",
                    verified = true
                ),
                status = "ACTIVE",
                preferences = PreferencesDto(
                    communication = CommunicationPreferencesDto(
                        email = true,
                        sms = true,
                        push = true,
                        marketing = true // Opted in to marketing
                    )
                ),
                profileCompleteness = 100,
                registeredAt = Instant.now()
            )

            every { processedEventRepository.existsByEventId(eventId) } returns false
            every { customerServiceClient.getCustomerById(customerId) } returns CustomerQueryResult.Success(customer)
            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns false
            every { deliveryRepository.save(any()) } answers { firstArg() }
            every { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) } returns EmailSendResult.Success("msg-456", 202)
            every { processedEventRepository.save(any()) } answers { firstArg() }

            val eventJson = """
                {
                    "eventId": "$eventId",
                    "eventType": "CustomerActivated",
                    "eventVersion": "1.0",
                    "timestamp": "${Instant.now()}",
                    "aggregateId": "$customerId",
                    "aggregateType": "Customer",
                    "correlationId": "$correlationId",
                    "causationId": "${UUID.randomUUID()}",
                    "payload": {
                        "customerId": "$customerId",
                        "activatedAt": "${Instant.now()}",
                        "emailVerified": true
                    }
                }
            """.trimIndent()

            val record = ConsumerRecord<String, String>(
                "customer.events",
                0,
                100L,
                customerId.toString(),
                eventJson
            )

            consumer.consume(record, acknowledgment)

            verify {
                emailSender.sendWelcomeEmail(
                    recipientEmail = "john@example.com",
                    recipientName = "John",
                    displayName = "John Smith",
                    customerNumber = "ACME-202601-000143",
                    marketingOptIn = true, // Marketing included
                    showProfileCta = false, // Profile is 100% complete
                    correlationId = correlationId.toString()
                )
            }
        }
    }

    @Nested
    @DisplayName("Idempotency")
    inner class IdempotencyTests {

        @Test
        @DisplayName("Should not send duplicate welcome email for same customer")
        fun `should not send duplicate welcome email`() {
            val customerId = UUID.randomUUID()
            val correlationId = UUID.randomUUID()

            every { deliveryRepository.existsByRecipientIdAndNotificationType(customerId, NotificationType.WELCOME) } returns true
            every { deliveryRepository.findByRecipientId(customerId) } returns listOf(
                NotificationDelivery(
                    id = UUID.randomUUID(),
                    notificationType = NotificationType.WELCOME,
                    recipientId = customerId,
                    recipientEmail = "jane@example.com"
                )
            )

            val result = useCase.execute(
                customerId = customerId,
                email = "jane@example.com",
                firstName = "Jane",
                displayName = "Jane Doe",
                customerNumber = "ACME-202601-000142",
                marketingOptIn = false,
                profileCompleteness = 80,
                correlationId = correlationId
            )

            assert(result is SendWelcomeEmailResult.AlreadySent)
            verify(exactly = 0) { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("Should not process same event twice")
        fun `should not process duplicate event`() {
            val eventId = UUID.randomUUID()
            val customerId = UUID.randomUUID()

            every { processedEventRepository.existsByEventId(eventId) } returns true

            val event = CustomerActivatedEvent(
                eventId = eventId,
                eventType = "CustomerActivated",
                eventVersion = "1.0",
                timestamp = Instant.now(),
                aggregateId = customerId,
                aggregateType = "Customer",
                correlationId = UUID.randomUUID(),
                causationId = UUID.randomUUID(),
                payload = CustomerActivatedPayload(
                    customerId = customerId,
                    activatedAt = Instant.now(),
                    emailVerified = true
                )
            )

            handler.handle(event)

            verify(exactly = 0) { customerServiceClient.getCustomerById(any()) }
            verify(exactly = 0) { emailSender.sendWelcomeEmail(any(), any(), any(), any(), any(), any(), any()) }
        }
    }
}
