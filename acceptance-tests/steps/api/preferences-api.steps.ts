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

Given("my phone number is not verified", async function (this: CustomWorld) {
  // This would typically be set up via the API or database
  // For now, we'll track it in test data
  this.setTestData("phoneVerified", false);
});

Given("my phone number is verified", async function (this: CustomWorld) {
  // This would typically be set up via the API or database
  this.setTestData("phoneVerified", true);
});

Given(
  "my current preferences are:",
  async function (this: CustomWorld, table: DataTable) {
    // In a real test, this would set up the initial state via API
    // For now, we just store the expected values
    const prefs = tableToPreferencesRequest(table);
    this.setTestData("initialPreferences", prefs);
  }
);

Given(
  "my current preferences have {string} set to {string}",
  async function (this: CustomWorld, path: string, value: string) {
    this.setTestData(`currentPref-${path}`, value);
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
  "the response should contain {string} with value {string}",
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
  "the response should contain {string}",
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
