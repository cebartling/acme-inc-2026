@api @cart @read-model
Feature: Cart read model
  The cart service answers GET /api/v1/carts/current from a MongoDB read model that a Kafka
  projector builds from snapshots the service writes to its outbox. Debezium publishes the outbox
  to Kafka. A change returns an X-Read-After header, and the next read sends it back and waits for
  that change, so a shopper always sees their own change.

  # Gadget Pro (Black) costs $119.99, $109.99 each for 3+, $99.99 each for 10+.

  Scenario: An added item shows on the next read
    When I add 2 "Gadget Pro / Black" to a new cart
    Then the API should respond with status 201
    When I get my current cart
    Then the API should respond with status 200
    And the cart item count should be 2

  Scenario: A changed quantity shows on the next read
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I change the quantity of that line to 3
    And I get my current cart
    Then the cart should have 1 line with quantity 3 at unit price 109.99

  Scenario: A removed line shows on the next read
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I remove that line
    And I get my current cart
    Then the API should respond with status 200
    And the cart should be empty

  Scenario: A cleared cart shows empty on the next read
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I clear my cart
    And I get my current cart
    Then the API should respond with status 200
    And the cart should be empty

  Scenario: A cart locked for checkout shows as locked on the next read
    Given I have added 2 "Gadget Pro / Black" to a new cart
    And I have started checkout on my cart
    When I get my current cart
    Then my cart should be locked for checkout

  Scenario: A guest with no cart still has none
    When I get the current cart without a session cookie
    Then the API should respond with status 204

  Scenario: A read that asks for a change the read model has not applied is told to retry
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I get my current cart asking to wait for an event that never arrives
    Then the API should respond with status 503
    And the response should have header "Retry-After" set to "1"

  Scenario: A read that sends something other than an event id is rejected
    Given I have added 2 "Gadget Pro / Black" to a new cart
    When I get my current cart sending "X-Read-After" as "not-an-event-id"
    Then the API should respond with status 400

  # Stops the shared Kafka container, so it is left out of the default run (@kafka-outage).
  # Run it on its own: npm run test:kafka-outage
  @kafka-outage
  Scenario: A change made while Kafka is stopped reaches the read model once Kafka is back
    Given Kafka is stopped
    When I add 2 "Gadget Pro / Black" to a new cart
    Then the API should respond with status 201
    When I get my current cart
    Then the API should respond with status 503
    And the response should have header "Retry-After" set to "1"
    When Kafka is started again
    Then my cart should eventually have 2 items
