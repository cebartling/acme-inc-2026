import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ProductDetailPage } from '../../pages/customer/product-detail.page.js';

/**
 * Step definitions for Add to Cart on the product detail page (US-0004-06).
 *
 * Each scenario runs in a fresh browser context, so it starts with no session cookie
 * and therefore an empty cart.
 */

function productPage(world: CustomWorld): ProductDetailPage {
  return new ProductDetailPage(world.page, world.getTestData<string>('productSlug')!);
}

Given('I am on the product page for {string}', async function (this: CustomWorld, slug: string) {
  this.setTestData('productSlug', slug);
  await productPage(this).open();
});

When('I set the quantity to {int}', async function (this: CustomWorld, quantity: number) {
  await productPage(this).setQuantity(quantity);
});

When('I click Add to Cart', async function (this: CustomWorld) {
  const page = productPage(this);
  await expect(page.addToCartButton).toBeEnabled();
  await page.addToCart();
  await expect(page.confirmation.or(page.error)).toBeVisible();
});

Then(
  'the add to cart confirmation should show {string}',
  async function (this: CustomWorld, text: string) {
    await expect(productPage(this).confirmation).toContainText(text);
  }
);

Then(
  'I should see the add to cart error {string}',
  async function (this: CustomWorld, message: string) {
    const page = productPage(this);
    await expect(page.error).toHaveText(message);
    await expect(page.error).toHaveAttribute('role', 'alert');
  }
);

Then('the cart badge should show {int}', async function (this: CustomWorld, count: number) {
  await expect(productPage(this).cartBadgeCount).toHaveText(String(count));
});

Then('the cart badge should show no count', async function (this: CustomWorld) {
  await expect(productPage(this).cartBadgeCount).toHaveCount(0);
});
