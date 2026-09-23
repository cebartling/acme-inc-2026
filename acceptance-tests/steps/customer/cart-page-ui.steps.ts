import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { CartPage } from '../../pages/customer/cart.page.js';

/**
 * Step definitions for the `/cart` page and cart persistence (US-0004-07).
 *
 * The add-to-cart and cart-badge steps live in add-to-cart-ui.steps.ts.
 */

const SESSION_COOKIE = 'acme_session_id';

function cartPage(world: CustomWorld): CartPage {
  return new CartPage(world.page);
}

Given('I am on the cart page', async function (this: CustomWorld) {
  const page = cartPage(this);
  await page.navigate();
  await expect(page.lines.first()).toBeVisible();
});

When('I open the cart page in a new tab', async function (this: CustomWorld) {
  // Same browser context, so the same cookie jar; later steps drive the new tab.
  this.page = await this.context.newPage();
  await cartPage(this).navigate();
});

When('I increase the line quantity', async function (this: CustomWorld) {
  await cartPage(this).increaseButton.click();
});

When('I type a line quantity of {int}', async function (this: CustomWorld, quantity: number) {
  await cartPage(this).typeQuantity(quantity);
});

When('I remove the line', async function (this: CustomWorld) {
  await cartPage(this).removeButton.click();
});

Then(
  'the browser should hold an HttpOnly SameSite=Lax session cookie for about {int} days',
  async function (this: CustomWorld, days: number) {
    const cookie = (await this.context.cookies()).find((c) => c.name === SESSION_COOKIE);
    expect(cookie, `expected a ${SESSION_COOKIE} cookie`).toBeDefined();
    expect(cookie!.httpOnly).toBe(true);
    expect(cookie!.sameSite).toBe('Lax');
    const lifetimeSeconds = days * 24 * 60 * 60;
    const remainingSeconds = cookie!.expires - Date.now() / 1000;
    // Allow a minute of slack for test runtime.
    expect(remainingSeconds).toBeGreaterThan(lifetimeSeconds - 60);
    expect(remainingSeconds).toBeLessThanOrEqual(lifetimeSeconds);
  }
);

Then(
  'page scripts should not be able to read the session cookie',
  async function (this: CustomWorld) {
    const visible = await this.page.evaluate<string>('document.cookie');
    expect(visible).not.toContain(SESSION_COOKIE);
  }
);

Then(
  'the cart should show {int} line with quantity {int}',
  async function (this: CustomWorld, lines: number, quantity: number) {
    const page = cartPage(this);
    await expect(page.lines).toHaveCount(lines);
    await expect(page.quantityInput).toHaveValue(String(quantity));
  }
);

Then('the line should show {string}', async function (this: CustomWorld, text: string) {
  await expect(cartPage(this).unitPrice).toHaveText(text);
});

Then('the cart subtotal should be {string}', async function (this: CustomWorld, amount: string) {
  await expect(cartPage(this).subtotal).toHaveText(amount);
});

Then('the estimated total should be {string}', async function (this: CustomWorld, amount: string) {
  await expect(cartPage(this).estimatedTotal).toHaveText(amount);
});

Then('the line should explain {string}', async function (this: CustomWorld, message: string) {
  const page = cartPage(this);
  await expect(page.lineMessage).toHaveText(message);
  await expect(page.lineMessage).toHaveAttribute('role', 'alert');
});

Then('the line quantity should be {int}', async function (this: CustomWorld, quantity: number) {
  await expect(cartPage(this).quantityInput).toHaveValue(String(quantity));
});

Then(
  'I should see the empty cart with a link to continue shopping',
  async function (this: CustomWorld) {
    const page = cartPage(this);
    await expect(page.emptyState).toBeVisible();
    await expect(page.continueShopping).toHaveAttribute('href', '/');
  }
);
