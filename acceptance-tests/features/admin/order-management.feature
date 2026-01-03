@admin @orders
Feature: Order Management
  As an admin
  I want to manage customer orders
  So that I can process and fulfill orders efficiently

  Background:
    Given I am logged in as an admin
    And I am on the admin orders page

  @smoke
  Scenario: View orders list
    Then I should see the heading "Orders"
    And I should see orders

  @smoke
  Scenario: View order details
    Given an order "ORD-001" exists with status "pending"
    When I view order "ORD-001" details
    Then the order detail modal should be visible
    And the order total should be displayed
    And the customer information should be displayed
    And the shipping address should be displayed

  @smoke
  Scenario: Update order status to processing
    Given an order "ORD-001" exists with status "pending"
    When I view order "ORD-001" details
    And I update the order status to "processing"
    And I close the order details
    Then the order "ORD-001" status should be "processing"

  Scenario: Update order status to shipped
    Given an order "ORD-002" exists with status "processing"
    When I view order "ORD-002" details
    And I update the order status to "shipped"
    And I close the order details
    Then the order "ORD-002" status should be "shipped"

  Scenario: Update order status to delivered
    Given an order "ORD-003" exists with status "shipped"
    When I view order "ORD-003" details
    And I update the order status to "delivered"
    And I close the order details
    Then the order "ORD-003" status should be "delivered"

  Scenario: Cancel an order
    Given an order "ORD-004" exists with status "pending"
    When I view order "ORD-004" details
    And I update the order status to "cancelled"
    And I close the order details
    Then the order "ORD-004" status should be "cancelled"

  Scenario: Search for order by ID
    Given an order "ORD-005" exists with status "pending"
    When I search for order "ORD-005"
    Then I should see order "ORD-005" in the list

  Scenario: Filter orders by status
    When I filter orders by status "pending"
    Then I should see orders

  Scenario: Filter orders by shipped status
    When I filter orders by status "shipped"
    Then I should see orders

  Scenario: Refresh orders list
    When I refresh the orders list
    Then I should see orders

  Scenario: Export orders
    When I export orders
    Then I should see a success message "Export started"
