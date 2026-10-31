import { When } from '@cucumber/cucumber';
import { CustomWorld } from '../../support/world.js';

/**
 * Step definitions for preferences when a session ends (PIN-459). Signing in and losing the
 * access cookie reuse the steps from customer-profile-loading and order-history-ui.
 */

// Email, not SMS: saving SMS needs a verified phone
When('I change a preference', async function (this: CustomWorld) {
  await this.page.locator('#email-notifications').click();
});

When('I save my preferences', async function (this: CustomWorld) {
  await this.page.getByRole('button', { name: 'Save Preferences' }).click();
});
