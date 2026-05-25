@customer @product-search
Feature: Product Search
  As a customer browsing the ACME platform
  I want to search for products using a search bar
  So that I can quickly find products I am interested in purchasing

  Background:
    Given I am on the home page

  # AC-0004-01-01 + AC-0004-01-02: Search results with count
  @smoke
  Scenario: Customer searches for a product and sees results with count
    When I enter a search query "widget"
    And I submit the search
    Then I should be on the search results page
    And I should see a result count above the product grid
    And the result count should contain "widget"
    And I should see product cards in the results

  # AC-0004-01-03: Empty state for zero-result queries
  Scenario: Customer searches for a product that does not exist
    When I enter a search query "xyzzy-nonexistent-product-12345"
    And I submit the search
    Then I should be on the search results page
    And I should see the empty search state
    And I should not see an error message

  # AC-0004-01-05: URL contains ?q= and is bookmarkable
  Scenario: Search query is reflected in the URL
    When I enter a search query "gadget"
    And I submit the search
    Then the URL should contain "q=gadget"

  # AC-0004-01-06: Pagination renders for >24 results
  Scenario: Pagination controls appear when there are more than 24 results
    Given there are more than 24 products matching "product"
    When I enter a search query "product"
    And I submit the search
    Then I should see pagination controls
    When I click the next page button
    Then the URL should contain "page=2"

  # AC-0004-01-07: Sort selector updates URL
  Scenario: Customer changes the sort order
    When I enter a search query "widget"
    And I submit the search
    Then I should be on the search results page
    When I select sort option "Price: Low to High"
    Then the URL should contain "sort=price_asc"

  # AC-0004-01-08: Clear button does not auto-submit
  Scenario: Clearing the search input does not auto-submit
    When I enter a search query "widget"
    And I submit the search
    Then I should be on the search results page
    When I clear the search input
    Then the search input should be empty
    And the search results should still be visible

  # AC-0004-01-10: Spelling suggestion renders and re-runs search
  Scenario: Spelling suggestion appears for near-miss query and triggers new search
    When I enter a search query "widgit"
    And I submit the search
    Then I should be on the search results page
    And I should see a spelling suggestion
    When I click the spelling suggestion link
    Then a new search is executed
