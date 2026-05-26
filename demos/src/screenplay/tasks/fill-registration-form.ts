import { Task, the, Wait, Duration } from '@serenity-js/core';
import { Clear, Click, Enter, Navigate } from '@serenity-js/web';
import { RegistrationPage } from '../page-elements/registration-page.ts';
import { config } from '../../config.ts';

export const FillRegistrationForm = {
  withCredentials: (email: string, password: string) =>
    Task.where(
      the`#actor fills out the registration form for ${email}`,
      Navigate.to(`${config.customerAppUrl}/register`),
      Wait.for(Duration.ofMilliseconds(500)),

      Clear.theValueOf(RegistrationPage.emailField()),
      Enter.theValue(email).into(RegistrationPage.emailField()),
      Wait.for(Duration.ofMilliseconds(400)),

      Clear.theValueOf(RegistrationPage.passwordField()),
      Enter.theValue(password).into(RegistrationPage.passwordField()),
      Clear.theValueOf(RegistrationPage.confirmPasswordField()),
      Enter.theValue(password).into(RegistrationPage.confirmPasswordField()),
      Wait.for(Duration.ofMilliseconds(400)),

      Clear.theValueOf(RegistrationPage.firstNameField()),
      Enter.theValue('Demo').into(RegistrationPage.firstNameField()),
      Clear.theValueOf(RegistrationPage.lastNameField()),
      Enter.theValue('User').into(RegistrationPage.lastNameField()),
      Wait.for(Duration.ofMilliseconds(400)),

      Click.on(RegistrationPage.tosCheckbox()),
      Click.on(RegistrationPage.privacyCheckbox()),
      Wait.for(Duration.ofMilliseconds(400))
    ),
};
