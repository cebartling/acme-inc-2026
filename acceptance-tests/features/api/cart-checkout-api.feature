@api @cart
Feature: Start Checkout API
  As a customer with items in my cart
  I want to start checkout
  So that my cart is checked and held while I complete my order

  # PIN-329 (journey 0005, step 1). Gadget Pro Black is seeded in stock and Silver out of stock.
  # Unlocking an abandoned checkout is PIN-330.

  # AC-1.2, AC-1.5
  Scenario: Starting checkout locks the cart against changes
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I start checkout on my cart
    Then the API should respond with status 200
    And a checkout session should be started for my cart
    When I add 1 more of the same variant to my cart
    Then the API should respond with status 409
    And the response should contain error code "CART_LOCKED"
    When I get my current cart
    Then my cart should be locked for checkout
    And the cart should have 1 line with quantity 2 at unit price 119.99

  Scenario: Starting checkout again returns the same session
    Given I have added 1 "Gadget Pro / Black" to a new cart
    And I have started checkout on my cart
    When I start checkout on my cart
    Then the API should respond with status 200
    And it should be the same checkout session

  # AC-1.1
  Scenario: An empty cart cannot start checkout
    Given I have added 1 "Gadget Pro / Black" to a new cart
    And I have removed that line
    When I start checkout on my cart
    Then the API should respond with status 422
    And the response should contain error code "CART_EMPTY"

  # AC-1.5, AC-1.7
  Scenario: A cart with an out-of-stock line cannot start checkout
    Given I have added 1 "Gadget Pro / Black" to a new cart
    And I have also added 1 "Gadget Pro / Silver" to my cart
    When I start checkout on my cart
    Then the API should respond with status 422
    And the response should contain error code "CART_VALIDATION_FAILED"
    And checkout should report "Gadget Pro / Silver" as "OUT_OF_STOCK"
    When I add 1 more of the same variant to my cart
    Then the API should respond with status 201
