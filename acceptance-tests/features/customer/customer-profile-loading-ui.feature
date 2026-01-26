@customer @profile @wip
Feature: Customer Profile Loading UI (US-0003-09)
  As a customer
  I want my profile to be loaded automatically after signin
  So that I can see my personalized dashboard

  Background:
    Given an active customer with email "ui-profile-test@example.com" exists
    And I am on the signin page

  Scenario: Profile is loaded automatically after successful signin
    When I sign in with valid credentials
    Then I should be redirected to the dashboard
    And I should see my display name on the dashboard
    And I should see my customer number on the dashboard
    And the profile completeness widget should be visible

  Scenario: Display loading state while fetching profile
    When I start signing in with valid credentials
    Then I should see a loading indicator
    When the signin completes
    Then the loading indicator should disappear
    And I should see my profile information

  Scenario: Handle profile loading error gracefully
    Given the customer profile API will return an error
    When I sign in with valid credentials
    Then I should be redirected to the dashboard
    And I should see an error message about profile loading
    And I should see a retry button
    When I click the retry button
    Then the profile should be loaded successfully

  Scenario: Profile persists across page refreshes
    Given I have signed in successfully
    And my profile is loaded
    When I refresh the page
    Then I should still see my display name
    And I should still see my customer number
    And the profile should not be fetched again from the API

  Scenario: Profile is cleared on logout
    Given I have signed in successfully
    And my profile is loaded
    When I sign out
    Then the profile should be cleared from storage
    And I should not see my display name

  Scenario: Dashboard shows profile completeness score
    Given I have signed in successfully
    And my profile is loaded
    Then the profile completeness widget should show my score
    And the widget should show completion percentage

  Scenario: Dashboard shows customer number
    Given I have signed in successfully
    And my profile is loaded
    Then I should see my customer number in format "ACME-YYYYMM-NNNNNN"

  Scenario: Dashboard shows last activity timestamp
    Given I have signed in successfully
    And my profile is loaded
    Then I should see when I last accessed my account
