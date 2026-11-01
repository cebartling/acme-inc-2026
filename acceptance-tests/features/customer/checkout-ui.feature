@customer @cart @checkout
Feature: Review the cart and place an order
  As a signed-in customer with items in my cart
  I want to review my order and place it
  So that I get an order number for what I bought

  # PIN-426, journey 0005 steps 1, 4, 5 and 6, without payment, shipping or tax.
  # Gadget Pro defaults to its Black variant.

  Background:
    Given an active customer with email "checkout-ui@example.com" exists

  Scenario: A guest is asked to sign in before checking out
    Given I am on the product page for "gadget-pro"
    And I click Add to Cart
    And I navigate to "/cart"
    When I press Checkout
    Then I should be asked to sign in and come back to the cart

  @smoke
  Scenario: A signed-in customer places an order
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And I am on the product page for "gadget-pro"
    And I set the quantity to 2
    And I click Add to Cart
    And I navigate to "/cart"
    When I press Checkout
    Then I should see "Gadget Pro" on the order review
    When I place the order
    Then I should see the order confirmation with an order number
    And the cart badge should show no count
    When I reload the confirmation page
    Then I should see the order confirmation with an order number
    And only one order request should have been sent

  # Journey 0005 E6: the cart only answers live sessions
  Scenario: Placing an order after the checkout session lapsed
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And I am on the product page for "gadget-pro"
    And I click Add to Cart
    And I navigate to "/cart"
    And I press Checkout
    And I should see "Gadget Pro" on the order review
    When my checkout session lapses
    And I place the order
    Then I should be told my checkout session expired

  # PIN-439: going back releases the checkout lock, so the cart isn't CART_LOCKED
  Scenario: Going back from the review leaves the cart editable
    Given I navigate to "/signin"
    And I sign in with valid credentials
    And I am on the product page for "gadget-pro"
    And I click Add to Cart
    And I navigate to "/cart"
    And I press Checkout
    And I should see "Gadget Pro" on the order review
    When I go back to my cart from the review
    And I increase the line quantity
    Then the line quantity should be 2
