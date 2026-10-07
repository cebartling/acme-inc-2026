import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

const RESEND_ROUTE = '**/api/v1/users/verify/resend';

Given('a customer has registered but not verified their email', async function (this: CustomWorld) {
  if (!this.identityApiClient) {
    this.initializeApiClients();
  }
  if (!this.testSessionId) {
    await this.createTestSession();
  }

  const email = `verify-resend-ui-${Date.now()}@acme.com`;
  const regResponse = await this.identityApiClient.post<{ userId: string }>(
    '/api/v1/users/register',
    {
      email,
      password: 'ValidP@ss123!',
      firstName: 'Test',
      lastName: 'User',
      tosAccepted: true,
      tosAcceptedAt: new Date().toISOString(),
      marketingOptIn: false,
    }
  );
  if (regResponse.status !== 201 && regResponse.status !== 200) {
    throw new Error(
      `Failed to register test user: ${regResponse.status} - ${JSON.stringify(regResponse.data)}`
    );
  }

  await this.registerUserWithSession(regResponse.data.userId, email);
  this.setTestData('pendingUserEmail', email);
});

Given('the resend verification API is rate limited', async function (this: CustomWorld) {
  await this.page.route(RESEND_ROUTE, (route) =>
    route.fulfill({
      status: 429,
      contentType: 'application/json',
      headers: { 'Retry-After': '2520' },
      body: JSON.stringify({
        error: 'RATE_LIMIT_EXCEEDED',
        message: 'Too many requests. Please try again in 42 minutes.',
      }),
    })
  );
});

When(
  'I open the resend verification page with {string}',
  async function (this: CustomWorld, query: string) {
    await this.page.goto(`${this.getCustomerAppUrl()}/verify/resend?${query}`);
    // Let React hydrate, so submitting runs the page's handler, not a native form post
    await this.page.waitForLoadState('networkidle');
  }
);

When('I request a new verification link for that customer', async function (this: CustomWorld) {
  const email = this.getTestData<string>('pendingUserEmail');
  expect(email).toBeDefined();
  const response = await submitResendForm(this, email!);
  // A real resend, not just a page message: identity accepted the request.
  expect(response.status()).toBe(200);
});

When(
  'I request a new verification link for {string}',
  async function (this: CustomWorld, email: string) {
    await submitResendForm(this, email);
  }
);

Then(
  'I should see the resend link message {string}',
  async function (this: CustomWorld, message: string) {
    await expect(this.page.getByTestId('verify-resend-link-error')).toHaveText(message);
  }
);

Then('I should see the resend verification form', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('verify-resend-form')).toBeVisible();
  await expect(this.page.getByTestId('verify-resend-email-input')).toBeVisible();
});

Then(
  'I should see the resend confirmation {string}',
  async function (this: CustomWorld, snippet: string) {
    await expect(this.page.getByTestId('verify-resend-sent')).toContainText(snippet);
  }
);

Then(
  'the customer should have been sent {int} verification emails',
  async function (this: CustomWorld, expected: number) {
    const email = this.getTestData<string>('pendingUserEmail');
    expect(email).toBeDefined();
    // Registration sends one; each resend must send another, not be skipped (PIN-346)
    await expect
      .poll(
        async () => {
          const response = await this.notificationApiClient.get<{ notificationType: string }[]>(
            `/api/v1/notifications/by-email/${encodeURIComponent(email!)}`
          );
          if (response.status !== 200) return -1;
          return response.data.filter((n) => n.notificationType === 'EMAIL_VERIFICATION').length;
        },
        { timeout: 20000 }
      )
      .toBe(expected);
  }
);

Then('I should see the resend error {string}', async function (this: CustomWorld, message: string) {
  await expect(this.page.getByTestId('verify-resend-error')).toHaveText(message);
});

async function submitResendForm(world: CustomWorld, email: string) {
  await world.page.getByTestId('verify-resend-email-input').fill(email);
  const [response] = await Promise.all([
    world.page.waitForResponse(
      (r) => r.url().includes('/api/v1/users/verify/resend') && r.request().method() === 'POST'
    ),
    world.page.getByTestId('verify-resend-submit').click(),
  ]);
  return response;
}
