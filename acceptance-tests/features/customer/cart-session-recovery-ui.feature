@customer @cart
Feature: Cart Session Recovery
  As a customer whose cart session has expired
  I want my next cart action to just work
  So that I am not blocked from shopping when my session times out

  # The session cookie is HttpOnly and minted by the cart service, so recovery happens on
  # the server: an add without a cookie starts a fresh session and cart. Items from the
  # expired session are not restored (AC-0004-12-06).

  Background:
    Given I am on the product page for "gadget-pro"
    And I set the quantity to 2
    And I click Add to Cart

  # AC-0004-12-01, AC-0004-12-03, AC-0004-12-06
  @smoke
  Scenario: Adding to cart after the session expired starts a fresh cart
    When my cart session cookie expires
    And I set the quantity to 1
    And I click Add to Cart
    Then the add to cart confirmation should show "1 × Gadget Pro (Black)"
    And the cart badge should show 1
    And the browser should hold an HttpOnly SameSite=Lax session cookie for about 30 days

  # AC-0004-12-02, AC-0004-12-05, AC-0004-12-06
  Scenario: Changing a line after the session expired shows the empty cart, not an error
    Given I am on the cart page
    When my cart session cookie expires
    And I increase the line quantity
    Then I should see the empty cart with a link to continue shopping
    And the cart badge should show no count
