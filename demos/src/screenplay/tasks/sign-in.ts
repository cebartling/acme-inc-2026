import { Duration, Task, the, Wait } from '@serenity-js/core';
import { Clear, Click, Enter, Key, Navigate, Press } from '@serenity-js/web';
import { SigninPage } from '../page-elements/signin-page.ts';
import { config } from '../../config.ts';

export const SignIn = {
  withCredentials: (email: string, password: string) =>
    Task.where(
      the`#actor signs in as ${email}`,
      Navigate.to(`${config.customerAppUrl}/signin`),
      Wait.for(Duration.ofMilliseconds(500)),

      Clear.theValueOf(SigninPage.emailField()),
      Enter.theValue(email).into(SigninPage.emailField()),
      Wait.for(Duration.ofMilliseconds(400)),

      Clear.theValueOf(SigninPage.passwordField()),
      Enter.theValue(password).into(SigninPage.passwordField()),
      // Blur the password field so the onBlur-validated form revalidates
      // and enables the submit button before we click it.
      Press.the(Key.Tab).in(SigninPage.passwordField()),
      Wait.for(Duration.ofMilliseconds(600)),

      Click.on(SigninPage.submitButton()),
      Wait.for(Duration.ofMilliseconds(2000))
    ),
};
