@customer @checkout @wip
Feature: Checkout Process
  As a customer
  I want to complete the checkout process
  So that I can purchase the items in my cart

  Background:
    Given I am logged in as a customer
    And I have items in my cart
    And I am on the checkout page

  @smoke
  Scenario: Complete checkout with valid information
    When I fill in my shipping address
    And I use the same address for billing
    And I fill in my payment details
    And I place the order
    Then I should see the order confirmation
    And I should see my order number

  Scenario: Checkout with separate billing address
    When I fill in my shipping address
    And I enter different billing address
    And I fill in my payment details
    And I place the order
    Then I should see the order confirmation

  Scenario: Checkout with custom shipping address
    When I fill in shipping address with:
      | First Name | John                |
      | Last Name  | Doe                 |
      | Street     | 456 Oak Avenue      |
      | City       | San Francisco       |
      | State      | CA                  |
      | Zip Code   | 94102               |
      | Country    | United States       |
    And I use the same address for billing
    And I fill in my payment details
    And I place the order
    Then I should see the order confirmation

  Scenario: Checkout with invalid card number
    When I fill in my shipping address
    And I use the same address for billing
    And I fill in payment details with:
      | Card Number  | 1234567890123456 |
      | Expiry       | 12/25            |
      | CVC          | 123              |
      | Name on Card | Test Customer    |
    And I place the order
    Then I should see a checkout error
    And the checkout error should say "Invalid card number"

  Scenario: Checkout with expired card
    When I fill in my shipping address
    And I use the same address for billing
    And I fill in payment details with:
      | Card Number  | 4111111111111111 |
      | Expiry       | 01/20            |
      | CVC          | 123              |
      | Name on Card | Test Customer    |
    And I place the order
    Then I should see a checkout error
    And the checkout error should say "Card has expired"

  Scenario: Go back to cart from checkout
    When I go back to cart from checkout
    Then I should be on the "cart" page

  Scenario: View order total during checkout
    Then the order total should be displayed
