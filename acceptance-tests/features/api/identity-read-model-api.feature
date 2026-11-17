@api @read-model @identity-read-model
Feature: Identity users read model
  Identity writes every event, and a snapshot of the user when a user's state changes, to its outbox
  in the same transaction as the change. Debezium publishes the outbox to Kafka, and a projector
  builds the users read model in MongoDB from the snapshots. Nothing reads the read model yet, so
  these scenarios read it through the test API.

  Background:
    Given the Identity Service is available

  Scenario: A registered user appears in the read model as pending verification
    When I register a user for the read model
    Then the user read model should eventually show "status" as "PENDING_VERIFICATION"
    And the user read model should eventually show "emailVerified" as "false"

  Scenario: Verifying the email activates the user in the read model
    Given I have registered a user for the read model
    When the read model user verifies their email
    Then the user read model should eventually show "status" as "ACTIVE"
    And the user read model should eventually show "emailVerified" as "true"

  Scenario: The signin that locks the account locks the user in the read model
    Given I have registered and verified a user for the read model
    When the read model user fails to sign in 5 times
    Then the user read model should eventually show "status" as "LOCKED"

  Scenario: Signing in after the lock has expired unlocks the user in the read model
    Given I have registered and verified a user for the read model
    And the read model user's lock expired a minute ago
    And the user read model should eventually show "status" as "LOCKED"
    When the read model user signs in with the right password
    Then the user read model should eventually show "status" as "ACTIVE"
    And the user read model should eventually show "lockedUntil" as "none"

  # Stops the shared Kafka container, so it is left out of the default run (@kafka-outage).
  # Run it on its own: npm run test:kafka-outage
  @kafka-outage
  Scenario: A registration made while Kafka is stopped reaches the read model once Kafka is back
    Given Kafka is stopped
    When I register a user for the read model
    Then the API should respond with status 201
    When Kafka is started again
    Then the user read model should eventually show "status" as "PENDING_VERIFICATION"
