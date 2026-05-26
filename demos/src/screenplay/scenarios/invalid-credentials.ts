import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { AttemptInvalidSignin } from '../tasks/attempt-invalid-signin.ts';
import { config } from '../../config.ts';
import { registerAndVerifyUser } from '../../identity-api.ts';

export const screenplay = true;

export default async function invalidCredentials(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  const stamp = Date.now();
  const email = `demo-invalid-${stamp}@acme.test`;
  const password = config.demoPassword;

  console.log(`  registering throwaway account ${email}...`);
  await registerAndVerifyUser({ email, password });

  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    AttemptInvalidSignin.showingProgressiveWarnings(email)
  );

  console.log('  demo complete — stopping before lockout threshold');
}
