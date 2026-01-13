package com.acme.customer.application

import com.acme.customer.domain.*
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileCompletenessCalculatorTest {

    private lateinit var addressRepository: AddressRepository
    private lateinit var consentRecordRepository: ConsentRecordRepository
    private lateinit var preferencesRepository: CustomerPreferencesRepository
    private lateinit var calculator: ProfileCompletenessCalculator

    @BeforeEach
    fun setUp() {
        addressRepository = mockk()
        consentRecordRepository = mockk()
        preferencesRepository = mockk()

        calculator = ProfileCompletenessCalculator(
            addressRepository = addressRepository,
            consentRecordRepository = consentRecordRepository,
            preferencesRepository = preferencesRepository
        )
    }

    @Test
    fun `calculate should return 0 for new customer with unverified email`() {
        // Given
        val customer = createTestCustomer()
        setupEmptyRepositories(customer.id)

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info is not complete because email is not verified
        assertEquals(0, completeness.overallScore)
        assertFalse(completeness.isComplete())
        assertTrue(completeness.needsAttention())
        assertNotNull(completeness.nextAction)
    }

    @Test
    fun `calculate should return 25 for customer with verified email and basic info`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
        }
        setupEmptyRepositories(customer.id)

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info is complete: 25%
        assertEquals(25, completeness.overallScore)
        val basicInfoSection = completeness.sections.find { it.name == "basicInfo" }
        assertNotNull(basicInfoSection)
        assertTrue(basicInfoSection.isComplete)
        assertEquals(100, basicInfoSection.score)
    }

    @Test
    fun `calculate should include contact info when phone number is added`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
            phoneCountryCode = "+1"
            phoneNumber = "5551234567"
        }
        setupEmptyRepositories(customer.id)

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info 25% + Contact info 15% = 40%
        assertEquals(40, completeness.overallScore)
        val contactInfoSection = completeness.sections.find { it.name == "contactInfo" }
        assertNotNull(contactInfoSection)
        assertTrue(contactInfoSection.isComplete)
        assertEquals(100, contactInfoSection.score)
    }

    @Test
    fun `calculate should mark personal details complete with date of birth only`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
            dateOfBirth = LocalDate.of(1990, 5, 15)
        }
        setupEmptyRepositories(customer.id)

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info 25% + Personal details 15% = 40%
        assertEquals(40, completeness.overallScore)
        val personalDetailsSection = completeness.sections.find { it.name == "personalDetails" }
        assertNotNull(personalDetailsSection)
        assertTrue(personalDetailsSection.isComplete)
        assertEquals(100, personalDetailsSection.score)
    }

    @Test
    fun `calculate should mark personal details complete with gender only`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
            gender = "MALE"
        }
        setupEmptyRepositories(customer.id)

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info 25% + Personal details 15% = 40%
        assertEquals(40, completeness.overallScore)
        val personalDetailsSection = completeness.sections.find { it.name == "personalDetails" }
        assertNotNull(personalDetailsSection)
        assertTrue(personalDetailsSection.isComplete)
    }

    @Test
    fun `calculate should not exceed 100 percent for personal details with both fields`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
            dateOfBirth = LocalDate.of(1990, 5, 15)
            gender = "FEMALE"
        }
        setupEmptyRepositories(customer.id)

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Personal details still 15% even with both fields
        assertEquals(40, completeness.overallScore)
        val personalDetailsSection = completeness.sections.find { it.name == "personalDetails" }
        assertNotNull(personalDetailsSection)
        assertEquals(100, personalDetailsSection.score)
    }

    @Test
    fun `calculate should include address when validated address exists`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
        }
        val validatedAddress = createTestAddress(customer.id, isValidated = true)

        every { addressRepository.findByCustomerId(customer.id) } returns listOf(validatedAddress)
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customer.id) } returns emptyList()
        every { preferencesRepository.findById(customer.id) } returns Optional.of(
            CustomerPreferences.createDefault(customer.id, false)
        )

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info 25% + Address 20% + Preferences 15% = 60%
        assertEquals(60, completeness.overallScore)
        val addressSection = completeness.sections.find { it.name == "address" }
        assertNotNull(addressSection)
        assertTrue(addressSection.isComplete)
    }

    @Test
    fun `calculate should not include address when address is not validated`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
        }
        val unvalidatedAddress = createTestAddress(customer.id, isValidated = false)

        every { addressRepository.findByCustomerId(customer.id) } returns listOf(unvalidatedAddress)
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customer.id) } returns emptyList()
        every { preferencesRepository.findById(customer.id) } returns Optional.of(
            CustomerPreferences.createDefault(customer.id, false)
        )

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info 25% + Preferences 15% = 40%
        assertEquals(40, completeness.overallScore)
        val addressSection = completeness.sections.find { it.name == "address" }
        assertNotNull(addressSection)
        assertFalse(addressSection.isComplete)
    }

    @Test
    fun `calculate should include preferences when preferences exist`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
        }

        every { addressRepository.findByCustomerId(customer.id) } returns emptyList()
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customer.id) } returns emptyList()
        every { preferencesRepository.findById(customer.id) } returns Optional.of(
            CustomerPreferences.createDefault(customer.id, false)
        )

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info 25% + Preferences 15% = 40%
        assertEquals(40, completeness.overallScore)
        val preferencesSection = completeness.sections.find { it.name == "preferences" }
        assertNotNull(preferencesSection)
        assertTrue(preferencesSection.isComplete)
    }

    @Test
    fun `calculate should include consent when required consents are granted`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
        }
        val dataProcessingConsent = createConsentRecord(
            customer.id,
            ConsentType.DATA_PROCESSING,
            granted = true
        )

        every { addressRepository.findByCustomerId(customer.id) } returns emptyList()
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customer.id) } returns listOf(dataProcessingConsent)
        every { preferencesRepository.findById(customer.id) } returns Optional.of(
            CustomerPreferences.createDefault(customer.id, false)
        )

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info 25% + Preferences 15% + Consent 10% = 50%
        assertEquals(50, completeness.overallScore)
        val consentSection = completeness.sections.find { it.name == "consent" }
        assertNotNull(consentSection)
        assertTrue(consentSection.isComplete)
    }

    @Test
    fun `calculate should return 100 for complete profile`() {
        // Given
        val customer = createTestCustomer().apply {
            emailVerified = true
            phoneCountryCode = "+1"
            phoneNumber = "5551234567"
            dateOfBirth = LocalDate.of(1990, 5, 15)
        }
        val validatedAddress = createTestAddress(customer.id, isValidated = true)
        val dataProcessingConsent = createConsentRecord(
            customer.id,
            ConsentType.DATA_PROCESSING,
            granted = true
        )

        every { addressRepository.findByCustomerId(customer.id) } returns listOf(validatedAddress)
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customer.id) } returns listOf(dataProcessingConsent)
        every { preferencesRepository.findById(customer.id) } returns Optional.of(
            CustomerPreferences.createDefault(customer.id, false)
        )

        // When
        val completeness = calculator.calculate(customer)

        // Then
        // Basic info 25% + Contact 15% + Personal 15% + Address 20% + Preferences 15% + Consent 10% = 100%
        assertEquals(100, completeness.overallScore)
        assertTrue(completeness.isComplete())
        assertFalse(completeness.needsAttention())
        assertNull(completeness.nextAction)
    }

    @Test
    fun `calculate should provide correct next action for incomplete profile`() {
        // Given
        val customer = createTestCustomer() // Not verified
        setupEmptyRepositories(customer.id)

        // When
        val completeness = calculator.calculate(customer)

        // Then
        assertNotNull(completeness.nextAction)
        assertEquals("basicInfo", completeness.nextAction!!.section)
        assertTrue(completeness.nextAction!!.action.contains("email") || completeness.nextAction!!.action.contains("name"))
    }

    @Test
    fun `calculate should return correct section weights`() {
        // Given
        val customer = createTestCustomer()
        setupEmptyRepositories(customer.id)

        // When
        val completeness = calculator.calculate(customer)

        // Then
        val sections = completeness.sections.associateBy { it.name }
        assertEquals(25, sections["basicInfo"]?.weight)
        assertEquals(15, sections["contactInfo"]?.weight)
        assertEquals(15, sections["personalDetails"]?.weight)
        assertEquals(20, sections["address"]?.weight)
        assertEquals(15, sections["preferences"]?.weight)
        assertEquals(10, sections["consent"]?.weight)
    }

    private fun createTestCustomer(
        customerId: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID()
    ): Customer {
        return Customer.createFromRegistration(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now()
        )
    }

    private fun createTestAddress(
        customerId: UUID,
        isValidated: Boolean
    ): Address {
        return Address.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            type = AddressType.SHIPPING,
            streetLine1 = "123 Main St",
            streetLine2 = null,
            city = "Anytown",
            state = "CA",
            postalCode = "12345",
            country = "US",
            label = "Home",
            isDefault = true
        ).apply {
            if (isValidated) {
                setValidationResult(isValid = true, details = "Address validated")
            }
        }
    }

    private fun createConsentRecord(
        customerId: UUID,
        consentType: ConsentType,
        granted: Boolean
    ): ConsentRecord {
        return ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = consentType,
            granted = granted,
            source = ConsentSource.REGISTRATION,
            ipAddress = "127.0.0.1",
            userAgent = "Test",
            expiresAt = null,
            version = 1
        )
    }

    private fun setupEmptyRepositories(customerId: UUID) {
        every { addressRepository.findByCustomerId(customerId) } returns emptyList()
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customerId) } returns emptyList()
        every { preferencesRepository.findById(customerId) } returns Optional.empty()
    }
}
