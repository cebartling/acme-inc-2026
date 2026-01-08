# ADR-0023: Kafka Consumer Group Patterns for Multi-Event Topics

## Status

Accepted

## Context

The Customer Service consumes events from the Identity Service via Kafka. The `identity.user.events` topic contains multiple event types:
- `UserRegistered` - Triggers customer profile creation
- `UserActivated` - Triggers customer profile activation
- `EmailVerified` - Email verification confirmation

When implementing consumers for these events, we needed to decide on the consumer group architecture. Two approaches were considered:

### Option A: Separate Consumer Groups (One per Event Type)
Each event type has its own consumer with a dedicated consumer group. Every consumer receives all messages and filters for its event type.

```
Topic: identity.user.events
├── UserRegisteredConsumer (group: customer-service-registration)
│   └── Receives ALL messages, processes only UserRegistered
├── UserActivatedConsumer (group: customer-service-activation)
│   └── Receives ALL messages, processes only UserActivated
```

### Option B: Single Consumer Group with Multiple Filtered Listeners
Multiple listeners share the same consumer group, each filtering for different event types.

```
Topic: identity.user.events
└── Consumer Group: customer-service
    ├── UserRegisteredConsumer (filters for UserRegistered)
    └── UserActivatedConsumer (filters for UserActivated)
```

### Option C: Unified Consumer with Event Routing
A single consumer handles all event types and routes to appropriate handlers.

```
Topic: identity.user.events
└── Consumer Group: customer-service
    └── IdentityEventConsumer
        ├── Routes UserRegistered → UserRegisteredHandler
        └── Routes UserActivated → UserActivatedHandler
```

## Decision

We chose **Option C: Unified Consumer with Event Routing**.

## Rationale

### Why Option B Fails

Option B appears efficient but has a critical flaw: **Kafka distributes messages within a consumer group, not to all consumers**.

When multiple listeners in the same consumer group subscribe to the same topic:
1. Kafka assigns partitions to consumers (not listeners)
2. Messages are delivered to only ONE consumer instance in the group
3. If a `UserRegistered` event is delivered to `UserActivatedConsumer`:
   - The consumer filters it out (wrong event type)
   - The consumer acknowledges the message
   - The message is never processed by `UserRegisteredConsumer`
   - **The event is effectively lost**

With random distribution, approximately 50% of events go to the wrong consumer and are discarded.

### Why Option A is Suboptimal

Option A works correctly but has drawbacks:
- **Duplicate reads**: Every message is read N times (once per consumer group)
- **Increased network traffic**: Same data transferred multiple times
- **Higher resource usage**: More consumer connections and memory
- **Offset management complexity**: Multiple consumer groups to monitor

### Why Option C is Preferred

Option C provides the best balance:
- **All events processed**: Single consumer receives all messages
- **Efficient**: Each message read exactly once
- **Simple offset management**: One consumer group to monitor
- **Clear routing logic**: Event type determines handler
- **Extensible**: Easy to add new event types

## Implementation

### Unified Consumer Structure

```kotlin
@Component
class IdentityEventConsumer(
    private val userRegisteredHandler: UserRegisteredHandler,
    private val userActivatedHandler: UserActivatedHandler,
    private val objectMapper: ObjectMapper
) {
    @KafkaListener(
        topics = ["\${customer.events.input-topic}"],
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
        val eventNode = objectMapper.readTree(record.value())
        val eventType = eventNode.get("eventType")?.asText()

        when (eventType) {
            "UserRegistered" -> {
                val event = objectMapper.treeToValue(eventNode, UserRegisteredEvent::class.java)
                userRegisteredHandler.handle(event)
            }
            "UserActivated" -> {
                val event = objectMapper.treeToValue(eventNode, UserActivatedEvent::class.java)
                userActivatedHandler.handle(event)
            }
            else -> {
                logger.debug("Ignoring unknown event type: {}", eventType)
            }
        }

        ack.acknowledge()
    }
}
```

### Key Design Principles

1. **Parse event type first**: Check `eventType` before full deserialization to avoid parsing errors on unknown event structures

2. **Acknowledge after routing**: Only acknowledge after the event has been routed (success or intentionally skipped)

3. **Handle unknown events gracefully**: Log and acknowledge unknown event types rather than failing

4. **Delegate to handlers**: Keep the consumer thin; business logic lives in handlers

## Consequences

### Positive

- All events from the topic are guaranteed to be processed
- Single consumer group simplifies monitoring and offset management
- Lower resource usage compared to multiple consumer groups
- Clear, maintainable code structure
- Easy to add support for new event types

### Negative

- Single point of failure for all event types (mitigated by consumer concurrency)
- Handler failures affect processing of all event types (mitigated by error handling)
- Slightly more complex consumer code than single-purpose consumers

### Neutral

- Requires consistent event envelope format with `eventType` field
- All handlers must be available at consumer startup

## Guidelines for Future Development

### When to Use a Unified Consumer

Use a unified consumer when:
- Multiple event types come from the same topic
- Events share a common envelope format
- Processing logic is independent between event types
- You want simple offset management

### When to Use Separate Consumer Groups

Use separate consumer groups when:
- Event types require different processing guarantees (e.g., different retry policies)
- Event types have vastly different processing times
- You need independent scaling per event type
- Events come from different topics

### Anti-Pattern to Avoid

**Never use multiple filtered listeners in the same consumer group for the same topic.**

This pattern silently loses events and is extremely difficult to debug because:
- No errors are thrown
- Messages appear to be consumed successfully
- Only observable through missing side effects (e.g., records not created)

## References

- [Kafka Consumer Groups Documentation](https://kafka.apache.org/documentation/#consumerconfigs_group.id)
- [Spring Kafka Listener Configuration](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html)
- ADR-0022: Transactional Outbox Pattern for Event Publishing
- ADR-0018: Idempotent Event Processing
