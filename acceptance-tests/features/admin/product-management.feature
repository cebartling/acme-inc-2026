@admin @products
Feature: Product Management
  As an admin
  I want to manage products in the catalog
  So that I can maintain the product inventory

  Background:
    Given I am logged in as an admin
    And I am on the admin products page

  @smoke
  Scenario: View products list
    Then I should see the heading "Products"
    And I should see products in the admin list

  @smoke
  Scenario: Create a new product
    When I click add product
    Then the product modal should be visible
    When I fill in the product details
    And I save the product
    Then I should see the product in the list
    And I should see a success message "Product created successfully"

  Scenario: Create product with custom details
    When I click add product
    And I fill in product details with:
      | Name        | Custom Widget         |
      | Description | A custom test widget  |
      | Price       | 49.99                 |
      | Category    | Electronics           |
      | SKU         | WIDGET-001            |
      | Quantity    | 50                    |
      | In Stock    | true                  |
    And I save the product
    Then I should see product "Custom Widget" in the list

  Scenario: Cancel product creation
    When I click add product
    And I fill in the product details
    And I cancel the product form
    Then the product modal should be hidden

  @smoke
  Scenario: Edit existing product
    Given a product "Existing Product" exists
    When I edit product "Existing Product"
    Then the product modal should be visible
    When I fill in product details with:
      | Name        | Updated Product       |
      | Description | Updated description   |
      | Price       | 99.99                 |
      | Category    | Electronics           |
      | SKU         | UPDATED-001           |
      | Quantity    | 25                    |
      | In Stock    | true                  |
    And I save the product
    Then I should see product "Updated Product" in the list

  @smoke
  Scenario: Delete a product
    Given a product "Product to Delete" exists
    When I delete product "Product to Delete"
    Then I should not see product "Product to Delete" in the list
    And I should see a success message "Product deleted successfully"

  Scenario: Search for products
    When I search for product "Widget"
    Then I should see products matching "Widget"

  Scenario: Filter products by category
    When I filter products by category "Electronics"
    Then I should see products in the admin list

  Scenario: Filter products by stock status
    When I filter products by stock status "out-of-stock"
    Then I should see products in the admin list
