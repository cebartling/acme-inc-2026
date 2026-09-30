@customer @cart
Feature: Out of Stock Handling
  As a customer who wants to purchase a product that is currently out of stock
  I want to see a clear out-of-stock indication and be offered helpful alternatives
  So that I understand I cannot add the item now and have options for when it becomes available

  # US-0004-10 (PIN-273). Gadget Pro's seeded variants (V4): Black and White in stock, Silver
  # out of stock. Nothing changes stock at runtime yet, so the cart scenario has the inventory
  # endpoint report a line out of stock. Notify When Available (AC-03) is PIN-316; the search
  # badge (AC-06) has no seeded product with every variant out of stock and is unit-tested.

  # AC-0004-10-01, -02, -04, -07, -08
  Scenario: An out-of-stock variant cannot be added, and alternatives are offered
    Given I am on the product page for "gadget-pro"
    When I select the color "Silver"
    Then the availability badge should show "Out of Stock"
    And the Add to Cart button should be disabled
    And pressing Add to Cart should make no cart request
    And I should see the product section "Alternatives you might like"
    When I select the color "Black"
    Then the availability badge should show "In Stock"
    And the Add to Cart button should be enabled

  # AC-0004-10-05
  Scenario: A cart line that went out of stock is flagged with a way to remove it
    Given I am on the product page for "gadget-pro"
    And I click Add to Cart
    And the inventory service reports the Black Gadget Pro out of stock
    And I am on the cart page
    Then the cart line should warn "Gadget Pro is now out of stock and cannot be included in your order."
    And the cart line's Remove button should read "Remove"
