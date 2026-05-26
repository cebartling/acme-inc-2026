import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import { Click } from '@serenity-js/web';
import { FillRegistrationForm } from './fill-registration-form.ts';
import { RegistrationPage } from '../page-elements/registration-page.ts';
import { registerAndVerifyUser } from '../../identity-api.ts';
import { saveRegisteredAccount } from '../../state.ts';
import { config } from '../../config.ts';

export const RegisterNewAccount = {
  withEmail: (email: string) => {
    const password = config.demoPassword;

    const verifyViaApi = Interaction.where(
      the`#actor verifies the account via the identity API`,
      async () => {
        await registerAndVerifyUser({ email, password });
        await saveRegisteredAccount({ email, password });
      }
    );

    return Task.where(
      the`#actor registers a new account as ${email}`,
      FillRegistrationForm.withCredentials(email, password),
      Click.on(RegistrationPage.createAccountButton()),
      Wait.for(Duration.ofMilliseconds(2000)),
      verifyViaApi
    );
  },
};
