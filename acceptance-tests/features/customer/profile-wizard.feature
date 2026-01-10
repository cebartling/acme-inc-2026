@customer @profile-wizard
Feature: Profile Completion Wizard
  As an activated customer
  I want a guided wizard to complete my profile
  So that I can provide additional information for a personalized shopping experience

  Background:
    Given I am on the profile completion wizard

  # AC-0002-08-03: Phone Number Validation
  @smoke
  Scenario: Display error for invalid phone number format
    When I enter a phone number with country code "+1" and number "123"
    And I click the continue button
    Then I should see a phone number validation error

  Scenario: Accept valid phone number
    When I enter a phone number with country code "+1" and number "5551234567"
    And I click the continue button
    Then I should not see a phone number validation error
    And I should be on the address step

  # AC-0002-08-04: Age Validation
  Scenario: Display error when date of birth indicates age less than 13
    When I enter a date of birth that is less than 13 years ago
    And I click the continue button
    Then I should see the date of birth error "You must be at least 13 years old"

  Scenario: Accept valid date of birth
    When I enter a date of birth "1990-05-15"
    And I click the continue button
    Then I should not see a date of birth validation error
    And I should be on the address step

  # AC-0002-08-10: Wizard Skip Option
  @smoke
  Scenario: Skip personal details step
    When I click the skip this step button
    Then I should be on the address step

  Scenario: Skip address step
    Given I am on the address step
    When I click the skip button
    Then I should be on the preferences step

  Scenario: Skip entire wizard
    When I click the skip profile completion link
    Then I should be redirected to the home page

  # Wizard Navigation
  Scenario: Navigate between wizard steps using progress indicator
    When I complete the personal details step
    And I complete the address step
    And I am on the preferences step
    When I click on the personal details step in the progress indicator
    Then I should be on the personal details step

  Scenario: Navigate back from address step
    Given I am on the address step
    When I click the back button
    Then I should be on the personal details step

  # Personal Details Step
  Scenario: Select gender from dropdown
    When I select gender "Female"
    And I click the continue button
    Then I should be on the address step
    And the gender "Female" should be saved

  Scenario: Select preferred language
    When I select preferred language "Spanish"
    And I click the continue button
    Then I should be on the address step

  Scenario: Select timezone
    When I select timezone "Eastern Time (ET)"
    And I click the continue button
    Then I should be on the address step

  # Address Step
  Scenario: Display required field errors for address
    Given I am on the address step
    When I click the continue button without filling required fields
    Then I should see the street address error "Street address is required"
    And I should see the city error "City is required"
    And I should see the postal code error "Postal code is required"

  @smoke
  Scenario: Complete address step with valid data
    Given I am on the address step
    When I fill in the address form with:
      | Address Type  | Both Shipping & Billing |
      | Street Line 1 | 123 Main Street         |
      | City          | New York                |
      | State         | New York                |
      | Postal Code   | 10001                   |
      | Country       | United States           |
    And I click the continue button
    Then I should be on the preferences step

  # Preferences Step
  Scenario: Toggle email notifications off
    Given I am on the preferences step
    When I toggle email notifications off
    And I click the review button
    Then I should be on the review step
    And email notifications should show as "Disabled"

  Scenario: Enable SMS notifications
    Given I am on the preferences step
    When I toggle SMS notifications on
    And I click the review button
    Then I should be on the review step
    And SMS notifications should show as "Enabled"

  Scenario: Select notification frequency
    Given I am on the preferences step
    When I select notification frequency "Daily digest"
    And I click the review button
    Then I should be on the review step
    And notification frequency should show as "Daily digest"

  # Review Step
  @smoke
  Scenario: Display all entered information on review step
    Given I have completed all wizard steps with:
      | Phone         | +1 5551234567           |
      | Date of Birth | 1990-05-15              |
      | Gender        | Female                  |
      | Language      | English (US)            |
      | Timezone      | Eastern Time (ET)       |
      | Street        | 123 Main Street         |
      | City          | New York                |
      | State         | New York                |
      | Postal Code   | 10001                   |
      | Country       | United States           |
    When I am on the review step
    Then I should see all my entered information displayed

  Scenario: Edit personal details from review step
    Given I am on the review step
    When I click edit on the personal details section
    Then I should be on the personal details step

  Scenario: Edit address from review step
    Given I am on the review step
    When I click edit on the address section
    Then I should be on the address step

  Scenario: Edit preferences from review step
    Given I am on the review step
    When I click edit on the preferences section
    Then I should be on the preferences step

  # Full Flow
  @smoke
  Scenario: Complete the entire profile wizard successfully
    When I fill in my personal details:
      | Phone Country Code | +1                |
      | Phone Number       | 5551234567        |
      | Date of Birth      | 1990-05-15        |
      | Gender             | Male              |
      | Language           | English (US)      |
      | Timezone           | Pacific Time (PT) |
    And I click the continue button
    And I fill in my address:
      | Address Type  | Shipping         |
      | Label         | Home             |
      | Street Line 1 | 456 Oak Avenue   |
      | City          | Los Angeles      |
      | State         | California       |
      | Postal Code   | 90001            |
      | Country       | United States    |
    And I click the continue button
    And I configure my preferences:
      | Email Notifications | on  |
      | SMS Notifications   | off |
      | Push Notifications  | on  |
      | Marketing           | off |
    And I click the review button
    And I click the complete profile button
    Then I should be redirected to the home page
