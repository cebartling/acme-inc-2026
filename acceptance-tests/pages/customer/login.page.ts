import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class LoginPage extends BasePage {
  // Locators
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly forgotPasswordLink: Locator;
  readonly registerLink: Locator;
  readonly errorMessage: Locator;
  readonly successMessage: Locator;
  readonly rememberMeCheckbox: Locator;

  constructor(page: Page) {
    super(page);
    this.emailInput = page.getByTestId('data-testid Email input field');
    this.passwordInput = page.getByTestId('data-testid Password input field');
    this.loginButton = page.getByRole('button', { name: 'Log in' });
    this.forgotPasswordLink = page.getByRole('link', { name: 'Forgot password?' });
    this.registerLink = page.getByRole('link', { name: 'Create an account' });
    this.errorMessage = page.getByTestId('error-message');
    this.successMessage = page.getByTestId('success-message');
    this.rememberMeCheckbox = page.getByLabel('Remember me');
  }

  get url(): string {
    return `${config.baseUrl.customer}/login`;
  }

  async login(email: string, password: string): Promise<void> {
    await this.fill(this.emailInput, email);
    await this.fill(this.passwordInput, password);
    await this.click(this.loginButton);
  }

  async loginWithRememberMe(email: string, password: string): Promise<void> {
    await this.fill(this.emailInput, email);
    await this.fill(this.passwordInput, password);
    await this.checkCheckbox(this.rememberMeCheckbox);
    await this.click(this.loginButton);
  }

  async getErrorMessage(): Promise<string> {
    return this.getText(this.errorMessage);
  }

  async isErrorDisplayed(): Promise<boolean> {
    return this.isVisible(this.errorMessage);
  }

  async goToForgotPassword(): Promise<void> {
    await this.click(this.forgotPasswordLink);
  }

  async goToRegister(): Promise<void> {
    await this.click(this.registerLink);
  }
}
