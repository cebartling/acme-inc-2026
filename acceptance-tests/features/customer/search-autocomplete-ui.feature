@customer @search-autocomplete
Feature: Search Autocomplete
  As a customer browsing the ACME platform
  I want to see autocomplete suggestions as I type in the search bar
  So that I can discover products and categories faster

  Background:
    Given I am on the home page

  # AC-0004-02-01 + AC-0004-02-02: Autocomplete appears with suggestion types
  @smoke
  Scenario: Autocomplete dropdown appears after typing 2 characters
    When I type "wi" in the header search bar
    Then I should see the autocomplete dropdown
    And the autocomplete dropdown should contain suggestions

  # AC-0004-02-08: Minimum query length
  Scenario: No autocomplete dropdown for a single character
    When I type "w" in the header search bar
    Then I should not see the autocomplete dropdown

  # AC-0004-02-05: Query suggestion executes search
  Scenario: Selecting a suggestion navigates to search results
    When I type "wid" in the header search bar
    And I should see the autocomplete dropdown
    And I click the first autocomplete suggestion
    Then I should be on the search results page

  # AC-0004-02-06: Keyboard navigation — Escape closes dropdown
  Scenario: Pressing Escape closes the autocomplete dropdown
    When I type "wi" in the header search bar
    Then I should see the autocomplete dropdown
    When I press the Escape key
    Then I should not see the autocomplete dropdown

  # AC-0004-02-10: Empty result does not show dropdown
  Scenario: No dropdown when autocomplete returns no suggestions
    When I type "xyzzy-none" in the header search bar
    Then I should not see the autocomplete dropdown
