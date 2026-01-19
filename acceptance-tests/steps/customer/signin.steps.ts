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
  // Blur to trigger onBlur validation
  await signinPage.blurEmail();
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
  // Blur to trigger onBlur validation
  await signinPage.blurPassword();
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

// ============================================================================
// Account Lockout UI Steps (US-0003-04)
// ============================================================================

When(
  'I attempt to signin {int} times with email {string} and wrong password',
  async function (this: CustomWorld, times: number, email: string) {
    const signinPage = new SigninPage(this.page);

    // Get the actual test user email
    const testUserEmail = this.getTestData<string>('testUserEmail') || email;

    for (let i = 0; i < times; i++) {
      // Fill in the form
      await signinPage.fillEmail(testUserEmail);
      await signinPage.fillPassword(`WrongPassword${i}!`);

      // Submit the form
      await signinPage.submitForm();

      // Wait for the response - either error message or lockout message
      await this.page.waitForTimeout(1000);

      // If not the last attempt, clear the form for the next attempt
      if (i < times - 1) {
        // Check if we got locked out early
        const lockoutMessage = this.page.getByTestId('lockout-message');
        const isLocked = await lockoutMessage.isVisible().catch(() => false);
        if (isLocked) {
          break;
        }
      }
    }
  }
);

Then('I should see the account lockout message', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('lockout-message')).toBeVisible({ timeout: 10000 });
  await expect(this.page.getByText('Account Locked')).toBeVisible();
});

Then('I should see the lockout countdown timer', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('lockout-countdown')).toBeVisible({ timeout: 5000 });
});

Then('I should see a link to reset my password', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('password-reset-link')).toBeVisible({ timeout: 5000 });
});

Then('the signin form should be disabled', async function (this: CustomWorld) {
  const signinPage = new SigninPage(this.page);
  const isDisabled = await signinPage.isSubmitButtonDisabled();
  expect(isDisabled).toBe(true);
});

Then('the lockout countdown should show minutes remaining', async function (this: CustomWorld) {
  const countdown = this.page.getByTestId('lockout-countdown');
  await expect(countdown).toBeVisible({ timeout: 5000 });
  // Check that the countdown contains "m" for minutes (e.g., "14m 30s")
  await expect(countdown).toContainText(/\d+m/);
});

When('I fill in the signin form with:', async function (this: CustomWorld, dataTable: { rowsHash: () => Record<string, string> }) {
  const data = dataTable.rowsHash();
  const signinPage = new SigninPage(this.page);

  // Get test user email if available
  const testUserEmail = this.getTestData<string>('testUserEmail');
  const email = testUserEmail || data.email;

  await signinPage.fillEmail(email);
  await signinPage.fillPassword(data.password);
});
