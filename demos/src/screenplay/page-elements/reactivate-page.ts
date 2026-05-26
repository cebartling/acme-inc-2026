import { By, PageElement } from '@serenity-js/web';

export const ReactivatePage = {
  form: () =>
    PageElement.located(By.css('[data-testid="reactivate-form"]'))
      .describedAs('reactivate account form'),

  emailInput: () =>
    PageElement.located(By.css('[data-testid="reactivate-email-input"]'))
      .describedAs('reactivate email input'),

  passwordInput: () =>
    PageElement.located(By.css('[data-testid="reactivate-password-input"]'))
      .describedAs('reactivate password input'),

  submitButton: () =>
    PageElement.located(By.css('[data-testid="reactivate-submit"]'))
      .describedAs('reactivate submit button'),

  sentConfirmation: () =>
    PageElement.located(By.css('[data-testid="reactivate-sent"]'))
      .describedAs('reactivation email sent confirmation'),
};
