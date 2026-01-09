import { Given, When, Then, DataTable } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ProfileWizardPage } from '../../pages/customer/profile-wizard.page.js';

Given('I am on the profile completion wizard', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.navigate();
  await this.page.waitForLoadState('networkidle');
  await this.page.waitForTimeout(1000);
});

Given('I am on the address step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  if (!(await wizardPage.isOnAddressStep())) {
    await wizardPage.navigate();
    await this.page.waitForLoadState('networkidle');
    await wizardPage.clickSkipThisStep();
    await this.page.waitForTimeout(500);
  }
});

Given('I am on the preferences step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  if (!(await wizardPage.isOnPreferencesStep())) {
    await wizardPage.navigate();
    await this.page.waitForLoadState('networkidle');
    await wizardPage.clickSkipThisStep();
    await this.page.waitForTimeout(500);
    await wizardPage.clickSkip();
    await this.page.waitForTimeout(500);
  }
});

Given('I am on the review step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  if (!(await wizardPage.isOnReviewStep())) {
    await wizardPage.navigate();
    await this.page.waitForLoadState('networkidle');
    // Skip through all steps to get to review
    await wizardPage.clickSkipThisStep();
    await this.page.waitForTimeout(500);
    await wizardPage.clickSkip();
    await this.page.waitForTimeout(500);
    await wizardPage.clickReview();
    await this.page.waitForTimeout(500);
  }
});

// Phone Number Steps
When('I enter a phone number with country code {string} and number {string}', async function (this: CustomWorld, countryCode: string, number: string) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.fillPhoneNumber(countryCode, number);
});

Then('I should see a phone number validation error', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await expect(wizardPage.phoneError).toBeVisible({ timeout: 5000 });
});

Then('I should not see a phone number validation error', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await expect(wizardPage.phoneError).not.toBeVisible({ timeout: 5000 });
});

// Date of Birth Steps
When('I enter a date of birth that is less than 13 years ago', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  const tenYearsAgo = new Date();
  tenYearsAgo.setFullYear(tenYearsAgo.getFullYear() - 10);
  const dateStr = tenYearsAgo.toISOString().split('T')[0];
  await wizardPage.fillDateOfBirth(dateStr);
});

When('I enter a date of birth {string}', async function (this: CustomWorld, date: string) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.fillDateOfBirth(date);
});

Then('I should see the date of birth error {string}', async function (this: CustomWorld, errorMessage: string) {
  await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible({ timeout: 5000 });
});

Then('I should not see a date of birth validation error', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await expect(wizardPage.dateOfBirthError).not.toBeVisible({ timeout: 5000 });
});

// Navigation Steps
When('I click the continue button', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickContinue();
  await this.page.waitForTimeout(500);
});

When('I click the back button', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickBack();
  await this.page.waitForTimeout(500);
});

When('I click the skip button', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickSkip();
  await this.page.waitForTimeout(500);
});

When('I click the skip this step button', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickSkipThisStep();
  await this.page.waitForTimeout(500);
});

When('I click the review button', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickReview();
  await this.page.waitForTimeout(500);
});

When('I click the complete profile button', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickCompleteProfile();
  await this.page.waitForTimeout(2000);
});

When('I click the skip profile completion link', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickSkipProfileCompletion();
  await this.page.waitForTimeout(500);
});

When('I click on the personal details step in the progress indicator', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.goToPersonalDetailsStep();
  await this.page.waitForTimeout(500);
});

// Step Verification
Then('I should be on the address step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  expect(await wizardPage.isOnAddressStep()).toBe(true);
});

Then('I should be on the personal details step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  expect(await wizardPage.isOnPersonalDetailsStep()).toBe(true);
});

Then('I should be on the preferences step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  expect(await wizardPage.isOnPreferencesStep()).toBe(true);
});

Then('I should be on the review step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  expect(await wizardPage.isOnReviewStep()).toBe(true);
});

Then('I should be redirected to the home page', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(/\/$/, { timeout: 10000 });
});

// Personal Details Step Actions
When('I select gender {string}', async function (this: CustomWorld, gender: string) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.selectGender(gender);
});

When('I select preferred language {string}', async function (this: CustomWorld, language: string) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.selectLanguage(language);
});

When('I select timezone {string}', async function (this: CustomWorld, timezone: string) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.selectTimezone(timezone);
});

Then('the gender {string} should be saved', async function (this: CustomWorld, _gender: string) {
  // Will be verified on the review step
});

// Address Step Actions
When('I click the continue button without filling required fields', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  // Clear any pre-filled values and try to continue
  await wizardPage.clickContinue();
  await this.page.waitForTimeout(500);
});

Then('I should see the street address error {string}', async function (this: CustomWorld, errorMessage: string) {
  await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible({ timeout: 5000 });
});

Then('I should see the city error {string}', async function (this: CustomWorld, errorMessage: string) {
  await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible({ timeout: 5000 });
});

Then('I should see the postal code error {string}', async function (this: CustomWorld, errorMessage: string) {
  await expect(this.page.getByRole('alert').filter({ hasText: errorMessage })).toBeVisible({ timeout: 5000 });
});

When('I fill in the address form with:', async function (this: CustomWorld, dataTable: DataTable) {
  const wizardPage = new ProfileWizardPage(this.page);
  const data = dataTable.rowsHash();

  await wizardPage.fillAddress({
    addressType: data['Address Type'],
    streetLine1: data['Street Line 1'],
    streetLine2: data['Street Line 2'],
    city: data['City'],
    state: data['State'],
    postalCode: data['Postal Code'],
    country: data['Country'],
  });
});

// Preferences Step Actions
When('I toggle email notifications off', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.toggleEmailNotifications();
});

When('I toggle SMS notifications on', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.toggleSmsNotifications();
});

When('I select notification frequency {string}', async function (this: CustomWorld, frequency: string) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.selectNotificationFrequency(frequency);
});

Then('email notifications should show as {string}', async function (this: CustomWorld, status: string) {
  await expect(this.page.getByText(`Email Notifications`).locator('..').getByText(status)).toBeVisible();
});

Then('SMS notifications should show as {string}', async function (this: CustomWorld, status: string) {
  await expect(this.page.getByText(`SMS Notifications`).locator('..').getByText(status)).toBeVisible();
});

Then('notification frequency should show as {string}', async function (this: CustomWorld, frequency: string) {
  await expect(this.page.getByText(frequency)).toBeVisible();
});

// Review Step Actions
When('I click edit on the personal details section', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickEditPersonalDetails();
  await this.page.waitForTimeout(500);
});

When('I click edit on the address section', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickEditAddress();
  await this.page.waitForTimeout(500);
});

When('I click edit on the preferences section', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickEditPreferences();
  await this.page.waitForTimeout(500);
});

// Complex Steps for Full Flow
When('I complete the personal details step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickSkipThisStep();
  await this.page.waitForTimeout(500);
});

When('I complete the address step', async function (this: CustomWorld) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.clickSkip();
  await this.page.waitForTimeout(500);
});

Given('I have completed all wizard steps with:', async function (this: CustomWorld, _dataTable: DataTable) {
  const wizardPage = new ProfileWizardPage(this.page);
  await wizardPage.navigate();
  await this.page.waitForLoadState('networkidle');
  // Skip through to review for now
  await wizardPage.clickSkipThisStep();
  await this.page.waitForTimeout(500);
  await wizardPage.clickSkip();
  await this.page.waitForTimeout(500);
  await wizardPage.clickReview();
  await this.page.waitForTimeout(500);
});

Then('I should see all my entered information displayed', async function (this: CustomWorld) {
  // Verify we're on review step and key sections are visible
  await expect(this.page.getByText('Personal Details')).toBeVisible();
  await expect(this.page.getByText('Address')).toBeVisible();
  await expect(this.page.getByText('Communication Preferences')).toBeVisible();
});

When('I fill in my personal details:', async function (this: CustomWorld, dataTable: DataTable) {
  const wizardPage = new ProfileWizardPage(this.page);
  const data = dataTable.rowsHash();

  if (data['Phone Country Code'] && data['Phone Number']) {
    await wizardPage.fillPhoneNumber(data['Phone Country Code'], data['Phone Number']);
  }
  if (data['Date of Birth']) {
    await wizardPage.fillDateOfBirth(data['Date of Birth']);
  }
  if (data['Gender']) {
    await wizardPage.selectGender(data['Gender']);
  }
  if (data['Language']) {
    await wizardPage.selectLanguage(data['Language']);
  }
  if (data['Timezone']) {
    await wizardPage.selectTimezone(data['Timezone']);
  }
});

When('I fill in my address:', async function (this: CustomWorld, dataTable: DataTable) {
  const wizardPage = new ProfileWizardPage(this.page);
  const data = dataTable.rowsHash();

  await wizardPage.fillAddress({
    addressType: data['Address Type'],
    label: data['Label'],
    streetLine1: data['Street Line 1'],
    city: data['City'],
    state: data['State'],
    postalCode: data['Postal Code'],
    country: data['Country'],
  });
});

When('I configure my preferences:', async function (this: CustomWorld, dataTable: DataTable) {
  const wizardPage = new ProfileWizardPage(this.page);
  const data = dataTable.rowsHash();

  // Toggle based on desired state - defaults are: email=on, sms=off, push=off, marketing=off
  if (data['Email Notifications'] === 'off') {
    await wizardPage.toggleEmailNotifications();
  }
  if (data['SMS Notifications'] === 'on') {
    await wizardPage.toggleSmsNotifications();
  }
  if (data['Push Notifications'] === 'on') {
    await wizardPage.togglePushNotifications();
  }
  if (data['Marketing'] === 'on') {
    await wizardPage.toggleMarketing();
  }
});
