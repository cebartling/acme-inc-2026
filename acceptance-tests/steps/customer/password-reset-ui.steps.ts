import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

interface PasswordResetTokenResponse {
  token: string;
  userId: string;
}

interface VerificationTokenResponse {
  token: string;
  userId: string;
}

function makeUniqueEmail(base: string): string {
  if (!base.includes('@')) return base;
  const [local, domain] = base.split('@');
  return `${local}-${Date.now()}@${domain}`;
}

async function createActiveUser(world: CustomWorld): Promise<string> {
  if (!world.identityApiClient) {
    world.initializeApiClients();
  }
  if (!world.testSessionId) {
    await world.createTestSession();
  }

  const email = makeUniqueEmail('password-reset-ui-fixture@acme.com');
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
    },
  );
  if (regResponse.status !== 201 && regResponse.status !== 200) {
    throw new Error(
      `Failed to register test user: ${regResponse.status} - ${JSON.stringify(regResponse.data)}`,
    );
  }

  const userId = regResponse.data.userId;
  await world.registerUserWithSession(userId, email);
  world.setTestData('testUserId', userId);
  world.setTestData('testUserEmail', email);
  world.setTestData('testUserPassword', password);

  const tokenResponse = await world.identityApiClient.get<VerificationTokenResponse>(
    `/api/v1/test/users/${userId}/verification-token`,
  );
  if (tokenResponse.status === 200 && tokenResponse.data.token) {
    await world.identityApiClient.get<void>(
      `/api/v1/users/verify?token=${tokenResponse.data.token}`,
    );
  }

  return userId;
}

async function issueResetToken(
  world: CustomWorld,
  expired: boolean,
): Promise<string> {
  let userId = world.getTestData<string>('testUserId');
  if (!userId) {
    userId = await createActiveUser(world);
  }
  const response = await world.identityApiClient.post<PasswordResetTokenResponse>(
    '/api/v1/test/password-reset-tokens',
    { userId, expired },
  );
  if (response.status !== 200) {
    throw new Error(
      `Failed to issue reset token: ${response.status} - ${JSON.stringify(response.data)}`,
    );
  }
  world.setTestData('passwordResetToken', response.data.token);
  return response.data.token;
}

Given('the customer storefront is available', async function (this: CustomWorld) {
  const response = await this.page.goto(this.getCustomerAppUrl());
  if (!response || response.status() >= 500) {
    throw new Error('Customer storefront is not available');
  }
});

Then('I should be on the forgot-password page', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(/\/forgot-password/, { timeout: 10000 });
});

When(
  'I enter {string} in the email field',
  async function (this: CustomWorld, email: string) {
    await this.page.getByTestId('forgot-password-email-input').fill(email);
  },
);

When('I submit the forgot-password form', async function (this: CustomWorld) {
  await this.page.getByTestId('forgot-password-submit').click();
});

Then(
  'I should see a {string} confirmation',
  async function (this: CustomWorld, snippet: string) {
    // Matches both forgot-password "Check your inbox." and reset-password
    // "Password updated." confirmation panels.
    const forgot = this.page.getByTestId('forgot-password-sent');
    const success = this.page.getByTestId('reset-password-success');
    const visible = await Promise.race([
      forgot.waitFor({ state: 'visible', timeout: 10000 }).then(() => 'forgot'),
      success.waitFor({ state: 'visible', timeout: 10000 }).then(() => 'success'),
    ]).catch(() => null);
    if (!visible) {
      throw new Error('No confirmation panel became visible');
    }
    const banner = visible === 'forgot' ? forgot : success;
    await expect(banner).toContainText(snippet, { timeout: 5000 });
  },
);

Then(
  'the confirmation should mention that the link expires in 1 hour',
  async function (this: CustomWorld) {
    await expect(this.page.getByTestId('forgot-password-sent')).toContainText(
      /expires in 1 hour/i,
    );
  },
);

Given(
  'I am on the reset-password page with an expired token',
  async function (this: CustomWorld) {
    const token = await issueResetToken(this, true);
    await this.page.goto(
      `${this.getCustomerAppUrl()}/reset-password?token=${encodeURIComponent(token)}`,
    );
  },
);

Then(
  'I should see an {string} message',
  async function (this: CustomWorld, _description: string) {
    // The feature uses "expired link" — assert the expired panel renders.
    await expect(this.page.getByTestId('reset-password-expired')).toBeVisible({
      timeout: 10000,
    });
  },
);

Then(
  'I should see a link to request a new reset link',
  async function (this: CustomWorld) {
    const expiredPanel = this.page.getByTestId('reset-password-expired');
    const link = expiredPanel.getByRole('link', { name: /request a new link/i });
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('href', /\/forgot-password/);
  },
);

Given(
  'a valid password reset token exists for an active user',
  async function (this: CustomWorld) {
    await issueResetToken(this, false);
  },
);

Given(
  'I am on the reset-password page with that token',
  async function (this: CustomWorld) {
    const token = this.getTestData<string>('passwordResetToken');
    if (!token) {
      throw new Error('No reset token has been issued');
    }
    await this.page.goto(
      `${this.getCustomerAppUrl()}/reset-password?token=${encodeURIComponent(token)}`,
    );
    // Wait for the form (post token-validation) to render.
    await expect(this.page.getByTestId('reset-password-form')).toBeVisible({
      timeout: 10000,
    });
  },
);

When(
  'I enter {string} in the new password field',
  async function (this: CustomWorld, password: string) {
    await this.page.getByTestId('reset-password-new-input').fill(password);
  },
);

When(
  'I enter {string} in the confirm password field',
  async function (this: CustomWorld, password: string) {
    await this.page.getByTestId('reset-password-confirm-input').fill(password);
  },
);

When('I submit the reset-password form', async function (this: CustomWorld) {
  await this.page.getByTestId('reset-password-submit').click();
});

Then(
  'a {string} button should link to \\/signin',
  async function (this: CustomWorld, _label: string) {
    const link = this.page.getByTestId('reset-password-signin-now');
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('href', /\/signin/);
  },
);

Then(
  'I should be redirected to \\/signin within {int} seconds',
  async function (this: CustomWorld, seconds: number) {
    await expect(this.page).toHaveURL(/\/signin(?:$|\?|#|\/)/, {
      timeout: seconds * 1000 + 1000,
    });
  },
);

Then(
  'I should see an error indicating the passwords do not match',
  async function (this: CustomWorld) {
    await expect(this.page.getByTestId('reset-password-error')).toContainText(
      /do not match/i,
      { timeout: 5000 },
    );
  },
);

Then(
  'the reset-password form should still be visible',
  async function (this: CustomWorld) {
    await expect(this.page.getByTestId('reset-password-form')).toBeVisible();
  },
);

Then(
  'I should see the password requirements list',
  async function (this: CustomWorld) {
    await expect(
      this.page.getByTestId('reset-password-requirements'),
    ).toBeVisible({ timeout: 10000 });
  },
);

Then(
  'at least one requirement should be marked as unmet',
  async function (this: CustomWorld) {
    const list = this.page.getByTestId('reset-password-requirements');
    // Unmet items render with a leading "✗" glyph in the route component.
    await expect(list).toContainText('✗', { timeout: 5000 });
  },
);
