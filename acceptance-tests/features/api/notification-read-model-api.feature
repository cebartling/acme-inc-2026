@api @read-model @notification-read-model
Feature: Notification deliveries read model
  Notification writes NotificationSent, and a snapshot of the delivery, to its outbox in the same
  transaction as the delivery record. Debezium publishes the outbox to Kafka, and a projector builds
  the deliveries read model in MongoDB from the snapshots. The three lookup endpoints answer from it,
  so a delivery shows up a moment after it is sent.

  Background:
    Given the Notification Service is available
    And the Identity Service is available

  Scenario: A verification email shows up in all three lookups
    When a user registers for the notification read model
    Then the notification lookups should eventually show a SENT verification email
    And looking the notification up by recipient id should find the same delivery
    And looking the notification up by its id should find the same delivery

  Scenario: NotificationSent reaches the notification events topic, keyed by the notification id
    When a user registers for the notification read model
    Then the notification lookups should eventually show a SENT verification email
    And a NotificationSent event for that notification should reach the "notification.events" topic, keyed by the notification id

  # Stops the shared Kafka container, so it is left out of the default run (@kafka-outage).
  # Run it on its own: npm run test:kafka-outage
  # The registration's own event waits in identity's outbox until Kafka is back, so this follows the
  # whole chain: identity's outbox, the consumer, notification's outbox, Debezium and the projector.
  @kafka-outage
  Scenario: A registration made while Kafka is stopped is notified, published and readable once Kafka is back
    Given Kafka is stopped
    When a user registers for the notification read model
    Then the API should respond with status 201
    When Kafka is started again
    Then the notification lookups should eventually show a SENT verification email
    And a NotificationSent event for that notification should reach the "notification.events" topic, keyed by the notification id
