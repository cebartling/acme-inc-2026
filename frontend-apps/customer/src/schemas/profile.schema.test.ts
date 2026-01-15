import { describe, it, expect } from "vitest";
import {
  personalDetailsSchema,
  addressSchema,
  preferencesSchema,
  communicationPreferencesSchema,
  privacyPreferencesSchema,
  displayPreferencesSchema,
  fullPreferencesSchema,
} from "./profile.schema";

describe("personalDetailsSchema", () => {
  describe("phone number validation", () => {
    it("accepts valid US phone number", () => {
      const result = personalDetailsSchema.safeParse({
        phoneCountryCode: "+1",
        phoneNumber: "5551234567",
      });
      expect(result.success).toBe(true);
    });

    it("accepts phone number with formatting characters", () => {
      const result = personalDetailsSchema.safeParse({
        phoneCountryCode: "+1",
        phoneNumber: "(555) 123-4567",
      });
      expect(result.success).toBe(true);
    });

    it("accepts valid UK phone number", () => {
      const result = personalDetailsSchema.safeParse({
        phoneCountryCode: "+44",
        phoneNumber: "7911123456",
      });
      expect(result.success).toBe(true);
    });

    it("rejects invalid phone number format", () => {
      const result = personalDetailsSchema.safeParse({
        phoneCountryCode: "+1",
        phoneNumber: "123",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Invalid phone number format"
        );
      }
    });

    it("accepts empty phone number (optional field)", () => {
      const result = personalDetailsSchema.safeParse({
        phoneCountryCode: "+1",
        phoneNumber: "",
      });
      expect(result.success).toBe(true);
    });

    it("accepts when both phone fields are omitted", () => {
      const result = personalDetailsSchema.safeParse({});
      expect(result.success).toBe(true);
    });
  });

  describe("date of birth validation", () => {
    it("accepts valid date for user 18 years old", () => {
      const eighteenYearsAgo = new Date();
      eighteenYearsAgo.setFullYear(eighteenYearsAgo.getFullYear() - 18);
      const result = personalDetailsSchema.safeParse({
        dateOfBirth: eighteenYearsAgo.toISOString().split("T")[0],
      });
      expect(result.success).toBe(true);
    });

    it("accepts valid date for user 13 years old", () => {
      // Use 13 years and 1 day ago to avoid edge case with exact birthday
      const thirteenYearsAgo = new Date();
      thirteenYearsAgo.setFullYear(thirteenYearsAgo.getFullYear() - 13);
      thirteenYearsAgo.setDate(thirteenYearsAgo.getDate() - 1);
      const result = personalDetailsSchema.safeParse({
        dateOfBirth: thirteenYearsAgo.toISOString().split("T")[0],
      });
      expect(result.success).toBe(true);
    });

    it("rejects date for user under 13 years old", () => {
      const tenYearsAgo = new Date();
      tenYearsAgo.setFullYear(tenYearsAgo.getFullYear() - 10);
      const result = personalDetailsSchema.safeParse({
        dateOfBirth: tenYearsAgo.toISOString().split("T")[0],
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "You must be at least 13 years old"
        );
      }
    });

    it("accepts empty date of birth (optional field)", () => {
      const result = personalDetailsSchema.safeParse({});
      expect(result.success).toBe(true);
    });
  });

  describe("gender validation", () => {
    it("accepts MALE gender", () => {
      const result = personalDetailsSchema.safeParse({ gender: "MALE" });
      expect(result.success).toBe(true);
    });

    it("accepts FEMALE gender", () => {
      const result = personalDetailsSchema.safeParse({ gender: "FEMALE" });
      expect(result.success).toBe(true);
    });

    it("accepts NON_BINARY gender", () => {
      const result = personalDetailsSchema.safeParse({ gender: "NON_BINARY" });
      expect(result.success).toBe(true);
    });

    it("accepts PREFER_NOT_TO_SAY gender", () => {
      const result = personalDetailsSchema.safeParse({
        gender: "PREFER_NOT_TO_SAY",
      });
      expect(result.success).toBe(true);
    });

    it("rejects invalid gender value", () => {
      const result = personalDetailsSchema.safeParse({ gender: "INVALID" });
      expect(result.success).toBe(false);
    });

    it("accepts when gender is omitted (optional)", () => {
      const result = personalDetailsSchema.safeParse({});
      expect(result.success).toBe(true);
    });
  });

  describe("locale and timezone validation", () => {
    it("accepts valid locale", () => {
      const result = personalDetailsSchema.safeParse({
        preferredLocale: "en-US",
      });
      expect(result.success).toBe(true);
    });

    it("accepts valid timezone", () => {
      const result = personalDetailsSchema.safeParse({
        timezone: "America/New_York",
      });
      expect(result.success).toBe(true);
    });

    it("accepts when locale and timezone are omitted", () => {
      const result = personalDetailsSchema.safeParse({});
      expect(result.success).toBe(true);
    });
  });
});

describe("addressSchema", () => {
  const validAddress = {
    addressType: "SHIPPING" as const,
    streetLine1: "123 Main St",
    city: "New York",
    stateProvince: "NY",
    postalCode: "10001",
    country: "US",
  };

  describe("required fields", () => {
    it("accepts valid address with all required fields", () => {
      const result = addressSchema.safeParse(validAddress);
      expect(result.success).toBe(true);
    });

    it("rejects missing street address", () => {
      const { streetLine1, ...incomplete } = validAddress;
      const result = addressSchema.safeParse(incomplete);
      expect(result.success).toBe(false);
    });

    it("rejects empty street address", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        streetLine1: "",
      });
      expect(result.success).toBe(false);
    });

    it("rejects missing city", () => {
      const { city, ...incomplete } = validAddress;
      const result = addressSchema.safeParse(incomplete);
      expect(result.success).toBe(false);
    });

    it("rejects empty city", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        city: "",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe("City is required");
      }
    });

    it("rejects missing state/province", () => {
      const { stateProvince, ...incomplete } = validAddress;
      const result = addressSchema.safeParse(incomplete);
      expect(result.success).toBe(false);
    });

    it("rejects empty state/province", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        stateProvince: "",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "State/Province is required"
        );
      }
    });

    it("rejects missing postal code", () => {
      const { postalCode, ...incomplete } = validAddress;
      const result = addressSchema.safeParse(incomplete);
      expect(result.success).toBe(false);
    });

    it("rejects empty postal code", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        postalCode: "",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe("Postal code is required");
      }
    });

    it("rejects missing country", () => {
      const { country, ...incomplete } = validAddress;
      const result = addressSchema.safeParse(incomplete);
      expect(result.success).toBe(false);
    });

    it("rejects empty country", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        country: "",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe("Country is required");
      }
    });
  });

  describe("address type validation", () => {
    it("accepts SHIPPING address type", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        addressType: "SHIPPING",
      });
      expect(result.success).toBe(true);
    });

    it("accepts BILLING address type", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        addressType: "BILLING",
      });
      expect(result.success).toBe(true);
    });

    it("accepts BOTH address type", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        addressType: "BOTH",
      });
      expect(result.success).toBe(true);
    });

    it("rejects invalid address type", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        addressType: "INVALID",
      });
      expect(result.success).toBe(false);
    });
  });

  describe("optional fields", () => {
    it("accepts address with optional streetLine2", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        streetLine2: "Apt 4B",
      });
      expect(result.success).toBe(true);
    });

    it("accepts address with optional label", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        label: "Home",
      });
      expect(result.success).toBe(true);
    });

    it("rejects label exceeding 50 characters", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        label: "A".repeat(51),
      });
      expect(result.success).toBe(false);
    });

    it("accepts label with exactly 50 characters", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        label: "A".repeat(50),
      });
      expect(result.success).toBe(true);
    });
  });

  describe("field length limits", () => {
    it("rejects streetLine1 exceeding 100 characters", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        streetLine1: "A".repeat(101),
      });
      expect(result.success).toBe(false);
    });

    it("rejects streetLine2 exceeding 100 characters", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        streetLine2: "A".repeat(101),
      });
      expect(result.success).toBe(false);
    });

    it("rejects city exceeding 50 characters", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        city: "A".repeat(51),
      });
      expect(result.success).toBe(false);
    });
  });

  describe("isDefault field", () => {
    it("defaults isDefault to true", () => {
      const result = addressSchema.safeParse(validAddress);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.isDefault).toBe(true);
      }
    });

    it("accepts explicit isDefault value", () => {
      const result = addressSchema.safeParse({
        ...validAddress,
        isDefault: false,
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.isDefault).toBe(false);
      }
    });
  });
});

describe("preferencesSchema", () => {
  describe("default values", () => {
    it("sets default values when parsing empty object", () => {
      const result = preferencesSchema.safeParse({});
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.emailNotifications).toBe(true);
        expect(result.data.smsNotifications).toBe(false);
        expect(result.data.pushNotifications).toBe(false);
        expect(result.data.marketingCommunications).toBe(false);
        expect(result.data.notificationFrequency).toBe("IMMEDIATE");
      }
    });
  });

  describe("notification settings", () => {
    it("accepts all notification types enabled", () => {
      const result = preferencesSchema.safeParse({
        emailNotifications: true,
        smsNotifications: true,
        pushNotifications: true,
        marketingCommunications: true,
      });
      expect(result.success).toBe(true);
    });

    it("accepts all notification types disabled", () => {
      const result = preferencesSchema.safeParse({
        emailNotifications: false,
        smsNotifications: false,
        pushNotifications: false,
        marketingCommunications: false,
      });
      expect(result.success).toBe(true);
    });
  });

  describe("notification frequency", () => {
    it("accepts IMMEDIATE frequency", () => {
      const result = preferencesSchema.safeParse({
        notificationFrequency: "IMMEDIATE",
      });
      expect(result.success).toBe(true);
    });

    it("accepts DAILY_DIGEST frequency", () => {
      const result = preferencesSchema.safeParse({
        notificationFrequency: "DAILY_DIGEST",
      });
      expect(result.success).toBe(true);
    });

    it("accepts WEEKLY_DIGEST frequency", () => {
      const result = preferencesSchema.safeParse({
        notificationFrequency: "WEEKLY_DIGEST",
      });
      expect(result.success).toBe(true);
    });

    it("rejects invalid frequency", () => {
      const result = preferencesSchema.safeParse({
        notificationFrequency: "HOURLY",
      });
      expect(result.success).toBe(false);
    });
  });
});

describe("communicationPreferencesSchema", () => {
  it("sets correct default values", () => {
    const result = communicationPreferencesSchema.safeParse({});
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.email).toBe(true);
      expect(result.data.sms).toBe(false);
      expect(result.data.push).toBe(false);
      expect(result.data.marketing).toBe(false);
      expect(result.data.frequency).toBe("IMMEDIATE");
    }
  });

  it("accepts custom values", () => {
    const result = communicationPreferencesSchema.safeParse({
      email: false,
      sms: true,
      push: true,
      marketing: true,
      frequency: "DAILY_DIGEST",
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.email).toBe(false);
      expect(result.data.sms).toBe(true);
      expect(result.data.frequency).toBe("DAILY_DIGEST");
    }
  });
});

describe("privacyPreferencesSchema", () => {
  it("sets correct default values", () => {
    const result = privacyPreferencesSchema.safeParse({});
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.shareDataWithPartners).toBe(false);
      expect(result.data.allowAnalytics).toBe(true);
      expect(result.data.allowPersonalization).toBe(true);
    }
  });

  it("accepts custom privacy settings", () => {
    const result = privacyPreferencesSchema.safeParse({
      shareDataWithPartners: true,
      allowAnalytics: false,
      allowPersonalization: false,
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.shareDataWithPartners).toBe(true);
      expect(result.data.allowAnalytics).toBe(false);
      expect(result.data.allowPersonalization).toBe(false);
    }
  });
});

describe("displayPreferencesSchema", () => {
  it("sets correct default values", () => {
    const result = displayPreferencesSchema.safeParse({});
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.language).toBe("en-US");
      expect(result.data.currency).toBe("USD");
      expect(result.data.timezone).toBe("UTC");
    }
  });

  it("accepts custom display settings", () => {
    const result = displayPreferencesSchema.safeParse({
      language: "es-ES",
      currency: "EUR",
      timezone: "Europe/Madrid",
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.language).toBe("es-ES");
      expect(result.data.currency).toBe("EUR");
      expect(result.data.timezone).toBe("Europe/Madrid");
    }
  });
});

describe("fullPreferencesSchema", () => {
  it("accepts valid full preferences object", () => {
    const result = fullPreferencesSchema.safeParse({
      communication: {
        email: true,
        sms: false,
        push: false,
        marketing: false,
        frequency: "IMMEDIATE",
      },
      privacy: {
        shareDataWithPartners: false,
        allowAnalytics: true,
        allowPersonalization: true,
      },
      display: {
        language: "en-US",
        currency: "USD",
        timezone: "America/New_York",
      },
    });
    expect(result.success).toBe(true);
  });

  it("rejects when communication section is missing", () => {
    const result = fullPreferencesSchema.safeParse({
      privacy: {
        shareDataWithPartners: false,
        allowAnalytics: true,
        allowPersonalization: true,
      },
      display: {
        language: "en-US",
        currency: "USD",
        timezone: "UTC",
      },
    });
    expect(result.success).toBe(false);
  });

  it("rejects when privacy section is missing", () => {
    const result = fullPreferencesSchema.safeParse({
      communication: {
        email: true,
        sms: false,
        push: false,
        marketing: false,
        frequency: "IMMEDIATE",
      },
      display: {
        language: "en-US",
        currency: "USD",
        timezone: "UTC",
      },
    });
    expect(result.success).toBe(false);
  });

  it("rejects when display section is missing", () => {
    const result = fullPreferencesSchema.safeParse({
      communication: {
        email: true,
        sms: false,
        push: false,
        marketing: false,
        frequency: "IMMEDIATE",
      },
      privacy: {
        shareDataWithPartners: false,
        allowAnalytics: true,
        allowPersonalization: true,
      },
    });
    expect(result.success).toBe(false);
  });
});
