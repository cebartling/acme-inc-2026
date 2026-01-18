import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class SigninPage extends BasePage {
  // Form field locators
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly rememberMeCheckbox: Locator;
  readonly submitButton: Locator;

  // Password visibility toggle
  readonly passwordToggle: Locator;

  // Error messages
  readonly emailError: Locator;
  readonly passwordError: Locator;
  readonly apiError: Locator;

  // Success indicators
  readonly emailSuccess: Locator;

  // Navigation links
  readonly forgotPasswordLink: Locator;
  readonly registrationLink: Locator;

  // Page elements
  readonly pageHeading: Locator;
  readonly logoutMessage: Locator;

  constructor(page: Page) {
    super(page);

    // Form fields - use appropriate locators for each field type
    this.emailInput = page.getByRole('textbox', { name: 'Email' });
    // Password input is type="password", not textbox role
    this.passwordInput = page.locator('input[id="password"]');
    this.rememberMeCheckbox = page.getByLabel(/remember me/i);
    this.submitButton = page.getByRole('button', { name: /sign in/i });

    // Password toggle
    this.passwordToggle = page.getByRole('button', { name: /show password|hide password/i });

    // Error messages (using role="alert" for accessibility)
    this.emailError = page.getByRole('alert').filter({ hasText: /email/i });
    this.passwordError = page.getByRole('alert').filter({ hasText: /password/i });
    this.apiError = page.locator('div[role="alert"]').filter({ hasText: /invalid|error/i });

    // Success indicators (check icons)
    this.emailSuccess = page.getByLabel('Valid').first();

    // Navigation links
    this.forgotPasswordLink = page.getByRole('link', { name: /forgot password/i });
    this.registrationLink = page.getByRole('link', { name: /create one/i });

    // Page elements
    this.pageHeading = page.getByRole('heading', { name: /welcome back/i });
    this.logoutMessage = page.getByRole('status').filter({ hasText: /signed out/i });
  }

  get url(): string {
    return `${config.baseUrl.customer}/signin`;
  }

  async fillEmail(email: string): Promise<void> {
    await this.fill(this.emailInput, email);
  }

  async fillPassword(password: string): Promise<void> {
    await this.fill(this.passwordInput, password);
  }

  async blurEmail(): Promise<void> {
    await this.emailInput.blur();
  }

  async blurPassword(): Promise<void> {
    await this.passwordInput.blur();
  }

  async checkRememberMe(): Promise<void> {
    // Radix UI checkboxes are buttons, use click
    await this.rememberMeCheckbox.click();
  }

  async submitForm(): Promise<void> {
    await this.click(this.submitButton);
  }

  async togglePasswordVisibility(): Promise<void> {
    await this.click(this.passwordToggle);
  }

  async isSubmitButtonEnabled(): Promise<boolean> {
    return this.isEnabled(this.submitButton);
  }

  async isSubmitButtonDisabled(): Promise<boolean> {
    return !(await this.isEnabled(this.submitButton));
  }

  async isPasswordFieldMasked(): Promise<boolean> {
    const type = await this.passwordInput.getAttribute('type');
    return type === 'password';
  }

  async isRememberMeChecked(): Promise<boolean> {
    const ariaChecked = await this.rememberMeCheckbox.getAttribute('aria-checked');
    const dataState = await this.rememberMeCheckbox.getAttribute('data-state');
    return ariaChecked === 'true' || dataState === 'checked';
  }

  async goToForgotPassword(): Promise<void> {
    await this.click(this.forgotPasswordLink);
  }

  async goToRegistration(): Promise<void> {
    await this.click(this.registrationLink);
  }

  async fillSigninForm(data: { email: string; password: string; rememberMe?: boolean }): Promise<void> {
    await this.fillEmail(data.email);
    await this.fillPassword(data.password);

    if (data.rememberMe) {
      await this.checkRememberMe();
    }
  }
}
