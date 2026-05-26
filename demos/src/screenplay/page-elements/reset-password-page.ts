import { By, PageElement } from '@serenity-js/web';

export const ResetPasswordPage = {
  expiredCard: () =>
    PageElement.located(By.css('[data-testid="reset-password-expired"]'))
      .describedAs('expired/invalid token card'),

  requestNewLink: () =>
    PageElement.located(By.css('a'))
      .describedAs('request a new link'),

  form: () =>
    PageElement.located(By.css('[data-testid="reset-password-form"]'))
      .describedAs('reset password form'),

  newPasswordInput: () =>
    PageElement.located(By.css('[data-testid="reset-password-new-input"]'))
      .describedAs('new password input'),

  confirmPasswordInput: () =>
    PageElement.located(By.css('[data-testid="reset-password-confirm-input"]'))
      .describedAs('confirm new password input'),

  submitButton: () =>
    PageElement.located(By.css('[data-testid="reset-password-submit"]'))
      .describedAs('reset password submit button'),

  successBanner: () =>
    PageElement.located(By.css('[data-testid="reset-password-success"]'))
      .describedAs('password updated success banner'),

  signInNowLink: () =>
    PageElement.located(By.css('[data-testid="reset-password-signin-now"]'))
      .describedAs('sign in now link'),
};
