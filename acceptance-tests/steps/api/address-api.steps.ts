import { Given, When, Then, DataTable } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ApiResponse } from '../../support/api-client.js';

interface AddAddressRequest {
  type: string;
  label?: string;
  street: {
    line1: string;
    line2?: string;
  };
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

interface AddressResponse {
  addressId: string;
  customerId: string;
  type: string;
  label?: string;
  street: {
    line1: string;
    line2?: string;
  };
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
  isValidated: boolean;
  coordinates?: {
    latitude: number;
    longitude: number;
  };
  createdAt: string;
}

interface ErrorResponse {
  error: string;
  message?: string;
  errors?: Record<string, string>;
}

// Note: 'the Customer Service is available' step is defined in customer-profile.steps.ts

Given('I have an authenticated customer', async function (this: CustomWorld) {
  // Create a test user and customer for authentication
  // Generate valid UUIDs for customer and user IDs
  const testCustomerId = crypto.randomUUID();
  const testUserId = crypto.randomUUID();

  this.setTestData('customerId', testCustomerId);
  this.setTestData('userId', testUserId);

  // Create the customer in the backend via test helper API
  try {
    await this.customerApiClient.post('/api/v1/test/customers', {
      customerId: testCustomerId,
      userId: testUserId,
      email: `test-${testCustomerId}@example.com`,
      firstName: 'Test',
      lastName: 'Customer',
      displayName: 'Test Customer',
      emailVerified: true,
    });
  } catch (error: unknown) {
    // Customer might already exist, that's okay
    if (
      error &&
      typeof error === 'object' &&
      'response' in error &&
      error.response
    ) {
      const err = error as { response: { status: number } };
      if (err.response.status !== 409) {
        console.warn('Warning: Could not create test customer:', err.response.status);
      }
    }
  }
});

Given(
  'I have a default shipping address labeled {string}',
  async function (this: CustomWorld, label: string) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');

    const request: AddAddressRequest = {
      type: 'SHIPPING',
      label,
      street: { line1: '100 Default Street' },
      city: 'Default City',
      state: 'DC',
      postalCode: '20001',
      country: 'US',
      isDefault: true,
    };

    const response = await this.customerApiClient.post<AddressResponse>(
      `/api/v1/customers/${customerId}/addresses`,
      request,
      { headers: { 'X-User-Id': userId! } }
    );

    this.setTestData(`address-${label}`, response.data);
  }
);

Given('I already have {int} shipping addresses', async function (this: CustomWorld, count: number) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  for (let i = 0; i < count; i++) {
    const request: AddAddressRequest = {
      type: 'SHIPPING',
      label: `Address ${i + 1}`,
      street: { line1: `${i + 1}00 Test Street` },
      city: 'Test City',
      state: 'TX',
      postalCode: `7500${i}`,
      country: 'US',
      isDefault: i === 0,
    };

    await this.customerApiClient.post<AddressResponse>(
      `/api/v1/customers/${customerId}/addresses`,
      request,
      { headers: { 'X-User-Id': userId! } }
    );
  }
});

Given(
  'I have an address labeled {string}',
  async function (this: CustomWorld, label: string) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');

    const request: AddAddressRequest = {
      type: 'SHIPPING',
      label,
      street: { line1: '200 Test Street' },
      city: 'Test City',
      state: 'TX',
      postalCode: '75001',
      country: 'US',
      isDefault: false,
    };

    const response = await this.customerApiClient.post<AddressResponse>(
      `/api/v1/customers/${customerId}/addresses`,
      request,
      { headers: { 'X-User-Id': userId! } }
    );

    this.setTestData(`address-${label}`, response.data);
  }
);

Given(
  'I have the following addresses:',
  async function (this: CustomWorld, dataTable: DataTable) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');
    const addresses = dataTable.hashes();

    for (const addr of addresses) {
      const request: AddAddressRequest = {
        type: addr.type,
        label: addr.label,
        street: { line1: `${Math.floor(Math.random() * 1000)} Test Street` },
        city: 'Test City',
        state: 'TX',
        postalCode: '75001',
        country: 'US',
        isDefault: addr.isDefault === 'true',
      };

      const response = await this.customerApiClient.post<AddressResponse>(
        `/api/v1/customers/${customerId}/addresses`,
        request,
        { headers: { 'X-User-Id': userId! } }
      );

      this.setTestData(`address-${addr.label}`, response.data);
    }
  }
);

Given(
  'another customer has an address with id {string}',
  async function (this: CustomWorld, addressId: string) {
    this.setTestData('otherCustomerAddressId', addressId);
  }
);

When(
  'I add a shipping address with:',
  async function (this: CustomWorld, dataTable: DataTable) {
    await addAddress(this, 'SHIPPING', dataTable);
  }
);

When(
  'I add a billing address with:',
  async function (this: CustomWorld, dataTable: DataTable) {
    await addAddress(this, 'BILLING', dataTable);
  }
);

async function addAddress(
  world: CustomWorld,
  type: string,
  dataTable: DataTable
): Promise<void> {
  const customerId = world.getTestData<string>('customerId');
  const userId = world.getTestData<string>('userId');
  const data = dataTable.rowsHash();

  const request: AddAddressRequest = {
    type,
    label: data.label || undefined,
    street: {
      line1: data.streetLine1,
      line2: data.streetLine2 || undefined,
    },
    city: data.city,
    state: data.state,
    postalCode: data.postalCode,
    country: data.country,
    isDefault: data.isDefault === 'true',
  };

  const response = await world.customerApiClient.post<AddressResponse | ErrorResponse>(
    `/api/v1/customers/${customerId}/addresses`,
    request,
    { headers: { 'X-User-Id': userId! } }
  );

  world.setTestData('lastResponse', response);
  world.setTestData('lastRequest', request);
}

When(
  'I update the {string} address with:',
  async function (this: CustomWorld, label: string, dataTable: DataTable) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');
    const address = this.getTestData<AddressResponse>(`address-${label}`);
    const data = dataTable.rowsHash();

    const updateRequest: Partial<AddAddressRequest> = {};
    if (data.city) updateRequest.city = data.city;
    if (data.state) updateRequest.state = data.state;
    if (data.postalCode) updateRequest.postalCode = data.postalCode;
    if (data.streetLine1) updateRequest.street = { line1: data.streetLine1 };

    const response = await this.customerApiClient.put<AddressResponse | ErrorResponse>(
      `/api/v1/customers/${customerId}/addresses/${address!.addressId}`,
      updateRequest,
      { headers: { 'X-User-Id': userId! } }
    );

    this.setTestData('lastResponse', response);
  }
);

When(
  'I update the {string} address with no changes',
  async function (this: CustomWorld, label: string) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');
    const address = this.getTestData<AddressResponse>(`address-${label}`);

    const response = await this.customerApiClient.put<AddressResponse | ErrorResponse>(
      `/api/v1/customers/${customerId}/addresses/${address!.addressId}`,
      {},
      { headers: { 'X-User-Id': userId! } }
    );

    this.setTestData('lastResponse', response);
  }
);

When('I delete the {string} address', async function (this: CustomWorld, label: string) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');
  const address = this.getTestData<AddressResponse>(`address-${label}`);

  const response = await this.customerApiClient.delete<void>(
    `/api/v1/customers/${customerId}/addresses/${address!.addressId}`,
    { headers: { 'X-User-Id': userId! } }
  );

  this.setTestData('lastResponse', response);
  this.setTestData('deletedAddressLabel', label);
});

When('I request all my addresses', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  const response = await this.customerApiClient.get<AddressResponse[]>(
    `/api/v1/customers/${customerId}/addresses`,
    { headers: { 'X-User-Id': userId! } }
  );

  this.setTestData('lastResponse', response);
});

When(
  'I request my addresses filtered by type {string}',
  async function (this: CustomWorld, type: string) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');

    const response = await this.customerApiClient.get<AddressResponse[]>(
      `/api/v1/customers/${customerId}/addresses?type=${type}`,
      { headers: { 'X-User-Id': userId! } }
    );

    this.setTestData('lastResponse', response);
  }
);

When('I request my default shipping address', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  const response = await this.customerApiClient.get<AddressResponse>(
    `/api/v1/customers/${customerId}/addresses/default/SHIPPING`,
    { headers: { 'X-User-Id': userId! } }
  );

  this.setTestData('lastResponse', response);
});

When('I try to access that address', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');
  const otherAddressId = this.getTestData<string>('otherCustomerAddressId');

  const response = await this.customerApiClient.get<AddressResponse | ErrorResponse>(
    `/api/v1/customers/${customerId}/addresses/${otherAddressId}`,
    { headers: { 'X-User-Id': userId! } }
  );

  this.setTestData('lastResponse', response);
});

Then(
  'the new address should be the default shipping address',
  async function (this: CustomWorld) {
    const response = this.getTestData<ApiResponse<AddressResponse>>('lastResponse');
    expect(response).toBeDefined();
    expect(response!.data.isDefault).toBe(true);
  }
);

Then(
  'the previous {string} address should no longer be default',
  async function (this: CustomWorld, label: string) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');
    const originalAddress = this.getTestData<AddressResponse>(`address-${label}`);

    const response = await this.customerApiClient.get<AddressResponse>(
      `/api/v1/customers/${customerId}/addresses/${originalAddress!.addressId}`,
      { headers: { 'X-User-Id': userId! } }
    );

    expect(response.data.isDefault).toBe(false);
  }
);

Then(
  'the {string} address should no longer exist',
  async function (this: CustomWorld, label: string) {
    const customerId = this.getTestData<string>('customerId');
    const userId = this.getTestData<string>('userId');
    const address = this.getTestData<AddressResponse>(`address-${label}`);

    const response = await this.customerApiClient.get<AddressResponse | ErrorResponse>(
      `/api/v1/customers/${customerId}/addresses/${address!.addressId}`,
      { headers: { 'X-User-Id': userId! } }
    );

    expect(response.status).toBe(404);
  }
);

Then(
  'the response should contain {int} addresses',
  async function (this: CustomWorld, count: number) {
    const response = this.getTestData<ApiResponse<AddressResponse[]>>('lastResponse');
    expect(response).toBeDefined();
    expect(response!.data).toHaveLength(count);
  }
);

Then(
  'all addresses should have type {string}',
  async function (this: CustomWorld, type: string) {
    const response = this.getTestData<ApiResponse<AddressResponse[]>>('lastResponse');
    expect(response).toBeDefined();
    for (const address of response!.data) {
      expect(address.type).toBe(type);
    }
  }
);

// Note: 'the response should contain {string} with value {string}' step is defined in registration-api.steps.ts
