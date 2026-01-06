@api @customer @registration
Feature: Customer Profile Creation (US-0002-03)
  As a Customer Management Service
  I want to automatically create a customer profile when a user registers
  So that the customer has a complete profile record for shopping and personalization

  Background:
    Given the Customer Service is available
    And the Identity Service is available
    And Kafka is available

  # AC-0002-03-01: Event Processing Timeliness
  @smoke
  Scenario: Create customer profile within 5 seconds of registration
    When a user registers with email "customer1@example.com"
    Then a customer profile should be created within 5 seconds
    And the customer profile should contain email "customer1@example.com"

  # AC-0002-03-02: Customer ID Generation
  Scenario: Generate UUID v7 customer ID
    When a user registers with email "customerid@example.com"
    Then a customer profile should be created
    And the customer ID should be a valid UUID v7
    And the customer ID should be distinct from the user ID

  # AC-0002-03-03: Customer Number Uniqueness
  Scenario: Generate sequential customer numbers within same month
    When 3 users register in the same month
    Then each customer should have a unique customer number
    And the customer numbers should follow format "ACME-YYYYMM-NNNNNN"
    And the numbers should be sequential

  # AC-0002-03-04: Transactional Consistency
  Scenario: Ensure transactional consistency for writes
    When a user registers with email "transactional@example.com"
    Then the customer profile should be created
    And the CustomerRegistered event should be in the event store
    And both should be in the same transaction

  # AC-0002-03-05: Read Model Projection
  Scenario: Project customer to MongoDB within 2 seconds
    When a user registers with email "projection@example.com"
    Then the customer profile should be created in PostgreSQL
    And the customer should appear in MongoDB within 2 seconds
    And the MongoDB document should contain all queryable fields

  # AC-0002-03-06: Event Causation Linking
  Scenario: Link CustomerRegistered event to UserRegistered event
    When a user registers with email "causation@example.com"
    Then a CustomerRegistered event should be published
    And the causationId should match the UserRegistered eventId
    And the correlationId should be propagated from the registration

  # AC-0002-03-07: Default Preferences Based on Marketing Opt-in
  Scenario Outline: Set marketing preferences based on opt-in
    When a user registers with email "<email>" and marketing opt-in "<optIn>"
    Then the customer profile should be created
    And the marketing communications preference should be "<expected>"

    Examples:
      | email                 | optIn | expected |
      | marketing1@example.com | true  | true     |
      | marketing2@example.com | false | false    |

  # AC-0002-03-08: Profile Completeness Score
  Scenario: Calculate initial profile completeness as 25%
    When a user registers with email "completeness@example.com"
    Then the customer profile should be created
    And the profile completeness score should be 25

  # AC-0002-03-10: Idempotent Processing
  @smoke
  Scenario: Handle duplicate events idempotently
    Given a UserRegistered event for user "duplicate@example.com"
    When the same UserRegistered event is received again
    Then only one customer profile should exist
    And no error should be logged for the duplicate event

  # Customer Number Monthly Reset
  Scenario: Reset customer number sequence at start of new month
    Given customers were created in the previous month
    When a user registers in a new month
    Then the customer number sequence should start from 000001

  # Customer Status
  Scenario: Set initial customer status to PENDING_VERIFICATION
    When a user registers with email "status@example.com"
    Then the customer profile should be created
    And the customer status should be "PENDING_VERIFICATION"

  # Customer Type
  Scenario: Set default customer type to INDIVIDUAL
    When a user registers with email "type@example.com"
    Then the customer profile should be created
    And the customer type should be "INDIVIDUAL"

  # Display Name Generation
  Scenario: Generate display name from first and last name
    When a user registers with:
      | email     | displayname@example.com |
      | firstName | Jane                    |
      | lastName  | Doe                     |
    Then the customer profile should be created
    And the display name should be "Jane Doe"

  # Default Locale and Currency
  Scenario: Set default locale and currency
    When a user registers with email "defaults@example.com"
    Then the customer profile should be created
    And the preferred locale should be "en-US"
    And the preferred currency should be "USD"
    And the timezone should be "UTC"
