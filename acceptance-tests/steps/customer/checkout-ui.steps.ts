import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { config } from '../../playwright.config.js';

/**
 * Step definitions for checkout in the customer app (PIN-426). Signing in, adding to the cart
 * and the cart badge reuse steps from customer-profile-loading, add-to-cart-ui and navigation.
 */

const isPlaceOrder = (method: string, url: string) =>
  method === 'POST' && new URL(url).pathname === '/api/v1/orders';

When('I press Checkout', async function (this: CustomWorld) {
  const checkout = this.page.getByTestId('checkout');
  await checkout.waitFor({ timeout: config.timeout.action });

  // A guest goes to sign-in without starting a checkout, so whichever comes first decides
  const started = this.page
    .waitForResponse(
      (r) => r.request().method() === 'POST' && /\/api\/v1\/carts\/[^/]+\/checkout$/.test(r.url()),
      { timeout: config.timeout.action }
    )
    .catch(() => null);
  const toSignin = this.page
    .waitForURL(/\/signin/, { timeout: config.timeout.action })
    .then(() => null)
    .catch(() => null);
  await checkout.click();
  const response = await Promise.race([started, toSignin]);
  if (response?.ok()) {
    const body = (await response.json()) as { checkoutSessionId: string };
    this.setTestData('checkoutSessionId', body.checkoutSessionId);
  }
});

Then('I should be asked to sign in and come back to the cart', async function (this: CustomWorld) {
  await this.page.waitForURL(/\/signin/, { timeout: config.timeout.action });
  const url = new URL(this.page.url());
  expect(url.searchParams.get('redirect')).toBe('/cart');
});

Then(
  'I should see {string} on the order review',
  async function (this: CustomWorld, productName: string) {
    await this.page.waitForURL(/\/checkout$/, { timeout: config.timeout.action });
    await expect(this.page.getByRole('heading', { name: 'Review your order' })).toBeVisible();
    await expect(this.page.getByText(productName)).toBeVisible();
    await expect(this.page.getByTestId('placeOrder')).toBeEnabled();
  }
);

When('my checkout session lapses', async function (this: CustomWorld) {
  const sessionId = this.getTestData<string>('checkoutSessionId');
  expect(sessionId, 'press Checkout as a signed-in customer first').toBeDefined();

  const response = await this.cartApiClient.post(
    `/api/v1/test/checkout-sessions/${sessionId}/lapse`
  );
  expect(response.status, 'the cart must run with the test profile').toBe(204);
});

When('I place the order', async function (this: CustomWorld) {
  const sent: string[] = [];
  this.page.on('request', (request) => {
    if (isPlaceOrder(request.method(), request.url())) sent.push(request.url());
  });
  this.setTestData('placeOrderRequests', sent);
  await this.page.getByTestId('placeOrder').click();
});

Then(
  'I should see the order confirmation with an order number',
  async function (this: CustomWorld) {
    await this.page.waitForURL(/\/orders\/[^/]+\/confirmation$/, {
      timeout: config.timeout.action,
    });
    await expect(this.page.getByTestId('orderNumber')).toHaveText(/^ACME-\d{8}-\d{6}$/);
  }
);

When('I reload the confirmation page', async function (this: CustomWorld) {
  await this.page.reload();
});

Then('only one order request should have been sent', async function (this: CustomWorld) {
  const sent = this.getTestData<string[]>('placeOrderRequests');
  expect(sent).toHaveLength(1);
});

When('I go back to my cart from the review', async function (this: CustomWorld) {
  await this.page.getByTestId('backToCart').click();
  await this.page.waitForURL(/\/cart$/, { timeout: config.timeout.action });
});

Then('I should be told my checkout session expired', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('checkoutExpired')).toContainText(
    'Your checkout session expired'
  );
});
