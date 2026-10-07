@customer @signin @email-verification
Feature: Request a new verification link (US-0002-05, PIN-346)
  As a customer whose verification link didn't work
  I want to request a new one with just my email
  So that I can verify my account without needing to sign in

  Background:
    Given the customer storefront is available

  # AC-0002-05-02 and AC-0002-05-04: identity redirects here with the reason
  Scenario Outline: Explain why the verification link didn't work
    When I open the resend verification page with "<query>"
    Then I should see the resend link message "<message>"
    And I should see the resend verification form

    Examples:
      | query         | message                                                  |
      | error=expired | Your verification link has expired. Request a new one. |
      | error=invalid | Invalid verification link. Request a new one.          |

  Scenario: Request a new link with only an email
    Given a customer has registered but not verified their email
    When I open the resend verification page with "error=expired"
    And I request a new verification link for that customer
    Then I should see the resend confirmation "Check your inbox."
    And the customer should have been sent 2 verification emails

  Scenario: An unknown email gets the same answer
    When I open the resend verification page with "error=invalid"
    And I request a new verification link for "nobody-pin-346@example.com"
    Then I should see the resend confirmation "Check your inbox."

  # AC-0002-05-07: rate limiting is off locally, so the 429 is mocked
  Scenario: Show the rate-limit message
    Given the resend verification API is rate limited
    When I open the resend verification page with "error=expired"
    And I request a new verification link for "limited@example.com"
    Then I should see the resend error "Too many requests. Please try again in 42 minutes."
