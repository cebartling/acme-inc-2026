# ADR-0026: Phone Number Validation with libphonenumber

## Status

Accepted

## Context

The platform needs to validate phone numbers for customer profiles. Phone number formats vary significantly by country:

| Country | Format Examples |
|---------|----------------|
| US/Canada | +1 (555) 123-4567, +15551234567 |
| UK | +44 7911 123456, +447911123456 |
| Germany | +49 151 23456789 |
| Japan | +81 90-1234-5678 |

Key challenges:
1. **Format Variations**: Each country has different length and format rules
2. **Input Flexibility**: Users may enter numbers with/without formatting
3. **Validation Accuracy**: Must distinguish valid from invalid numbers
4. **International Support**: Platform operates globally

Alternative approaches considered:

1. **Regex-based validation**: Simple but imprecise, requires per-country patterns
2. **Length-only validation**: Too permissive, allows many invalid numbers
3. **External API validation**: Adds latency and dependency on third-party
4. **libphonenumber library**: Google's comprehensive phone number library

## Decision

We will use **Google's libphonenumber** library for phone number validation on both backend and frontend:

### Backend (Kotlin/JVM)

```kotlin
implementation("com.googlecode.libphonenumber:libphonenumber:8.13.50")
```

Validation component:
```kotlin
@Component
class PhoneNumberValidator {
    private val phoneUtil = PhoneNumberUtil.getInstance()

    fun validate(countryCode: String, number: String): PhoneValidationResult {
        return try {
            val fullNumber = "$countryCode$number"
            val phoneNumber = phoneUtil.parse(fullNumber, null)

            if (phoneUtil.isValidNumber(phoneNumber)) {
                PhoneValidationResult.Valid(
                    formattedNumber = phoneUtil.format(phoneNumber, E164),
                    countryCode = "+${phoneNumber.countryCode}",
                    nationalNumber = phoneNumber.nationalNumber.toString()
                )
            } else {
                PhoneValidationResult.Invalid("Invalid phone number format")
            }
        } catch (e: NumberParseException) {
            PhoneValidationResult.Invalid(mapErrorMessage(e.errorType))
        }
    }
}
```

### Frontend (TypeScript)

```json
"libphonenumber-js": "^1.12.8"
```

Validation in Zod schema:
```typescript
import { isValidPhoneNumber } from 'libphonenumber-js';

const phoneSchema = z.object({
  phoneCountryCode: z.string(),
  phoneNumber: z.string(),
}).refine(
  (data) => {
    const fullNumber = `${data.phoneCountryCode}${data.phoneNumber}`;
    return isValidPhoneNumber(fullNumber);
  },
  { message: 'Invalid phone number format', path: ['phoneNumber'] }
);
```

### Error Messages

Provide user-friendly error messages:

| Error Type | Message |
|------------|---------|
| TOO_SHORT | Phone number is too short |
| TOO_LONG | Phone number is too long |
| INVALID_COUNTRY_CODE | Invalid country code |
| NOT_A_NUMBER | The input does not appear to be a phone number |

### Storage Format

Store phone numbers in E.164 format:
- Country code and national number stored separately
- Enables later formatting for display
- Supports re-validation if rules change

## Consequences

### Positive

- **Comprehensive Coverage**: Supports all countries and number types
- **Accurate Validation**: Uses telecom industry metadata
- **Consistent Experience**: Same validation on frontend and backend
- **Format Normalization**: Can convert any input to standard format
- **Helpful Errors**: Specific error messages guide users
- **Well Maintained**: Google actively maintains the library

### Negative

- **Bundle Size**: libphonenumber-js adds ~90KB (minified) to frontend bundle
- **Metadata Updates**: Telecom rules change, library must be updated periodically
- **Not Real-Time Verification**: Validates format, not that number is reachable

### Mitigations

- Use `libphonenumber-js` "min" variant for smaller bundle if needed
- Include library updates in regular dependency maintenance
- For critical use cases (2FA), consider SMS verification separately

## Related Decisions

- ADR-0020: Sealed Classes for Use Case Results (PhoneValidationResult pattern)
- ADR-0025: Multi-Step Wizard Pattern (phone validation in wizard)
