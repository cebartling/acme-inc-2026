@api @read-model @product-read-model
Feature: Product catalog read model
  Product has no write path: its catalog comes from Flyway migrations. Debezium captures the four
  catalog tables, and a projector builds the catalog read model in MongoDB from their row changes.
  The read endpoints (categories, product detail, prices, availability, search and autocomplete)
  answer from it, so a change to a catalog row shows up a moment later.

  Background:
    Given the Product Service is available

  Scenario: The seeded catalog is in the read model
    Then the category counts the Product Service reports should match the published products in the catalog database

  @catalog-change
  Scenario: A stock change in the catalog reaches the availability endpoint
    Given a variant of a published product is in stock
    When the variant is marked out of stock in the catalog database
    Then the Product Service should eventually report the variant OUT_OF_STOCK
    When the variant is marked in stock in the catalog database
    Then the Product Service should eventually report the variant IN_STOCK

  @catalog-change
  Scenario: A product added to the catalog is served and searchable, and gone once it is deleted
    When a product with a variant is added to the catalog database
    Then the Product Service should eventually serve the product's detail
    And searching for the product's unique word should find only that product
    And the Product Service should report the product's variant IN_STOCK
    When the product is deleted from the catalog database
    Then the Product Service should eventually answer 404 for the product's detail
    And searching for the product's unique word should find nothing
    And the Product Service should answer 404 for the product's variant
