@customer @profile-completeness-widget
Feature: Profile Completeness Widget (US-0002-12)
  As an authenticated customer
  I want to see my profile completeness on the dashboard
  So that I am motivated to complete my profile for a better experience

  Background:
    Given the Customer Service is available
    And I am logged in as an authenticated customer

  # AC-0002-12-02: UI Visibility
  @smoke
  Scenario: Display profile completeness widget on dashboard
    When I navigate to the dashboard
    Then I should see the profile completeness widget
    And the widget should display a circular progress ring
    And the widget should show the completeness percentage
    And the widget should display "Profile Completeness" as the title

  # AC-0002-12-03: Score Display with Color Coding
  Scenario: Progress ring shows red color for low completeness
    Given my profile completeness is below 50 percent
    When I navigate to the dashboard
    Then the progress ring should be displayed in red

  Scenario: Progress ring shows yellow color for medium completeness
    Given my profile completeness is between 50 and 79 percent
    When I navigate to the dashboard
    Then the progress ring should be displayed in yellow

  Scenario: Progress ring shows green color for high completeness
    Given my profile completeness is 80 percent or higher
    When I navigate to the dashboard
    Then the progress ring should be displayed in green

  # AC-0002-12-04: Incomplete Profile Prompt
  Scenario: Show prompt when profile is below 80 percent
    Given my profile completeness is below 80 percent
    When I navigate to the dashboard
    Then I should see a message to complete my profile
    And I should see the "Complete Your Profile" button

  Scenario: No prompt when profile is 100 percent complete
    Given my profile is 100 percent complete
    When I navigate to the dashboard
    Then I should see "Your profile is complete!" message
    And I should not see the "Complete Your Profile" button

  # AC-0002-12-05: Section Progress Details
  @smoke
  Scenario: Display section breakdown with completion status
    When I navigate to the dashboard
    Then I should see the following sections in the widget:
      | section          |
      | Basic Info       |
      | Contact Info     |
      | Personal Details |
      | Address          |
      | Preferences      |
      | Consent          |

  Scenario: Expand section to see individual items
    When I navigate to the dashboard
    And I click on the "Basic Info" section
    Then the section should expand
    And I should see the following items:
      | item           |
      | First Name     |
      | Last Name      |
      | Email Verified |

  Scenario: Completed sections show checkmark
    Given my basic info is complete
    When I navigate to the dashboard
    Then the "Basic Info" section should show a checkmark
    And the "Basic Info" section should show "100%" score

  Scenario: Incomplete sections show empty circle
    Given my contact info is incomplete
    When I navigate to the dashboard
    Then the "Contact Info" section should show an empty circle
    And the "Contact Info" section should show "0%" score

  # AC-0002-12-06: Next Action Recommendation
  Scenario: Show next recommended action when profile is incomplete
    Given my profile is not 100 percent complete
    And the next recommended action is to add a phone number
    When I navigate to the dashboard
    Then I should see "Recommended Next Step" banner
    And I should see the "Complete Now" button

  Scenario: Click complete now navigates to profile wizard
    Given my profile is not 100 percent complete
    When I navigate to the dashboard
    And I click the Complete Now button
    Then I should be navigated to the profile completion page

  # Widget States
  Scenario: Widget shows loading state while fetching data
    When I navigate to the dashboard
    Then the widget should initially show a loading skeleton

  Scenario: Widget shows error state when API fails
    Given the profile completeness API is unavailable
    When I navigate to the dashboard
    Then the widget should display an error message

  # Navigation
  Scenario: Complete Your Profile button navigates to wizard
    Given my profile is not 100 percent complete
    When I navigate to the dashboard
    And I click the Complete Your Profile button
    Then I should be navigated to the profile completion page
