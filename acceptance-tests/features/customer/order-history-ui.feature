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

  Scenario: A guest comes back to the same page of their orders after signing in
    When I navigate to "/orders?page=2"
    And I sign in from the page I was sent to
    Then I should be on "/orders?page=2"

  # A hand-typed URL, but sign-in's redirect check used to turn it into the router's error page.
  Scenario: An order ID that looks like a URL doesn't break sign-in
    When I navigate to "/orders/a%3A%2F%2Fb"
    And I sign in from the page I was sent to
    Then I should see the order not found page

  Scenario: A customer whose access cookie is gone is signed out, and comes back
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And my access cookie is gone
    When I navigate to "/orders?page=2"
    Then the header should show me signed out
    When I sign in from the page I was sent to
    Then I should be on "/orders?page=2"

  Scenario: A session that can't be refreshed comes back to the page after sign-in
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And my session can't be refreshed
    When I navigate to "/orders?page=2"
    Then the header should show me signed out
    When I sign in from the page I was sent to
    Then I should be on "/orders?page=2"

  Scenario: A customer whose access cookie is gone goes from the dashboard to sign-in and back
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And my access cookie is gone
    When I navigate to "/dashboard"
    Then the header should show me signed out
    When I sign in from the page I was sent to
    Then I should be on "/dashboard"

  Scenario: A dead session doesn't move a customer off a password reset link
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And my session can't be refreshed
    When I navigate to "/reset-password?token=not-a-real-token"
    Then I should stay on "/reset-password?token=not-a-real-token"

  Scenario: Orders that fail to load can be tried again
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And the next read of my orders fails
    When I navigate to "/orders"
    Then I should be told my orders couldn't be loaded
    When I try loading my orders again
    Then I should be told I have no orders

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
    When I open my orders from the user menu
    Then I should see that order in my order history with 2 items
    When I open that order
    Then I should see that order's details with "Gadget Pro"

  # The service would need 11 orders for a real second page, so its list is reported as page 2.
  Scenario: Back to your orders returns to the page the order was opened from
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
    And my order history has a second page
    When I navigate to "/orders?page=2"
    And I open that order
    Then I should be on the order's page with "?page=2"
    When I click the "Back to your orders" link
    Then I should be on "/orders?page=2"

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

  Scenario: The dashboard links to the order history
    Given I navigate to "/signin"
    And I sign in with valid credentials
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
