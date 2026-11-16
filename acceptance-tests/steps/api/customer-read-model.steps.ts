import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { CustomWorld } from '../../support/world.js';
import { ApiResponse } from '../../support/api-client.js';
import { accessCookieFor } from '../../support/access-token.js';

const READ_AFTER = 'X-Read-After';
const KAFKA_CONTAINER = process.env.KAFKA_CONTAINER ?? 'acme-kafka';
/** Debezium reconnects and the projector catches up within seconds of Kafka coming back. */
const CATCH_UP_TIMEOUT_MS = 60_000;
const POLL_INTERVAL_MS = 1_000;

/** Reads a dotted path, such as "profile.timezone", from a response body. */
function valueAt(body: unknown, path: string): unknown {
  return path
    .split('.')
    .reduce<unknown>((value, key) => (value as Record<string, unknown> | undefined)?.[key], body);
}

async function getCustomerRecord(
  world: CustomWorld,
  headers: Record<string, string> = {}
): Promise<ApiResponse<unknown>> {
  const customerId = world.getTestData<string>('customerId');
  const userId = world.getTestData<string>('userId');
  return world.customerApiClient.get(`/api/v1/customers/${customerId}`, {
    headers: { Cookie: await accessCookieFor(world, userId!), ...headers },
  });
}

function remember(world: CustomWorld, response: ApiResponse<unknown>): void {
  world.setTestData('lastResponse', response);
  world.setTestData('lastResponseStatus', response.status);
  world.setTestData('lastResponseData', response.data);
}

function docker(...args: string[]): void {
  execFileSync('docker', args, { stdio: 'pipe' });
}

When('I request my customer record', async function (this: CustomWorld) {
  remember(this, await getCustomerRecord(this));
});

When(
  'I request my customer record asking to wait for an event that never arrives',
  async function (this: CustomWorld) {
    remember(this, await getCustomerRecord(this, { [READ_AFTER]: crypto.randomUUID() }));
  }
);

When(
  'I request my customer record sending {string} as {string}',
  async function (this: CustomWorld, header: string, value: string) {
    remember(this, await getCustomerRecord(this, { [header]: value }));
  }
);

Then(
  'the customer record should have {string} set to {string}',
  async function (this: CustomWorld, path: string, expected: string) {
    const data = this.getTestData<unknown>('lastResponseData');
    expect(String(valueAt(data, path))).toBe(expected);
  }
);

Then(
  'the response should have header {string} set to {string}',
  async function (this: CustomWorld, header: string, expected: string) {
    const response = this.getTestData<ApiResponse<unknown>>('lastResponse');
    expect(response?.headers[header.toLowerCase()]).toBe(expected);
  }
);

Given('Kafka is stopped', async function (this: CustomWorld) {
  docker('stop', KAFKA_CONTAINER);
});

When('Kafka is started again', async function (this: CustomWorld) {
  docker('start', KAFKA_CONTAINER);
});

Then(
  'my customer record should eventually have {string} set to {string}',
  { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
  async function (this: CustomWorld, path: string, expected: string) {
    const deadline = Date.now() + CATCH_UP_TIMEOUT_MS;
    let last = 'no response';
    while (Date.now() < deadline) {
      try {
        // Sends no X-Read-After, so it answers at once with whatever the read model holds now
        const response = await getCustomerRecord(this);
        last = `${response.status} ${String(valueAt(response.data, path))}`;
        if (response.status === 200 && String(valueAt(response.data, path)) === expected) {
          return;
        }
      } catch (error) {
        // The customer service may be reconnecting to Kafka; try again
        last = (error as Error).message;
      }
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
    }
    throw new Error(
      `${path} did not become "${expected}" within ${CATCH_UP_TIMEOUT_MS} ms (last seen: ${last})`
    );
  }
);
