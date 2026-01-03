import { Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

Then('I should see an error message {string}', async function (this: CustomWorld, message: string) {
  const errorElement = this.page.getByTestId('error-message');
  await expect(errorElement).toBeVisible();
  await expect(errorElement).toContainText(message);
});

Then('I should see a success message {string}', async function (this: CustomWorld, message: string) {
  const successElement = this.page.getByTestId('success-message');
  await expect(successElement).toBeVisible();
  await expect(successElement).toContainText(message);
});

Then('I should see a notification {string}', async function (this: CustomWorld, message: string) {
  const notification = this.page.getByRole('alert');
  await expect(notification).toBeVisible();
  await expect(notification).toContainText(message);
});

Then('the {string} field should have an error', async function (this: CustomWorld, fieldName: string) {
  const field = this.page.getByLabel(fieldName);
  await expect(field).toHaveAttribute('aria-invalid', 'true');
});

Then('the {string} button should be disabled', async function (this: CustomWorld, buttonText: string) {
  await expect(this.page.getByRole('button', { name: buttonText })).toBeDisabled();
});

Then('the {string} button should be enabled', async function (this: CustomWorld, buttonText: string) {
  await expect(this.page.getByRole('button', { name: buttonText })).toBeEnabled();
});

Then('I should see {int} items in the list', async function (this: CustomWorld, count: number) {
  const items = this.page.getByTestId('list-item');
  await expect(items).toHaveCount(count);
});

Then('the element {string} should be visible', async function (this: CustomWorld, testId: string) {
  await expect(this.page.getByTestId(testId)).toBeVisible();
});

Then('the element {string} should not be visible', async function (this: CustomWorld, testId: string) {
  await expect(this.page.getByTestId(testId)).not.toBeVisible();
});

Then('the input {string} should have value {string}', async function (
  this: CustomWorld,
  label: string,
  value: string
) {
  await expect(this.page.getByLabel(label)).toHaveValue(value);
});

Then('the input {string} should be empty', async function (this: CustomWorld, label: string) {
  await expect(this.page.getByLabel(label)).toHaveValue('');
});

Then('I should see a loading indicator', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('loading-indicator')).toBeVisible();
});

Then('I should not see a loading indicator', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('loading-indicator')).not.toBeVisible();
});

Then('the table should have {int} rows', async function (this: CustomWorld, count: number) {
  const rows = this.page.locator('table tbody tr');
  await expect(rows).toHaveCount(count);
});

Then('I should see an empty state message', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('empty-state')).toBeVisible();
});
