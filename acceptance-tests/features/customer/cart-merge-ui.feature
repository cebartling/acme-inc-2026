@customer @cart
Feature: Cart Merge on Sign-in
  As a guest customer who has added items to my cart and then signs in
  I want my guest cart merged into my account cart without doing anything
  So that I do not lose the items I added before authenticating

  # Gadget Pro defaults to its Black variant. The cart service caps a variant at 10.

  Background:
    Given an active customer with email "cart-merge-ui@example.com" exists

  # AC-0004-08-01, AC-0004-08-06
  @smoke
  Scenario: Signing in brings the guest cart along
    Given I am on the product page for "gadget-pro"
    And I set the quantity to 2
    And I click Add to Cart
    When I navigate to "/signin"
    And I sign in with valid credentials
    Then the cart badge should show 2

  # AC-0004-08-04
  Scenario: A capped merge explains itself and can be dismissed
    Given I am signed in through the API as that customer
    And the customer's account cart has 2 "Gadget Pro / Black"
    And I am on the product page for "gadget-pro"
    And I set the quantity to 9
    And I click Add to Cart
    When I navigate to "/signin"
    And I sign in with valid credentials
    Then the cart badge should show 10
    And I should see the cart notice "Quantity for Gadget Pro was adjusted to the maximum of 10."
    When I dismiss the cart notice
    Then the cart notice should be gone

  Scenario: Signing out leaves the account cart behind
    Given I am on the product page for "gadget-pro"
    And I set the quantity to 2
    And I click Add to Cart
    And I navigate to "/signin"
    And I sign in with valid credentials
    And the cart badge should show 2
    When I sign out
    Then the cart badge should show no count
