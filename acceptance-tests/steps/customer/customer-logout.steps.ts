import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

When('I open the user menu', async function (this: CustomWorld) {
  const trigger = this.page.getByRole('button', { name: /open user menu/i });
  await trigger.click();
});

When('I click the {string} menu item', async function (this: CustomWorld, label: string) {
  const item = this.page.getByRole('menuitem', { name: new RegExp(`^${label}$`, 'i') });
  await item.click();
});

When('I confirm the all-devices logout', async function (this: CustomWorld) {
  const confirm = this.page.getByRole('button', { name: /^sign out all$/i });
  await confirm.click();
});

When('I cancel the all-devices logout', async function (this: CustomWorld) {
  const cancel = this.page.getByRole('button', { name: /^cancel$/i });
  await cancel.click();
});

Then(
  'I should be redirected to the signin page with logout=true',
  async function (this: CustomWorld) {
    await this.page.waitForURL(/\/signin\?.*logout=true/, { timeout: 10000 });
  },
);

Then('I should see a signed-out banner', async function (this: CustomWorld) {
  await expect(
    this.page.getByText(/you have been signed out|signed out/i),
  ).toBeVisible({ timeout: 5000 });
});

Then('the auth storage should be cleared', async function (this: CustomWorld) {
  const stored = await this.page.evaluate(() => localStorage.getItem('auth-storage'));
  if (!stored) {
    return; // already cleared
  }
  const parsed = JSON.parse(stored) as { state?: { user?: unknown; isAuthenticated?: boolean } };
  expect(parsed.state?.user ?? null).toBeNull();
  expect(parsed.state?.isAuthenticated ?? false).toBe(false);
});

Then('the customer storage should be cleared', async function (this: CustomWorld) {
  const stored = await this.page.evaluate(() => localStorage.getItem('customer-storage'));
  if (!stored) {
    return;
  }
  const parsed = JSON.parse(stored) as { state?: { profile?: unknown } };
  expect(parsed.state?.profile ?? null).toBeNull();
});

Then('a confirmation dialog should appear', async function (this: CustomWorld) {
  await expect(this.page.getByRole('alertdialog')).toBeVisible({ timeout: 5000 });
});

Then('the dialog title should mention all devices', async function (this: CustomWorld) {
  await expect(
    this.page.getByRole('alertdialog').getByText(/all devices/i),
  ).toBeVisible();
});

Then('I should remain signed in', async function (this: CustomWorld) {
  // Confirm the URL did not navigate to /signin
  await this.page.waitForTimeout(500);
  expect(this.page.url()).not.toMatch(/\/signin/);
});
