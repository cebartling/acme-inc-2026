@api @read-model
Feature: Customer read model
  The customer service answers reads from a MongoDB read model that a Kafka projector builds from
  snapshots the service writes to its outbox. Debezium publishes the outbox to Kafka. A write
  returns an X-Read-After header, and the next read sends it back and waits for that write, so a
  client always reads its own changes.

  Background:
    Given the Customer Service is available
    And I have an authenticated customer

  Scenario: A profile change shows on the next read
    When I update my profile with:
      | timezone | America/Chicago |
    Then the API should respond with status 200
    When I request my customer record
    Then the API should respond with status 200
    And the customer record should have "profile.timezone" set to "America/Chicago"

  Scenario: A new address shows on the next read
    When I add a shipping address with:
      | streetLine1 | 742 Evergreen Terrace |
      | city        | Springfield           |
      | state       | IL                    |
      | postalCode  | 62701                 |
      | country     | US                    |
      | label       | Home                  |
      | isDefault   | true                  |
    Then the API should respond with status 201
    When I request all my addresses
    Then the API should respond with status 200
    And the response should contain 1 addresses

  Scenario: A preference change shows on the next read
    When I update my preferences with:
      | path                | value |
      | communication.email | false |
    Then the API should respond with status 200
    When I request my preferences
    Then the API should respond with status 200
    And the preferences response should have "communication.email" set to "false"

  Scenario: A consent change shows on the next read
    When I grant consent for "MARKETING" from "PRIVACY_SETTINGS"
    Then the API should respond with status 200
    When I request my consents
    Then the API should respond with status 200
    And my consents should show "MARKETING" as granted

  Scenario: A read that asks for a write the read model has not applied is told to retry
    When I request my customer record asking to wait for an event that never arrives
    Then the API should respond with status 503
    And the response should have header "Retry-After" set to "1"

  Scenario: A read that sends something other than an event id is rejected
    When I request my customer record sending "X-Read-After" as "not-an-event-id"
    Then the API should respond with status 400

  # Stops the shared Kafka container, so it is left out of the default run (@kafka-outage).
  # Run it on its own: npm run test:kafka-outage
  @kafka-outage
  Scenario: A write made while Kafka is stopped reaches the read model once Kafka is back
    Given Kafka is stopped
    When I update my profile with:
      | timezone | America/Denver |
    Then the API should respond with status 200
    When I request my customer record
    Then the API should respond with status 503
    And the response should have header "Retry-After" set to "1"
    When Kafka is started again
    Then my customer record should eventually have "profile.timezone" set to "America/Denver"
