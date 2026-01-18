import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { SigninPage } from '../../pages/customer/signin.page.js';

Given('I am on the signin page', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.navigate();
  // Wait for page to be fully loaded and hydrated
  await this.page.waitForLoadState('networkidle');
  // Extra wait for React hydration
  await this.page.waitForTimeout(1000);
});

// Email validation steps
When('I enter signin email {string}', async function (this: CustomWorld, email: string) {
  const signinPage = new SigninPage(this.page);
  await signinPage.fillEmail(email);
});

When('I leave the signin email field empty', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.emailInput.focus();
});

When('I move focus away from the signin email field', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.blurEmail();
});

Then('I should see the signin email error {string}', async function (this: CustomWorld, errorMessage: string) {
  await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible({ timeout: 10000 });
});

Then('the signin email error should not be visible', async function (this: CustomWorld) {
  await expect(this.page.getByRole('alert').filter({ hasText: /email/i })).not.toBeVisible();
});

Then('I should see a success indicator on the signin email field', async function (this: CustomWorld) {
  await expect(this.page.getByLabel('Valid').first()).toBeVisible();
});

// Password validation steps
When('I enter signin password {string}', async function (this: CustomWorld, password: string) {
  const signinPage = new SigninPage(this.page);
  await signinPage.fillPassword(password);
});

When('I leave the signin password field empty', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.passwordInput.focus();
});

When('I move focus away from the signin password field', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.blurPassword();
});

Then('I should see the signin password error {string}', async function (this: CustomWorld, errorMessage: string) {
  await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible({ timeout: 10000 });
});

Then('the signin password error should not be visible', async function (this: CustomWorld) {
  await expect(this.page.getByRole('alert').filter({ hasText: /password/i })).not.toBeVisible();
});

// Password visibility steps
Then('the signin password field should be masked', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  const isMasked = await signinPage.isPasswordFieldMasked();
  expect(isMasked).toBe(true);
});

Then('the signin password field should show the password text', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  const isMasked = await signinPage.isPasswordFieldMasked();
  expect(isMasked).toBe(false);
});

When('I click the signin password visibility toggle', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.togglePasswordVisibility();
});

// Remember me steps
Then('the remember me checkbox should be unchecked', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  const isChecked = await signinPage.isRememberMeChecked();
  expect(isChecked).toBe(false);
});

Then('the remember me checkbox should be checked', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  const isChecked = await signinPage.isRememberMeChecked();
  expect(isChecked).toBe(true);
});

When('I check the remember me checkbox', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.checkRememberMe();
});

// Submit button steps
Then('the signin button should be disabled', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  const isDisabled = await signinPage.isSubmitButtonDisabled();
  expect(isDisabled).toBe(true);
});

Then('the signin button should be enabled', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  const isEnabled = await signinPage.isSubmitButtonEnabled();
  expect(isEnabled).toBe(true);
});

// Navigation link steps
Then('I should see the forgot password link', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await expect(signinPage.forgotPasswordLink).toBeVisible();
});

When('I click the forgot password link', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.goToForgotPassword();
});

Then('I should see the registration link on signin page', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await expect(signinPage.registrationLink).toBeVisible();
});

When('I click the registration link on signin page', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.goToRegistration();
});

// API error steps
Given('the signin API will return an error', async function (this: CustomWorld) {
  // This step sets up the expectation for an error response
  // In a real implementation, this might mock the API or set a test flag
  // For now, we just use invalid credentials in the test
});

When('I submit the signin form', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  await signinPage.submitForm();
});

Then('I should see the signin error message', async function (this: CustomWorld) {
  // Wait for the error message to appear after form submission
  await expect(
    this.page.locator('div[role="alert"]').filter({ hasText: /invalid|error/i })
  ).toBeVisible({ timeout: 10000 });
});

Then('I should be redirected to the dashboard page', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(/\/dashboard/, { timeout: 10000 });
});
