import { z } from 'zod';
import { isValidPhoneNumber, parsePhoneNumber } from 'libphonenumber-js';

// Step 1: Personal Details Schema
export const personalDetailsSchema = z.object({
  phoneCountryCode: z.string().optional(),
  phoneNumber: z.string().optional(),
  dateOfBirth: z.string().optional().refine(
    (val) => {
      if (!val) return true; // Optional
      const date = new Date(val);
      const age = Math.floor((Date.now() - date.getTime()) / (365.25 * 24 * 60 * 60 * 1000));
      return age >= 13;
    },
    { message: 'You must be at least 13 years old' }
  ),
  gender: z.enum(['MALE', 'FEMALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY']).optional(),
  preferredLocale: z.string().optional(),
  timezone: z.string().optional(),
}).refine(
  (data) => {
    // If phone number is provided, validate it
    if (data.phoneNumber && data.phoneCountryCode) {
      // Strip non-digit characters from phone number before validation
      const cleanNumber = data.phoneNumber.replace(/\D/g, '');
      if (!cleanNumber) return true; // Empty after stripping is valid (optional field)
      const fullNumber = `${data.phoneCountryCode}${cleanNumber}`;
      return isValidPhoneNumber(fullNumber);
    }
    return true;
  },
  {
    message: 'Invalid phone number format',
    path: ['phoneNumber'],
  }
);

// Step 2: Address Schema
export const addressSchema = z.object({
  addressType: z.enum(['SHIPPING', 'BILLING', 'BOTH']),
  label: z.string().max(50).optional(),
  streetLine1: z.string().min(1, 'Street address is required').max(100),
  streetLine2: z.string().max(100).optional(),
  city: z.string().min(1, 'City is required').max(50),
  stateProvince: z.string().min(1, 'State/Province is required'),
  postalCode: z.string().min(1, 'Postal code is required'),
  country: z.string().min(1, 'Country is required'),
  isDefault: z.boolean().default(true),
});

// Step 3: Preferences Schema
export const preferencesSchema = z.object({
  emailNotifications: z.boolean().default(true),
  smsNotifications: z.boolean().default(false),
  pushNotifications: z.boolean().default(false),
  marketingCommunications: z.boolean().default(false),
  notificationFrequency: z.enum(['IMMEDIATE', 'DAILY', 'WEEKLY']).default('IMMEDIATE'),
});

// Combined Profile Data for API submission
export const profileUpdateSchema = z.object({
  phone: z.object({
    countryCode: z.string(),
    number: z.string(),
  }).optional(),
  dateOfBirth: z.string().optional(),
  gender: z.string().optional(),
  preferredLocale: z.string().optional(),
  timezone: z.string().optional(),
});

export type PersonalDetailsFormData = z.infer<typeof personalDetailsSchema>;
export type AddressFormData = z.infer<typeof addressSchema>;
export type PreferencesFormData = z.infer<typeof preferencesSchema>;
export type ProfileUpdateData = z.infer<typeof profileUpdateSchema>;

// Wizard data combines all steps
export interface WizardData {
  personalDetails: PersonalDetailsFormData;
  address: AddressFormData | null;
  preferences: PreferencesFormData;
}

// Common country codes for phone input
export const COUNTRY_CODES = [
  { code: '+1', label: 'US/CA (+1)' },
  { code: '+44', label: 'UK (+44)' },
  { code: '+49', label: 'DE (+49)' },
  { code: '+33', label: 'FR (+33)' },
  { code: '+34', label: 'ES (+34)' },
  { code: '+39', label: 'IT (+39)' },
  { code: '+81', label: 'JP (+81)' },
  { code: '+86', label: 'CN (+86)' },
  { code: '+91', label: 'IN (+91)' },
  { code: '+61', label: 'AU (+61)' },
];

// Gender options
export const GENDER_OPTIONS = [
  { value: 'MALE', label: 'Male' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'NON_BINARY', label: 'Non-binary' },
  { value: 'PREFER_NOT_TO_SAY', label: 'Prefer not to say' },
];

// Common locales
export const LOCALE_OPTIONS = [
  { value: 'en-US', label: 'English (US)' },
  { value: 'en-GB', label: 'English (UK)' },
  { value: 'es', label: 'Spanish' },
  { value: 'fr', label: 'French' },
  { value: 'de', label: 'German' },
  { value: 'it', label: 'Italian' },
  { value: 'pt', label: 'Portuguese' },
  { value: 'ja', label: 'Japanese' },
  { value: 'zh', label: 'Chinese' },
];

// Common timezones
export const TIMEZONE_OPTIONS = [
  { value: 'America/New_York', label: 'Eastern Time (ET)' },
  { value: 'America/Chicago', label: 'Central Time (CT)' },
  { value: 'America/Denver', label: 'Mountain Time (MT)' },
  { value: 'America/Los_Angeles', label: 'Pacific Time (PT)' },
  { value: 'America/Phoenix', label: 'Arizona (AZ)' },
  { value: 'America/Anchorage', label: 'Alaska (AK)' },
  { value: 'Pacific/Honolulu', label: 'Hawaii (HI)' },
  { value: 'Europe/London', label: 'London (GMT)' },
  { value: 'Europe/Paris', label: 'Paris (CET)' },
  { value: 'Europe/Berlin', label: 'Berlin (CET)' },
  { value: 'Asia/Tokyo', label: 'Tokyo (JST)' },
  { value: 'Asia/Shanghai', label: 'Shanghai (CST)' },
  { value: 'Australia/Sydney', label: 'Sydney (AEST)' },
  { value: 'UTC', label: 'UTC' },
];

// Address type options
export const ADDRESS_TYPE_OPTIONS = [
  { value: 'SHIPPING', label: 'Shipping' },
  { value: 'BILLING', label: 'Billing' },
  { value: 'BOTH', label: 'Both Shipping & Billing' },
];

// Notification frequency options
export const NOTIFICATION_FREQUENCY_OPTIONS = [
  { value: 'IMMEDIATE', label: 'Immediate' },
  { value: 'DAILY', label: 'Daily digest' },
  { value: 'WEEKLY', label: 'Weekly digest' },
];

// Countries for address
export const COUNTRY_OPTIONS = [
  { value: 'US', label: 'United States' },
  { value: 'CA', label: 'Canada' },
  { value: 'GB', label: 'United Kingdom' },
  { value: 'DE', label: 'Germany' },
  { value: 'FR', label: 'France' },
  { value: 'ES', label: 'Spain' },
  { value: 'IT', label: 'Italy' },
  { value: 'AU', label: 'Australia' },
  { value: 'JP', label: 'Japan' },
  { value: 'MX', label: 'Mexico' },
];

// US States
export const US_STATES = [
  { value: 'AL', label: 'Alabama' },
  { value: 'AK', label: 'Alaska' },
  { value: 'AZ', label: 'Arizona' },
  { value: 'AR', label: 'Arkansas' },
  { value: 'CA', label: 'California' },
  { value: 'CO', label: 'Colorado' },
  { value: 'CT', label: 'Connecticut' },
  { value: 'DE', label: 'Delaware' },
  { value: 'FL', label: 'Florida' },
  { value: 'GA', label: 'Georgia' },
  { value: 'HI', label: 'Hawaii' },
  { value: 'ID', label: 'Idaho' },
  { value: 'IL', label: 'Illinois' },
  { value: 'IN', label: 'Indiana' },
  { value: 'IA', label: 'Iowa' },
  { value: 'KS', label: 'Kansas' },
  { value: 'KY', label: 'Kentucky' },
  { value: 'LA', label: 'Louisiana' },
  { value: 'ME', label: 'Maine' },
  { value: 'MD', label: 'Maryland' },
  { value: 'MA', label: 'Massachusetts' },
  { value: 'MI', label: 'Michigan' },
  { value: 'MN', label: 'Minnesota' },
  { value: 'MS', label: 'Mississippi' },
  { value: 'MO', label: 'Missouri' },
  { value: 'MT', label: 'Montana' },
  { value: 'NE', label: 'Nebraska' },
  { value: 'NV', label: 'Nevada' },
  { value: 'NH', label: 'New Hampshire' },
  { value: 'NJ', label: 'New Jersey' },
  { value: 'NM', label: 'New Mexico' },
  { value: 'NY', label: 'New York' },
  { value: 'NC', label: 'North Carolina' },
  { value: 'ND', label: 'North Dakota' },
  { value: 'OH', label: 'Ohio' },
  { value: 'OK', label: 'Oklahoma' },
  { value: 'OR', label: 'Oregon' },
  { value: 'PA', label: 'Pennsylvania' },
  { value: 'RI', label: 'Rhode Island' },
  { value: 'SC', label: 'South Carolina' },
  { value: 'SD', label: 'South Dakota' },
  { value: 'TN', label: 'Tennessee' },
  { value: 'TX', label: 'Texas' },
  { value: 'UT', label: 'Utah' },
  { value: 'VT', label: 'Vermont' },
  { value: 'VA', label: 'Virginia' },
  { value: 'WA', label: 'Washington' },
  { value: 'WV', label: 'West Virginia' },
  { value: 'WI', label: 'Wisconsin' },
  { value: 'WY', label: 'Wyoming' },
  { value: 'DC', label: 'Washington, D.C.' },
];
