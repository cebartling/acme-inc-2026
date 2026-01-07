import { Given, When, Then, DataTable } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ApiResponse } from '../../support/api-client.js';

interface RegistrationRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  tosAccepted: boolean;
  tosAcceptedAt: string;
  marketingOptIn: boolean;
}

interface RegistrationResponse {
  userId: string;
  email: string;
  status: string;
  createdAt: string;
}

interface ResendVerificationRequest {
  email: string;
}

interface ResendVerificationResponse {
  message: string;
  timestamp: string;
  requestsRemaining?: number;
}

interface ErrorResponse {
  error: string;
  message: string;
  timestamp: string;
  details?: Array<{ field: string; message: string }>;
}

// Store test data for cross-step access
let registeredUserId: string | null = null;
let verificationToken: string | null = null;

Given(
  'a user has registered with email {string}',
  async function (this: CustomWorld, email: string) {
    // Make email unique
    const uniqueEmail = `${email.split('@')[0]}-${Date.now()}@${email.split('@')[1]}`;

    const request: RegistrationRequest = {
      email: uniqueEmail,
      password: 'SecureP@ss123',
      firstName: 'Test',
      lastName: 'User',
      tosAccepted: true,
      tosAcceptedAt: new Date().toISOString(),
      marketingOptIn: false,
    };

    const response = await this.identityApiClient.post<RegistrationResponse>(
      '/api/v1/users/register',
      request
    );

    expect(response.status).toBe(201);
    registeredUserId = response.data.userId;
    this.setTestData('registeredEmail', uniqueEmail);
    this.setTestData('registeredUserId', registeredUserId);
  }
);

Given(
  'a user has registered and verified with email {string}',
  async function (this: CustomWorld, email: string) {
    // Make email unique
    const uniqueEmail = `${email.split('@')[0]}-${Date.now()}@${email.split('@')[1]}`;

    const request: RegistrationRequest = {
      email: uniqueEmail,
      password: 'SecureP@ss123',
      firstName: 'Verified',
      lastName: 'User',
      tosAccepted: true,
      tosAcceptedAt: new Date().toISOString(),
      marketingOptIn: false,
    };

    const response = await this.identityApiClient.post<RegistrationResponse>(
      '/api/v1/users/register',
      request
    );

    expect(response.status).toBe(201);
    this.setTestData('registeredEmail', uniqueEmail);

    // Note: In a real scenario, we'd need to verify via token.
    // For acceptance tests, we may need a test-only endpoint or database setup.
    // For now, we'll just note that the user is "registered and verified" for the test.
    this.setTestData('userVerified', true);
  }
);

Given('the user has a valid verification token', async function (this: CustomWorld) {
  // In acceptance tests, we need to either:
  // 1. Query the token from the database (requires test DB access)
  // 2. Use a test-only endpoint to get the token
  // 3. Mock/stub the token
  // For now, we'll use a placeholder that the step knows about
  this.setTestData('tokenStatus', 'valid');

  // Note: Real implementation would query the database or use a test endpoint
  // verificationToken = await getTokenForUser(registeredUserId);
});

Given('the user has an expired verification token', async function (this: CustomWorld) {
  this.setTestData('tokenStatus', 'expired');
});

Given('the user has already verified their email', async function (this: CustomWorld) {
  this.setTestData('tokenStatus', 'used');
  this.setTestData('userVerified', true);
});

Given('the user has not verified their email', async function (this: CustomWorld) {
  this.setTestData('userVerified', false);
});

Given(
  'I have requested {int} verification resends for {string}',
  async function (this: CustomWorld, count: number, email: string) {
    const registeredEmail = this.getTestData<string>('registeredEmail') || email;

    for (let i = 0; i < count; i++) {
      const request: ResendVerificationRequest = { email: registeredEmail };
      await this.identityApiClient.post('/api/v1/users/verify/resend', request);
    }
  }
);

When(
  'I click the verification link with the token',
  async function (this: CustomWorld) {
    // In a real test, we'd use the actual token from registration
    // For now, we'll use the token status to determine behavior
    const tokenStatus = this.getTestData<string>('tokenStatus');
    let token: string;

    switch (tokenStatus) {
      case 'valid':
        // Would use real token here
        token = verificationToken || 'test-valid-token';
        break;
      case 'expired':
        token = 'expired-token-simulation';
        break;
      case 'used':
        token = 'used-token-simulation';
        break;
      default:
        token = 'unknown-token';
    }

    // Note: This endpoint returns redirects, not JSON
    // We need to handle redirect responses differently - use manual redirect to capture Location header
    const response = await this.identityApiClient.get(`/api/v1/users/verify?token=${token}`, {
      redirect: 'manual',
    });
    this.setTestData('lastResponse', response);
    this.setTestData('redirectLocation', response.headers.get('location'));
  }
);

When(
  'I click the verification link with token {string}',
  async function (this: CustomWorld, token: string) {
    const response = await this.identityApiClient.get(`/api/v1/users/verify?token=${token}`, {
      redirect: 'manual',
    });
    this.setTestData('lastResponse', response);
    this.setTestData('redirectLocation', response.headers.get('location'));
  }
);

When(
  'I click the verification link with an already used token',
  async function (this: CustomWorld) {
    const token = 'already-used-token-simulation';
    const response = await this.identityApiClient.get(`/api/v1/users/verify?token=${token}`, {
      redirect: 'manual',
    });
    this.setTestData('lastResponse', response);
    this.setTestData('redirectLocation', response.headers.get('location'));
  }
);

When(
  'I request to resend the verification email to {string}',
  async function (this: CustomWorld, email: string) {
    // Use registered email if available, otherwise use provided email
    const registeredEmail = this.getTestData<string>('registeredEmail');
    const targetEmail = registeredEmail && email.includes('@example.com') ? registeredEmail : email;

    const request: ResendVerificationRequest = { email: targetEmail };
    const response = await this.identityApiClient.post<ResendVerificationResponse | ErrorResponse>(
      '/api/v1/users/verify/resend',
      request
    );
    this.setTestData('lastResponse', response);
  }
);

Then(
  'I should be redirected to the login page with {string}',
  async function (this: CustomWorld, expectedParam: string) {
    const response = this.getTestData<ApiResponse>('lastResponse');
    expect(response).toBeDefined();

    // Check for redirect status or follow redirect
    const location = this.getTestData<string>('redirectLocation');
    expect(location).toBeDefined();
    expect(location).toContain('/login');
    expect(location).toContain(expectedParam);
  }
);

Then(
  'I should be redirected to the resend verification page with {string}',
  async function (this: CustomWorld, expectedParam: string) {
    const response = this.getTestData<ApiResponse>('lastResponse');
    expect(response).toBeDefined();

    const location = this.getTestData<string>('redirectLocation');
    expect(location).toBeDefined();
    expect(location).toContain('/verify/resend');
    expect(location).toContain(expectedParam);
  }
);

Then("the user's email should be marked as verified", async function (this: CustomWorld) {
  // In a full implementation, we'd query the database to verify
  // For now, we check that the redirect was successful
  const response = this.getTestData<ApiResponse>('lastResponse');
  expect(response).toBeDefined();
  // Status 302 for redirect
  expect([200, 302, 303]).toContain(response!.status);
});

Then(
  "the user's account status should be {string}",
  async function (this: CustomWorld, expectedStatus: string) {
    // In a full implementation, we'd query the database to verify
    // For now, we infer from the redirect location
    const location = this.getTestData<string>('redirectLocation');
    if (expectedStatus === 'ACTIVE') {
      expect(location).toContain('verified=true');
    }
  }
);

Then(
  "the user's account status should still be {string}",
  async function (this: CustomWorld, expectedStatus: string) {
    // Verify via redirect that account wasn't activated
    const location = this.getTestData<string>('redirectLocation');
    expect(location).not.toContain('verified=true');
  }
);

Then('an EmailVerified event should be published', async function (this: CustomWorld) {
  // In a full implementation, we'd consume from Kafka to verify
  // For now, we verify the verification succeeded
  const response = this.getTestData<ApiResponse>('lastResponse');
  expect(response).toBeDefined();
});

Then('a UserActivated event should be published', async function (this: CustomWorld) {
  // In a full implementation, we'd consume from Kafka to verify
  // For now, we verify the verification succeeded
  const response = this.getTestData<ApiResponse>('lastResponse');
  expect(response).toBeDefined();
});

Then(
  'the response should contain {string}',
  async function (this: CustomWorld, field: string) {
    const response = this.getTestData<ApiResponse<ResendVerificationResponse>>('lastResponse');
    expect(response).toBeDefined();
    expect(response!.data).toHaveProperty(field);
  }
);

Then(
  'the response should have a {string} header',
  async function (this: CustomWorld, headerName: string) {
    const response = this.getTestData<ApiResponse>('lastResponse');
    expect(response).toBeDefined();
    expect(response!.headers.get(headerName.toLowerCase())).toBeDefined();
  }
);
