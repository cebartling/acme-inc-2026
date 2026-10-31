@customer @orders
Feature: See my orders
  As a signed-in customer
  I want to see the orders I have placed
  So that I can check what I bought

  # PIN-428, epic 005 F5.

  Background:
    Given an active customer with email "order-history-ui@example.com" exists

  Scenario: A guest is asked to sign in to see their orders
    When I navigate to "/orders"
    Then I should be asked to sign in and come back to my orders

  Scenario: A customer with no orders sees an empty state
    Given I navigate to "/signin"
    And I sign in with valid credentials
    When I navigate to "/orders"
    Then I should be told I have no orders

  @smoke
  Scenario: A placed order appears in the history and opens its detail page
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And I am on the product page for "gadget-pro"
    And I set the quantity to 2
    And I click Add to Cart
    And I navigate to "/cart"
    And I press Checkout
    And I should see "Gadget Pro" on the order review
    And I place the order
    And I should see the order confirmation with an order number
    And I note the order number
    When I navigate to "/orders"
    Then I should see that order in my order history with 2 items
    When I open that order
    Then I should see that order's details with "Gadget Pro"

  Scenario: The confirmation page links to the order's detail page
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And I am on the product page for "gadget-pro"
    And I click Add to Cart
    And I navigate to "/cart"
    And I press Checkout
    And I should see "Gadget Pro" on the order review
    And I place the order
    And I should see the order confirmation with an order number
    And I note the order number
    When I click the "View order" link
    Then I should see that order's details with "Gadget Pro"

  # Seeds the profile like profile-completeness-widget.feature: GET /customers/me needs an
  # X-User-Id header nothing sends, so a real sign-in gets "Error Loading Profile" on /dashboard.
  Scenario: The dashboard links to the order history
    Given I am logged in as an authenticated customer
    When I navigate to the dashboard
    Then the dashboard should link to my orders

  Scenario Outline: An order that can't be found shows the not-found page
    Given I navigate to "/signin"
    And I sign in with valid credentials
    When I navigate to "<path>"
    Then I should see the order not found page

    Examples:
      | path                                         |
      | /orders/00000000-0000-0000-0000-000000000000 |
      | /orders/not-a-uuid                           |
