import { When, Then, Given } from '@cucumber/cucumber';
import { expect, Request } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ProductDetailPage } from '../../pages/customer/product-detail.page.js';
import { CartPage } from '../../pages/customer/cart.page.js';

/**
 * Step definitions for Out of Stock Handling (US-0004-10, PIN-273). Reuses "I am on the
 * product page for", "I click Add to Cart" and "I am on the cart page".
 */

/** Gadget Pro's Black variant (V4__seed_product_variants.sql). */
const GADGET_PRO_BLACK = '11111111-1111-1111-1111-000000000001';

function productPage(world: CustomWorld): ProductDetailPage {
  return new ProductDetailPage(world.page, world.getTestData<string>('productSlug')!);
}

When('I select the color {string}', async function (this: CustomWorld, color: string) {
  await productPage(this).selectColor(color);
});

Then(
  'the availability badge should show {string}',
  async function (this: CustomWorld, text: string) {
    await expect(productPage(this).availabilityBadge).toHaveText(text);
  }
);

Then('the Add to Cart button should be disabled', async function (this: CustomWorld) {
  const button = productPage(this).addToCartButton;
  await expect(button).toBeDisabled();
  await expect(button).toHaveAttribute('aria-disabled', 'true');
});

Then('the Add to Cart button should be enabled', async function (this: CustomWorld) {
  await expect(productPage(this).addToCartButton).toBeEnabled();
});

Then('pressing Add to Cart should make no cart request', async function (this: CustomWorld) {
  const cartRequests: Request[] = [];
  const onRequest = (request: Request) => {
    if (request.method() === 'POST' && request.url().includes('/api/v1/carts/items')) {
      cartRequests.push(request);
    }
  };
  this.page.on('request', onRequest);
  try {
    // A disabled button ignores the click; force it so the test proves nothing is sent
    await productPage(this).addToCartButton.click({ force: true });
    await this.page.waitForTimeout(500);
  } finally {
    this.page.off('request', onRequest);
  }
  expect(cartRequests).toHaveLength(0);
});

Then(
  'I should see the product section {string}',
  async function (this: CustomWorld, heading: string) {
    await expect(this.page.getByRole('heading', { name: heading })).toBeVisible();
  }
);

Given(
  'the inventory service reports the Black Gadget Pro out of stock',
  async function (this: CustomWorld) {
    await this.page.route(`**/api/v1/inventory/availability/${GADGET_PRO_BLACK}`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ variantId: GADGET_PRO_BLACK, availability: 'OUT_OF_STOCK' }),
      })
    );
  }
);

Then('the cart line should warn {string}', async function (this: CustomWorld, message: string) {
  const warning = new CartPage(this.page).outOfStockWarning;
  await expect(warning).toHaveText(message);
  await expect(warning).toHaveAttribute('role', 'alert');
});

Then(
  "the cart line's Remove button should read {string}",
  async function (this: CustomWorld, label: string) {
    await expect(new CartPage(this.page).removeButton).toContainText(label);
  }
);
