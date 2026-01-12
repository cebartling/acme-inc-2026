import { Given, When, Then, DataTable } from "@cucumber/cucumber";
import { expect } from "@playwright/test";
import { CustomWorld } from "../../support/world.js";

interface PreferencesRequest {
  communication?: {
    email?: boolean;
    sms?: boolean;
    push?: boolean;
    marketing?: boolean;
    frequency?: string;
  };
  privacy?: {
    shareDataWithPartners?: boolean;
    allowAnalytics?: boolean;
    allowPersonalization?: boolean;
  };
  display?: {
    language?: string;
    currency?: string;
    timezone?: string;
  };
}

interface PreferencesResponse {
  customerId: string;
  preferences: {
    communication: {
      email: boolean;
      sms: boolean;
      push: boolean;
      marketing: boolean;
      frequency: string;
    };
    privacy: {
      shareDataWithPartners: boolean;
      allowAnalytics: boolean;
      allowPersonalization: boolean;
    };
    display: {
      language: string;
      currency: string;
      timezone: string;
    };
  };
  updatedAt: string;
}

interface ErrorResponse {
  error?: string;
  message?: string;
  errors?: string[];
}

/**
 * Helper function to convert table data to nested preferences request object.
 */
function tableToPreferencesRequest(table: DataTable): PreferencesRequest {
  const request: PreferencesRequest = {};
  const rows = table.hashes();

  for (const row of rows) {
    const path = row.path;
    const value = row.value;
    const parts = path.split(".");

    if (parts[0] === "communication") {
      if (!request.communication) request.communication = {};
      const key = parts[1] as keyof NonNullable<
        PreferencesRequest["communication"]
      >;
      if (key === "frequency") {
        request.communication[key] = value;
      } else {
        request.communication[key] = value === "true";
      }
    } else if (parts[0] === "privacy") {
      if (!request.privacy) request.privacy = {};
      const key = parts[1] as keyof NonNullable<PreferencesRequest["privacy"]>;
      request.privacy[key] = value === "true";
    } else if (parts[0] === "display") {
      if (!request.display) request.display = {};
      const key = parts[1] as keyof NonNullable<PreferencesRequest["display"]>;
      request.display[key] = value;
    }
  }

  return request;
}

/**
 * Helper function to get nested value from response by dot-notation path.
 */
function getNestedValue(obj: unknown, path: string): unknown {
  const parts = path.split(".");
  let current = obj as Record<string, unknown>;

  for (const part of parts) {
    if (current === null || current === undefined) return undefined;
    current = current[part] as Record<string, unknown>;
  }

  return current;
}

// Note: 'I have an authenticated customer' is defined in address-api.steps.ts

/**
 * Ensures a test customer exists in the backend with the specified phone verification status.
 * Uses the test helper API (only available in test profile).
 */
async function ensureTestCustomerWithPhoneStatus(
  world: CustomWorld,
  phoneVerified: boolean
): Promise<void> {
  const customerId = world.getTestData<string>("customerId");
  const userId = world.getTestData<string>("userId");

  // First, ensure the test customer exists
  try {
    await world.customerApiClient.post("/api/v1/test/customers", {
      customerId,
      userId,
      phoneNumber: phoneVerified ? "5551234567" : null,
      phoneCountryCode: phoneVerified ? "+1" : null,
      phoneVerified,
    });
  } catch (error: unknown) {
    // Customer might already exist, that's okay
    if (
      error &&
      typeof error === "object" &&
      "response" in error &&
      error.response
    ) {
      const err = error as { response: { status: number } };
      if (err.response.status !== 200 && err.response.status !== 409) {
        throw error;
      }
    }
  }

  // Update phone verification status
  try {
    await world.customerApiClient.put(
      `/api/v1/test/customers/${customerId}/phone-verification`,
      {
        verified: phoneVerified,
        phoneNumber: phoneVerified ? "5551234567" : null,
        phoneCountryCode: phoneVerified ? "+1" : null,
      }
    );
  } catch (error: unknown) {
    // Log but don't fail - the test helper might not be available
    console.warn(
      `Warning: Could not update phone verification status. ` +
        `Ensure the backend is running with the 'test' profile.`
    );
  }

  world.setTestData("phoneVerified", phoneVerified);
}

Given("my phone number is not verified", async function (this: CustomWorld) {
  await ensureTestCustomerWithPhoneStatus(this, false);
});

Given("my phone number is verified", async function (this: CustomWorld) {
  await ensureTestCustomerWithPhoneStatus(this, true);
});

Given(
  "my current preferences are:",
  async function (this: CustomWorld, table: DataTable) {
    const customerId = this.getTestData<string>("customerId");
    const userId = this.getTestData<string>("userId");
    const prefs = tableToPreferencesRequest(table);

    // Set up initial preferences via API
    try {
      const response = await this.customerApiClient.put<PreferencesResponse>(
        `/api/v1/customers/${customerId}/preferences`,
        prefs,
        { headers: { "X-User-Id": userId! } }
      );

      if (response.status !== 200) {
        throw new Error(
          `Failed to set initial preferences: ${response.status}`
        );
      }

      this.setTestData("initialPreferences", prefs);
    } catch (error: unknown) {
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        error.response
      ) {
        const err = error as {
          response: { status: number; data: ErrorResponse };
        };
        throw new Error(
          `Failed to set initial preferences: ${err.response.status} - ${JSON.stringify(err.response.data)}`
        );
      }
      throw error;
    }
  }
);

Given(
  "my current preferences have {string} set to {string}",
  async function (this: CustomWorld, path: string, value: string) {
    const customerId = this.getTestData<string>("customerId");
    const userId = this.getTestData<string>("userId");

    // Build a preferences request with just this one field
    const table = {
      hashes: () => [{ path, value }],
    } as DataTable;
    const prefs = tableToPreferencesRequest(table);

    // Set up the preference via API
    try {
      const response = await this.customerApiClient.put<PreferencesResponse>(
        `/api/v1/customers/${customerId}/preferences`,
        prefs,
        { headers: { "X-User-Id": userId! } }
      );

      // Store the current value for later verification
      this.setTestData(`currentPref-${path}`, value);
    } catch (error: unknown) {
      // If setting fails (e.g., no actual change), that's fine for this setup step
      // We just want to ensure the value is what we expect
      this.setTestData(`currentPref-${path}`, value);
    }
  }
);

Given(
  "another customer exists with id {string}",
  async function (this: CustomWorld, customerId: string) {
    this.setTestData("otherCustomerId", customerId);
  }
);

When(
  "I update my preferences with:",
  async function (this: CustomWorld, table: DataTable) {
    const customerId = this.getTestData<string>("customerId");
    const userId = this.getTestData<string>("userId");
    const request = tableToPreferencesRequest(table);

    try {
      const response = await this.customerApiClient.put<PreferencesResponse>(
        `/api/v1/customers/${customerId}/preferences`,
        request,
        { headers: { "X-User-Id": userId! } }
      );

      this.setTestData("lastResponse", response);
      this.setTestData("lastResponseStatus", response.status);
      this.setTestData("lastResponseData", response.data);
    } catch (error: unknown) {
      // Handle error responses
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        error.response
      ) {
        const err = error as {
          response: { status: number; data: ErrorResponse };
        };
        this.setTestData("lastResponseStatus", err.response.status);
        this.setTestData("lastResponseData", err.response.data);
      } else {
        throw error;
      }
    }
  }
);

When("I request my preferences", async function (this: CustomWorld) {
  const customerId = this.getTestData<string>("customerId");
  const userId = this.getTestData<string>("userId");

  try {
    const response = await this.customerApiClient.get<PreferencesResponse>(
      `/api/v1/customers/${customerId}/preferences`,
      { headers: { "X-User-Id": userId! } }
    );

    this.setTestData("lastResponseStatus", response.status);
    this.setTestData("lastResponseData", response.data);
  } catch (error: unknown) {
    if (
      error &&
      typeof error === "object" &&
      "response" in error &&
      error.response
    ) {
      const err = error as {
        response: { status: number; data: ErrorResponse };
      };
      this.setTestData("lastResponseStatus", err.response.status);
      this.setTestData("lastResponseData", err.response.data);
    } else {
      throw error;
    }
  }
});

When(
  "I try to get preferences for customer {string}",
  async function (this: CustomWorld, customerId: string) {
    const userId = this.getTestData<string>("userId");

    try {
      const response = await this.customerApiClient.get<PreferencesResponse>(
        `/api/v1/customers/${customerId}/preferences`,
        { headers: { "X-User-Id": userId! } }
      );

      this.setTestData("lastResponseStatus", response.status);
      this.setTestData("lastResponseData", response.data);
    } catch (error: unknown) {
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        error.response
      ) {
        const err = error as {
          response: { status: number; data: ErrorResponse };
        };
        this.setTestData("lastResponseStatus", err.response.status);
        this.setTestData("lastResponseData", err.response.data);
      } else {
        throw error;
      }
    }
  }
);

When(
  "I try to update preferences for customer {string} with:",
  async function (this: CustomWorld, customerId: string, table: DataTable) {
    const userId = this.getTestData<string>("userId");
    const request = tableToPreferencesRequest(table);

    try {
      const response = await this.customerApiClient.put<PreferencesResponse>(
        `/api/v1/customers/${customerId}/preferences`,
        request,
        { headers: { "X-User-Id": userId! } }
      );

      this.setTestData("lastResponseStatus", response.status);
      this.setTestData("lastResponseData", response.data);
    } catch (error: unknown) {
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        error.response
      ) {
        const err = error as {
          response: { status: number; data: ErrorResponse };
        };
        this.setTestData("lastResponseStatus", err.response.status);
        this.setTestData("lastResponseData", err.response.data);
      } else {
        throw error;
      }
    }
  }
);

When(
  "I send an empty preferences update request",
  async function (this: CustomWorld) {
    const customerId = this.getTestData<string>("customerId");
    const userId = this.getTestData<string>("userId");

    try {
      const response = await this.customerApiClient.put<PreferencesResponse>(
        `/api/v1/customers/${customerId}/preferences`,
        {},
        { headers: { "X-User-Id": userId! } }
      );

      this.setTestData("lastResponseStatus", response.status);
      this.setTestData("lastResponseData", response.data);
    } catch (error: unknown) {
      if (
        error &&
        typeof error === "object" &&
        "response" in error &&
        error.response
      ) {
        const err = error as {
          response: { status: number; data: ErrorResponse };
        };
        this.setTestData("lastResponseStatus", err.response.status);
        this.setTestData("lastResponseData", err.response.data);
      } else {
        throw error;
      }
    }
  }
);

Then(
  "the preferences response should have {string} set to {string}",
  async function (this: CustomWorld, path: string, expectedValue: string) {
    const data = this.getTestData<unknown>("lastResponseData");

    // Handle nested paths like "communication.email" -> "preferences.communication.email"
    let fullPath = path;
    if (
      path.startsWith("communication.") ||
      path.startsWith("privacy.") ||
      path.startsWith("display.")
    ) {
      fullPath = `preferences.${path}`;
    }

    const actualValue = getNestedValue(data, fullPath);

    // Convert expected value for comparison
    let expected: unknown = expectedValue;
    if (expectedValue === "true") expected = true;
    if (expectedValue === "false") expected = false;

    expect(String(actualValue)).toBe(String(expected));
  }
);

Then(
  "the preferences response should include {string}",
  async function (this: CustomWorld, path: string) {
    const data = this.getTestData<unknown>("lastResponseData");
    const value = getNestedValue(data, path);
    expect(value).toBeDefined();
  }
);

Then(
  "the response should contain a validation error for {string}",
  async function (this: CustomWorld, field: string) {
    const data = this.getTestData<ErrorResponse>("lastResponseData");
    expect(
      data.errors?.some((err) =>
        err.toLowerCase().includes(field.toLowerCase())
      ) ||
        data.error?.toLowerCase().includes(field.toLowerCase()) ||
        data.message?.toLowerCase().includes(field.toLowerCase())
    ).toBe(true);
  }
);
