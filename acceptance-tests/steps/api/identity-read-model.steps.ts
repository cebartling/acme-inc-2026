import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { verifyEmail } from '../../support/verify-email.js';

const PASSWORD = 'SecureP@ss123';
/** Debezium picks the outbox row up and the projector applies it within seconds; Kafka coming back takes longer. */
const CATCH_UP_TIMEOUT_MS = 60_000;
const POLL_INTERVAL_MS = 1_000;

interface ReadModelUser {
  userId: string;
  email: string;
}

function readModelUser(world: CustomWorld): ReadModelUser {
  const user = world.getTestData<ReadModelUser>('readModelUser');
  expect(user, 'register the user for the read model first').toBeDefined();
  return user!;
}

async function register(world: CustomWorld): Promise<void> {
  const email = `read-model-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
  const response = await world.identityApiClient.post<{ userId: string }>(
    '/api/v1/users/register',
    {
      email,
      password: PASSWORD,
      firstName: 'Rhea',
      lastName: 'Model',
      tosAccepted: true,
      tosAcceptedAt: new Date().toISOString(),
      marketingOptIn: false,
    }
  );
  world.setTestData('lastResponse', response);
  world.setTestData('lastResponseStatus', response.status);
  world.setTestData('lastResponseData', response.data);
  if (response.status === 201) {
    world.setTestData<ReadModelUser>('readModelUser', { userId: response.data.userId, email });
  }
}

async function signIn(world: CustomWorld, password: string): Promise<number> {
  const response = await world.identityApiClient.post('/api/v1/auth/signin', {
    email: readModelUser(world).email,
    password,
    rememberMe: false,
  });
  return response.status;
}

async function readModel(
  world: CustomWorld
): Promise<{ status: number; data: Record<string, unknown> }> {
  const response = await world.identityApiClient.get<Record<string, unknown>>(
    `/api/v1/test/users/${readModelUser(world).userId}/read-model`
  );
  return { status: response.status, data: response.data };
}

Given('I have registered a user for the read model', async function (this: CustomWorld) {
  await register(this);
  expect(this.getTestData<number>('lastResponseStatus')).toBe(201);
});

When('I register a user for the read model', async function (this: CustomWorld) {
  await register(this);
});

Given(
  'I have registered and verified a user for the read model',
  async function (this: CustomWorld) {
    await register(this);
    expect(this.getTestData<number>('lastResponseStatus')).toBe(201);
    await verifyEmail(this.identityApiClient, readModelUser(this).userId);
  }
);

When('the read model user verifies their email', async function (this: CustomWorld) {
  await verifyEmail(this.identityApiClient, readModelUser(this).userId);
});

When(
  'the read model user fails to sign in {int} times',
  async function (this: CustomWorld, times: number) {
    for (let attempt = 1; attempt <= times; attempt++) {
      // The signin that reaches the limit locks the account, and answers 423 Locked
      const expected = attempt === times ? 423 : 401;
      expect(await signIn(this, 'WrongPassword123!')).toBe(expected);
    }
  }
);

Given("the read model user's lock expired a minute ago", async function (this: CustomWorld) {
  const response = await this.identityApiClient.post(
    `/api/v1/test/users/${readModelUser(this).userId}/status`,
    {
      status: 'LOCKED',
      lockedUntil: new Date(Date.now() - 60_000).toISOString(),
      failedAttempts: 5,
    }
  );
  expect(response.status).toBe(200);
});

When('the read model user signs in with the right password', async function (this: CustomWorld) {
  expect(await signIn(this, PASSWORD)).toBe(200);
});

Then(
  'the user read model should eventually show {string} as {string}',
  { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
  async function (this: CustomWorld, field: string, expected: string) {
    const deadline = Date.now() + CATCH_UP_TIMEOUT_MS;
    let last = 'no response';
    while (Date.now() < deadline) {
      try {
        const { status, data } = await readModel(this);
        const value = data?.[field];
        const actual = value === undefined || value === null ? 'none' : String(value);
        last = `${status} ${actual}`;
        if (status === 200 && actual === expected) return;
      } catch (error) {
        // identity may be reconnecting to Kafka or MongoDB; try again
        last = (error as Error).message;
      }
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
    }
    throw new Error(
      `${field} did not become "${expected}" in the read model within ${CATCH_UP_TIMEOUT_MS} ms (last seen: ${last})`
    );
  }
);

Then(
  'the user read model should not contain the password hash',
  async function (this: CustomWorld) {
    const { status, data } = await readModel(this);
    expect(status).toBe(200);
    const json = JSON.stringify(data);
    for (const secret of ['passwordHash', 'totpSecret', 'phoneNumber', '$argon2']) {
      expect(json, `the read model must not contain ${secret}`).not.toContain(secret);
    }
  }
);
