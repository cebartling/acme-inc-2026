import { When, Then } from '@cucumber/cucumber';
import { CustomWorld } from '../../support/world.js';

/**
 * Step definitions for the cart read model: the X-Read-After behaviour of
 * GET /api/v1/carts/current (PIN-551). The shared steps for adding, reading and the Kafka
 * outage live in cart-api.steps.ts and customer-read-model.steps.ts.
 */

const SESSION_COOKIE = 'acme_session_id';
const READ_AFTER = 'X-Read-After';
/** Debezium reconnects and the projector catches up within seconds of Kafka coming back. */
const CATCH_UP_TIMEOUT_MS = 60_000;
const POLL_INTERVAL_MS = 1_000;

function sessionHeaders(world: CustomWorld): Record<string, string> {
  const sessionId = world.getTestData<string>('cartSessionId');
  return sessionId ? { Cookie: `${SESSION_COOKIE}=${sessionId}` } : {};
}

async function getCurrentCart(world: CustomWorld, extra: Record<string, string> = {}) {
  return world.cartApiClient.get<{ summary?: { itemCount: number } }>('/api/v1/carts/current', {
    headers: { ...sessionHeaders(world), ...extra },
  });
}

When(
  'I get my current cart asking to wait for an event that never arrives',
  async function (this: CustomWorld) {
    this.setLastResponse(await getCurrentCart(this, { [READ_AFTER]: crypto.randomUUID() }));
  }
);

When(
  'I get my current cart sending {string} as {string}',
  async function (this: CustomWorld, header: string, value: string) {
    this.setLastResponse(await getCurrentCart(this, { [header]: value }));
  }
);

Then(
  'my cart should eventually have {int} items',
  { timeout: CATCH_UP_TIMEOUT_MS + 10_000 },
  async function (this: CustomWorld, count: number) {
    const deadline = Date.now() + CATCH_UP_TIMEOUT_MS;
    let last = 'no response';
    while (Date.now() < deadline) {
      try {
        // The earlier read used up the pending X-Read-After, so this one answers at once with
        // whatever the read model holds now
        const response = await getCurrentCart(this);
        last = `${response.status} ${response.data?.summary?.itemCount}`;
        if (response.status === 200 && response.data?.summary?.itemCount === count) return;
      } catch (error) {
        // The cart service may be reconnecting to Kafka; try again
        last = (error as Error).message;
      }
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
    }
    throw new Error(
      `the cart did not reach ${count} items within ${CATCH_UP_TIMEOUT_MS} ms (last seen: ${last})`
    );
  }
);
