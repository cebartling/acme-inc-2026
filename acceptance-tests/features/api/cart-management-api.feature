@api @cart
Feature: Guest Cart Management API
  As a guest customer
  I want my cart to be readable and editable across requests
  So that it persists while I shop and stays correctly priced

  # Gadget Pro (Black) costs $119.99, $109.99 each for 3+, $99.99 each for 10+.
  # The cart service caps a variant at 10 units per cart.

  # AC-0004-07-10: a first-time visitor has no cart, and that is not an error
  Scenario: A visitor with no session has no current cart
    When I get the current cart without a session cookie
    Then the API should respond with status 204

  # AC-0004-07-02, AC-0004-07-03
  Scenario: The session cookie reads back the same cart
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I get my current cart
    Then the API should respond with status 200
    And the cart should have 1 line with quantity 2 at unit price 119.99
    And the cart item count should be 2

  # AC-0004-07-04, AC-0004-07-07
  Scenario: Changing a quantity reprices the line up and down the tiers
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I change the quantity of that line to 3
    Then the API should respond with status 200
    And the cart should have 1 line with quantity 3 at unit price 109.99
    When I change the quantity of that line to 1
    Then the cart should have 1 line with quantity 1 at unit price 119.99

  # AC-0004-07-08
  Scenario: An over-max quantity is rejected with the limit
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I change the quantity of that line to 11
    Then the API should respond with status 422
    And the response should contain error "Maximum order quantity is 10 for this item"
    And the response should include a max quantity of 10

  # AC-0004-07-05
  Scenario: Removing the only line leaves an empty cart
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I remove that line
    Then the API should respond with status 200
    And the cart should be empty

  # Ownership: another session's cart is indistinguishable from a missing one
  Scenario: Another session cannot change or remove my cart's lines
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When another session changes the quantity of that line to 5
    Then the API should respond with status 404
    When another session removes that line
    Then the API should respond with status 404
    When I get my current cart
    Then the cart should have 1 line with quantity 2 at unit price 119.99

  # US-0004-12: every guest request slides the cookie, so an active cart never expires
  Scenario: Reading, changing and removing re-issue the session cookie
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I get my current cart
    Then the response should re-issue the same session cookie
    When I change the quantity of that line to 3
    Then the response should re-issue the same session cookie
    When I remove that line
    Then the response should re-issue the same session cookie
