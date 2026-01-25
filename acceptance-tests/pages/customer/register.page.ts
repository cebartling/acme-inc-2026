import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class RegisterPage extends BasePage {
  // Form field locators
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly confirmPasswordInput: Locator;
  readonly firstNameInput: Locator;
  readonly lastNameInput: Locator;
  readonly tosCheckbox: Locator;
  readonly privacyPolicyCheckbox: Locator;
  readonly marketingOptInCheckbox: Locator;
  readonly submitButton: Locator;

  // Password visibility toggles
  readonly passwordToggle: Locator;
  readonly confirmPasswordToggle: Locator;

  // Password strength indicator
  readonly passwordStrengthIndicator: Locator;
  readonly passwordStrengthLabel: Locator;

  // Error messages
  readonly emailError: Locator;
  readonly passwordError: Locator;
  readonly confirmPasswordError: Locator;
  readonly firstNameError: Locator;
  readonly lastNameError: Locator;
  readonly tosError: Locator;
  readonly privacyPolicyError: Locator;

  // Success indicators
  readonly emailSuccess: Locator;
  readonly passwordSuccess: Locator;
  readonly confirmPasswordSuccess: Locator;
  readonly firstNameSuccess: Locator;
  readonly lastNameSuccess: Locator;

  // Character counters
  readonly firstNameCounter: Locator;
  readonly lastNameCounter: Locator;

  // Page elements
  readonly pageHeading: Locator;
  readonly loginLink: Locator;

  constructor(page: Page) {
    super(page);

    // Form fields - use appropriate locators for each field type
    this.emailInput = page.getByRole('textbox', { name: 'Email' });
    // Password inputs are type="password", not textbox role
    this.passwordInput = page.locator('input[name="password"]');
    this.confirmPasswordInput = page.locator('input[name="confirmPassword"]');
    this.firstNameInput = page.getByLabel('First Name');
    this.lastNameInput = page.getByLabel('Last Name');
    this.tosCheckbox = page.getByLabel(/I accept the Terms of Service/);
    this.privacyPolicyCheckbox = page.getByLabel(/I accept the Privacy Policy/);
    this.marketingOptInCheckbox = page.getByLabel(/marketing emails/);
    this.submitButton = page.getByRole('button', { name: 'Create Account' });

    // Password toggles
    this.passwordToggle = page
      .getByRole('button', { name: /show password|hide password/i })
      .first();
    this.confirmPasswordToggle = page
      .getByRole('button', { name: /show password|hide password/i })
      .last();

    // Password strength
    this.passwordStrengthIndicator = page.locator('[class*="bg-"][class*="-500"]').first();
    this.passwordStrengthLabel = page.getByText(/^(Weak|Fair|Good|Strong)$/);

    // Error messages (using role="alert" for accessibility)
    this.emailError = page.getByRole('alert').filter({ hasText: /email/i });
    this.passwordError = page
      .getByRole('alert')
      .filter({ hasText: /password/i })
      .first();
    this.confirmPasswordError = page.getByRole('alert').filter({ hasText: /match/i });
    this.firstNameError = page.getByRole('alert').filter({ hasText: /first name/i });
    this.lastNameError = page.getByRole('alert').filter({ hasText: /last name/i });
    this.tosError = page.getByRole('alert').filter({ hasText: /Terms of Service/i });
    this.privacyPolicyError = page.getByRole('alert').filter({ hasText: /Privacy Policy/i });

    // Success indicators (check icons)
    this.emailSuccess = page.getByLabel('Email').locator('..').locator('..').getByLabel('Valid');
    this.passwordSuccess = page
      .locator('label:has-text("Password")')
      .locator('..')
      .getByLabel('Valid');
    this.confirmPasswordSuccess = page
      .locator('label:has-text("Confirm Password")')
      .locator('..')
      .getByLabel('Valid');
    this.firstNameSuccess = page
      .locator('label:has-text("First Name")')
      .locator('..')
      .getByLabel('Valid');
    this.lastNameSuccess = page
      .locator('label:has-text("Last Name")')
      .locator('..')
      .getByLabel('Valid');

    // Character counters
    this.firstNameCounter = page.getByText(/\d+\/50/).first();
    this.lastNameCounter = page.getByText(/\d+\/50/).last();

    // Page elements
    this.pageHeading = page.getByRole('heading', { name: 'Welcome to ACME' });
    this.loginLink = page.getByRole('link', { name: 'Sign in' });
  }

  get url(): string {
    return `${config.baseUrl.customer}/register`;
  }

  async fillEmail(email: string): Promise<void> {
    await this.fill(this.emailInput, email);
  }

  async fillPassword(password: string): Promise<void> {
    await this.fill(this.passwordInput, password);
  }

  async fillConfirmPassword(password: string): Promise<void> {
    await this.fill(this.confirmPasswordInput, password);
  }

  async fillFirstName(firstName: string): Promise<void> {
    await this.fill(this.firstNameInput, firstName);
  }

  async fillLastName(lastName: string): Promise<void> {
    await this.fill(this.lastNameInput, lastName);
  }

  async acceptTermsOfService(): Promise<void> {
    // Radix UI checkboxes are buttons, use click instead of check
    await this.tosCheckbox.click();
  }

  async acceptPrivacyPolicy(): Promise<void> {
    // Radix UI checkboxes are buttons, use click instead of check
    await this.privacyPolicyCheckbox.click();
  }

  async optInToMarketing(): Promise<void> {
    // Radix UI checkboxes are buttons, use click instead of check
    await this.marketingOptInCheckbox.click();
  }

  async submitForm(): Promise<void> {
    await this.click(this.submitButton);
  }

  async togglePasswordVisibility(): Promise<void> {
    await this.click(this.passwordToggle);
  }

  async toggleConfirmPasswordVisibility(): Promise<void> {
    await this.click(this.confirmPasswordToggle);
  }

  async blurEmail(): Promise<void> {
    await this.emailInput.blur();
  }

  async blurPassword(): Promise<void> {
    await this.passwordInput.blur();
  }

  async blurConfirmPassword(): Promise<void> {
    await this.confirmPasswordInput.blur();
  }

  async blurFirstName(): Promise<void> {
    await this.firstNameInput.blur();
  }

  async blurLastName(): Promise<void> {
    await this.lastNameInput.blur();
  }

  async fillRegistrationForm(data: {
    email: string;
    password: string;
    confirmPassword: string;
    firstName: string;
    lastName: string;
    acceptTos?: boolean;
    acceptPrivacy?: boolean;
    optInMarketing?: boolean;
  }): Promise<void> {
    await this.fillEmail(data.email);
    await this.fillPassword(data.password);
    await this.fillConfirmPassword(data.confirmPassword);
    await this.fillFirstName(data.firstName);
    await this.fillLastName(data.lastName);

    if (data.acceptTos !== false) {
      await this.acceptTermsOfService();
    }

    if (data.acceptPrivacy !== false) {
      await this.acceptPrivacyPolicy();
    }

    if (data.optInMarketing) {
      await this.optInToMarketing();
    }
  }

  async isSubmitButtonEnabled(): Promise<boolean> {
    return this.isEnabled(this.submitButton);
  }

  async isSubmitButtonDisabled(): Promise<boolean> {
    return !(await this.isEnabled(this.submitButton));
  }

  async getPasswordStrengthLabel(): Promise<string> {
    return this.getText(this.passwordStrengthLabel);
  }

  async isPasswordStrengthVisible(): Promise<boolean> {
    return this.isVisible(this.passwordStrengthLabel);
  }

  async isPasswordFieldMasked(): Promise<boolean> {
    const type = await this.passwordInput.getAttribute('type');
    return type === 'password';
  }

  async isConfirmPasswordFieldMasked(): Promise<boolean> {
    const type = await this.confirmPasswordInput.getAttribute('type');
    return type === 'password';
  }

  async getFirstNameValue(): Promise<string> {
    return (await this.firstNameInput.inputValue()) || '';
  }

  async getLastNameValue(): Promise<string> {
    return (await this.lastNameInput.inputValue()) || '';
  }

  async goToLogin(): Promise<void> {
    await this.click(this.loginLink);
  }
}
