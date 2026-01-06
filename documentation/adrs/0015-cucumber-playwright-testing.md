# ADR-0015: Cucumber.js and Playwright for Acceptance Testing

## Status

Accepted

## Context

A microservices-based e-commerce platform requires comprehensive testing at multiple levels. While unit and integration tests validate individual components, we need acceptance tests that:

- Validate end-to-end user journeys across the full stack
- Express requirements in business-readable language for stakeholder collaboration
- Support both API-level and UI-level testing scenarios
- Run reliably in CI/CD pipelines
- Provide clear reporting for test results and failures

Candidates considered:
- **Cucumber.js + Playwright**: BDD framework with powerful browser automation
- **Cypress**: Developer-friendly but limited cross-browser and API testing
- **Selenium**: Mature but slower and more brittle
- **Jest + Testing Library**: Good for component testing, weaker for E2E
- **Playwright alone**: Powerful but lacks BDD structure

## Decision

We will use **Cucumber.js** with **Playwright** for acceptance testing.

Architecture:
```
┌─────────────────────────────────────────────────────────────┐
│                    Acceptance Test Suite                     │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    Cucumber.js                           ││
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  ││
│  │  │   Feature   │  │    Step     │  │     World       │  ││
│  │  │   Files     │  │ Definitions │  │    Context      │  ││
│  │  │  (.feature) │  │   (.ts)     │  │   (Playwright)  │  ││
│  │  └─────────────┘  └─────────────┘  └─────────────────┘  ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
              │                           │
              ▼                           ▼
      ┌───────────────┐           ┌───────────────┐
      │   Frontend    │           │   Backend     │
      │   (Browser)   │           │   (API)       │
      └───────────────┘           └───────────────┘
```

Feature file example:
```gherkin
Feature: Customer Profile Creation

  @smoke @customer
  Scenario: New customer creates profile after registration
    Given I am a newly registered user
    When I navigate to the profile creation page
    And I fill in my profile details:
      | field     | value           |
      | firstName | John            |
      | lastName  | Doe             |
      | phone     | +1-555-123-4567 |
    And I submit the profile form
    Then I should see a success message
    And my profile should be saved in the system
```

Step definition structure:
```typescript
Given('I am a newly registered user', async function(this: CustomWorld) {
  await this.apiClient.registerUser(this.testUser);
  await this.page.goto('/login');
  await this.loginPage.login(this.testUser);
});
```

Test organization:
- **Tags**: `@smoke`, `@regression`, `@api`, `@customer`, `@admin`
- **Parallel Execution**: Tests run concurrently across scenarios
- **Reports**: HTML, JSON, and Allure reports

## Consequences

### Positive

- **Living Documentation**: Feature files serve as executable specifications
- **Stakeholder Collaboration**: Business-readable scenarios enable review by non-developers
- **Cross-Browser Testing**: Playwright supports Chromium, Firefox, and WebKit
- **API + UI Testing**: Single framework for both browser and API tests
- **Parallel Execution**: Fast test runs with concurrent scenario execution
- **Visual Testing**: Screenshot comparison for UI regression detection

### Negative

- **Maintenance Overhead**: Feature files and step definitions require synchronization
- **Step Definition Sprawl**: Can become unwieldy without proper organization
- **Flaky Tests**: UI tests can be sensitive to timing and environment
- **Learning Curve**: BDD patterns require training for effective use

### Mitigations

- Organize step definitions by domain (auth, profile, orders)
- Use Playwright's auto-waiting to reduce flakiness
- Implement retry logic for transient failures
- Tag tests appropriately for selective execution
- Maintain page object patterns for UI interactions
