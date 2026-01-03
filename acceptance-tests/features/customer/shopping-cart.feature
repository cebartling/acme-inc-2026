@customer @cart
Feature: Shopping Cart
  As a customer
  I want to manage items in my shopping cart
  So that I can purchase multiple products at once

  Background:
    Given I am logged in as a customer

  @smoke
  Scenario: Add product to cart
    Given I am viewing product "test-product"
    When I add the product to cart
    Then the cart badge should show 1

  Scenario: Add multiple quantities to cart
    Given I am viewing product "test-product"
    When I add 3 items to cart
    Then the cart badge should show 3

  Scenario: View cart contents
    Given I have items in my cart
    When I go to cart
    Then I should see 1 items in my cart

  @smoke
  Scenario: Update item quantity in cart
    Given I am on the cart page
    And I have items in my cart
    When I update item 1 quantity to 5
    Then the item 1 quantity should be 5

  Scenario: Remove item from cart
    Given I am on the cart page
    And I have items in my cart
    When I remove item 1 from cart
    Then the cart should be empty

  Scenario: Apply valid promo code
    Given I am on the cart page
    And I have items in my cart
    When I apply promo code "SAVE10"
    Then I should see a promo discount of 10.00

  Scenario: Apply invalid promo code
    Given I am on the cart page
    And I have items in my cart
    When I apply promo code "INVALIDCODE"
    Then I should see a promo code error

  Scenario: Proceed to checkout from cart
    Given I am on the cart page
    And I have items in my cart
    When I proceed to checkout
    Then I should be on the "checkout" page

  Scenario: Continue shopping from cart
    Given I am on the cart page
    When I click continue shopping
    Then I should be on the "products" page

  Scenario: Empty cart message
    Given I have an empty cart
    When I go to cart
    Then the cart should be empty
    And I should see the text "Your cart is empty"
