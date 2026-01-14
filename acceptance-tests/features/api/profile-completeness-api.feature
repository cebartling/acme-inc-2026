@api @customer-service @profile-completeness
Feature: Profile Completeness Tracking API (US-0002-12)
  As an activated customer
  I want to see my profile completeness score and what's missing
  So that I am motivated to complete my profile for a better experience

  Background:
    Given the Customer Service is available
    And I have an authenticated customer

  # AC-0002-12-02: UI Visibility
  @smoke
  Scenario: Get profile completeness for new customer
    When I request my profile completeness
    Then the API should respond with status 200
    And the completeness response should include "customerId"
    And the completeness response should include "overallScore"
    And the completeness response should include "sections"
    And the completeness response should include "updatedAt"

  # AC-0002-12-07: Weighted Calculation
  @smoke
  Scenario: Verify section weights match documented values
    When I request my profile completeness
    Then the API should respond with status 200
    And the completeness section "basicInfo" should have weight 25
    And the completeness section "contactInfo" should have weight 15
    And the completeness section "personalDetails" should have weight 15
    And the completeness section "address" should have weight 20
    And the completeness section "preferences" should have weight 15
    And the completeness section "consent" should have weight 10

  # AC-0002-12-05: Section Progress Details
  Scenario: Section breakdown shows individual items
    When I request my profile completeness
    Then the API should respond with status 200
    And the completeness section "basicInfo" should include items:
      | name          |
      | firstName     |
      | lastName      |
      | emailVerified |
    And the completeness section "contactInfo" should include items:
      | name        |
      | phoneNumber |
    And the completeness section "personalDetails" should include items:
      | name        |
      | dateOfBirth |
      | gender      |

  # AC-0002-12-01: Real-time Recalculation
  Scenario: Score increases when phone number is added
    Given my profile completeness score is recorded
    When I update my profile with phone "+1" "2024561234"
    And I request my profile completeness
    Then the completeness response should have "overallScore" greater than the previous score
    And the completeness section "contactInfo" should have "isComplete" set to "true"

  # AC-0002-12-06: Personal Details OR Logic
  Scenario: Personal details complete with only date of birth
    When I update my profile with:
      | field        | value      |
      | dateOfBirth  | 1990-05-15 |
    And I request my profile completeness
    Then the API should respond with status 200
    And the completeness section "personalDetails" should have "score" set to "100"
    And the completeness section "personalDetails" should have "isComplete" set to "true"

  Scenario: Personal details complete with only gender
    When I update my profile with:
      | field  | value |
      | gender | MALE  |
    And I request my profile completeness
    Then the API should respond with status 200
    And the completeness section "personalDetails" should have "score" set to "100"
    And the completeness section "personalDetails" should have "isComplete" set to "true"

  Scenario: Personal details does not exceed 100% with both fields
    When I update my profile with:
      | field        | value      |
      | dateOfBirth  | 1990-05-15 |
      | gender       | FEMALE     |
    And I request my profile completeness
    Then the API should respond with status 200
    And the completeness section "personalDetails" should have "score" set to "100"

  # Address Validation
  Scenario: Address section complete when validated address exists
    Given I add a shipping address:
      | field       | value        |
      | streetLine1 | 123 Main St  |
      | city        | New York     |
      | state       | NY           |
      | postalCode  | 10001        |
      | country     | US           |
    And the address is validated
    When I request my profile completeness
    Then the completeness section "address" should have "score" set to "100"
    And the completeness section "address" should have "isComplete" set to "true"

  Scenario: Address section incomplete when address not validated
    Given I add a shipping address:
      | field       | value        |
      | streetLine1 | 123 Main St  |
      | city        | New York     |
      | state       | NY           |
      | postalCode  | 10001        |
      | country     | US           |
    And the address is not validated
    When I request my profile completeness
    Then the completeness section "address" should have "score" set to "0"
    And the completeness section "address" should have "isComplete" set to "false"

  # Consent
  Scenario: Consent section complete when required consents granted
    Given I have granted consent for "DATA_PROCESSING"
    When I request my profile completeness
    Then the completeness section "consent" should have "score" set to "100"
    And the completeness section "consent" should have "isComplete" set to "true"

  # Next Action
  @smoke
  Scenario: Next action shows first incomplete item
    When I request my profile completeness
    And the profile is not 100% complete
    Then the completeness response should include "nextAction"
    And the "nextAction" should include "section"
    And the "nextAction" should include "action"
    And the "nextAction" should include "url"

  Scenario: No next action when profile is complete
    Given my profile is 100% complete
    When I request my profile completeness
    Then the completeness response should have "overallScore" set to "100"
    And the completeness response should not include "nextAction"

  # AC-0002-12-04: Incomplete Profile Prompt
  Scenario: Profile needs attention when below 80%
    Given my profile completeness is less than 80
    When I request my profile completeness
    Then the completeness response should have "overallScore" less than "80"
    And the completeness response should include "nextAction"

  # Authorization
  @security
  Scenario: Reject access to another customer's completeness
    Given another customer exists with id "00000000-0000-0000-0000-000000000099"
    When I try to get completeness for customer "00000000-0000-0000-0000-000000000099"
    Then the API should respond with status 404

  # AC-0002-12-08: Progress Persistence
  Scenario: Progress persists across sessions
    Given my profile has been updated with phone "+1" "2024561234"
    When I request my profile completeness
    Then the completeness section "contactInfo" should have "isComplete" set to "true"
    # Simulate new session
    When I re-authenticate
    And I request my profile completeness
    Then the completeness section "contactInfo" should have "isComplete" set to "true"
