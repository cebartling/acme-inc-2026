import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class ProfileWizardPage extends BasePage {
  // Progress indicator
  readonly progressIndicator: Locator;
  readonly personalDetailsStepIndicator: Locator;
  readonly addressStepIndicator: Locator;
  readonly preferencesStepIndicator: Locator;
  readonly reviewStepIndicator: Locator;

  // Personal Details Step Fields
  readonly phoneCountryCodeSelect: Locator;
  readonly phoneNumberInput: Locator;
  readonly dateOfBirthInput: Locator;
  readonly genderSelect: Locator;
  readonly languageSelect: Locator;
  readonly timezoneSelect: Locator;

  // Address Step Fields
  readonly addressTypeSelect: Locator;
  readonly addressLabelInput: Locator;
  readonly streetLine1Input: Locator;
  readonly streetLine2Input: Locator;
  readonly cityInput: Locator;
  readonly stateSelect: Locator;
  readonly stateInput: Locator;
  readonly postalCodeInput: Locator;
  readonly countrySelect: Locator;
  readonly defaultAddressCheckbox: Locator;

  // Preferences Step Fields
  readonly emailNotificationsSwitch: Locator;
  readonly smsNotificationsSwitch: Locator;
  readonly pushNotificationsSwitch: Locator;
  readonly marketingSwitch: Locator;
  readonly notificationFrequencySelect: Locator;

  // Navigation Buttons
  readonly continueButton: Locator;
  readonly backButton: Locator;
  readonly skipButton: Locator;
  readonly skipThisStepButton: Locator;
  readonly reviewButton: Locator;
  readonly completeProfileButton: Locator;
  readonly skipProfileCompletionLink: Locator;

  // Review Step
  readonly personalDetailsSection: Locator;
  readonly addressSection: Locator;
  readonly preferencesSection: Locator;
  readonly editPersonalDetailsButton: Locator;
  readonly editAddressButton: Locator;
  readonly editPreferencesButton: Locator;

  // Error Messages
  readonly phoneError: Locator;
  readonly dateOfBirthError: Locator;
  readonly streetAddressError: Locator;
  readonly cityError: Locator;
  readonly postalCodeError: Locator;

  // Page Heading
  readonly pageHeading: Locator;

  constructor(page: Page) {
    super(page);

    // Progress indicator
    this.progressIndicator = page.locator('nav[aria-label="Progress"]');
    this.personalDetailsStepIndicator = page.getByRole('button', { name: /Step 1: Personal Details/i });
    this.addressStepIndicator = page.getByRole('button', { name: /Step 2: Address/i });
    this.preferencesStepIndicator = page.getByRole('button', { name: /Step 3: Preferences/i });
    this.reviewStepIndicator = page.getByRole('button', { name: /Step 4: Review/i });

    // Personal Details Step Fields
    this.phoneCountryCodeSelect = page.locator('button').filter({ hasText: /\+\d/ }).first();
    this.phoneNumberInput = page.getByPlaceholder('555-123-4567');
    this.dateOfBirthInput = page.locator('input[type="date"]');
    this.genderSelect = page.getByRole('combobox').filter({ hasText: /Select gender|Male|Female|Non-binary|Prefer not to say/i }).first();
    this.languageSelect = page.getByRole('combobox').filter({ hasText: /Select language|English|Spanish|French|German/i }).first();
    this.timezoneSelect = page.getByRole('combobox').filter({ hasText: /Select timezone|UTC|Eastern|Central|Mountain|Pacific/i }).first();

    // Address Step Fields
    this.addressTypeSelect = page.getByRole('combobox').filter({ hasText: /Shipping|Billing|Both/ }).first();
    this.addressLabelInput = page.getByPlaceholder('e.g., Home, Office');
    this.streetLine1Input = page.getByPlaceholder('123 Main St');
    this.streetLine2Input = page.getByPlaceholder('Apt 4B');
    this.cityInput = page.getByPlaceholder('City');
    this.stateSelect = page.getByRole('combobox').filter({ hasText: /Select state|Alabama|Alaska|Arizona|California|Colorado|Florida|Georgia|Illinois|New York|Texas|Washington/i }).first();
    this.stateInput = page.getByPlaceholder('State/Province');
    this.postalCodeInput = page.getByPlaceholder('12345');
    this.countrySelect = page.getByRole('combobox').filter({ hasText: /Select country|United States|Canada|United Kingdom/i }).first();
    this.defaultAddressCheckbox = page.getByLabel('Set as default address');

    // Preferences Step Fields
    this.emailNotificationsSwitch = page.getByRole('switch', { name: 'Email notifications' });
    this.smsNotificationsSwitch = page.getByRole('switch', { name: 'SMS notifications' });
    this.pushNotificationsSwitch = page.getByRole('switch', { name: 'Push notifications' });
    this.marketingSwitch = page.getByRole('switch', { name: 'Marketing communications' });
    this.notificationFrequencySelect = page.getByRole('combobox').filter({ hasText: /Select frequency|Immediate|Daily|Weekly/i }).first();

    // Navigation Buttons
    this.continueButton = page.getByRole('button', { name: 'Continue' });
    this.backButton = page.getByRole('button', { name: 'Back' });
    this.skipButton = page.getByRole('button', { name: 'Skip' });
    this.skipThisStepButton = page.getByRole('button', { name: 'Skip This Step' });
    this.reviewButton = page.getByRole('button', { name: 'Review', exact: true });
    this.completeProfileButton = page.getByRole('button', { name: 'Complete Profile' });
    this.skipProfileCompletionLink = page.getByRole('button', { name: /Skip profile completion/i });

    // Review Step Sections
    this.personalDetailsSection = page.locator('div').filter({ hasText: 'Personal Details' }).first();
    this.addressSection = page.locator('div').filter({ hasText: /^Address/ });
    this.preferencesSection = page.locator('div').filter({ hasText: 'Communication Preferences' });
    this.editPersonalDetailsButton = this.personalDetailsSection.getByRole('button', { name: 'Edit' });
    this.editAddressButton = this.addressSection.getByRole('button', { name: 'Edit' });
    this.editPreferencesButton = this.preferencesSection.getByRole('button', { name: 'Edit' });

    // Error Messages
    this.phoneError = page.getByRole('alert').filter({ hasText: /phone/i });
    this.dateOfBirthError = page.getByRole('alert').filter({ hasText: /years old/i });
    this.streetAddressError = page.getByRole('alert').filter({ hasText: /street address/i });
    this.cityError = page.getByRole('alert').filter({ hasText: /city/i });
    this.postalCodeError = page.getByRole('alert').filter({ hasText: /postal code/i });

    // Page Heading
    this.pageHeading = page.getByRole('heading', { name: 'Complete Your Profile' });
  }

  get url(): string {
    return `${config.baseUrl.customer}/profile/complete`;
  }

  // Personal Details Step Methods
  async fillPhoneNumber(countryCode: string, number: string): Promise<void> {
    await this.phoneCountryCodeSelect.click();
    await this.page.getByRole('option', { name: new RegExp(countryCode.replace('+', '\\+')) }).first().click();
    await this.fill(this.phoneNumberInput, number);
  }

  async fillDateOfBirth(date: string): Promise<void> {
    await this.fill(this.dateOfBirthInput, date);
  }

  async selectGender(gender: string): Promise<void> {
    await this.genderSelect.click();
    await this.page.waitForTimeout(100); // Wait for dropdown animation
    await this.page.getByRole('option', { name: gender, exact: true }).first().click();
  }

  async selectLanguage(language: string): Promise<void> {
    await this.languageSelect.click();
    await this.page.waitForTimeout(100); // Wait for dropdown animation
    await this.page.getByRole('option', { name: language }).first().click();
  }

  async selectTimezone(timezone: string): Promise<void> {
    await this.timezoneSelect.click();
    await this.page.waitForTimeout(100); // Wait for dropdown animation
    await this.page.getByRole('option', { name: timezone }).first().click();
  }

  // Address Step Methods
  async fillAddress(data: {
    addressType?: string;
    label?: string;
    streetLine1: string;
    streetLine2?: string;
    city: string;
    state: string;
    postalCode: string;
    country?: string;
    isDefault?: boolean;
  }): Promise<void> {
    if (data.addressType) {
      await this.addressTypeSelect.click();
      await this.page.waitForTimeout(100);
      await this.page.getByRole('option', { name: data.addressType }).first().click();
    }
    if (data.label) {
      await this.fill(this.addressLabelInput, data.label);
    }
    await this.fill(this.streetLine1Input, data.streetLine1);
    if (data.streetLine2) {
      await this.fill(this.streetLine2Input, data.streetLine2);
    }
    await this.fill(this.cityInput, data.city);

    // Handle state - use select for US, input for others
    // Default country is US, so check against both possible values
    const isUS = !data.country || data.country === 'United States' || data.country === 'US';
    if (isUS) {
      await this.stateSelect.click();
      await this.page.waitForTimeout(100);
      await this.page.getByRole('option', { name: data.state }).first().click();
    } else {
      await this.fill(this.stateInput, data.state);
    }

    await this.fill(this.postalCodeInput, data.postalCode);

    if (data.country) {
      await this.countrySelect.click();
      await this.page.waitForTimeout(100);
      await this.page.getByRole('option', { name: data.country }).first().click();
    }

    if (data.isDefault === false) {
      await this.defaultAddressCheckbox.click();
    }
  }

  // Preferences Step Methods
  async toggleEmailNotifications(): Promise<void> {
    await this.emailNotificationsSwitch.click();
  }

  async toggleSmsNotifications(): Promise<void> {
    await this.smsNotificationsSwitch.click();
  }

  async togglePushNotifications(): Promise<void> {
    await this.pushNotificationsSwitch.click();
  }

  async toggleMarketing(): Promise<void> {
    await this.marketingSwitch.click();
  }

  async selectNotificationFrequency(frequency: string): Promise<void> {
    await this.notificationFrequencySelect.click();
    await this.page.getByRole('option', { name: frequency }).click();
  }

  // Navigation Methods
  async clickContinue(): Promise<void> {
    await this.click(this.continueButton);
  }

  async clickBack(): Promise<void> {
    await this.click(this.backButton);
  }

  async clickSkip(): Promise<void> {
    await this.click(this.skipButton);
  }

  async clickSkipThisStep(): Promise<void> {
    await this.click(this.skipThisStepButton);
  }

  async clickReview(): Promise<void> {
    await this.click(this.reviewButton);
  }

  async clickCompleteProfile(): Promise<void> {
    await this.click(this.completeProfileButton);
  }

  async clickSkipProfileCompletion(): Promise<void> {
    await this.click(this.skipProfileCompletionLink);
  }

  // Step Navigation via Progress Indicator
  async goToPersonalDetailsStep(): Promise<void> {
    await this.click(this.personalDetailsStepIndicator);
  }

  async goToAddressStep(): Promise<void> {
    await this.click(this.addressStepIndicator);
  }

  async goToPreferencesStep(): Promise<void> {
    await this.click(this.preferencesStepIndicator);
  }

  async goToReviewStep(): Promise<void> {
    await this.click(this.reviewStepIndicator);
  }

  // Review Step Methods
  async clickEditPersonalDetails(): Promise<void> {
    await this.click(this.editPersonalDetailsButton);
  }

  async clickEditAddress(): Promise<void> {
    await this.click(this.editAddressButton);
  }

  async clickEditPreferences(): Promise<void> {
    await this.click(this.editPreferencesButton);
  }

  // Step Detection
  async getCurrentStepName(): Promise<string> {
    // Wait for the step heading to be stable
    await this.page.waitForLoadState('domcontentloaded');
    const heading = await this.page.locator('h2').first().textContent();
    return heading || '';
  }

  async isOnPersonalDetailsStep(): Promise<boolean> {
    try {
      await this.page.locator('h2', { hasText: 'Personal Details' }).waitFor({ state: 'visible', timeout: 3000 });
      return true;
    } catch {
      return false;
    }
  }

  async isOnAddressStep(): Promise<boolean> {
    try {
      await this.page.locator('h2', { hasText: 'Address' }).waitFor({ state: 'visible', timeout: 3000 });
      return true;
    } catch {
      return false;
    }
  }

  async isOnPreferencesStep(): Promise<boolean> {
    try {
      await this.page.locator('h2', { hasText: 'Preferences' }).waitFor({ state: 'visible', timeout: 3000 });
      return true;
    } catch {
      return false;
    }
  }

  async isOnReviewStep(): Promise<boolean> {
    try {
      await this.page.locator('h2', { hasText: 'Review' }).waitFor({ state: 'visible', timeout: 3000 });
      return true;
    } catch {
      return false;
    }
  }
}
