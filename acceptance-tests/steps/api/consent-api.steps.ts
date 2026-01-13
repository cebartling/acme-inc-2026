import { Given, When, Then } from "@cucumber/cucumber";
import { expect } from "@playwright/test";
import { CustomWorld } from "../../support/world.js";

interface ConsentRequest {
  consentType: string;
  granted: boolean;
  source: string;
  ipAddress: string;
  userAgent?: string;
}

interface ConsentResponse {
  consentId: string;
  customerId: string;
  consentType: string;
  granted: boolean;
  grantedAt?: string;
  source: string;
  expiresAt?: string;
  version: number;
}

interface ConsentStatusResponse {
  consentType: string;
  currentStatus: boolean;
  grantedAt?: string;
  expiresAt?: string;
  source: string;
  required: boolean;
}

interface ConsentsListResponse {
  customerId: string;
  consents: ConsentStatusResponse[];
}

interface ConsentHistoryEntry {
  consentId: string;
  consentType: string;
  granted: boolean;
  timestamp: string;
  source: string;
  ipAddress: string;
  userAgent?: string;
}

interface ConsentHistoryExportResponse {
  customerId: string;
  exportedAt: string;
  consentHistory: ConsentHistoryEntry[];
}

interface ErrorResponse {
  error?: string;
  message?: string;
}

const DEFAULT_IP = "192.168.1.1";
const DEFAULT_USER_AGENT = "Cucumber-Test-Agent/1.0";
// A valid UUID that represents a non-existent customer for authorization tests
const OTHER_CUSTOMER_UUID = "00000000-0000-0000-0000-000000000099";

/**
 * Converts a customer ID string to a valid UUID.
 * For placeholder values like "other-customer-id", returns a fixed UUID
 * that will trigger authorization failures (customer doesn't match user).
 */
function normalizeCustomerId(customerId: string): string {
  // Check if it's already a valid UUID format
  const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (uuidPattern.test(customerId)) {
    return customerId;
  }
  // For placeholder values, return a valid UUID that represents a different customer
  return OTHER_CUSTOMER_UUID;
}

/**
 * Ensures a test customer exists in the backend.
 * Uses the test helper API (only available in test profile).
 */
async function ensureTestCustomerExists(world: CustomWorld): Promise<void> {
  const customerId = world.getTestData<string>("customerId");
  const userId = world.getTestData<string>("userId");

  const response = await world.customerApiClient.post("/api/v1/test/customers", {
    customerId,
    userId,
    phoneNumber: null,
    phoneCountryCode: null,
    phoneVerified: false,
  });

  // 200 = success, 201 = created, 409 = already exists - all are acceptable
  if (response.status !== 200 && response.status !== 201 && response.status !== 409) {
    // Log warning but don't fail - the test helper might not be available
    console.warn(
      `Warning: Could not create test customer. Status: ${response.status}. ` +
      `Ensure the backend is running with the 'test' profile. ` +
      `Response: ${JSON.stringify(response.data)}`
    );
  }
}

// Helper to grant consent
async function grantConsent(
  world: CustomWorld,
  consentType: string,
  source: string,
  ipAddress: string = DEFAULT_IP,
  userAgent: string = DEFAULT_USER_AGENT,
  customerId?: string
): Promise<void> {
  // Ensure the test customer exists before granting consent
  if (!customerId) {
    await ensureTestCustomerExists(world);
  }

  const custId = customerId || world.getTestData<string>("customerId");
  const userId = world.getTestData<string>("userId");

  const request: ConsentRequest = {
    consentType,
    granted: true,
    source,
    ipAddress,
    userAgent,
  };

  try {
    const response = await world.customerApiClient.post<ConsentResponse>(
      `/api/v1/customers/${custId}/consents`,
      request,
      { headers: { "X-User-Id": userId! } }
    );

    world.setTestData("lastResponseStatus", response.status);
    world.setTestData("lastResponseData", response.data);
    world.setTestData("lastConsentResponse", response.data);
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
      world.setTestData("lastResponseStatus", err.response.status);
      world.setTestData("lastResponseData", err.response.data);
    } else {
      throw error;
    }
  }
}

// Helper to revoke consent
async function revokeConsent(
  world: CustomWorld,
  consentType: string,
  source: string,
  customerId?: string
): Promise<void> {
  // Ensure the test customer exists before revoking consent
  if (!customerId) {
    await ensureTestCustomerExists(world);
  }

  const custId = customerId || world.getTestData<string>("customerId");
  const userId = world.getTestData<string>("userId");

  const request: ConsentRequest = {
    consentType,
    granted: false,
    source,
    ipAddress: DEFAULT_IP,
    userAgent: DEFAULT_USER_AGENT,
  };

  try {
    const response = await world.customerApiClient.post<ConsentResponse>(
      `/api/v1/customers/${custId}/consents`,
      request,
      { headers: { "X-User-Id": userId! } }
    );

    world.setTestData("lastResponseStatus", response.status);
    world.setTestData("lastResponseData", response.data);
    world.setTestData("lastConsentResponse", response.data);
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
      world.setTestData("lastResponseStatus", err.response.status);
      world.setTestData("lastResponseData", err.response.data);
    } else {
      throw error;
    }
  }
}

// Given steps

Given(
  "I have DATA_PROCESSING consent from registration",
  async function (this: CustomWorld) {
    // DATA_PROCESSING consent is implicitly granted at registration
    // We just need to ensure the customer exists
    // For the test, we'll grant it explicitly via the test helper
    await grantConsent(this, "DATA_PROCESSING", "REGISTRATION");
  }
);

Given(
  "I have granted consent for {string}",
  async function (this: CustomWorld, consentType: string) {
    await grantConsent(this, consentType, "PROFILE_WIZARD");
  }
);

Given(
  "I have granted and revoked several consents",
  async function (this: CustomWorld) {
    // Grant DATA_PROCESSING
    await grantConsent(this, "DATA_PROCESSING", "REGISTRATION");
    // Grant MARKETING
    await grantConsent(this, "MARKETING", "PROFILE_WIZARD");
    // Grant ANALYTICS
    await grantConsent(this, "ANALYTICS", "PRIVACY_SETTINGS");
    // Revoke ANALYTICS
    await revokeConsent(this, "ANALYTICS", "PRIVACY_SETTINGS");
    // Grant PERSONALIZATION
    await grantConsent(this, "PERSONALIZATION", "API");
  }
);

Given("I have no consent records", async function (this: CustomWorld) {
  // For this test, we assume the customer has just been created
  // and has no consent records yet.
  // This is a test setup assumption.
});

// When steps

When(
  "I grant consent for {string} from {string}",
  async function (this: CustomWorld, consentType: string, source: string) {
    await grantConsent(this, consentType, source);
  }
);

When(
  "I grant consent for {string} from {string} with IP {string} and user agent {string}",
  async function (
    this: CustomWorld,
    consentType: string,
    source: string,
    ipAddress: string,
    userAgent: string
  ) {
    await grantConsent(this, consentType, source, ipAddress, userAgent);
  }
);

When(
  "I revoke consent for {string} from {string}",
  async function (this: CustomWorld, consentType: string, source: string) {
    await revokeConsent(this, consentType, source);
  }
);

When(
  "I try to revoke consent for {string} from {string}",
  async function (this: CustomWorld, consentType: string, source: string) {
    await revokeConsent(this, consentType, source);
  }
);

When(
  "I try to grant consent for {string} from {string}",
  async function (this: CustomWorld, consentType: string, source: string) {
    await grantConsent(this, consentType, source);
  }
);

When(
  "I try to grant consent for {string} for customer {string}",
  async function (
    this: CustomWorld,
    consentType: string,
    customerId: string
  ) {
    const normalizedCustomerId = normalizeCustomerId(customerId);
    await grantConsent(this, consentType, "PROFILE_WIZARD", DEFAULT_IP, DEFAULT_USER_AGENT, normalizedCustomerId);
  }
);

When("I request my consents", async function (this: CustomWorld) {
  // Ensure the test customer exists
  await ensureTestCustomerExists(this);

  const customerId = this.getTestData<string>("customerId");
  const userId = this.getTestData<string>("userId");

  try {
    const response = await this.customerApiClient.get<ConsentsListResponse>(
      `/api/v1/customers/${customerId}/consents`,
      { headers: { "X-User-Id": userId! } }
    );

    this.setTestData("lastResponseStatus", response.status);
    this.setTestData("lastResponseData", response.data);
    this.setTestData("consentsListResponse", response.data);
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
  "I try to get consents for customer {string}",
  async function (this: CustomWorld, customerId: string) {
    const userId = this.getTestData<string>("userId");
    const normalizedCustomerId = normalizeCustomerId(customerId);

    try {
      const response = await this.customerApiClient.get<ConsentsListResponse>(
        `/api/v1/customers/${normalizedCustomerId}/consents`,
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

When("I export my consent history", async function (this: CustomWorld) {
  // Ensure the test customer exists
  await ensureTestCustomerExists(this);

  const customerId = this.getTestData<string>("customerId");
  const userId = this.getTestData<string>("userId");

  try {
    const response =
      await this.customerApiClient.get<ConsentHistoryExportResponse>(
        `/api/v1/customers/${customerId}/consents/history?format=json`,
        { headers: { "X-User-Id": userId! } }
      );

    this.setTestData("lastResponseStatus", response.status);
    this.setTestData("lastResponseData", response.data);
    this.setTestData("consentHistoryResponse", response.data);
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
  "I export my consent history with format {string}",
  async function (this: CustomWorld, format: string) {
    // Ensure the test customer exists
    await ensureTestCustomerExists(this);

    const customerId = this.getTestData<string>("customerId");
    const userId = this.getTestData<string>("userId");

    try {
      const response =
        await this.customerApiClient.get<ConsentHistoryExportResponse>(
          `/api/v1/customers/${customerId}/consents/history?format=${format}`,
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
  "I try to export consent history for customer {string}",
  async function (this: CustomWorld, customerId: string) {
    const userId = this.getTestData<string>("userId");
    const normalizedCustomerId = normalizeCustomerId(customerId);

    try {
      const response =
        await this.customerApiClient.get<ConsentHistoryExportResponse>(
          `/api/v1/customers/${normalizedCustomerId}/consents/history?format=json`,
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

// Then steps

Then(
  "the consent response should have {string} set to {string}",
  async function (this: CustomWorld, field: string, expectedValue: string) {
    const data = this.getTestData<ConsentResponse>("lastResponseData");

    const actualValue = (data as Record<string, unknown>)[field];

    // Convert expected value for comparison
    let expected: unknown = expectedValue;
    if (expectedValue === "true") expected = true;
    if (expectedValue === "false") expected = false;
    if (!isNaN(Number(expectedValue))) expected = Number(expectedValue);

    expect(String(actualValue)).toBe(String(expected));
  }
);

Then(
  "the consent response should include {string}",
  async function (this: CustomWorld, field: string) {
    const data = this.getTestData<ConsentResponse>("lastResponseData");
    expect((data as Record<string, unknown>)[field]).toBeDefined();
  }
);

Then(
  "the consent response should have {string} set approximately 1 year from now",
  async function (this: CustomWorld, field: string) {
    const data = this.getTestData<ConsentResponse>("lastResponseData");
    const expiresAt = (data as Record<string, unknown>)[field] as string;

    expect(expiresAt).toBeDefined();

    const expiresDate = new Date(expiresAt);
    const now = new Date();
    const oneYearFromNow = new Date(
      now.getFullYear() + 1,
      now.getMonth(),
      now.getDate()
    );

    // Allow 1 day tolerance
    const diffDays = Math.abs(
      (expiresDate.getTime() - oneYearFromNow.getTime()) / (1000 * 60 * 60 * 24)
    );
    expect(diffDays).toBeLessThan(1);
  }
);

Then(
  "my consents should show {string} as granted",
  async function (this: CustomWorld, consentType: string) {
    const data = this.getTestData<ConsentsListResponse>("lastResponseData");
    const consent = data.consents.find((c) => c.consentType === consentType);

    expect(consent).toBeDefined();
    expect(consent!.currentStatus).toBe(true);
  }
);

Then(
  "my consents should show {string} as not granted",
  async function (this: CustomWorld, consentType: string) {
    const data = this.getTestData<ConsentsListResponse>("lastResponseData");
    const consent = data.consents.find((c) => c.consentType === consentType);

    expect(consent).toBeDefined();
    expect(consent!.currentStatus).toBe(false);
  }
);

Then(
  "my consents should show {string} as required",
  async function (this: CustomWorld, consentType: string) {
    const data = this.getTestData<ConsentsListResponse>("lastResponseData");
    const consent = data.consents.find((c) => c.consentType === consentType);

    expect(consent).toBeDefined();
    expect(consent!.required).toBe(true);
  }
);

Then(
  "my consents should show {string} with source {string}",
  async function (this: CustomWorld, consentType: string, source: string) {
    const data = this.getTestData<ConsentsListResponse>("lastResponseData");
    const consent = data.consents.find((c) => c.consentType === consentType);

    expect(consent).toBeDefined();
    expect(consent!.source).toBe(source);
  }
);

Then(
  "my consents should show {string} with no expiration",
  async function (this: CustomWorld, consentType: string) {
    const data = this.getTestData<ConsentsListResponse>("lastResponseData");
    const consent = data.consents.find((c) => c.consentType === consentType);

    expect(consent).toBeDefined();
    expect(consent!.expiresAt).toBeNull();
  }
);

Then(
  "the consents response should include {string}",
  async function (this: CustomWorld, field: string) {
    const data = this.getTestData<ConsentsListResponse>("lastResponseData");
    expect((data as Record<string, unknown>)[field]).toBeDefined();
  }
);

Then(
  "the consents should include all consent types",
  async function (this: CustomWorld) {
    const data = this.getTestData<ConsentsListResponse>("lastResponseData");
    const consentTypes = [
      "DATA_PROCESSING",
      "MARKETING",
      "ANALYTICS",
      "THIRD_PARTY",
      "PERSONALIZATION",
    ];

    for (const type of consentTypes) {
      const consent = data.consents.find((c) => c.consentType === type);
      expect(consent).toBeDefined();
    }
  }
);

Then(
  "the consent history export should include {string}",
  async function (this: CustomWorld, field: string) {
    const data = this.getTestData<ConsentHistoryExportResponse>(
      "lastResponseData"
    );
    expect((data as Record<string, unknown>)[field]).toBeDefined();
  }
);

Then(
  "the consent history should contain a record for {string}",
  async function (this: CustomWorld, consentType: string) {
    const data = this.getTestData<ConsentHistoryExportResponse>(
      "lastResponseData"
    );
    const record = data.consentHistory.find(
      (r) => r.consentType === consentType
    );
    expect(record).toBeDefined();
    this.setTestData("lastConsentHistoryRecord", record);
  }
);

Then(
  "the consent history record should have {string} set to {string}",
  async function (this: CustomWorld, field: string, expectedValue: string) {
    const record = this.getTestData<ConsentHistoryEntry>(
      "lastConsentHistoryRecord"
    );
    const actualValue = (record as Record<string, unknown>)[field];
    expect(String(actualValue)).toBe(expectedValue);
  }
);

Then(
  "each consent history record should include {string}",
  async function (this: CustomWorld, field: string) {
    const data = this.getTestData<ConsentHistoryExportResponse>(
      "lastResponseData"
    );

    for (const record of data.consentHistory) {
      expect((record as Record<string, unknown>)[field]).toBeDefined();
    }
  }
);

Then(
  "the consent history should be empty",
  async function (this: CustomWorld) {
    const data = this.getTestData<ConsentHistoryExportResponse>(
      "lastResponseData"
    );
    expect(data.consentHistory).toHaveLength(0);
  }
);
