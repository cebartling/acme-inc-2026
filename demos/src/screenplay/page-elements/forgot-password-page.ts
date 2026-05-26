import { By, PageElement } from '@serenity-js/web';

export const ForgotPasswordPage = {
  heading: () =>
    PageElement.located(By.css('h1'))
      .describedAs('forgot password heading'),

  emailInput: () =>
    PageElement.located(By.css('[data-testid="forgot-password-email-input"]'))
      .describedAs('forgot password email input'),

  submitButton: () =>
    PageElement.located(By.css('[data-testid="forgot-password-submit"]'))
      .describedAs('forgot password submit button'),

  sentConfirmation: () =>
    PageElement.located(By.css('[data-testid="forgot-password-sent"]'))
      .describedAs('check your inbox confirmation'),
};
