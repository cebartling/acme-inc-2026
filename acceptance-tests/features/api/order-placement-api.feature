@api @order
Feature: Place Order API
  As a signed-in customer in checkout
  I want to place my order
  So that my cart becomes an order I can track

  # PIN-366 (journey 0005, step 5, without payment, shipping or tax). The order service reads
  # the checkout session's cart and converts it, forwarding the customer's access_token cookie.

  Background:
    Given an active customer with email "order-placement@example.com" exists
    And I am signed in through the API as that customer
    And the customer's account cart has 2 "Gadget Pro / Black"
    And I have started checkout on my account cart

  # AC-5.4
  Scenario: Placing an order converts the cart
    When I place an order for my checkout session
    Then the API should respond with status 201
    And the order should be submitted with an order number for today
    And my account cart should be converted into the order

  # AC-5.8
  Scenario: Placing the same order twice with the same idempotency key creates one order
    Given I place an order for my checkout session
    When I place the order again with the same idempotency key
    Then the API should respond with status 201
    And it should be the same order

  Scenario: Placing an order requires signing in
    When I place an order without signing in
    Then the API should respond with status 401

  # PIN-440: asking about an order that was lost in transit places nothing
  Scenario: A placed order can be found by its idempotency key
    Given I place an order for my checkout session
    When I look up the order by its idempotency key
    Then the API should respond with status 200
    And it should be the same order

  Scenario: An idempotency key with no order finds nothing, and the cart stays in checkout
    When I look up an order by a key that was never used
    Then the API should respond with status 404
    And I place an order for my checkout session
    And the API should respond with status 201

  Scenario: Another customer can't find my order by its idempotency key
    Given I place an order for my checkout session
    And an active customer with email "order-other-customer@example.com" exists
    And I am signed in through the API as that customer
    When I look up the order by its idempotency key
    Then the API should respond with status 404

  # PIN-367 (journey 0005, step 6)
  Scenario: A placed order can be read back and appears in my order history
    Given I place an order for my checkout session
    When I get my order
    Then the API should respond with status 200
    And the order should have 2 of "Gadget Pro"
    When I list my orders
    Then the API should respond with status 200
    And my order should be first in the list, with 2 items

  Scenario: Another customer can't read my order
    Given I place an order for my checkout session
    And an active customer with email "order-other-customer@example.com" exists
    And I am signed in through the API as that customer
    When I get my order
    Then the API should respond with status 404

  # PIN-429
  Scenario: A cancelled order can still be read back and stays in my order history
    Given I place an order for my checkout session
    When I cancel my order because "Changed my mind"
    Then the API should respond with status 200
    And my order should be cancelled
    When I get my order
    Then the API should respond with status 200
    And my order should be cancelled
    When I list my orders
    Then the API should respond with status 200
    And my order should be first in the list, with 2 items

  Scenario: Cancelling an order twice answers the same cancelled order
    Given I place an order for my checkout session
    And I cancel my order because "Changed my mind"
    When I cancel my order because "Changed my mind"
    Then the API should respond with status 200
    And my order should be cancelled

  Scenario: Cancelling an order needs a reason
    Given I place an order for my checkout session
    When I cancel my order without a reason
    Then the API should respond with status 400
    And the response should contain error code "INVALID_REQUEST"

  Scenario: Another customer can't cancel my order
    Given I place an order for my checkout session
    And an active customer with email "order-other-customer@example.com" exists
    And I am signed in through the API as that customer
    When I cancel my order because "Not mine"
    Then the API should respond with status 404
