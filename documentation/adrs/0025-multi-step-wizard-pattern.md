# ADR-0025: Multi-Step Wizard Pattern for Profile Completion

## Status

Accepted

## Context

The customer profile completion flow requires collecting multiple categories of information:

1. Personal details (phone, date of birth, gender, language, timezone)
2. Address information (shipping/billing addresses)
3. Communication preferences (email, SMS, push notifications)

Traditional approaches include:

1. **Single long form**: All fields on one page
2. **Accordion/tabs**: Collapsible sections on one page
3. **Multi-step wizard**: Sequential steps with progress indication
4. **Inline progressive disclosure**: Show more fields as user progresses

Key requirements:
- Non-expert users should feel guided, not overwhelmed
- Users should be able to skip optional sections
- Progress should be visually clear
- Users should be able to go back and edit previous sections
- State must persist across steps

## Decision

We will implement a **multi-step wizard pattern** with the following characteristics:

### Frontend Architecture

**State Management with Zustand**:
```typescript
interface ProfileWizardState {
  currentStep: WizardStep;
  personalDetails: PersonalDetailsFormData | null;
  address: AddressFormData | null;
  preferences: PreferencesFormData | null;
  completedSteps: Set<WizardStep>;

  setCurrentStep: (step: WizardStep) => void;
  goToNextStep: () => void;
  goToPreviousStep: () => void;
  // ... other actions
}
```

**Component Structure**:
```
ProfileWizard/
├── ProfileWizard.tsx       # Main container
├── WizardProgress.tsx      # Step indicator
├── PersonalDetailsStep.tsx # Step 1
├── AddressStep.tsx         # Step 2
├── PreferencesStep.tsx     # Step 3
└── ReviewStep.tsx          # Step 4 (confirmation)
```

**Step Navigation Rules**:
- Users can always go to previous steps
- Users can navigate to completed steps via progress indicator
- Users cannot skip to future uncompleted steps
- Each step has "Continue", "Back", and "Skip" options where appropriate

### Form Validation with Zod

Each step has its own Zod schema for validation:
```typescript
export const personalDetailsSchema = z.object({
  phoneCountryCode: z.string().optional(),
  phoneNumber: z.string().optional(),
  dateOfBirth: z.string().optional().refine(...),
  // ...
});
```

### Backend API Design

Single PATCH endpoint for profile updates:
```http
PATCH /api/v1/customers/{customerId}/profile
Content-Type: application/json

{
  "phone": { "countryCode": "+1", "number": "5551234567" },
  "dateOfBirth": "1990-05-15",
  "gender": "FEMALE",
  "preferredLocale": "en-US",
  "timezone": "America/New_York"
}
```

The frontend aggregates all wizard data and submits once on final completion.

## Consequences

### Positive

- **Reduced Cognitive Load**: Users focus on one category at a time
- **Clear Progress**: Visual indicator shows completion status
- **Flexibility**: Users can skip optional sections
- **Better Validation**: Per-step validation catches errors early
- **Mobile Friendly**: Each step fits well on mobile screens
- **Testable**: Each step can be tested independently

### Negative

- **More Clicks**: Users must click through multiple steps
- **State Complexity**: Wizard state must be carefully managed
- **Navigation Edge Cases**: Back/skip/edit flows add complexity
- **Testing Overhead**: More UI components to test

### Mitigations

- Zustand provides simple, predictable state management
- Each step validates independently before proceeding
- Clear progress indicator reduces user confusion
- Comprehensive acceptance tests cover navigation flows

## Related Decisions

- ADR-0010: Kotlin with Spring Boot for Backend Services
- ADR-0015: Cucumber.js + Playwright Testing
- ADR-0020: Sealed Classes for Use Case Results
