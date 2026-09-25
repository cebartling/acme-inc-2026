@api @cart
Feature: Add Item to Cart API
  As a guest customer
  I want the shopping cart service to keep the items I add
  So that my cart is priced correctly and survives across requests

  # Gadget Pro (Black) costs $119.99, or $109.99 each for 3 or more.
  # The cart service caps a variant at 10 units per cart.

  # AC-0004-06-10, AC-0004-07-01, AC-0004-07-09
  @smoke
  Scenario: First add creates a guest cart and sets the session cookie
    When I add 2 "Gadget Pro / Black" to a new cart
    Then the API should respond with status 201
    And the response should set an HttpOnly SameSite=Lax session cookie
    And the cart should have 1 line with quantity 2 at unit price 119.99
    And the cart item count should be 2

  # AC-0004-06-04, AC-0004-06-09
  Scenario: Adding the same variant again merges the line and applies tier pricing
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I add 1 more of the same variant to my cart
    Then the API should respond with status 201
    And the response should re-issue the same session cookie
    And the cart should have 1 line with quantity 3 at unit price 109.99

  # AC-0004-06-05
  Scenario: Exceeding the maximum order quantity is rejected
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I add 9 more of the same variant to my cart
    Then the API should respond with status 422
    And the response should contain error "Maximum order quantity is 10 for this item"

  Scenario: Adding an unknown variant is rejected
    When I add 1 of an unknown variant to a new cart
    Then the API should respond with status 404
