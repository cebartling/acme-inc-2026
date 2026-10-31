import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { config } from '../../playwright.config.js';

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
