import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

/**
 * Step definitions for the sign-in cart merge notice (US-0004-08). Signing in, the cart badge
 * and seeding the account cart reuse steps from customer-profile-loading, add-to-cart-ui and
 * cart-api.
 */

function cartNotice(world: CustomWorld) {
  return world.page.getByTestId('cartMergeNotice');
}

Then('I should see the cart notice {string}', async function (this: CustomWorld, text: string) {
  const notice = cartNotice(this);
  await expect(notice).toContainText(text);
  await expect(notice).toHaveAttribute('role', 'status');
});

When('I dismiss the cart notice', async function (this: CustomWorld) {
  await this.page.getByRole('button', { name: 'Dismiss cart notice' }).click();
});

Then('the cart notice should be gone', async function (this: CustomWorld) {
  await expect(cartNotice(this)).toHaveCount(0);
});
