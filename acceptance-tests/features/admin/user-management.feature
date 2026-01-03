@admin @users
Feature: User Management
  As an admin
  I want to manage user accounts
  So that I can control access to the platform

  Background:
    Given I am logged in as an admin
    And I am on the admin users page

  @smoke
  Scenario: View users list
    Then I should see the heading "Users"
    And I should see users

  @smoke
  Scenario: Create a new customer user
    When I click add user
    Then the user modal should be visible
    When I fill in the user details
    And I save the user
    Then I should see the user in the list
    And I should see a success message "User created successfully"

  Scenario: Create user with custom details
    When I click add user
    And I fill in user details with:
      | Email      | newuser@example.com  |
      | First Name | New                  |
      | Last Name  | User                 |
      | Password   | SecurePass123!       |
      | Role       | customer             |
    And I save the user
    Then I should see user "newuser@example.com" in the list

  Scenario: Create admin user
    When I click add user
    And I fill in user details with:
      | Email      | newadmin@example.com |
      | First Name | Admin                |
      | Last Name  | User                 |
      | Password   | AdminPass123!        |
      | Role       | admin                |
    And I save the user
    Then I should see user "newadmin@example.com" in the list
    And the user "newadmin@example.com" should have role "admin"

  Scenario: Cancel user creation
    When I click add user
    And I fill in the user details
    And I cancel the user form
    Then the user modal should be hidden

  @smoke
  Scenario: Edit existing user
    Given a user "existing@example.com" exists
    When I edit user "existing@example.com"
    Then the user modal should be visible
    When I fill in user details with:
      | Email      | existing@example.com |
      | First Name | Updated              |
      | Last Name  | Name                 |
      | Role       | customer             |
    And I save the user
    Then I should see user "existing@example.com" in the list

  @smoke
  Scenario: Delete a user
    Given a user "delete@example.com" exists
    When I delete user "delete@example.com"
    Then I should not see user "delete@example.com" in the list
    And I should see a success message "User deleted successfully"

  Scenario: Deactivate a user
    Given a user "active@example.com" exists
    When I toggle active status for user "active@example.com"
    Then I should see a success message "User deactivated"

  Scenario: Search for users
    Given a user "searchme@example.com" exists
    When I search for user "searchme"
    Then I should see user "searchme@example.com" in the list

  Scenario: Filter users by role
    When I filter users by role "admin"
    Then I should see users

  Scenario: Filter users by status
    When I filter users by status "active"
    Then I should see users
