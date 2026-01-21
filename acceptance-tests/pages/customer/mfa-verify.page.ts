import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

/**
 * Page object for the MFA Verification page.
 *
 * Handles interactions with the MFA code entry form and method switcher.
 */
export class MfaVerifyPage extends BasePage {
  // Method switcher buttons
  readonly authenticatorButton: Locator;
  readonly smsButton: Locator;

  // Code input field
  readonly codeInput: Locator;

  // Remember device checkbox
  readonly rememberDeviceCheckbox: Locator;

  // Submit button
  readonly submitButton: Locator;

  // SMS-specific elements
  readonly resendCodeButton: Locator;
  readonly maskedPhoneText: Locator;

  // Error and status messages
  readonly errorMessage: Locator;
  readonly remainingAttemptsText: Locator;

  // Page heading
  readonly pageHeading: Locator;

  // Expired session card
  readonly expiredSessionCard: Locator;
  readonly backToSignInButton: Locator;

  constructor(page: Page) {
    super(page);

    // Method switcher buttons - identified by text content
    this.authenticatorButton = page.getByRole('button', { name: /authenticator/i });
    this.smsButton = page.getByRole('button', { name: /sms/i });

    // Code input - 6-digit OTP input
    this.codeInput = page.getByRole('textbox', { name: /code|verification/i });

    // Remember device checkbox
    this.rememberDeviceCheckbox = page.getByLabel(/remember|trust this device/i);

    // Submit button
    this.submitButton = page.getByRole('button', { name: /verify|submit/i });

    // SMS-specific
    this.resendCodeButton = page.getByRole('button', { name: /resend/i });
    this.maskedPhoneText = page.locator('text=/\\*\\*\\*-\\*\\*\\*-\\d{4}/');

    // Error messages
    this.errorMessage = page.getByRole('alert');
    this.remainingAttemptsText = page.locator('text=/\\d+ attempt/i');

    // Page heading
    this.pageHeading = page.getByRole('heading', { name: /verify your identity/i });

    // Expired session
    this.expiredSessionCard = page.locator('text=/session expired/i');
    this.backToSignInButton = page.getByRole('link', { name: /back to sign in/i });
  }

  get url(): string {
    return `${config.baseUrl.customer}/mfa-verify`;
  }

  /**
   * Navigate to MFA verify page with token and email in session storage.
   * This simulates arriving at the page after a successful credential validation.
   */
  async navigateWithMfaState(mfaToken: string, email: string, mfaMethods: string[]): Promise<void> {
    // First navigate to the base customer app to set session storage
    await this.page.goto(config.baseUrl.customer);
    await this.page.waitForLoadState('domcontentloaded');

    // Set MFA state in session storage
    await this.page.evaluate(
      ({ token, email, methods }) => {
        sessionStorage.setItem(
          'mfaState',
          JSON.stringify({
            mfaToken: token,
            email: email,
            mfaMethods: methods,
          })
        );
      },
      { token: mfaToken, email, methods: mfaMethods }
    );

    // Now navigate to the MFA verify page
    await this.page.goto(this.url);
    await this.waitForPageLoad();
  }

  /**
   * Check if the method switcher is visible (both TOTP and SMS buttons).
   */
  async isMethodSwitcherVisible(): Promise<boolean> {
    const totpVisible = await this.authenticatorButton.isVisible();
    const smsVisible = await this.smsButton.isVisible();
    return totpVisible && smsVisible;
  }

  /**
   * Get the currently selected MFA method based on button styling.
   */
  async getCurrentMethod(): Promise<'TOTP' | 'SMS' | null> {
    // Check which button has the "default" variant (not outline)
    const totpVariant = await this.authenticatorButton.getAttribute('data-variant');
    const smsVariant = await this.smsButton.getAttribute('data-variant');

    // If TOTP button is default (not outline), TOTP is selected
    if (totpVariant !== 'outline') {
      return 'TOTP';
    }
    if (smsVariant !== 'outline') {
      return 'SMS';
    }

    // Fallback: check class names for variant styling
    const totpClass = await this.authenticatorButton.getAttribute('class');
    const smsClass = await this.smsButton.getAttribute('class');

    // Default variant typically has primary background color
    if (totpClass?.includes('bg-primary') || totpClass?.includes('bg-slate')) {
      return 'TOTP';
    }
    if (smsClass?.includes('bg-primary') || smsClass?.includes('bg-slate')) {
      return 'SMS';
    }

    return null;
  }

  /**
   * Switch to TOTP/Authenticator method.
   */
  async switchToAuthenticator(): Promise<void> {
    await this.authenticatorButton.click();
  }

  /**
   * Switch to SMS method.
   */
  async switchToSms(): Promise<void> {
    await this.smsButton.click();
  }

  /**
   * Enter the verification code.
   */
  async enterCode(code: string): Promise<void> {
    // OTP inputs may be multiple single-digit inputs or one input
    const inputs = this.page.locator('input[type="text"], input[inputmode="numeric"]');
    const count = await inputs.count();

    if (count === 6) {
      // Six separate inputs - enter one digit each
      for (let i = 0; i < 6; i++) {
        await inputs.nth(i).fill(code[i]);
      }
    } else {
      // Single input field
      await this.codeInput.fill(code);
    }
  }

  /**
   * Submit the verification form.
   */
  async submitForm(): Promise<void> {
    await this.submitButton.click();
  }

  /**
   * Click the resend code button.
   */
  async clickResendCode(): Promise<void> {
    await this.resendCodeButton.click();
  }

  /**
   * Check if the resend button is disabled (in cooldown).
   */
  async isResendButtonDisabled(): Promise<boolean> {
    return !(await this.resendCodeButton.isEnabled());
  }

  /**
   * Get the resend cooldown text if visible.
   */
  async getResendCooldownText(): Promise<string | null> {
    const text = await this.resendCodeButton.textContent();
    if (text?.includes('in')) {
      return text;
    }
    return null;
  }

  /**
   * Check if the SMS method shows the masked phone number.
   */
  async isMaskedPhoneVisible(): Promise<boolean> {
    return this.maskedPhoneText.isVisible();
  }

  /**
   * Check if the submit button is enabled.
   */
  async isSubmitButtonEnabled(): Promise<boolean> {
    return this.submitButton.isEnabled();
  }

  /**
   * Get the error message text if visible.
   */
  async getErrorMessage(): Promise<string | null> {
    if (await this.errorMessage.isVisible()) {
      return this.errorMessage.textContent();
    }
    return null;
  }
}
