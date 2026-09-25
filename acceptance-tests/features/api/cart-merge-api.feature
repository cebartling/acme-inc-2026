@api @cart
Feature: Cart Merge on Sign-in API
  As a guest customer who has added items to my cart and then signs in
  I want my guest cart merged into my account cart
  So that I do not lose the items I added before authenticating

  # Gadget Pro (Black) costs $119.99, $109.99 each for 3+, $99.99 each for 10+.
  # The cart service caps a variant at 10 units per cart.

  Background:
    Given an active customer with email "cart-merge@example.com" exists
    And I am signed in through the API as that customer

  # AC-0004-08-02
  @smoke
  Scenario: The account cart's own items are kept alongside the guest's
    Given the customer's account cart has 1 "Gadget Pro / White"
    And I have added 2 "Gadget Pro / Black" to a new cart
    When I merge my guest cart as the signed-in customer
    Then the API should respond with status 200
    And the cart should contain 1 of "Gadget Pro / White"
    And the cart should contain 2 of "Gadget Pro / Black"
    And the merge should report 1 item merged and 0 quantity adjustments

  # AC-0004-08-03
  Scenario: A variant in both carts becomes one line and is repriced at its tier
    Given the customer's account cart has 1 "Gadget Pro / Black"
    And I have added 2 "Gadget Pro / Black" to a new cart
    When I merge my guest cart as the signed-in customer
    Then the API should respond with status 200
    And the cart should have 1 line with quantity 3 at unit price 109.99
    And the merge should report 1 item merged and 0 quantity adjustments

  # AC-0004-08-04
  Scenario: A merged quantity over the maximum is capped and reported
    Given the customer's account cart has 2 "Gadget Pro / Black"
    And I have added 9 "Gadget Pro / Black" to a new cart
    When I merge my guest cart as the signed-in customer
    Then the API should respond with status 200
    And the cart should have 1 line with quantity 10 at unit price 99.99
    And the merge should report 1 item merged and 1 quantity adjustment

  # AC-0004-08-05
  Scenario: A merged guest cart is gone for its session and cannot be merged twice
    Given I have added 2 "Gadget Pro / Black" to a new cart
    And I have merged my guest cart as the signed-in customer
    When I get my current cart as the old guest session
    Then the API should respond with status 204
    When I merge my guest cart as the signed-in customer
    Then the API should respond with status 200
    And the merge should report nothing merged
    And the cart should have 1 line with quantity 2 at unit price 119.99

  # AC-0004-08-07
  Scenario: The signed-in cart is reachable from another device
    Given I have added 2 "Gadget Pro / Black" to a new cart
    And I have merged my guest cart as the signed-in customer
    When I get my current cart as the signed-in customer on another device
    Then the API should respond with status 200
    And the cart should have 1 line with quantity 2 at unit price 119.99

  # AC-0004-08-08
  Scenario: An empty guest cart merges as a no-op
    Given the customer's account cart has 1 "Gadget Pro / Black"
    And I have added 2 "Gadget Pro / Black" to a new cart
    And I have removed that line
    When I merge my guest cart as the signed-in customer
    Then the API should respond with status 200
    And the merge should report nothing merged
    And the cart should have 1 line with quantity 1 at unit price 119.99

  Scenario: Merging requires a signed-in customer
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I merge without signing in
    Then the API should respond with status 401
    And the response should contain error "SIGN_IN_REQUIRED"

  # US-0004-12: a session whose cart is gone gets a fresh cart; its old lines are 404s
  Scenario: The old guest session starts a fresh cart after its cart was merged
    Given I have added 2 "Gadget Pro / Black" to a new cart
    And I have merged my guest cart as the signed-in customer
    When I change the quantity of that line to 3
    Then the API should respond with status 404
    When I add 1 more of the same variant to my cart
    Then the API should respond with status 201
    And the cart should have 1 line with quantity 1 at unit price 119.99
    And the response should re-issue the same session cookie
