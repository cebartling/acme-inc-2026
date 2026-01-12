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
    And the preferences response should have "communication.email" set to "false"
    And the preferences response should have "communication.sms" set to "true"
    And the preferences response should have "display.language" set to "es-ES"
    When I request my preferences
    Then the preferences response should have "communication.email" set to "false"
    And the preferences response should have "communication.sms" set to "true"
    And the preferences response should have "display.language" set to "es-ES"

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
    And the preferences response should have "communication.sms" set to "true"

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
    And the preferences response should have "communication.frequency" set to "DAILY_DIGEST"
    And the preferences response should have "communication.email" set to "true"
    And the preferences response should have "communication.sms" set to "false"
    And the preferences response should have "privacy.allowAnalytics" set to "true"
    And the preferences response should have "display.language" set to "en-US"

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
    And the preferences response should have "display.language" set to "ja-JP"

  # Notification Frequency Options
  Scenario Outline: Update notification frequency
    When I update my preferences with:
      | path                    | value      |
      | communication.frequency | <frequency> |
    Then the API should respond with status 200
    And the preferences response should have "communication.frequency" set to "<frequency>"

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
    And the preferences response should have "privacy.shareDataWithPartners" set to "true"
    And the preferences response should have "privacy.allowAnalytics" set to "false"
    And the preferences response should have "privacy.allowPersonalization" set to "false"

  # Display Preferences
  Scenario: Update display preferences
    When I update my preferences with:
      | path             | value            |
      | display.language | fr-FR            |
      | display.currency | EUR              |
      | display.timezone | Europe/Paris     |
    Then the API should respond with status 200
    And the preferences response should have "display.language" set to "fr-FR"
    And the preferences response should have "display.currency" set to "EUR"
    And the preferences response should have "display.timezone" set to "Europe/Paris"

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
    And the preferences response should include "customerId"
    And the preferences response should include "preferences.communication"
    And the preferences response should include "preferences.privacy"
    And the preferences response should include "preferences.display"
    And the preferences response should include "updatedAt"

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
