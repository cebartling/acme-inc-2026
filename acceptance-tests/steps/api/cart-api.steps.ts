import { randomUUID } from 'node:crypto';
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

/**
 * Seeded product-service variants (product V4/V5 migrations), by "Product / Variant".
 * Variant ids are fixed by the seed; productId only feeds the client-supplied snapshot,
 * which the cart service stores without checking, so a placeholder is enough.
 */
const VARIANTS: Record<string, { variantId: string; productId: string; sku: string }> = {
  'Gadget Pro / Black': {
    variantId: '11111111-1111-1111-1111-000000000001',
    productId: '11111111-1111-1111-1111-111111111111',
    sku: 'ACME-GP-BLK',
  },
  'Gadget Pro / White': {
    variantId: '11111111-1111-1111-1111-000000000002',
    productId: '11111111-1111-1111-1111-111111111111',
    sku: 'ACME-GP-WHT',
  },
};

interface CartResponse {
  id: string;
  items: {
    id: string;
    variantId: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
  }[];
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
  const variantId = (body as { variantId: string }).variantId;
  world.setTestData('lastCartVariant', variantId);
  rememberCartLine(world, response, variantId);
  world.setLastResponse(response);
  return response;
}

/** Keeps the cart and line ids so later steps can PATCH or DELETE that line. */
function rememberCartLine(world: CustomWorld, response: ApiResponse<unknown>, variantId: string) {
  if (response.status >= 300) return;
  const cart = response.data as CartResponse;
  const line = cart.items.find((i) => i.variantId === variantId);
  world.setTestData('cartId', cart.id);
  if (line) world.setTestData('cartItemId', line.id);
}

function sessionHeaders(sessionId: string | undefined): Record<string, string> {
  return sessionId ? { Cookie: `${SESSION_COOKIE}=${sessionId}` } : {};
}

function linePath(world: CustomWorld): string {
  return `/api/v1/carts/${world.getTestData<string>('cartId')}/items/${world.getTestData<string>('cartItemId')}`;
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
    // Later adds overwrite cartSessionId from Set-Cookie; this keeps the original to compare.
    this.setTestData('firstCartSessionId', this.getTestData<string>('cartSessionId'));
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

// US-0004-12: the guest cookie slides, so an active shopper's cart never expires under them.
Then('the response should re-issue the same session cookie', async function (this: CustomWorld) {
  const cookie = sessionCookieFrom(this.getLastResponse()!);
  expect(cookie, 'expected a Set-Cookie for acme_session_id').toBeDefined();
  expect(cookie!.split(';')[0]).toBe(
    `${SESSION_COOKIE}=${this.getTestData<string>('firstCartSessionId')}`
  );
  expect(cookie).toContain('HttpOnly');
  expect(cookie).toContain('SameSite=Lax');
  expect(cookie).toContain('Max-Age=2592000');
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

// --- US-0004-07: read, update and remove ---------------------------------------------

When('I get my current cart', async function (this: CustomWorld) {
  const sessionId = this.getTestData<string>('cartSessionId');
  const response = await this.cartApiClient.get<CartResponse>('/api/v1/carts/current', {
    headers: sessionHeaders(sessionId),
  });
  this.setLastResponse(response);
});

When('I get the current cart without a session cookie', async function (this: CustomWorld) {
  this.setLastResponse(await this.cartApiClient.get('/api/v1/carts/current'));
});

When(
  'I change the quantity of that line to {int}',
  async function (this: CustomWorld, quantity: number) {
    const response = await this.cartApiClient.patch<CartResponse>(
      linePath(this),
      { quantity },
      { headers: sessionHeaders(this.getTestData<string>('cartSessionId')) }
    );
    this.setLastResponse(response);
  }
);

When('I remove that line', async function (this: CustomWorld) {
  const response = await this.cartApiClient.delete<CartResponse>(linePath(this), {
    headers: sessionHeaders(this.getTestData<string>('cartSessionId')),
  });
  this.setLastResponse(response);
});

// A different, valid session ID: the cart and line exist, but they are not this session's.
When(
  'another session changes the quantity of that line to {int}',
  async function (this: CustomWorld, quantity: number) {
    const response = await this.cartApiClient.patch(
      linePath(this),
      { quantity },
      { headers: sessionHeaders(randomUUID()) }
    );
    this.setLastResponse(response);
  }
);

When('another session removes that line', async function (this: CustomWorld) {
  const response = await this.cartApiClient.delete(linePath(this), {
    headers: sessionHeaders(randomUUID()),
  });
  this.setLastResponse(response);
});

Then(
  'the response should include a max quantity of {int}',
  async function (this: CustomWorld, max: number) {
    const data = this.getLastResponse<{ maxQuantity?: number }>()!.data;
    expect(data.maxQuantity).toBe(max);
  }
);

Then('the cart should be empty', async function (this: CustomWorld) {
  const cart = this.getLastResponse<CartResponse>()!.data;
  expect(cart.items).toHaveLength(0);
  expect(cart.summary.itemCount).toBe(0);
});

// --- US-0004-08: merge on sign-in ------------------------------------------------------

const ACCESS_TOKEN_COOKIE = 'access_token';
/** The password `an active customer with email {string} exists` registers with. */
const REGISTERED_PASSWORD = 'SecureP@ss123';

interface MergeResponse extends CartResponse {
  mergeResult: { itemsMerged: number; quantitiesAdjusted: unknown[] } | null;
}

/** A `Cookie` header carrying the guest session and/or the signed-in access token. */
function cartCookies(
  world: CustomWorld,
  { session, token }: { session: boolean; token: boolean }
): Record<string, string> {
  const parts: string[] = [];
  const sessionId = world.getTestData<string>('cartSessionId');
  const accessToken = world.getTestData<string>('cartAccessToken');
  if (session && !sessionId)
    throw new Error('no guest session yet; add an item to a new cart first');
  if (token && !accessToken) throw new Error('no access token yet; sign in through the API first');
  if (session) parts.push(`${SESSION_COOKIE}=${sessionId}`);
  if (token) parts.push(`${ACCESS_TOKEN_COOKIE}=${accessToken}`);
  return parts.length > 0 ? { Cookie: parts.join('; ') } : {};
}

Given('I am signed in through the API as that customer', async function (this: CustomWorld) {
  const email = this.getTestData<string>('registeredEmail');
  expect(email, 'register the customer first').toBeDefined();

  const response = await this.identityApiClient.post('/api/v1/auth/signin', {
    email,
    password: REGISTERED_PASSWORD,
    rememberMe: false,
  });
  expect(response.status).toBe(200);

  const cookie = response.headers['set-cookie']?.find((c) =>
    c.startsWith(`${ACCESS_TOKEN_COOKIE}=`)
  );
  expect(cookie, 'sign-in should set an access_token cookie').toBeDefined();
  this.setTestData('cartAccessToken', cookie!.split(';')[0].split('=')[1]);
});

// A signed-in add with no session cookie: it can only land in the account cart. It does not
// update cartId/cartItemId, which name the guest cart's line for `I remove that line`.
Given(
  "the customer's account cart has {int} {string}",
  async function (this: CustomWorld, quantity: number, name: string) {
    const response = await this.cartApiClient.post<CartResponse>(
      '/api/v1/carts/items',
      addToCartBody(name, quantity),
      { headers: cartCookies(this, { session: false, token: true }) }
    );
    expect(response.status).toBe(201);
    expect(sessionCookieFrom(response), 'a signed-in add mints no guest session').toBeUndefined();
  }
);

When('I merge my guest cart as the signed-in customer', async function (this: CustomWorld) {
  const response = await this.cartApiClient.post<MergeResponse>('/api/v1/carts/merge', undefined, {
    headers: cartCookies(this, { session: true, token: true }),
  });
  this.setLastResponse(response);
});

When('I merge without signing in', async function (this: CustomWorld) {
  const response = await this.cartApiClient.post('/api/v1/carts/merge', undefined, {
    headers: cartCookies(this, { session: true, token: false }),
  });
  this.setLastResponse(response);
});

When(
  'I get my current cart as the signed-in customer on another device',
  async function (this: CustomWorld) {
    const response = await this.cartApiClient.get<CartResponse>('/api/v1/carts/current', {
      headers: cartCookies(this, { session: false, token: true }),
    });
    this.setLastResponse(response);
  }
);

When('I get my current cart as the old guest session', async function (this: CustomWorld) {
  const response = await this.cartApiClient.get('/api/v1/carts/current', {
    headers: cartCookies(this, { session: true, token: false }),
  });
  this.setLastResponse(response);
});

Then(
  'the merge should report {int} item(s) merged and {int} quantity adjustment(s)',
  async function (this: CustomWorld, merged: number, adjusted: number) {
    const response = this.getLastResponse<MergeResponse>()!;
    expect(response.status, 'a merge that moved items answers 200 with the cart').toBe(200);
    const result = response.data.mergeResult;
    expect(result, 'expected a mergeResult').not.toBeNull();
    expect(result!.itemsMerged).toBe(merged);
    expect(result!.quantitiesAdjusted).toHaveLength(adjusted);
  }
);

Then('the merge should report nothing merged', async function (this: CustomWorld) {
  const response = this.getLastResponse<MergeResponse>()!;
  // A 204 (no account cart at all) has no body to read a mergeResult from.
  expect(response.status, 'expected the account cart back with a 200').toBe(200);
  expect(response.data.mergeResult).toBeNull();
});

// Setup forms of the merge and remove steps: they assert success, so a failed setup is
// reported where it happened rather than as a confusing failure in a later step.
Given('I have merged my guest cart as the signed-in customer', async function (this: CustomWorld) {
  const response = await this.cartApiClient.post<MergeResponse>('/api/v1/carts/merge', undefined, {
    headers: cartCookies(this, { session: true, token: true }),
  });
  expect(response.status, 'setup merge').toBe(200);
  this.setLastResponse(response);
});

Given('I have removed that line', async function (this: CustomWorld) {
  const response = await this.cartApiClient.delete<CartResponse>(linePath(this), {
    headers: cartCookies(this, { session: true, token: false }),
  });
  expect(response.status, 'setup remove').toBe(200);
  this.setLastResponse(response);
});

Then(
  'the cart should contain {int} of {string}',
  async function (this: CustomWorld, quantity: number, name: string) {
    const cart = this.getLastResponse<CartResponse>()!.data;
    const variantId = VARIANTS[name]?.variantId;
    expect(variantId, `unknown test variant "${name}"`).toBeDefined();
    expect(cart.items.find((i) => i.variantId === variantId)?.quantity).toBe(quantity);
  }
);
