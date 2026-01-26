@api @customer
Feature: Customer Profile Loading (US-0003-09)
  As a Customer Service
  I want to load customer profiles from cache or database
  So that authenticated customers can access their profile data efficiently

  Background:
    Given the Customer Service is available
    And the Identity Service is available

  @smoke
  Scenario: Get current customer profile successfully
    Given an active customer with email "profile-test@example.com" exists
    When I request my customer profile as the authenticated user
    Then the response status should be 200
    And the profile response should contain my customer ID
    And the profile response should contain my user ID
    And the profile response should contain my customer number
    And the profile response should contain my email address
    And the profile response should contain my name information
    And the profile response should contain my preferences
    And the profile response should contain my profile completeness score

  Scenario: Profile includes all required fields
    Given an active customer with email "complete-profile@example.com" exists
    When I request my customer profile as the authenticated user
    Then the profile response should include field "customerId"
    And the profile response should include field "userId"
    And the profile response should include field "customerNumber"
    And the profile response should include field "email.address"
    And the profile response should include field "email.verified"
    And the profile response should include field "name.firstName"
    And the profile response should include field "name.lastName"
    And the profile response should include field "name.displayName"
    And the profile response should include field "status"
    And the profile response should include field "type"
    And the profile response should include field "profileCompleteness"
    And the profile response should include field "registeredAt"
    And the profile response should include field "lastActivityAt"

  Scenario: Profile includes communication preferences
    Given an active customer with email "preferences-test@example.com" exists
    When I request my customer profile as the authenticated user
    Then the profile response should include field "preferences.communication.email"
    And the profile response should include field "preferences.communication.sms"
    And the profile response should include field "preferences.communication.push"
    And the profile response should include field "preferences.communication.marketing"
    And the profile response should include field "preferences.communication.frequency"

  Scenario: Profile includes privacy preferences
    Given an active customer with email "privacy-test@example.com" exists
    When I request my customer profile as the authenticated user
    Then the profile response should include field "preferences.privacy.shareDataWithPartners"
    And the profile response should include field "preferences.privacy.allowAnalytics"
    And the profile response should include field "preferences.privacy.allowPersonalization"

  Scenario: Profile includes display preferences
    Given an active customer with email "display-test@example.com" exists
    When I request my customer profile as the authenticated user
    Then the profile response should include field "preferences.display.language"
    And the profile response should include field "preferences.display.currency"
    And the profile response should include field "preferences.display.timezone"

  @smoke
  Scenario: Return 401 when not authenticated
    When I request customer profile without authentication
    Then the response status should be 401

  Scenario: Return 404 when customer profile does not exist
    Given I am authenticated as a user with no customer profile
    When I request my customer profile as the authenticated user
    Then the response status should be 404

  Scenario: Profile is cached after first request
    Given an active customer with email "cache-test@example.com" exists
    When I request my customer profile as the authenticated user
    Then the response status should be 200
    When I request my customer profile as the authenticated user again
    Then the response status should be 200
    And the second request should be faster than the first

  @wip
  Scenario: Cache is invalidated after profile update
    Given an active customer with email "cache-invalidation@example.com" exists
    When I request my customer profile as the authenticated user
    And I update my profile information
    And I request my customer profile as the authenticated user
    Then the profile should contain the updated information

  Scenario: Activity tracking updates lastActivityAt
    Given an active customer with email "activity-tracking@example.com" exists
    When I request my customer profile as the authenticated user
    And I wait for activity tracking to complete
    Then the customer's lastActivityAt should be updated
