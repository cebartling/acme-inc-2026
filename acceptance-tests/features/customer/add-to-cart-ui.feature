@customer @cart
Feature: Add Item to Cart
  As a customer who has chosen a product and variant
  I want to add the item to my shopping cart
  So that I can purchase it along with other items I have selected

  # Gadget Pro defaults to its Black variant: $119.99, or $109.99 each for 3 or more.
  # The cart service caps a variant at 10 units per cart.
  #
  # The out-of-stock disabled button is covered by AddToCartForm's unit tests; out-of-stock
  # handling end to end belongs to US-0004-10.

  Background:
    Given I am on the product page for "gadget-pro"

  # AC-0004-06-06, AC-0004-06-07
  @smoke
  Scenario: Customer adds an item and sees the confirmation and cart badge
    When I set the quantity to 2
    And I click Add to Cart
    Then the add to cart confirmation should show "2 × Gadget Pro (Black)"
    And the add to cart confirmation should show "Line total: $239.98"
    And the cart badge should show 2

  # AC-0004-06-04, AC-0004-06-09
  Scenario: Adding the same variant again merges into one line at the tier price
    When I set the quantity to 2
    And I click Add to Cart
    And I set the quantity to 1
    And I click Add to Cart
    Then the add to cart confirmation should show "$109.99 each"
    And the add to cart confirmation should show "(3 in cart)"
    And the cart badge should show 3

  # AC-0004-06-05
  Scenario: Exceeding the maximum order quantity shows an error
    When I set the quantity to 11
    And I click Add to Cart
    Then I should see the add to cart error "Maximum order quantity is 10 for this item"
    And the cart badge should show no count
