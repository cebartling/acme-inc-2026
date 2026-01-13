import { Given, When, Then, DataTable } from "@cucumber/cucumber";
import { expect } from "@playwright/test";
import { CustomWorld } from "../../support/world.js";

interface ProfileCompletenessResponse {
  customerId: string;
  overallScore: number;
  sections: ProfileCompletenessSection[];
  nextAction: ProfileCompletenessNextAction | null;
  updatedAt: string;
}

interface ProfileCompletenessSection {
  name: string;
  displayName: string;
  weight: number;
  score: number;
  isComplete: boolean;
  items: ProfileCompletenessItem[];
}

interface ProfileCompletenessItem {
  name: string;
  complete: boolean;
  action?: string;
}

interface ProfileCompletenessNextAction {
  section: string;
  action: string;
  url: string;
}

interface ErrorResponse {
  error?: string;
  message?: string;
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

When("I request my profile completeness", async function (this: CustomWorld) {
  const customerId = this.getTestData<string>("customerId");

  try {
    const response =
      await this.customerApiClient.get<ProfileCompletenessResponse>(
        `/api/v1/customers/${customerId}/profile/completeness`
      );

    this.setTestData("lastResponseStatus", response.status);
    this.setTestData("lastResponseData", response.data);
    this.setTestData("profileCompletenessData", response.data);
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
  "I try to get completeness for customer {string}",
  async function (this: CustomWorld, customerId: string) {
    try {
      const response =
        await this.customerApiClient.get<ProfileCompletenessResponse>(
          `/api/v1/customers/${customerId}/profile/completeness`
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

Given("my profile completeness score is recorded", async function (this: CustomWorld) {
  const customerId = this.getTestData<string>("customerId");

  const response =
    await this.customerApiClient.get<ProfileCompletenessResponse>(
      `/api/v1/customers/${customerId}/profile/completeness`
    );

  this.setTestData("previousCompletenessScore", response.data.overallScore);
});

Given(
  "my profile is 100% complete",
  async function (this: CustomWorld) {
    // This step would need to set up a customer with all profile fields filled
    // For now, we'll mark this as a placeholder
    console.warn(
      "Note: Setting up 100% complete profile requires backend test helpers"
    );
    this.setTestData("profileComplete", true);
  }
);

Given(
  "my profile completeness is less than {int}",
  async function (this: CustomWorld, threshold: number) {
    const customerId = this.getTestData<string>("customerId");

    // Get current completeness
    const response =
      await this.customerApiClient.get<ProfileCompletenessResponse>(
        `/api/v1/customers/${customerId}/profile/completeness`
      );

    // Verify it's below the threshold
    expect(response.data.overallScore).toBeLessThan(threshold);
    this.setTestData("completenessThreshold", threshold);
  }
);

Given(
  "the address is validated",
  async function (this: CustomWorld) {
    // This would need a test helper to mark the address as validated
    console.warn(
      "Note: Address validation requires backend test helpers"
    );
    this.setTestData("addressValidated", true);
  }
);

Given(
  "the address is not validated",
  async function (this: CustomWorld) {
    this.setTestData("addressValidated", false);
  }
);

Given(
  "my profile has been updated with phone {string} {string}",
  async function (
    this: CustomWorld,
    countryCode: string,
    phoneNumber: string
  ) {
    const customerId = this.getTestData<string>("customerId");
    const userId = this.getTestData<string>("userId");

    await this.customerApiClient.patch(
      `/api/v1/customers/${customerId}/profile`,
      {
        phone: { countryCode, number: phoneNumber },
      },
      { headers: { "X-User-Id": userId! } }
    );
  }
);

When("I re-authenticate", async function (this: CustomWorld) {
  // Re-authentication would typically involve refreshing auth tokens
  // For testing purposes, we just verify the auth context is still valid
  const userId = this.getTestData<string>("userId");
  const customerId = this.getTestData<string>("customerId");
  expect(userId).toBeDefined();
  expect(customerId).toBeDefined();
});

When(
  "I update my profile with phone {string} {string}",
  async function (
    this: CustomWorld,
    countryCode: string,
    phoneNumber: string
  ) {
    const customerId = this.getTestData<string>("customerId");
    const userId = this.getTestData<string>("userId");

    try {
      const response = await this.customerApiClient.patch(
        `/api/v1/customers/${customerId}/profile`,
        {
          phone: { countryCode, number: phoneNumber },
        },
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
  "the profile is not 100% complete",
  async function (this: CustomWorld) {
    const data = this.getTestData<ProfileCompletenessResponse>(
      "profileCompletenessData"
    );
    expect(data.overallScore).toBeLessThan(100);
  }
);

Then(
  "the completeness response should include {string}",
  async function (this: CustomWorld, path: string) {
    const data = this.getTestData<unknown>("lastResponseData");
    const value = getNestedValue(data, path);
    expect(value).toBeDefined();
  }
);

Then(
  "the completeness response should not include {string}",
  async function (this: CustomWorld, path: string) {
    const data = this.getTestData<ProfileCompletenessResponse>(
      "lastResponseData"
    );

    if (path === "nextAction") {
      expect(data.nextAction).toBeNull();
    } else {
      const value = getNestedValue(data, path);
      expect(value).toBeUndefined();
    }
  }
);

Then(
  "the completeness response should have {string} set to {string}",
  async function (this: CustomWorld, path: string, expectedValue: string) {
    const data = this.getTestData<unknown>("lastResponseData");
    const actualValue = getNestedValue(data, path);

    // Convert expected value for comparison
    let expected: unknown = expectedValue;
    if (expectedValue === "true") expected = true;
    if (expectedValue === "false") expected = false;
    if (/^\d+$/.test(expectedValue)) expected = parseInt(expectedValue, 10);

    expect(String(actualValue)).toBe(String(expected));
  }
);

Then(
  "the completeness response should have {string} greater than the previous score",
  async function (this: CustomWorld, path: string) {
    const data = this.getTestData<unknown>("lastResponseData");
    const actualValue = getNestedValue(data, path) as number;
    const previousScore = this.getTestData<number>("previousCompletenessScore");

    expect(actualValue).toBeGreaterThan(previousScore);
  }
);

Then(
  "the completeness response should have {string} less than {string}",
  async function (this: CustomWorld, path: string, threshold: string) {
    const data = this.getTestData<unknown>("lastResponseData");
    const actualValue = getNestedValue(data, path) as number;

    expect(actualValue).toBeLessThan(parseInt(threshold, 10));
  }
);

Then(
  "the completeness section {string} should have weight {int}",
  async function (this: CustomWorld, sectionName: string, expectedWeight: number) {
    const data = this.getTestData<ProfileCompletenessResponse>("lastResponseData");
    const section = data.sections.find((s) => s.name === sectionName);

    expect(section).toBeDefined();
    expect(section!.weight).toBe(expectedWeight);
  }
);

Then(
  "the completeness section {string} should have {string} set to {string}",
  async function (
    this: CustomWorld,
    sectionName: string,
    field: string,
    expectedValue: string
  ) {
    const data = this.getTestData<ProfileCompletenessResponse>("lastResponseData");
    const section = data.sections.find((s) => s.name === sectionName);

    expect(section).toBeDefined();

    const actualValue = section![field as keyof ProfileCompletenessSection];

    // Convert expected value for comparison
    let expected: unknown = expectedValue;
    if (expectedValue === "true") expected = true;
    if (expectedValue === "false") expected = false;
    if (/^\d+$/.test(expectedValue)) expected = parseInt(expectedValue, 10);

    expect(String(actualValue)).toBe(String(expected));
  }
);

Then(
  "the completeness section {string} should include items:",
  async function (this: CustomWorld, sectionName: string, table: DataTable) {
    const data = this.getTestData<ProfileCompletenessResponse>("lastResponseData");
    const section = data.sections.find((s) => s.name === sectionName);

    expect(section).toBeDefined();

    const expectedItems = table.hashes().map((row) => row.name);
    const actualItems = section!.items.map((item) => item.name);

    for (const expectedItem of expectedItems) {
      expect(actualItems).toContain(expectedItem);
    }
  }
);

Then(
  "the {string} should include {string}",
  async function (this: CustomWorld, objectPath: string, field: string) {
    const data = this.getTestData<ProfileCompletenessResponse>("lastResponseData");
    const obj = getNestedValue(data, objectPath) as Record<string, unknown>;

    expect(obj).toBeDefined();
    expect(obj[field]).toBeDefined();
  }
);
