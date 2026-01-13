@api @customer-service @consent @wip
Feature: Consent Management API (US-0002-11)
  As an activated customer
  I want to manage my consent for data processing and marketing
  So that I have control over how my data is used and comply with privacy regulations

  Background:
    Given the Customer Service is available
    And I have an authenticated customer

  # AC-0002-11-01: Immutable Consent Records
  @smoke
  Scenario: Grant marketing consent creates immutable record
    When I grant consent for "MARKETING" from "PROFILE_WIZARD"
    Then the API should respond with status 200
    And the consent response should have "consentType" set to "MARKETING"
    And the consent response should have "granted" set to "true"
    And the consent response should include "consentId"
    And the consent response should include "grantedAt"
    And the consent response should include "expiresAt"
    And the consent response should include "version"

  # AC-0002-11-02: Consent Audit Trail
  @smoke
  Scenario: Consent records include audit trail information
    When I grant consent for "ANALYTICS" from "PRIVACY_SETTINGS" with IP "192.168.1.100" and user agent "Mozilla/5.0"
    Then the API should respond with status 200
    And the consent response should have "source" set to "PRIVACY_SETTINGS"
    When I export my consent history
    Then the API should respond with status 200
    And the consent history should contain a record for "ANALYTICS"
    And the consent history record should have "ipAddress" set to "192.168.1.100"
    And the consent history record should have "userAgent" set to "Mozilla/5.0"

  # AC-0002-11-03: Implicit DATA_PROCESSING Consent
  Scenario: DATA_PROCESSING consent is granted at registration
    Given I have DATA_PROCESSING consent from registration
    When I request my consents
    Then the API should respond with status 200
    And my consents should show "DATA_PROCESSING" as granted
    And my consents should show "DATA_PROCESSING" as required
    And my consents should show "DATA_PROCESSING" with source "REGISTRATION"

  # AC-0002-11-04: Consent Revocation
  @smoke
  Scenario: Revoke previously granted consent
    Given I have granted consent for "MARKETING"
    When I revoke consent for "MARKETING" from "PRIVACY_SETTINGS"
    Then the API should respond with status 200
    And the consent response should have "granted" set to "false"
    When I request my consents
    Then my consents should show "MARKETING" as not granted

  Scenario: Revocation is effective immediately
    Given I have granted consent for "THIRD_PARTY"
    When I revoke consent for "THIRD_PARTY" from "PRIVACY_SETTINGS"
    Then the API should respond with status 200
    When I request my consents
    Then my consents should show "THIRD_PARTY" as not granted

  # AC-0002-11-05: Configurable Expiration
  Scenario: Optional consents have expiration date
    When I grant consent for "PERSONALIZATION" from "PROFILE_WIZARD"
    Then the API should respond with status 200
    And the consent response should have "expiresAt" set approximately 1 year from now

  Scenario: DATA_PROCESSING consent has no expiration
    Given I have DATA_PROCESSING consent from registration
    When I request my consents
    Then my consents should show "DATA_PROCESSING" with no expiration

  # AC-0002-11-06: Event Publishing for Compliance
  Scenario: Consent grant triggers ConsentGranted event
    When I grant consent for "ANALYTICS" from "API"
    Then the API should respond with status 200
    # Event publishing is verified via integration tests

  # AC-0002-11-07: GDPR Export
  @smoke @gdpr
  Scenario: Export complete consent history
    Given I have granted and revoked several consents
    When I export my consent history
    Then the API should respond with status 200
    And the consent history export should include "customerId"
    And the consent history export should include "exportedAt"
    And the consent history export should include "consentHistory"
    And each consent history record should include "consentId"
    And each consent history record should include "consentType"
    And each consent history record should include "granted"
    And each consent history record should include "timestamp"
    And each consent history record should include "source"
    And each consent history record should include "ipAddress"

  Scenario: Export with no consent history returns empty list
    Given I have no consent records
    When I export my consent history
    Then the API should respond with status 200
    And the consent history should be empty

  # AC-0002-11-08: Required Consent Protection
  @smoke
  Scenario: Reject revocation of DATA_PROCESSING consent
    Given I have DATA_PROCESSING consent from registration
    When I try to revoke consent for "DATA_PROCESSING" from "PRIVACY_SETTINGS"
    Then the API should respond with status 400
    And the response should contain error "REQUIRED_CONSENT"
    And the response should contain message "This consent is required for service delivery. To remove it, please close your account."

  # AC-0002-11-09: Consent Version Tracking
  Scenario: Consent changes increment version number
    When I grant consent for "MARKETING" from "PROFILE_WIZARD"
    Then the consent response should have "version" set to "1"
    When I revoke consent for "MARKETING" from "PRIVACY_SETTINGS"
    Then the consent response should have "version" set to "2"
    When I grant consent for "MARKETING" from "PROFILE_WIZARD"
    Then the consent response should have "version" set to "3"

  # List Consents
  @smoke
  Scenario: List all consent statuses
    When I request my consents
    Then the API should respond with status 200
    And the consents response should include "customerId"
    And the consents response should include "consents"
    And the consents should include all consent types

  # Consent Types
  Scenario Outline: Grant and revoke each consent type
    When I grant consent for "<consentType>" from "PRIVACY_SETTINGS"
    Then the API should respond with status 200
    And the consent response should have "consentType" set to "<consentType>"
    And the consent response should have "granted" set to "true"
    When I revoke consent for "<consentType>" from "PRIVACY_SETTINGS"
    Then the API should respond with status 200
    And the consent response should have "granted" set to "false"

    Examples:
      | consentType     |
      | MARKETING       |
      | ANALYTICS       |
      | THIRD_PARTY     |
      | PERSONALIZATION |

  # Validation
  Scenario: Reject invalid consent type
    When I try to grant consent for "INVALID_TYPE" from "PROFILE_WIZARD"
    Then the API should respond with status 400
    And the response should contain error "INVALID_CONSENT_TYPE"

  Scenario: Reject invalid consent source
    When I try to grant consent for "MARKETING" from "INVALID_SOURCE"
    Then the API should respond with status 400
    And the response should contain error "INVALID_SOURCE"

  # No Change Detection
  Scenario: Return no change when consent already in requested state
    Given I have granted consent for "MARKETING"
    When I grant consent for "MARKETING" from "PROFILE_WIZARD"
    Then the API should respond with status 200
    And the response should contain message "Consent already in requested state"

  # Authorization
  @security
  Scenario: Reject access to another customer's consents
    Given another customer exists with id "other-customer-id"
    When I try to get consents for customer "other-customer-id"
    Then the API should respond with status 403
    And the response should contain error "You are not authorized to view this customer's consents"

  @security
  Scenario: Reject consent update for another customer
    Given another customer exists with id "other-customer-id"
    When I try to grant consent for "MARKETING" for customer "other-customer-id"
    Then the API should respond with status 403
    And the response should contain error "You are not authorized to update this customer's consents"

  @security
  Scenario: Reject consent history export for another customer
    Given another customer exists with id "other-customer-id"
    When I try to export consent history for customer "other-customer-id"
    Then the API should respond with status 403
    And the response should contain error "You are not authorized to export this customer's consent history"

  # Invalid Format
  Scenario: Reject unsupported export format
    When I export my consent history with format "xml"
    Then the API should respond with status 400
    And the response should contain error "UNSUPPORTED_FORMAT"
