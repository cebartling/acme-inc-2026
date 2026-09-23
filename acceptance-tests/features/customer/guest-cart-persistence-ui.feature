@customer @cart
Feature: Guest Cart Persistence
  As a guest customer browsing without an account
  I want my shopping cart to be saved across page navigations and browser sessions
  So that I do not lose my selected items if I leave and return to the site

  # Gadget Pro (Black) costs $119.99, $109.99 each for 3+, $99.99 each for 10+.
  # The cart service caps a variant at 10 units per cart.

  Background:
    Given I am on the product page for "gadget-pro"
    And I set the quantity to 2
    And I click Add to Cart

  # AC-0004-07-01, AC-0004-07-09
  @smoke
  Scenario: The session cookie is HttpOnly, SameSite=Lax and lasts 30 days
    Then the browser should hold an HttpOnly SameSite=Lax session cookie for about 30 days
    And page scripts should not be able to read the session cookie

  # AC-0004-07-02, AC-0004-07-10
  Scenario: The cart badge survives navigation and a reload
    When I navigate to "/"
    Then the cart badge should show 2
    When I refresh the page
    Then the cart badge should show 2

  # AC-0004-07-03
  Scenario: A new tab restores the cart
    When I open the cart page in a new tab
    Then the cart should show 1 line with quantity 2
    And the cart badge should show 2

  # AC-0004-07-04, AC-0004-07-07
  Scenario: Increasing a quantity reprices the line and the totals
    Given I am on the cart page
    When I increase the line quantity
    Then the line should show "$109.99 each"
    And the cart subtotal should be "$329.97"
    And the estimated total should be "$329.97"
    And the cart badge should show 3

  # AC-0004-07-08
  Scenario: An over-max quantity is clamped with an explanation
    Given I am on the cart page
    When I type a line quantity of 11
    Then the line should explain "Maximum order quantity is 10 for this item"
    And the line quantity should be 10
    And the cart subtotal should be "$999.90"

  # AC-0004-07-05, AC-0004-07-06
  Scenario: Removing the last item shows the empty cart
    Given I am on the cart page
    When I remove the line
    Then I should see the empty cart with a link to continue shopping
    And the cart badge should show no count
