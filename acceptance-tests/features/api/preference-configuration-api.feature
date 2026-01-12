@api @customer-service @preferences @wip
Feature: Preference Configuration API (US-0002-10)
  As an activated customer
  I want to configure my communication and privacy preferences
  So that I receive notifications in my preferred way and control my data

  Background:
    Given the Customer Service is available
    And I have an authenticated customer

  # AC-0002-10-01: Immediate Effect
  @smoke
  Scenario: Preferences changes take effect immediately
    When I update my preferences with:
      | path                   | value        |
      | communication.email    | false        |
      | communication.sms      | true         |
      | display.language       | es-ES        |
    Then the API should respond with status 200
    And the response should contain "communication.email" with value "false"
    And the response should contain "communication.sms" with value "true"
    And the response should contain "display.language" with value "es-ES"
    When I request my preferences
    Then the response should contain "communication.email" with value "false"
    And the response should contain "communication.sms" with value "true"
    And the response should contain "display.language" with value "es-ES"

  # AC-0002-10-03: SMS Requires Verified Phone
  Scenario: Reject SMS enable when phone not verified
    Given my phone number is not verified
    When I update my preferences with:
      | path              | value |
      | communication.sms | true  |
    Then the API should respond with status 400
    And the response should contain error "PHONE_NOT_VERIFIED"
    And the response should contain message "Phone number must be verified to enable SMS notifications"

  Scenario: Allow SMS enable when phone is verified
    Given my phone number is verified
    When I update my preferences with:
      | path              | value |
      | communication.sms | true  |
    Then the API should respond with status 200
    And the response should contain "communication.sms" with value "true"

  # AC-0002-10-07: Partial Updates
  @smoke
  Scenario: Partial update only changes specified fields
    Given my current preferences are:
      | path                        | value     |
      | communication.email         | true      |
      | communication.sms           | false     |
      | communication.marketing     | false     |
      | communication.frequency     | IMMEDIATE |
      | privacy.allowAnalytics      | true      |
      | display.language            | en-US     |
    When I update my preferences with:
      | path                    | value        |
      | communication.frequency | DAILY_DIGEST |
    Then the API should respond with status 200
    And the response should contain "communication.frequency" with value "DAILY_DIGEST"
    And the response should contain "communication.email" with value "true"
    And the response should contain "communication.sms" with value "false"
    And the response should contain "privacy.allowAnalytics" with value "true"
    And the response should contain "display.language" with value "en-US"

  # AC-0002-10-08: Language Validation
  Scenario: Reject unsupported language
    When I update my preferences with:
      | path             | value |
      | display.language | xx-XX |
    Then the API should respond with status 400
    And the response should contain error "UNSUPPORTED_LANGUAGE"
    And the response should contain message "Unsupported language: xx-XX"

  Scenario: Accept supported language
    When I update my preferences with:
      | path             | value |
      | display.language | ja-JP |
    Then the API should respond with status 200
    And the response should contain "display.language" with value "ja-JP"

  # Notification Frequency Options
  Scenario Outline: Update notification frequency
    When I update my preferences with:
      | path                    | value      |
      | communication.frequency | <frequency> |
    Then the API should respond with status 200
    And the response should contain "communication.frequency" with value "<frequency>"

    Examples:
      | frequency     |
      | IMMEDIATE     |
      | DAILY_DIGEST  |
      | WEEKLY_DIGEST |

  Scenario: Reject invalid notification frequency
    When I update my preferences with:
      | path                    | value   |
      | communication.frequency | INVALID |
    Then the API should respond with status 400
    And the response should contain a validation error for "frequency"

  # Privacy Preferences
  Scenario: Update all privacy preferences
    When I update my preferences with:
      | path                           | value |
      | privacy.shareDataWithPartners  | true  |
      | privacy.allowAnalytics         | false |
      | privacy.allowPersonalization   | false |
    Then the API should respond with status 200
    And the response should contain "privacy.shareDataWithPartners" with value "true"
    And the response should contain "privacy.allowAnalytics" with value "false"
    And the response should contain "privacy.allowPersonalization" with value "false"

  # Display Preferences
  Scenario: Update display preferences
    When I update my preferences with:
      | path             | value            |
      | display.language | fr-FR            |
      | display.currency | EUR              |
      | display.timezone | Europe/Paris     |
    Then the API should respond with status 200
    And the response should contain "display.language" with value "fr-FR"
    And the response should contain "display.currency" with value "EUR"
    And the response should contain "display.timezone" with value "Europe/Paris"

  Scenario: Reject invalid timezone
    When I update my preferences with:
      | path             | value           |
      | display.timezone | Invalid/Zone    |
    Then the API should respond with status 400
    And the response should contain a validation error for "timezone"

  Scenario: Reject unsupported currency
    When I update my preferences with:
      | path             | value |
      | display.currency | XYZ   |
    Then the API should respond with status 400
    And the response should contain a validation error for "currency"

  # GET Preferences
  @smoke
  Scenario: Get current preferences
    When I request my preferences
    Then the API should respond with status 200
    And the response should contain "customerId"
    And the response should contain "preferences.communication"
    And the response should contain "preferences.privacy"
    And the response should contain "preferences.display"
    And the response should contain "updatedAt"

  # Authorization
  @security
  Scenario: Reject access to another customer's preferences
    Given another customer exists with id "other-customer-id"
    When I try to get preferences for customer "other-customer-id"
    Then the API should respond with status 403
    And the response should contain error "You are not authorized to view these preferences"

  @security
  Scenario: Reject update to another customer's preferences
    Given another customer exists with id "other-customer-id"
    When I try to update preferences for customer "other-customer-id" with:
      | path              | value |
      | communication.sms | true  |
    Then the API should respond with status 403
    And the response should contain error "You are not authorized to update these preferences"

  # No Updates
  Scenario: Reject request with no actual updates
    When I send an empty preferences update request
    Then the API should respond with status 400
    And the response should contain error "No updates provided"

  Scenario: Reject when values same as current
    Given my current preferences have "communication.email" set to "true"
    When I update my preferences with:
      | path               | value |
      | communication.email | true  |
    Then the API should respond with status 400
    And the response should contain error "No updates provided"
