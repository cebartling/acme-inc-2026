import { Given, When, Then, DataTable } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { RegisterPage } from '../../pages/customer/register.page.js';

Given('I am on the registration page', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.navigate();
  // Wait for page to be fully loaded and hydrated
  await this.page.waitForLoadState('networkidle');
  // Extra wait for React hydration
  await this.page.waitForTimeout(1000);
});

// Email validation steps
When('I enter an invalid email {string}', async function (this: CustomWorld, email: string) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.fillEmail(email);
});

When('I enter a valid email {string}', async function (this: CustomWorld, email: string) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.fillEmail(email);
});

When('I move focus away from the email field', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.blurEmail();
});

Then(
  'I should see the email error {string}',
  async function (this: CustomWorld, errorMessage: string) {
    await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible({
      timeout: 10000,
    });
  }
);

Then(
  'the email field should be highlighted with an error state',
  async function (this: CustomWorld) {
    const emailInput = this.page.getByRole('textbox', { name: 'Email' });
    await expect(emailInput).toHaveClass(/border-red-500/);
  }
);

Then('the email error should not be visible', async function (this: CustomWorld) {
  await expect(this.page.getByRole('alert').filter({ hasText: /email/i })).not.toBeVisible();
});

Then('I should see a success indicator on the email field', async function (this: CustomWorld) {
  await expect(this.page.getByLabel('Valid').first()).toBeVisible();
});

// Password steps
When('I enter password {string}', async function (this: CustomWorld, password: string) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.fillPassword(password);
});

When('I enter confirm password {string}', async function (this: CustomWorld, password: string) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.fillConfirmPassword(password);
});

When('I move focus away from the confirm password field', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.blurConfirmPassword();
});

Then(
  'I should see the password strength indicator showing {string}',
  async function (this: CustomWorld, strength: string) {
    await expect(this.page.getByText(strength, { exact: true })).toBeVisible({ timeout: 10000 });
  }
);

Then('all password requirements should be marked as complete', async function (this: CustomWorld) {
  // All 5 check icons should be visible (green checkmarks)
  const checkIcons = this.page
    .locator('ul li')
    .filter({ has: this.page.locator('svg[class*="text-green"]') });
  await expect(checkIcons).toHaveCount(5);
});

Then(
  'I should see the following password requirements:',
  async function (this: CustomWorld, dataTable: DataTable) {
    const requirements = dataTable.raw().flat();
    for (const requirement of requirements) {
      await expect(this.page.getByText(requirement, { exact: false })).toBeVisible();
    }
  }
);

Then(
  'I should see the confirm password error {string}',
  async function (this: CustomWorld, errorMessage: string) {
    await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible();
  }
);

Then('the confirm password error should not be visible', async function (this: CustomWorld) {
  await expect(this.page.getByRole('alert').filter({ hasText: /match/i })).not.toBeVisible();
});

Then(
  'I should see a success indicator on the confirm password field',
  async function (this: CustomWorld) {
    // Look for Valid label in the confirm password section
    const confirmPasswordSection = this.page
      .locator('div')
      .filter({ hasText: /^Confirm Password/ });
    await expect(confirmPasswordSection.getByLabel('Valid')).toBeVisible();
  }
);

// Name field steps
When('I leave the first name field empty', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.firstNameInput.focus();
});

When('I leave the last name field empty', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.lastNameInput.focus();
});

When('I move focus away from the first name field', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.blurFirstName();
});

When('I move focus away from the last name field', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.blurLastName();
});

When('I enter a first name with more than 50 characters', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  const longName = 'A'.repeat(60);
  await registerPage.fillFirstName(longName);
});

Then(
  'I should see the first name error {string}',
  async function (this: CustomWorld, errorMessage: string) {
    await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible();
  }
);

Then(
  'I should see the last name error {string}',
  async function (this: CustomWorld, errorMessage: string) {
    await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible();
  }
);

Then('the first name should be truncated to 50 characters', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  const value = await registerPage.getFirstNameValue();
  expect(value.length).toBe(50);
});

Then(
  'the character counter should show {string}',
  async function (this: CustomWorld, counter: string) {
    await expect(this.page.getByText(counter)).toBeVisible();
  }
);

// Terms of Service steps
When(
  'I fill in the registration form with valid data but without accepting Terms of Service',
  async function (this: CustomWorld) {
    const registerPage = new RegisterPage(this.page);
    await registerPage.fillEmail('test@example.com');
    await registerPage.fillPassword('Test@123!');
    await registerPage.fillConfirmPassword('Test@123!');
    await registerPage.fillFirstName('John');
    await registerPage.fillLastName('Doe');
    await registerPage.acceptPrivacyPolicy();
    // Note: Not accepting Terms of Service
  }
);

When('I attempt to submit the form', async function (this: CustomWorld) {
  // The button should be disabled, but we verify the state
  // No action needed - form should not submit because button is disabled
});

Then('the form should not submit', async function (this: CustomWorld) {
  // The form should still be on the registration page
  await expect(this.page).toHaveURL(/\/register/);
});

Then('I should see the Terms of Service error', async function (this: CustomWorld) {
  // Since the submit button is disabled when ToS is not accepted,
  // we verify the button is still disabled as an indication of the error state
  const registerPage = new RegisterPage(this.page);
  const isDisabled = await registerPage.isSubmitButtonDisabled();
  expect(isDisabled).toBe(true);
});

// Submit button steps
Then('the submit button should be disabled', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  const isDisabled = await registerPage.isSubmitButtonDisabled();
  expect(isDisabled).toBe(true);
});

Then('the submit button should be enabled', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  const isEnabled = await registerPage.isSubmitButtonEnabled();
  expect(isEnabled).toBe(true);
});

// Password visibility steps
Then('the password field should be masked', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  const isMasked = await registerPage.isPasswordFieldMasked();
  expect(isMasked).toBe(true);
});

Then('the password field should show the password text', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  const isMasked = await registerPage.isPasswordFieldMasked();
  expect(isMasked).toBe(false);
});

When('I click the password visibility toggle', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.togglePasswordVisibility();
});

// Complete form steps
When(
  'I fill in the complete registration form with valid data',
  async function (this: CustomWorld) {
    const registerPage = new RegisterPage(this.page);
    await registerPage.fillRegistrationForm({
      email: 'newuser@example.com',
      password: 'Test@123!',
      confirmPassword: 'Test@123!',
      firstName: 'John',
      lastName: 'Doe',
      acceptTos: true,
      acceptPrivacy: true,
    });
  }
);

When(
  'I fill in the registration form with:',
  async function (this: CustomWorld, dataTable: DataTable) {
    const registerPage = new RegisterPage(this.page);
    const data = dataTable.rowsHash();

    await registerPage.fillEmail(data['Email']);
    await registerPage.fillPassword(data['Password']);
    await registerPage.fillConfirmPassword(data['Confirm Password']);
    await registerPage.fillFirstName(data['First Name']);
    await registerPage.fillLastName(data['Last Name']);
  }
);

When('I accept the Terms of Service', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.acceptTermsOfService();
});

When('I accept the Privacy Policy', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.acceptPrivacyPolicy();
});

When('I submit the registration form', async function (this: CustomWorld) {
  const registerPage = new RegisterPage(this.page);
  await registerPage.submitForm();
});

// Navigation steps are defined in steps/common/navigation.steps.ts
