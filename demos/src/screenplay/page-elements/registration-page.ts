import { By, PageElement } from '@serenity-js/web';

export const RegistrationPage = {
  heading: () =>
    PageElement.located(By.css('h1'))
      .describedAs('registration page heading'),

  emailField: () =>
    PageElement.located(By.css('input[name="email"]'))
      .describedAs('email input'),

  passwordField: () =>
    PageElement.located(By.css('input[name="password"]'))
      .describedAs('password input'),

  confirmPasswordField: () =>
    PageElement.located(By.css('input[name="confirmPassword"]'))
      .describedAs('confirm password input'),

  firstNameField: () =>
    PageElement.located(By.css('input[name="firstName"]'))
      .describedAs('first name input'),

  lastNameField: () =>
    PageElement.located(By.css('input[name="lastName"]'))
      .describedAs('last name input'),

  tosCheckbox: () =>
    PageElement.located(By.css('#tosAccepted'))
      .describedAs('Terms of Service checkbox'),

  privacyCheckbox: () =>
    PageElement.located(By.css('#privacyPolicyAccepted'))
      .describedAs('Privacy Policy checkbox'),

  createAccountButton: () =>
    PageElement.located(By.css('button[type="submit"]'))
      .describedAs('Create Account button'),
};
