@customer @search-resilience
Feature: Search Service Resilience
  As a customer attempting to search for products
  I want to be able to browse products even when the Search Service is temporarily unavailable
  So that I can still discover and purchase products without being blocked by a service outage

  Background:
    Given I am on the home page

  # AC-0004-09-01: Circuit breaker opens after 5 consecutive failures, and
  # subsequent searches bypass the service instead of waiting on a timeout.
  @smoke
  Scenario: Circuit breaker opens after five consecutive search failures
    Given the search service is unavailable
    When I run 5 failing searches
    Then the search service should have received 5 requests
    When I search for "bypassed-query"
    Then the search service should still have received 5 requests
    And I should see the search unavailable banner

  # AC-0004-09-03: The banner explains the outage and points at category browsing.
  Scenario: Customer sees the search unavailable banner
    Given the search service is unavailable
    When I search for "widget"
    Then I should see the search unavailable banner
    And the banner should say "Search is temporarily unavailable. Browse by category instead."
    And I should see the search retry button

  # AC-0004-09-02: Category browsing replaces results while search is down.
  Scenario: Customer is offered category browsing during an outage
    Given the search service is unavailable
    When I search for "widget"
    Then I should see the category browsing fallback
    And I should see at least one fallback category
    And I should not see the search results grid

  # AC-0004-09-07: Clicking a category loads its products from the catalog,
  # and product details remain reachable.
  Scenario: Customer browses a category and opens a product during an outage
    Given the search service is unavailable
    When I search for "widget"
    And I select the fallback category "Tools"
    Then I should see fallback products
    When I open the first fallback product
    Then I should be on a product detail page

  # AC-0004-09-05: A response slower than the 2s timeout counts as a failure
  # and the fallback serves that request.
  Scenario: A slow search is abandoned and falls back to category browsing
    Given the search service is responding slowly
    When I search for "widget"
    Then I should see the search unavailable banner within 6 seconds
    And I should see the category browsing fallback

  # AC-0004-09-06: No generic error page, and the customer can keep going.
  # Note: the outage banner is itself role="alert", so this cannot reuse the generic
  # "I should not see an error message" step from product search.
  Scenario: Customer never sees an error page during a search outage
    Given the search service is unavailable
    When I search for "widget"
    Then I should not see an error page
    And the search input should still be usable
    And I should see the category browsing fallback

  # AC-0004-09-04: The circuit stays open for the reset window, so a retry
  # inside it must not reach the service.
  Scenario: Retrying inside the reset window does not call the search service
    Given the search service is unavailable
    When I run 5 failing searches
    And the search service recovers
    And I click the search retry button
    Then the search service should still have received 5 requests
    And I should see the search unavailable banner

  # AC-0004-09-04: After the reset window the next search probes the service,
  # and a successful probe closes the circuit and restores normal search.
  @slow
  Scenario: Search recovers automatically once the service is healthy again
    Given the search service is unavailable
    When I run 4 failing searches
    And I search for "widget"
    Then I should see the search unavailable banner
    When the search service recovers
    And I wait for the circuit breaker reset window
    And I click the search retry button
    Then I should not see the search unavailable banner
    And I should see search results
