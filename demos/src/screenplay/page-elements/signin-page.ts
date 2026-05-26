import { By, PageElement } from '@serenity-js/web';

export const SigninPage = {
  heading: () =>
    PageElement.located(By.css('h1'))
      .describedAs('sign-in page heading'),

  emailField: () =>
    PageElement.located(By.css('input[name="email"]'))
      .describedAs('email input'),

  passwordField: () =>
    PageElement.located(By.css('input#password'))
      .describedAs('password input'),

  submitButton: () =>
    PageElement.located(By.css('button[type="submit"]'))
      .describedAs('Sign In button'),

  errorBanner: () =>
    PageElement.located(By.css('[data-testid="signin-error-banner"]'))
      .describedAs('sign-in error banner'),

  remainingAttempts: () =>
    PageElement.located(By.css('[data-testid="signin-remaining-attempts"]'))
      .describedAs('remaining attempts indicator'),

  errorResetLink: () =>
    PageElement.located(By.css('[data-testid="signin-error-reset-link"]'))
      .describedAs('reset password link in error banner'),

  inactiveAccountCard: () =>
    PageElement.located(By.css('[data-testid="inactive-account-message"]'))
      .describedAs('inactive account message card'),

  resendVerificationButton: () =>
    PageElement.located(By.css('[data-testid="resend-verification-button"]'))
      .describedAs('resend verification email button'),

  resendVerificationSuccess: () =>
    PageElement.located(By.css('[data-testid="resend-verification-success"]'))
      .describedAs('resend verification success message'),

  contactSupportLink: () =>
    PageElement.located(By.css('[data-testid="contact-support-link"]'))
      .describedAs('contact support link'),

  supportEmailLink: () =>
    PageElement.located(By.css('[data-testid="support-email-link"]'))
      .describedAs('support email link'),

  reactivateAccountLink: () =>
    PageElement.located(By.css('[data-testid="reactivate-account-link"]'))
      .describedAs('reactivate account link'),

  userMenuButton: () =>
    PageElement.located(By.css('button[aria-label*="user menu" i]'))
      .describedAs('user menu button'),

  signOutMenuItem: () =>
    PageElement.located(By.css('[role="menuitem"]'))
      .describedAs('sign out menu item'),

  signOutAllMenuItem: () =>
    PageElement.located(By.css('[role="menuitem"]'))
      .describedAs('sign out all devices menu item'),
};
