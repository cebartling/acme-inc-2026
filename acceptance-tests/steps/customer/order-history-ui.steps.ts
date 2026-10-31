import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { config } from '../../playwright.config.js';
import { SigninPage } from '../../pages/customer/signin.page.js';

/**
 * Step definitions for the customer app's order history (PIN-428). Signing in and placing an
 * order reuse the steps from customer-profile-loading, add-to-cart-ui and checkout-ui.
 */

Then('I should be asked to sign in and come back to my orders', async function (this: CustomWorld) {
  await this.page.waitForURL(/\/signin/, { timeout: config.timeout.action });
  const url = new URL(this.page.url());
  expect(url.searchParams.get('redirect')).toBe('/orders');
});

Then('I should be told I have no orders', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('ordersEmpty')).toContainText(
    'You haven’t placed any orders yet'
  );
});

Then('the dashboard should link to my orders', async function (this: CustomWorld) {
  await expect(this.page.getByRole('link', { name: 'View your orders' })).toHaveAttribute(
    'href',
    /^\/orders(\?|$)/
  );
});

When('I open my orders from the user menu', async function (this: CustomWorld) {
  await this.page.getByRole('button', { name: /open user menu/i }).click();
  await this.page.getByRole('menuitem', { name: 'Your Orders' }).click();
});

When('I note the order number', async function (this: CustomWorld) {
  const orderNumber = await this.page.getByTestId('orderNumber').textContent();
  expect(orderNumber).toBeTruthy();
  this.setTestData('orderNumber', orderNumber);
});

Then(
  'I should see that order in my order history with {int} items',
  async function (this: CustomWorld, itemCount: number) {
    await this.page.waitForURL(/\/orders(\?|$)/, { timeout: config.timeout.action });
    const row = this.page
      .getByTestId('orderRow')
      .filter({ hasText: this.getTestData<string>('orderNumber') });
    await expect(row).toContainText(`${itemCount} items`);
    await expect(row.getByTestId('orderStatus')).toHaveText('SUBMITTED');
  }
);

When('I open that order', async function (this: CustomWorld) {
  await this.page
    .getByTestId('orderRow')
    .filter({ hasText: this.getTestData<string>('orderNumber') })
    .getByRole('link')
    .click();
});

Then(
  "I should see that order's details with {string}",
  async function (this: CustomWorld, productName: string) {
    await this.page.waitForURL(/\/orders\/[^/]+$/, { timeout: config.timeout.action });
    await expect(this.page.getByTestId('orderNumber')).toHaveText(
      this.getTestData<string>('orderNumber')!
    );
    await expect(this.page.getByTestId('orderLine')).toContainText(productName);
  }
);

Then('I should see the order not found page', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('orderNotFound')).toContainText('Order not found');
});

When('I sign in from the page I was sent to', async function (this: CustomWorld) {
  await this.page.waitForURL(/\/signin/, { timeout: config.timeout.action });
  const signinPage = new SigninPage(this.page);
  await signinPage.fillEmail(this.getTestData<string>('registeredEmail')!);
  await signinPage.fillPassword('SecureP@ss123');
  await signinPage.blurPassword();
  await signinPage.submitForm();
  await this.page.waitForURL((url) => url.pathname !== '/signin', {
    timeout: config.timeout.action,
  });
});

Then('I should be on {string}', async function (this: CustomWorld, path: string) {
  await expect
    .poll(() => {
      const url = new URL(this.page.url());
      return url.pathname + url.search;
    })
    .toBe(path);
});

Then('I should stay on {string}', async function (this: CustomWorld, path: string) {
  await this.page.waitForTimeout(1000);
  const url = new URL(this.page.url());
  expect(url.pathname + url.search).toBe(path);
});

Given('my access cookie is gone', async function (this: CustomWorld) {
  await this.context.clearCookies({ name: 'access_token' });
});

// A token the order service can't verify makes the app refresh, and with no refresh cookie
// identity refuses.
Given("my session can't be refreshed", async function (this: CustomWorld) {
  await this.context.clearCookies({ name: 'access_token' });
  await this.context.clearCookies({ name: 'refresh_token' });
  await this.context.addCookies([
    { name: 'access_token', value: 'not-a-token', url: new URL(this.page.url()).origin },
  ]);
});

Then('the header should show me signed out', async function (this: CustomWorld) {
  await this.page.waitForURL(/\/signin/, { timeout: config.timeout.action });
  await expect(this.page.getByRole('button', { name: /open user menu/i })).toHaveCount(0);
  const stored = await this.page.evaluate(() => localStorage.getItem('auth-storage'));
  expect(JSON.parse(stored!).state.isAuthenticated).toBe(false);
});

Given('the next read of my orders fails', async function (this: CustomWorld) {
  await this.page.route('**/api/v1/orders?*', (route) => route.abort(), { times: 1 });
});

Then("I should be told my orders couldn't be loaded", async function (this: CustomWorld) {
  await expect(this.page.getByRole('alert')).toContainText('couldn’t load your orders');
});

When('I try loading my orders again', async function (this: CustomWorld) {
  await this.page.getByRole('button', { name: 'Try again' }).click();
});
