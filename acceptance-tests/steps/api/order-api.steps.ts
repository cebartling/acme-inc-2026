import { randomUUID } from 'node:crypto';
import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

// --- PIN-366: place an order from a checkout session -----------------------------------

interface PlaceOrderResponse {
  orderId: string;
  orderNumber: string;
  status: string;
  createdAt: string;
  summary: { subtotal: { amount: number; currency: string } };
}

/** The signed-in customer's `access_token` cookie, from `I am signed in through the API...`. */
function accessTokenCookie(world: CustomWorld): Record<string, string> {
  const accessToken = world.getTestData<string>('cartAccessToken');
  expect(accessToken, 'sign in through the API first').toBeDefined();
  return { Cookie: `access_token=${accessToken}` };
}

Given('I have started checkout on my account cart', async function (this: CustomWorld) {
  const current = await this.cartApiClient.get<{ id: string }>('/api/v1/carts/current', {
    headers: accessTokenCookie(this),
  });
  expect(current.status, 'the account cart has items').toBe(200);

  const checkout = await this.cartApiClient.post<{ checkoutSessionId: string }>(
    `/api/v1/carts/${current.data.id}/checkout`,
    undefined,
    { headers: accessTokenCookie(this) }
  );
  expect(checkout.status, 'setup checkout').toBe(200);
  this.setTestData('checkoutSessionId', checkout.data.checkoutSessionId);
});

async function placeOrder(world: CustomWorld, headers: Record<string, string>) {
  const response = await world.orderApiClient.post<PlaceOrderResponse>(
    '/api/v1/orders',
    { checkoutSessionId: world.getTestData<string>('checkoutSessionId') },
    { headers }
  );
  world.setLastResponse(response);
  return response;
}

When('I place an order for my checkout session', async function (this: CustomWorld) {
  const idempotencyKey = randomUUID();
  this.setTestData('idempotencyKey', idempotencyKey);
  const response = await placeOrder(this, {
    ...accessTokenCookie(this),
    'Idempotency-Key': idempotencyKey,
  });
  if (response.status === 201) this.setTestData('orderId', response.data.orderId);
});

When('I place the order again with the same idempotency key', async function (this: CustomWorld) {
  const idempotencyKey = this.getTestData<string>('idempotencyKey');
  expect(idempotencyKey, 'place the order first').toBeDefined();
  await placeOrder(this, { ...accessTokenCookie(this), 'Idempotency-Key': idempotencyKey! });
});

When('I place an order without signing in', async function (this: CustomWorld) {
  this.setTestData('checkoutSessionId', randomUUID());
  await placeOrder(this, { 'Idempotency-Key': randomUUID() });
});

Then(
  'the order should be submitted with an order number for today',
  async function (this: CustomWorld) {
    const order = this.getLastResponse<PlaceOrderResponse>()!.data;
    const today = new Date().toISOString().slice(0, 10).replaceAll('-', '');
    expect(order.status).toBe('SUBMITTED');
    expect(order.orderNumber).toMatch(new RegExp(`^ACME-${today}-\\d{6}$`));
    expect(order.orderId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-/);
    expect(order.summary.subtotal.currency).toBe('USD');
  }
);

Then('it should be the same order', async function (this: CustomWorld) {
  expect(this.getLastResponse<PlaceOrderResponse>()!.data.orderId).toBe(
    this.getTestData<string>('orderId')
  );
});

Then('my account cart should be converted into the order', async function (this: CustomWorld) {
  // A CONVERTED cart is no longer anyone's current cart
  const current = await this.cartApiClient.get('/api/v1/carts/current', {
    headers: accessTokenCookie(this),
  });
  expect(current.status).toBe(204);
});
