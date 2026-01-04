@customer @authentication @wip
Feature: Customer Authentication
  As a customer
  I want to be able to log in and out of my account
  So that I can access my personalized shopping experience

  Background:
    Given I am on the login page

  @smoke
  Scenario: Successful login with valid credentials
    Given I am a registered customer
    When I login with valid credentials
    Then I should be logged in
    And I should be redirected to the home page
    And I should see my user menu

  Scenario: Failed login with invalid email
    When I login with email "invalid@example.com" and password "WrongPassword123!"
    Then I should see a login error
    And I should see the error "Invalid email or password"

  Scenario: Failed login with invalid password
    Given I am a registered customer
    When I enter my email "existing.customer@example.com"
    And I enter my password "WrongPassword!"
    And I submit the login form
    Then I should see a login error
    And I should see the error "Invalid email or password"

  Scenario: Login with remember me option
    Given I am a registered customer
    When I enter my email "existing.customer@example.com"
    And I enter my password "ExistingPassword123!"
    And I check the remember me checkbox
    And I submit the login form
    Then I should be logged in

  @smoke
  Scenario: Logout from customer account
    Given I am logged in as a customer
    When I click the logout button
    Then I should be logged out
    And I should be on the "login" page

  Scenario: Navigate to registration from login page
    When I click the "Create an account" link
    Then I should be on the "register" page

  Scenario: Navigate to forgot password from login page
    When I click the "Forgot password?" link
    Then the URL should contain "forgot-password"
