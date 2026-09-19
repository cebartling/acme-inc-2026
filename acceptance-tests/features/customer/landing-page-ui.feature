@customer @landing-page
Feature: Landing Page
  As a customer visiting the ACME platform
  I want the landing page call to action to take me somewhere useful
  So that I can start shopping

  Background:
    Given I am on the home page

  Scenario: Shop Now takes the customer to product search
    When I click the "Shop Now" link
    Then the URL should contain "/search"
    And I should see the text "Enter a search query above to find products."
