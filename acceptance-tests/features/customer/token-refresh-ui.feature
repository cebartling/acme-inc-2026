@customer @signin @token @refresh
Feature: Token Refresh — Customer UI (US-0003-12)
  As a signed-in customer
  I want my browser session to be refreshed transparently when my access
  token expires
  So that I can keep using the app without being interrupted

  # ---------------------------------------------------------------------------
  # Why these scenarios are @wip in this PR
  #
  # The token-refresh contract is covered at two layers already:
  #   - API: features/api/token-refresh-api.feature exercises every
  #     code path of POST /api/v1/auth/refresh (happy path, reuse
  #     detection, missing cookie, post-logout) end-to-end against the
  #     real identity service.
  #   - Frontend unit: src/services/api.test.ts (Vitest, 26 cases)
  #     covers the interceptor in isolation — single-flight queueing,
  #     concurrent 401 sharing, retry, failure-path cleanup + redirect.
  #
  # The remaining gap is the full browser → frontend interceptor →
  # identity → retry loop. Driving it requires:
  #   1. A customer-service (or any authenticated downstream endpoint)
  #      that returns 401 with `error: "TOKEN_EXPIRED"` specifically
  #      when an access token JWT has expired (not just generic 401).
  #   2. A way to force the access-token cookie into an expired state
  #      from the test, since waiting 15 minutes is infeasible.
  #      Likely a new test-only endpoint on identity that issues an
  #      access-token JWT with `exp` already in the past, then
  #      Playwright sets it via context.addCookies().
  #
  # Both are larger than the scope of this PR. The scenarios below are
  # left @wip with concrete step phrasing so a follow-up commit only
  # needs to add the two pieces of plumbing.
  # ---------------------------------------------------------------------------

  Background:
    Given the customer application is running

  @wip
  Scenario: Expired access token is refreshed transparently mid-navigation
    Given a signed-in customer is on the dashboard
    And the customer's access token has expired
    When the customer triggers any authenticated request
    Then the customer remains on the dashboard
    And exactly one POST to /api/v1/auth/refresh was issued
    And the original request completes successfully

  @wip
  Scenario: Refresh failure redirects to signin with the logout banner
    Given a signed-in customer is on the dashboard
    And the customer's session has been invalidated server-side
    When the customer triggers any authenticated request
    Then the customer is redirected to "/signin?logout=true"
    And the customer sees the "You have been signed out." message
    And the auth store is cleared
