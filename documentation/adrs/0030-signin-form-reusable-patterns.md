# ADR-0030: Signin Form Reusable Component Patterns

## Status

Accepted

## Context

The customer signin form (US-0003-01) requires implementing authentication UI components that follow consistent patterns with the existing registration form. Key considerations include:

1. **Form Validation**: Same validation UX as registration (onBlur validation, real-time feedback)
2. **Component Reuse**: Existing FormField and PasswordInput components from registration
3. **State Management**: Integration with existing Zustand auth store
4. **Routing**: TanStack Router file-based routing conventions
5. **Accessibility**: WCAG 2.1 AA compliance with proper ARIA attributes

We evaluated two approaches:
- **Create new signin-specific components**: Full control, potential duplication
- **Reuse registration components**: DRY principle, consistent UX, shared maintenance

## Decision

We will **reuse existing form components** from the registration module and follow established patterns:

### Component Architecture

```
src/
├── components/
│   ├── registration/
│   │   ├── FormField.tsx       # Reused for signin
│   │   └── PasswordInput.tsx   # Reused for signin
│   └── signin/
│       ├── SigninForm.tsx      # New signin-specific form
│       └── index.ts            # Barrel export
├── schemas/
│   └── signin.schema.ts        # Simplified validation (no strength requirements)
└── routes/
    └── signin.tsx              # File-based route
```

### Form Pattern with React Hook Form + Zod

```typescript
const {
  register,
  handleSubmit,
  formState: { errors, touchedFields, isValid, dirtyFields },
  setValue,
  trigger,
} = useForm<SigninFormData>({
  resolver: zodResolver(signinSchema),
  mode: "onBlur",  // Consistent validation trigger
  defaultValues: {
    email: "",
    password: "",
    rememberMe: false,
  },
});
```

### Simplified Validation Schema

Signin uses minimal validation compared to registration:

```typescript
export const signinSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Please enter a valid email address"),
  password: z.string().min(1, "Password is required"),  // No strength requirements
  rememberMe: z.boolean().default(false),
});
```

### Route Search Parameters

TanStack Router search schema for redirect handling:

```typescript
const signinSearchSchema = z.object({
  redirect: z.string().optional(),  // Post-signin redirect URL
  logout: z.string().optional(),    // Show logout success message
});
```

### Auth Store Integration

```typescript
const setUser = useAuthStore((state) => state.setUser);

// On successful signin
setUser({
  userId: response.userId,
  customerId: response.customerId,
  email: data.email,
  firstName: response.firstName,
  lastName: response.lastName,
});
```

## Consequences

### Positive

- **Consistency**: Users experience identical form behavior across registration and signin
- **Reduced Duplication**: FormField and PasswordInput components shared
- **Unified Testing**: Component tests apply to both flows
- **Faster Development**: Established patterns accelerate implementation
- **Accessibility**: Proven accessible patterns from registration

### Negative

- **Coupling**: Changes to FormField/PasswordInput affect both forms
- **Testing Constraints**: Current React 19 + TanStack Start environment limits component test coverage for hooks
- **API Placeholder**: Form currently uses simulated API calls pending backend integration

### Mitigations

- Schema tests provide comprehensive validation coverage (13 tests, 100% pass)
- Acceptance tests with Cucumber/Playwright will cover full user flows
- API integration documented with TODO comments and follows established contract
- Static property tests document expected full test coverage when environment supports hooks

## Testing Strategy

Due to React hooks compatibility issues in TanStack Start + React 19 + Vitest:

1. **Schema Tests**: Full coverage of signin.schema.ts (13 tests)
2. **Static Component Tests**: Property verification for SigninForm
3. **Acceptance Tests**: Cucumber/Playwright for end-to-end flows
4. **Documented Skip List**: Full component test suite documented for future enablement

## Related Decisions

- ADR-0025: Multi-Step Wizard Pattern for Profile Completion
- ADR-0015: Cucumber.js + Playwright Testing
