import { Given, When, Then, DataTable } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

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
  const parts = path.split('.');
  let current = obj as Record<string, unknown>;

  for (const part of parts) {
    if (current === null || current === undefined) return undefined;
    current = current[part] as Record<string, unknown>;
  }

  return current;
}

When('I request my profile completeness', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  try {
    const response = await this.customerApiClient.get<ProfileCompletenessResponse>(
      `/api/v1/customers/${customerId}/profile/completeness`,
      { headers: { 'X-User-Id': userId! } }
    );

    this.setTestData('lastResponseStatus', response.status);
    this.setTestData('lastResponseData', response.data);
    this.setTestData('profileCompletenessData', response.data);
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'response' in error && error.response) {
      const err = error as {
        response: { status: number; data: ErrorResponse };
      };
      this.setTestData('lastResponseStatus', err.response.status);
      this.setTestData('lastResponseData', err.response.data);
    } else {
      throw error;
    }
  }
});

When(
  'I try to get completeness for customer {string}',
  async function (this: CustomWorld, customerId: string) {
    // Use the authenticated user's ID to trigger authorization check
    const userId = this.getTestData<string>('userId');

    try {
      const response = await this.customerApiClient.get<ProfileCompletenessResponse>(
        `/api/v1/customers/${customerId}/profile/completeness`,
        { headers: { 'X-User-Id': userId! } }
      );

      this.setTestData('lastResponseStatus', response.status);
      this.setTestData('lastResponseData', response.data);
    } catch (error: unknown) {
      if (error && typeof error === 'object' && 'response' in error && error.response) {
        const err = error as {
          response: { status: number; data: ErrorResponse };
        };
        this.setTestData('lastResponseStatus', err.response.status);
        this.setTestData('lastResponseData', err.response.data);
      } else {
        throw error;
      }
    }
  }
);

Given('my profile completeness score is recorded', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  const response = await this.customerApiClient.get<ProfileCompletenessResponse>(
    `/api/v1/customers/${customerId}/profile/completeness`,
    { headers: { 'X-User-Id': userId! } }
  );

  this.setTestData('previousCompletenessScore', response.data.overallScore);
});

Given('my profile is 100% complete', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  // 1. Update profile with personal details (dateOfBirth or gender)
  try {
    await this.customerApiClient.patch(
      `/api/v1/customers/${customerId}/profile`,
      {
        dateOfBirth: '1990-05-15',
        gender: 'MALE',
        phone: { countryCode: '+1', number: '2024561234' },
      },
      { headers: { 'X-User-Id': userId! } }
    );
  } catch (error) {
    console.warn('Warning: Could not update profile for 100% completion');
  }

  // 2. Add and validate an address
  try {
    const addressResponse = await this.customerApiClient.post<{ addressId: string }>(
      `/api/v1/customers/${customerId}/addresses`,
      {
        type: 'SHIPPING',
        street: { line1: '123 Complete St' },
        city: 'New York',
        state: 'NY',
        postalCode: '10001',
        country: 'US',
        isDefault: true,
      },
      { headers: { 'X-User-Id': userId! } }
    );

    // Validate the address using test helper
    const addressId = addressResponse.data.addressId;
    if (addressId) {
      await this.customerApiClient.put(
        `/api/v1/test/customers/${customerId}/addresses/${addressId}/validate`,
        { isValid: true }
      );
    }
  } catch (error) {
    console.warn('Warning: Could not add/validate address for 100% completion');
  }

  // 3. Grant required consent
  try {
    await this.customerApiClient.post(
      `/api/v1/customers/${customerId}/consents`,
      {
        consentType: 'DATA_PROCESSING',
        granted: true,
        source: 'REGISTRATION',
        ipAddress: '127.0.0.1',
      },
      { headers: { 'X-User-Id': userId! } }
    );
  } catch (error) {
    console.warn('Warning: Could not grant consent for 100% completion');
  }

  // 4. Set preferences
  try {
    await this.customerApiClient.put(
      `/api/v1/customers/${customerId}/preferences`,
      {
        communication: {
          email: true,
          sms: false,
          push: false,
          marketing: false,
          frequency: 'IMMEDIATE',
        },
        privacy: {
          shareDataWithPartners: false,
          allowAnalytics: true,
          allowPersonalization: true,
        },
        display: {
          language: 'en-US',
          currency: 'USD',
          timezone: 'UTC',
        },
      },
      { headers: { 'X-User-Id': userId! } }
    );
  } catch (error) {
    console.warn('Warning: Could not update preferences for 100% completion');
  }

  this.setTestData('profileComplete', true);
});

Given(
  'my profile completeness is less than {int}',
  async function (this: CustomWorld, threshold: number) {
    const customerId = this.getTestData<string>('customerId');

    // Get current completeness
    const response = await this.customerApiClient.get<ProfileCompletenessResponse>(
      `/api/v1/customers/${customerId}/profile/completeness`
    );

    // Verify it's below the threshold
    expect(response.data.overallScore).toBeLessThan(threshold);
    this.setTestData('completenessThreshold', threshold);
  }
);

Given('the address is validated', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const addressId = this.getTestData<string>('lastAddressId');

  if (!addressId) {
    console.warn('Warning: No address ID found to validate');
    return;
  }

  try {
    await this.customerApiClient.put(
      `/api/v1/test/customers/${customerId}/addresses/${addressId}/validate`,
      { isValid: true }
    );
    this.setTestData('addressValidated', true);
  } catch (error) {
    console.warn('Warning: Could not validate address:', error);
  }
});

Given('the address is not validated', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const addressId = this.getTestData<string>('lastAddressId');

  if (addressId) {
    try {
      // Explicitly mark the address as not validated
      await this.customerApiClient.put(
        `/api/v1/test/customers/${customerId}/addresses/${addressId}/validate`,
        { isValid: false }
      );
    } catch (error) {
      console.warn('Warning: Could not set address as not validated:', error);
    }
  }
  this.setTestData('addressValidated', false);
});

Given(
  'my profile has been updated with phone {string} {string}',
  async function (this: CustomWorld, countryCode: string, phoneNumber: string) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');

    await this.customerApiClient.patch(
      `/api/v1/customers/${customerId}/profile`,
      {
        phone: { countryCode, number: phoneNumber },
      },
      { headers: { 'X-User-Id': userId! } }
    );
  }
);

When('I re-authenticate', async function (this: CustomWorld) {
  // Re-authentication would typically involve refreshing auth tokens
  // For testing purposes, we just verify the auth context is still valid
  const userId = this.getTestData<string>('userId');
  const customerId = this.getTestData<string>('customerId');
  expect(userId).toBeDefined();
  expect(customerId).toBeDefined();
});

Given('I add a shipping address:', async function (this: CustomWorld, dataTable: DataTable) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');
  const rows = dataTable.rowsHash();

  try {
    const response = await this.customerApiClient.post<{ addressId: string }>(
      `/api/v1/customers/${customerId}/addresses`,
      {
        type: 'SHIPPING',
        street: {
          line1: rows.streetLine1,
          line2: rows.streetLine2 || null,
        },
        city: rows.city,
        state: rows.state,
        postalCode: rows.postalCode,
        country: rows.country,
        isDefault: true,
      },
      { headers: { 'X-User-Id': userId! } }
    );

    if (response.data && response.data.addressId) {
      this.setTestData('lastAddressId', response.data.addressId);
      this.setTestData('lastAddressResponse', response.data);
    } else {
      console.warn(
        'Warning: Address created but no addressId in response:',
        JSON.stringify(response.data)
      );
    }
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'response' in error && error.response) {
      const err = error as { response: { status: number; data: unknown } };
      console.warn(
        'Warning: Could not add shipping address. Status:',
        err.response.status,
        'Data:',
        JSON.stringify(err.response.data)
      );
    } else {
      console.warn('Warning: Could not add shipping address:', error);
    }
  }
});

When('I update my profile with:', async function (this: CustomWorld, dataTable: DataTable) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');
  const rows = dataTable.rowsHash();

  const updatePayload: Record<string, unknown> = {};

  if (rows.dateOfBirth) {
    updatePayload.dateOfBirth = rows.dateOfBirth;
  }
  if (rows.gender) {
    updatePayload.gender = rows.gender;
  }
  if (rows.preferredLocale) {
    updatePayload.preferredLocale = rows.preferredLocale;
  }
  if (rows.timezone) {
    updatePayload.timezone = rows.timezone;
  }

  try {
    const response = await this.customerApiClient.patch(
      `/api/v1/customers/${customerId}/profile`,
      updatePayload,
      { headers: { 'X-User-Id': userId! } }
    );

    this.setTestData('lastResponseStatus', response.status);
    this.setTestData('lastResponseData', response.data);
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'response' in error && error.response) {
      const err = error as {
        response: { status: number; data: ErrorResponse };
      };
      this.setTestData('lastResponseStatus', err.response.status);
      this.setTestData('lastResponseData', err.response.data);
    } else {
      throw error;
    }
  }
});

When(
  'I update my profile with phone {string} {string}',
  async function (this: CustomWorld, countryCode: string, phoneNumber: string) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');

    try {
      const response = await this.customerApiClient.patch(
        `/api/v1/customers/${customerId}/profile`,
        {
          phone: { countryCode, number: phoneNumber },
        },
        { headers: { 'X-User-Id': userId! } }
      );

      this.setTestData('lastResponseStatus', response.status);
      this.setTestData('lastResponseData', response.data);
    } catch (error: unknown) {
      if (error && typeof error === 'object' && 'response' in error && error.response) {
        const err = error as {
          response: { status: number; data: ErrorResponse };
        };
        this.setTestData('lastResponseStatus', err.response.status);
        this.setTestData('lastResponseData', err.response.data);
      } else {
        throw error;
      }
    }
  }
);

When('the profile is not 100% complete', async function (this: CustomWorld) {
  const data = this.getTestData<ProfileCompletenessResponse>('profileCompletenessData');
  expect(data.overallScore).toBeLessThan(100);
});

Then(
  'the completeness response should include {string}',
  async function (this: CustomWorld, path: string) {
    const data = this.getTestData<unknown>('lastResponseData');
    const value = getNestedValue(data, path);
    expect(value).toBeDefined();
  }
);

Then(
  'the completeness response should not include {string}',
  async function (this: CustomWorld, path: string) {
    const data = this.getTestData<ProfileCompletenessResponse>('lastResponseData');

    if (path === 'nextAction') {
      expect(data.nextAction).toBeNull();
    } else {
      const value = getNestedValue(data, path);
      expect(value).toBeUndefined();
    }
  }
);

Then(
  'the completeness response should have {string} set to {string}',
  async function (this: CustomWorld, path: string, expectedValue: string) {
    const data = this.getTestData<unknown>('lastResponseData');
    const actualValue = getNestedValue(data, path);

    // Convert expected value for comparison
    let expected: unknown = expectedValue;
    if (expectedValue === 'true') expected = true;
    if (expectedValue === 'false') expected = false;
    if (/^\d+$/.test(expectedValue)) expected = parseInt(expectedValue, 10);

    expect(String(actualValue)).toBe(String(expected));
  }
);

Then(
  'the completeness response should have {string} greater than the previous score',
  async function (this: CustomWorld, path: string) {
    const data = this.getTestData<unknown>('lastResponseData');
    const actualValue = getNestedValue(data, path) as number;
    const previousScore = this.getTestData<number>('previousCompletenessScore');

    expect(actualValue).toBeGreaterThan(previousScore);
  }
);

Then(
  'the completeness response should have {string} less than {string}',
  async function (this: CustomWorld, path: string, threshold: string) {
    const data = this.getTestData<unknown>('lastResponseData');
    const actualValue = getNestedValue(data, path) as number;

    expect(actualValue).toBeLessThan(parseInt(threshold, 10));
  }
);

Then(
  'the completeness section {string} should have weight {int}',
  async function (this: CustomWorld, sectionName: string, expectedWeight: number) {
    const data = this.getTestData<ProfileCompletenessResponse>('lastResponseData');
    const section = data.sections.find((s) => s.name === sectionName);

    expect(section).toBeDefined();
    expect(section!.weight).toBe(expectedWeight);
  }
);

Then(
  'the completeness section {string} should have {string} set to {string}',
  async function (this: CustomWorld, sectionName: string, field: string, expectedValue: string) {
    const data = this.getTestData<ProfileCompletenessResponse>('lastResponseData');
    const section = data.sections.find((s) => s.name === sectionName);

    expect(section).toBeDefined();

    // Handle Jackson boolean property naming (isComplete may be serialized as "complete")
    let actualValue = section![field as keyof ProfileCompletenessSection];
    if (actualValue === undefined && field === 'isComplete') {
      // Fallback: Jackson might serialize "isComplete" as "complete"
      actualValue = (section as Record<string, unknown>)['complete'] as boolean;
    }

    // Convert expected value for comparison
    let expected: unknown = expectedValue;
    if (expectedValue === 'true') expected = true;
    if (expectedValue === 'false') expected = false;
    if (/^\d+$/.test(expectedValue)) expected = parseInt(expectedValue, 10);

    expect(String(actualValue)).toBe(String(expected));
  }
);

Then(
  'the completeness section {string} should include items:',
  async function (this: CustomWorld, sectionName: string, table: DataTable) {
    const data = this.getTestData<ProfileCompletenessResponse>('lastResponseData');
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
  'the {string} should include {string}',
  async function (this: CustomWorld, objectPath: string, field: string) {
    const data = this.getTestData<ProfileCompletenessResponse>('lastResponseData');
    const obj = getNestedValue(data, objectPath) as Record<string, unknown>;

    expect(obj).toBeDefined();
    expect(obj[field]).toBeDefined();
  }
);
