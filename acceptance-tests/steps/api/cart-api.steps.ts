import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ApiResponse } from '../../support/api-client.js';

/**
 * Step definitions for the shopping cart API (US-0004-06).
 *
 * The cart is keyed by the service-issued `acme_session_id` cookie. These steps carry it
 * between requests by hand, the way a browser would.
 */

const SESSION_COOKIE = 'acme_session_id';

/** Seeded product-service variants (product V4/V5 migrations), by "Product / Variant". */
const VARIANTS: Record<string, { variantId: string; productId: string; sku: string }> = {
  'Gadget Pro / Black': {
    variantId: '11111111-1111-1111-1111-000000000001',
    productId: '11111111-1111-1111-1111-111111111111',
    sku: 'ACME-GP-BLK',
  },
};

interface CartResponse {
  id: string;
  items: { variantId: string; quantity: number; unitPrice: number; lineTotal: number }[];
  summary: { itemCount: number; subtotal: number; currency: string };
}

function addToCartBody(name: string, quantity: number) {
  const variant = VARIANTS[name];
  if (!variant) {
    throw new Error(`Unknown test variant "${name}"; add it to VARIANTS in cart-api.steps.ts`);
  }
  const [productName, variantName] = name.split(' / ');
  return {
    variantId: variant.variantId,
    quantity,
    productSnapshot: {
      productId: variant.productId,
      name: productName,
      sku: variant.sku,
      variantName,
      imageUrl: null,
      attributes: { color: variantName },
    },
  };
}

function sessionCookieFrom(response: ApiResponse<unknown>): string | undefined {
  return response.headers['set-cookie']?.find((c) => c.startsWith(`${SESSION_COOKIE}=`));
}

async function addItem(world: CustomWorld, body: unknown, withSession: boolean) {
  const sessionId = world.getTestData<string>('cartSessionId');
  const headers: Record<string, string> =
    withSession && sessionId ? { Cookie: `${SESSION_COOKIE}=${sessionId}` } : {};
  const response = await world.cartApiClient.post<CartResponse>('/api/v1/carts/items', body, {
    headers,
  });

  const cookie = sessionCookieFrom(response);
  if (cookie) {
    world.setTestData('cartSessionId', cookie.split(';')[0].split('=')[1]);
  }
  world.setTestData('lastCartVariant', (body as { variantId: string }).variantId);
  world.setLastResponse(response);
  return response;
}

When(
  'I add {int} {string} to a new cart',
  async function (this: CustomWorld, quantity: number, name: string) {
    await addItem(this, addToCartBody(name, quantity), false);
  }
);

Given(
  'I have added {int} {string} to a new cart',
  async function (this: CustomWorld, quantity: number, name: string) {
    const response = await addItem(this, addToCartBody(name, quantity), false);
    expect(response.status).toBe(201);
    this.setTestData('lastCartItemName', name);
  }
);

When(
  'I add {int} more of the same variant to my cart',
  async function (this: CustomWorld, quantity: number) {
    const name = this.getTestData<string>('lastCartItemName')!;
    await addItem(this, addToCartBody(name, quantity), true);
  }
);

When(
  'I add {int} of an unknown variant to a new cart',
  async function (this: CustomWorld, quantity: number) {
    const body = addToCartBody('Gadget Pro / Black', quantity);
    body.variantId = '00000000-0000-0000-0000-000000000000';
    await addItem(this, body, false);
  }
);

Then(
  'the response should set an HttpOnly SameSite=Lax session cookie',
  async function (this: CustomWorld) {
    const cookie = sessionCookieFrom(this.getLastResponse()!);
    expect(cookie, 'expected a Set-Cookie for acme_session_id').toBeDefined();
    expect(cookie).toContain('HttpOnly');
    expect(cookie).toContain('SameSite=Lax');
    expect(cookie).toContain('Max-Age=2592000');
  }
);

Then('the response should not set a session cookie', async function (this: CustomWorld) {
  expect(sessionCookieFrom(this.getLastResponse()!)).toBeUndefined();
});

Then(
  'the cart should have {int} line(s) with quantity {int} at unit price {float}',
  async function (this: CustomWorld, lines: number, quantity: number, unitPrice: number) {
    const cart = this.getLastResponse<CartResponse>()!.data;
    expect(cart.items).toHaveLength(lines);
    const line = cart.items.find((i) => i.variantId === this.getTestData('lastCartVariant'))!;
    expect(line.quantity).toBe(quantity);
    expect(line.unitPrice).toBeCloseTo(unitPrice, 2);
    expect(line.lineTotal).toBeCloseTo(unitPrice * quantity, 2);
  }
);

Then('the cart item count should be {int}', async function (this: CustomWorld, count: number) {
  expect(this.getLastResponse<CartResponse>()!.data.summary.itemCount).toBe(count);
});
