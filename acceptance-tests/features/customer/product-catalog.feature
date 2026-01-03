@customer @catalog
Feature: Product Catalog
  As a customer
  I want to browse and search products
  So that I can find items I want to purchase

  @smoke
  Scenario: View product list
    Given I am on the products page
    Then I should see the heading "Products"
    And I should see products matching ""

  Scenario: Search for products
    Given I am on the customer home page
    When I search for "laptop"
    Then I should see products matching "laptop"

  Scenario: Search with no results
    Given I am on the customer home page
    When I search for "xyznonexistentproduct123"
    Then I should see no products found

  @smoke
  Scenario: View product details
    Given I am on the products page
    When I click on product "Test Product"
    Then I should see the product details
    And the product should be in stock

  Scenario: Filter products by category
    Given I am on the products page
    When I filter by category "Electronics"
    Then I should see products matching "Electronics"

  Scenario: Filter products by price range
    Given I am on the products page
    When I filter by price range 10 to 50
    Then I should see products matching ""

  Scenario: Sort products by price low to high
    Given I am on the products page
    When I sort products by "price-asc"
    Then products should be sorted by price ascending

  Scenario: Sort products by price high to low
    Given I am on the products page
    When I sort products by "price-desc"
    Then products should be sorted by price descending

  Scenario: View out of stock product
    Given I am viewing product "out-of-stock-product"
    Then the product should be out of stock
    And the add to cart button should be disabled

  Scenario: Load more products
    Given I am on the products page
    When I click load more products
    Then I should see products matching ""
