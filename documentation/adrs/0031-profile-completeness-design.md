# ADR-0031: Profile Completeness Tracking Design

## Status

Accepted

## Context

As part of user story US-0002-12, we need to implement profile completeness tracking to:
1. Calculate and display a customer's profile completeness score
2. Provide detailed section breakdowns
3. Guide users on what to complete next
4. Publish an event when profiles reach 100% completion

Several design decisions needed to be made regarding:
- How to calculate completeness (weighted vs. flat)
- Where to perform calculations (real-time vs. cached)
- How to handle the OR logic for personal details
- Integration with existing domain events

## Decision

### Weighted Section Calculation

We chose a **weighted section approach** with the following weights:

| Section           | Weight | Rationale                                      |
|-------------------|--------|------------------------------------------------|
| Basic Info        | 25%    | Core identity fields (name, verified email)    |
| Contact Info      | 15%    | Communication capability                       |
| Personal Details  | 15%    | Personalization (DOB or gender via OR logic)   |
| Address           | 20%    | Higher weight for e-commerce delivery needs    |
| Preferences       | 15%    | User control over communication                |
| Consent           | 10%    | Required but minimal friction                  |

Each section scores either 0% or 100% (binary completion), and the overall score is the weighted sum.

### Personal Details OR Logic

For the personal details section, we implemented **OR logic**: either date of birth OR gender completes the section at 100%. This reduces friction while still gathering useful personalization data. The implementation checks both fields but only requires one to be present.

### Real-time Calculation

Profile completeness is calculated **on-demand** rather than cached:

**Advantages:**
- Always accurate (no stale cache issues)
- Simpler implementation without invalidation complexity
- Acceptable performance for single-customer queries

**Trade-offs:**
- Requires fetching related data (addresses, consents, preferences)
- Slightly higher latency than cached value

We store `profileCompleteness` as a denormalized field on the Customer entity for display purposes (e.g., in lists), but the detailed calculation is always performed fresh.

### Integration Points

The `ProfileCompletionService` is called from:
- `UpdateProfileUseCase` - When profile fields change
- `AddAddressUseCase` - When validated addresses are added
- `GrantConsentUseCase` - When required consents are granted
- `ActivateCustomerUseCase` - When email is verified

This ensures the `ProfileCompleted` event is published exactly once when the profile reaches 100%.

### ProfileCompleted Event

The `ProfileCompleted` domain event:
- Is published via the transactional outbox pattern
- Includes `timeToComplete` (duration from registration)
- Uses the standard event structure (eventId, correlationId, etc.)

## Alternatives Considered

### Alternative 1: Per-Field Weighted Calculation

Calculate completeness based on individual fields with different weights.

**Rejected because:**
- More complex to maintain
- Harder to explain to users
- Section-based approach provides better UX guidance

### Alternative 2: Cached Completeness with Event-driven Updates

Cache the completeness score and update it via event listeners.

**Rejected because:**
- Added complexity of cache invalidation
- Risk of stale data
- Current approach has acceptable performance

### Alternative 3: Eventual Consistency for Completeness

Update completeness asynchronously after profile changes.

**Rejected because:**
- Users expect immediate feedback
- Real-time calculation is fast enough
- Simpler implementation

## Consequences

### Positive

- Clear, weighted calculation aligned with business priorities
- Flexible OR logic for personal details reduces user friction
- Real-time accuracy for completeness scores
- Standard event publishing for downstream services

### Negative

- Multiple repository calls per completeness calculation
- Must integrate completion check into multiple use cases

### Neutral

- Frontend must handle the detailed section breakdown response
- Acceptance tests need backend test helpers for complete profile setup

## Implementation Notes

Key files:
- `ProfileCompletenessCalculator.kt` - Core calculation logic
- `ProfileCompletionService.kt` - Orchestrates completion checking
- `ProfileCompleteness.kt` - Domain model
- `ProfileCompletedEvent.kt` - Domain event
- `ProfileCompletenessWidget.tsx` - Frontend component

## References

- [US-0002-12: Profile Completeness Tracking](../user-stories/0002-create-customer-profile/US-0002-12-profile-completeness-tracking.md)
- [ARCHITECTURE.md](../ARCHITECTURE.md) - System architecture documentation
