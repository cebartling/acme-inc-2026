import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { MfaVerifyPage } from '../../pages/customer/mfa-verify.page.js';
import { SigninPage } from '../../pages/customer/signin.page.js';
import { generateSync } from 'otplib';

// ============================================================================
// GIVEN Steps
// ============================================================================

// Note: "the user has TOTP MFA enabled" and "the user has SMS MFA enabled"
// steps are defined in mfa-verification.steps.ts

// ============================================================================
// WHEN Steps
// ============================================================================

When('I complete credential validation and navigate to MFA verification page', async function (this: CustomWorld) {
  const email = this.getTestData<string>('testUserEmail');
  const password = this.getTestData<string>('testUserPassword');

  if (!email || !password) {
    throw new Error('Test user email and password must be set');
  }

  // Perform signin via API to get MFA token
  const response = await this.identityApiClient.post<{
    status: string;
    mfaToken: string;
    mfaMethods: string[];
    expiresIn: number;
  }>('/api/v1/auth/signin', {
    email,
    password,
    rememberMe: false,
  });

  if (response.status !== 200 || response.data.status !== 'MFA_REQUIRED') {
    throw new Error(`Expected MFA_REQUIRED response, got: ${response.status} - ${JSON.stringify(response.data)}`);
  }

  const { mfaToken, mfaMethods } = response.data;

  this.setTestData('mfaToken', mfaToken);
  this.setTestData('mfaMethods', mfaMethods);

  // If SMS is in the methods, fetch the SMS code for later use
  const phoneNumber = this.getTestData<string>('phoneNumber');
  if (mfaMethods.includes('SMS') && phoneNumber) {
    const codeResponse = await this.identityApiClient.get<{ code: string }>(
      `/api/v1/test/sms/last-code?phoneNumber=${encodeURIComponent(phoneNumber)}`
    );
    if (codeResponse.status === 200 && codeResponse.data.code) {
      this.setTestData('lastSmsCode', codeResponse.data.code);
    }
  }

  // Navigate to MFA verify page with state in session storage
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await mfaVerifyPage.navigateWithMfaState(mfaToken, email, mfaMethods);

  // Wait for page to be ready
  await this.page.waitForLoadState('networkidle');
});

When('I click the SMS method button', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await mfaVerifyPage.switchToSms();
  // Wait for UI to update
  await this.page.waitForTimeout(300);
});

When('I click the Authenticator method button', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await mfaVerifyPage.switchToAuthenticator();
  // Wait for UI to update
  await this.page.waitForTimeout(300);
});

When('I enter a valid TOTP code', async function (this: CustomWorld) {
  const totpSecret = this.getTestData<string>('totpSecret');

  if (!totpSecret) {
    throw new Error('TOTP secret must be set');
  }

  // Generate a valid TOTP code
  const code = generateSync({ secret: totpSecret });

  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await mfaVerifyPage.enterCode(code);
});

When('I enter the correct SMS code', async function (this: CustomWorld) {
  const phoneNumber = this.getTestData<string>('phoneNumber');
  let code = this.getTestData<string>('lastSmsCode');

  // If we don't have the code, fetch it
  if (!code && phoneNumber) {
    const codeResponse = await this.identityApiClient.get<{ code: string }>(
      `/api/v1/test/sms/last-code?phoneNumber=${encodeURIComponent(phoneNumber)}`
    );
    if (codeResponse.status === 200 && codeResponse.data.code) {
      code = codeResponse.data.code;
      this.setTestData('lastSmsCode', code);
    }
  }

  if (!code) {
    throw new Error('SMS code not available');
  }

  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await mfaVerifyPage.enterCode(code);
});

When('I submit the MFA verification form', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await mfaVerifyPage.submitForm();
  // Wait for navigation or response
  await this.page.waitForTimeout(500);
});

// ============================================================================
// THEN Steps
// ============================================================================

Then('I should see the MFA method switcher', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  const isVisible = await mfaVerifyPage.isMethodSwitcherVisible();
  expect(isVisible).toBe(true);
});

Then('I should not see the MFA method switcher', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  const isVisible = await mfaVerifyPage.isMethodSwitcherVisible();
  expect(isVisible).toBe(false);
});

Then('the Authenticator button should be visible', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await expect(mfaVerifyPage.authenticatorButton).toBeVisible();
});

Then('the SMS button should be visible', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await expect(mfaVerifyPage.smsButton).toBeVisible();
});

Then('the Authenticator method should be selected by default', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);

  // Check that the Authenticator button has the "default" (filled) variant
  // by checking it doesn't have the "outline" class/variant
  const authenticatorClass = await mfaVerifyPage.authenticatorButton.getAttribute('class');
  const smsClass = await mfaVerifyPage.smsButton.getAttribute('class');

  // The selected button should NOT be outline variant
  // Note: exact class names depend on shadcn/ui implementation
  expect(authenticatorClass).not.toContain('border-input');
  expect(smsClass).toContain('border-input');
});

Then('the SMS method should be selected', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);

  // Check that the SMS button has the "default" (filled) variant
  const smsClass = await mfaVerifyPage.smsButton.getAttribute('class');

  // The selected button should NOT be outline variant
  expect(smsClass).not.toContain('border-input');
});

Then('the Authenticator method should be selected', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);

  // Check that the Authenticator button has the "default" (filled) variant
  const authenticatorClass = await mfaVerifyPage.authenticatorButton.getAttribute('class');

  // The selected button should NOT be outline variant
  expect(authenticatorClass).not.toContain('border-input');
});

Then('I should see the resend code button', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await expect(mfaVerifyPage.resendCodeButton).toBeVisible({ timeout: 5000 });
});

Then('the resend code button should not be visible', async function (this: CustomWorld) {
  const mfaVerifyPage = new MfaVerifyPage(this.page);
  await expect(mfaVerifyPage.resendCodeButton).not.toBeVisible();
});

Then('I should be redirected to the dashboard page', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(/\/dashboard/, { timeout: 10000 });
});
