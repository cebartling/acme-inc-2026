@api @customer @address @wip
Feature: Address Management API (US-0002-09)
  As an activated customer
  I want to add, edit, and manage my addresses
  So that I can have accurate shipping and billing information for orders

  Background:
    Given the Customer Service is available
    And I have an authenticated customer

  # AC-0002-09-01: Add Address Successfully
  @smoke
  Scenario: Successfully add a new shipping address
    When I add a shipping address with:
      | streetLine1 | 123 Main Street |
      | streetLine2 | Apt 4B          |
      | city        | New York        |
      | state       | NY              |
      | postalCode  | 10001           |
      | country     | US              |
      | label       | Home            |
      | isDefault   | true            |
    Then the API should respond with status 201
    And the response should contain a valid UUID for "addressId"
    And the response should contain "type" with value "SHIPPING"
    And the response should contain "isDefault" with value "true"

  # AC-0002-09-03: Default Address Management
  Scenario: Setting new default clears previous default
    Given I have a default shipping address labeled "Work"
    When I add a shipping address with:
      | streetLine1 | 456 Oak Avenue |
      | city        | Boston         |
      | state       | MA             |
      | postalCode  | 02101          |
      | country     | US             |
      | label       | New Home       |
      | isDefault   | true           |
    Then the API should respond with status 201
    And the new address should be the default shipping address
    And the previous "Work" address should no longer be default

  # AC-0002-09-05: Address Limit
  Scenario: Reject address when limit exceeded
    Given I already have 10 shipping addresses
    When I add a shipping address with:
      | streetLine1 | 789 New Street |
      | city        | Chicago        |
      | state       | IL             |
      | postalCode  | 60601          |
      | country     | US             |
      | label       | Overflow       |
      | isDefault   | false          |
    Then the API should respond with status 400
    And the response should contain error "MAX_ADDRESSES_REACHED"
    And the response should contain message "Maximum of 10 addresses per type allowed"

  # AC-0002-09-06: Unique Labels
  Scenario: Reject duplicate address label
    Given I have an address labeled "Home"
    When I add a shipping address with:
      | streetLine1 | 100 Different Street |
      | city        | Seattle              |
      | state       | WA                   |
      | postalCode  | 98101                |
      | country     | US                   |
      | label       | Home                 |
      | isDefault   | false                |
    Then the API should respond with status 400
    And the response should contain error "DUPLICATE_LABEL"
    And the response should contain message "Address label already exists: Home"

  # AC-0002-09-09: PO Box Restrictions
  @smoke
  Scenario: Reject PO Box for shipping address
    When I add a shipping address with:
      | streetLine1 | PO Box 123 |
      | city        | Miami      |
      | state       | FL         |
      | postalCode  | 33101      |
      | country     | US         |
      | isDefault   | false      |
    Then the API should respond with status 400
    And the response should contain error "PO_BOX_NOT_ALLOWED"
    And the response should contain message "PO Box addresses cannot be used for shipping"

  Scenario: Allow PO Box for billing address
    When I add a billing address with:
      | streetLine1 | PO Box 456 |
      | city        | Miami      |
      | state       | FL         |
      | postalCode  | 33101      |
      | country     | US         |
      | isDefault   | false      |
    Then the API should respond with status 201
    And the response should contain "type" with value "BILLING"

  # Update Address
  Scenario: Successfully update an address
    Given I have an address labeled "Office"
    When I update the "Office" address with:
      | city       | San Francisco |
      | state      | CA            |
      | postalCode | 94102         |
    Then the API should respond with status 200
    And the response should contain "city" with value "San Francisco"

  Scenario: Reject update with no changes
    Given I have an address labeled "Office"
    When I update the "Office" address with no changes
    Then the API should respond with status 400
    And the response should contain error "No updates provided"

  # Delete Address
  Scenario: Successfully delete an address
    Given I have an address labeled "Temporary"
    When I delete the "Temporary" address
    Then the API should respond with status 204
    And the "Temporary" address should no longer exist

  # List Addresses
  Scenario: List all addresses for customer
    Given I have the following addresses:
      | label     | type     |
      | Home      | SHIPPING |
      | Office    | SHIPPING |
      | Billing   | BILLING  |
    When I request all my addresses
    Then the API should respond with status 200
    And the response should contain 3 addresses

  Scenario: Filter addresses by type
    Given I have the following addresses:
      | label     | type     |
      | Home      | SHIPPING |
      | Office    | SHIPPING |
      | Billing   | BILLING  |
    When I request my addresses filtered by type "SHIPPING"
    Then the API should respond with status 200
    And the response should contain 2 addresses
    And all addresses should have type "SHIPPING"

  # Get Default Address
  Scenario: Get default shipping address
    Given I have the following addresses:
      | label  | type     | isDefault |
      | Home   | SHIPPING | false     |
      | Office | SHIPPING | true      |
    When I request my default shipping address
    Then the API should respond with status 200
    And the response should contain "label" with value "Office"

  # Authorization
  @security
  Scenario: Reject access to another customer's addresses
    Given another customer has an address with id "other-address-id"
    When I try to access that address
    Then the API should respond with status 403
    And the response should contain error "You are not authorized to view this address"

  # Validation Errors
  Scenario: Reject address with blank street
    When I add a shipping address with:
      | streetLine1 |        |
      | city        | Denver |
      | state       | CO     |
      | postalCode  | 80201  |
      | country     | US     |
      | isDefault   | false  |
    Then the API should respond with status 400
    And the response should contain error "VALIDATION_ERROR"
    And the response should contain a validation error for field "street.line1"

  Scenario: Reject address with invalid country code
    When I add a shipping address with:
      | streetLine1 | 123 Main St |
      | city        | Denver      |
      | state       | CO          |
      | postalCode  | 80201       |
      | country     | USA         |
      | isDefault   | false       |
    Then the API should respond with status 400
    And the response should contain error "VALIDATION_ERROR"
    And the response should contain a validation error for field "country"
