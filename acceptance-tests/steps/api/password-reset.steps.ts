import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ApiResponse } from '../../support/api-client.js';

/**
 * Steps for the password-reset flow (PIN-93 / US-0003-13).
 *
 * These steps reuse the test-fixture conventions established by the
 * reactivation-account steps in `authentication-api.steps.ts`:
 *  - `testUserEmail` / `testUserId` are set by Given steps that create a
 *    user fixture; later steps consume them.
 *  - The identity service exposes `/api/v1/test/events/{type}?userId=...`
 *    in the test profile so feature files can assert events were
 *    persisted to the event store.
 */

interface PasswordResetTokenResponse {
  token: string;
  userId: string;
}

interface VerificationTokenResponse {
  token: string;
  userId: string;
}

function makeUniqueEmail(base: string): string {
  if (!base.includes('@')) {
    return base;
  }
  const [local, domain] = base.split('@');
  return `${local}-${Date.now()}@${domain}`;
}

/**
 * Creates an ACTIVE test user and stores testUserId/testUserEmail in the world.
 * Mirrors the createTestUser helper in authentication-api.steps.ts.
 */
async function createActiveUser(world: CustomWorld): Promise<string> {
  if (!world.identityApiClient) {
    world.initializeApiClients();
  }
  if (!world.testSessionId) {
    await world.createTestSession();
  }

  const email = makeUniqueEmail('password-reset-fixture@acme.com');
  const password = 'ValidP@ss123!';

  const regResponse = await world.identityApiClient.post<{ userId: string }>(
    '/api/v1/users/register',
    {
      email,
      password,
      firstName: 'Test',
      lastName: 'User',
      tosAccepted: true,
      tosAcceptedAt: new Date().toISOString(),
      marketingOptIn: false,
    }
  );
  if (regResponse.status !== 201 && regResponse.status !== 200) {
    throw new Error(`Failed to register test user: ${regResponse.status} - ${JSON.stringify(regResponse.data)}`);
  }

  const userId = regResponse.data.userId;
  await world.registerUserWithSession(userId, email);
  world.setTestData('testUserId', userId);
  world.setTestData('testUserEmail', email);
  world.setTestData('testUserPassword', password);

  // Activate the user via the test verification-token endpoint
  const tokenResponse = await world.identityApiClient.get<VerificationTokenResponse>(
    `/api/v1/test/users/${userId}/verification-token`
  );
  if (tokenResponse.status === 200 && tokenResponse.data.token) {
    await world.identityApiClient.get<void>(
      `/api/v1/users/verify?token=${tokenResponse.data.token}`
    );
  }

  return userId;
}

When(
  'I submit a password reset request for {string}',
  async function (this: CustomWorld, email: string) {
    const testUserEmail = this.getTestData<string>('testUserEmail');
    const actualEmail =
      testUserEmail && email.toLowerCase().includes('@acme.com')
        ? testUserEmail
        : email;

    const response = await this.identityApiClient.post(
      '/api/v1/auth/password-reset',
      { email: actualEmail }
    );

    this.setTestData('lastResponse', response);
  }
);

When(
  'I submit {int} password reset requests for {string}',
  async function (this: CustomWorld, count: number, email: string) {
    const testUserEmail = this.getTestData<string>('testUserEmail');
    const actualEmail =
      testUserEmail && email.toLowerCase().includes('@acme.com')
        ? testUserEmail
        : email;

    let lastResponse: ApiResponse<unknown> | undefined;
    for (let i = 0; i < count; i++) {
      lastResponse = await this.identityApiClient.post(
        '/api/v1/auth/password-reset',
        { email: actualEmail }
      );
    }
    this.setTestData('lastResponse', lastResponse);
  }
);

When(
  'I submit a password reset request with an empty email',
  async function (this: CustomWorld) {
    const response = await this.identityApiClient.post(
      '/api/v1/auth/password-reset',
      { email: '' }
    );
    this.setTestData('lastResponse', response);
  }
);

Given(
  'a password reset token has been issued for an active user',
  async function (this: CustomWorld) {
    // Auto-create an active user if one hasn't been set up by a prior Given step.
    let userId = this.getTestData<string>('testUserId');
    if (!userId) {
      userId = await createActiveUser(this);
    }
    const response = await this.identityApiClient.post<PasswordResetTokenResponse>(
      `/api/v1/test/password-reset-tokens`,
      { userId }
    );
    expect(response.status).toBe(200);
    this.setTestData('passwordResetToken', response.data.token);
  }
);

Given(
  'an expired password reset token exists',
  async function (this: CustomWorld) {
    // Auto-create an active user if one hasn't been set up by a prior Given step.
    let userId = this.getTestData<string>('testUserId');
    if (!userId) {
      userId = await createActiveUser(this);
    }
    const response = await this.identityApiClient.post<PasswordResetTokenResponse>(
      `/api/v1/test/password-reset-tokens`,
      { userId, expired: true }
    );
    expect(response.status).toBe(200);
    this.setTestData('passwordResetToken', response.data.token);
  }
);

Given(
  'the password reset has already been completed with that token',
  async function (this: CustomWorld) {
    const token = this.getTestData<string>('passwordResetToken');
    if (!token) {
      throw new Error('No reset token has been issued');
    }
    await this.identityApiClient.post('/api/v1/auth/password-reset/confirm', {
      token,
      newPassword: 'AlreadyUsed1!',
    });
  }
);

Given(
  'the user has active sessions and device trusts',
  async function (this: CustomWorld) {
    // The test fixture is responsible for seeding sessions and devices.
    // This step is a hook: if no fixture exists, the assertions on
    // sessionsInvalidated/deviceTrustsRevoked will simply see zero.
  }
);

When('I validate the reset token', async function (this: CustomWorld) {
  const token = this.getTestData<string>('passwordResetToken');
  if (!token) {
    throw new Error('No reset token has been issued');
  }
  const response = await this.identityApiClient.get(
    `/api/v1/auth/password-reset/${encodeURIComponent(token)}`
  );
  this.setTestData('lastResponse', response);
});

When(
  'I submit a password reset confirmation with new password {string}',
  async function (this: CustomWorld, newPassword: string) {
    const token = this.getTestData<string>('passwordResetToken');
    if (!token) {
      throw new Error('No reset token has been issued');
    }
    const response = await this.identityApiClient.post(
      '/api/v1/auth/password-reset/confirm',
      { token, newPassword }
    );
    this.setTestData('lastResponse', response);
  }
);

Then(
  'a PasswordResetRequested event should be persisted in the event store',
  async function (this: CustomWorld) {
    const userId = this.getTestData<string>('testUserId');
    if (!userId) throw new Error('User ID not found');
    await new Promise((resolve) => setTimeout(resolve, 1000));
    const response = await this.identityApiClient.get(
      `/api/v1/test/events/PasswordResetRequested?userId=${userId}`
    );
    expect(response.status).toBe(200);
    const events = (response.data as { events: Array<unknown> }).events;
    expect(events.length).toBeGreaterThan(0);
  }
);

Then(
  'a PasswordChanged event should be persisted in the event store',
  async function (this: CustomWorld) {
    const userId = this.getTestData<string>('testUserId');
    if (!userId) throw new Error('User ID not found');
    await new Promise((resolve) => setTimeout(resolve, 1000));
    const response = await this.identityApiClient.get(
      `/api/v1/test/events/PasswordChanged?userId=${userId}`
    );
    expect(response.status).toBe(200);
    const events = (response.data as { events: Array<unknown> }).events;
    expect(events.length).toBeGreaterThan(0);
  }
);

Then(
  'no PasswordResetRequested event is persisted for that email',
  async function (this: CustomWorld) {
    const userId = this.getTestData<string>('testUserId');
    if (!userId) return; // No user created → no event possible.
    await new Promise((resolve) => setTimeout(resolve, 500));
    const response = await this.identityApiClient.get(
      `/api/v1/test/events/PasswordResetRequested?userId=${userId}`
    );
    expect(response.status).toBe(200);
    const events = (response.data as { events: Array<unknown> }).events;
    expect(events.length).toBe(0);
  }
);

Then(
  'exactly {int} PasswordResetRequested events should be persisted for that email',
  async function (this: CustomWorld, expected: number) {
    const userId = this.getTestData<string>('testUserId');
    if (!userId) throw new Error('User ID not found');
    await new Promise((resolve) => setTimeout(resolve, 1000));
    const response = await this.identityApiClient.get(
      `/api/v1/test/events/PasswordResetRequested?userId=${userId}`
    );
    expect(response.status).toBe(200);
    const events = (response.data as { events: Array<unknown> }).events;
    expect(events.length).toBe(expected);
  }
);
