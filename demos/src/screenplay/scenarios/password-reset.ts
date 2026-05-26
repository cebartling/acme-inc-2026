import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { DemonstratePasswordReset } from '../tasks/demonstrate-password-reset.ts';
import { config } from '../../config.ts';
import { registerAndVerifyUser } from '../../identity-api.ts';

export const screenplay = true;

export default async function passwordReset(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  const stamp = Date.now();
  const email = `demo-reset-${stamp}@acme.test`;
  const originalPassword = config.demoPassword;
  const newPassword = 'NewSecure@Pass1!';

  console.log(`  registering throwaway account ${email}...`);
  const { userId } = await registerAndVerifyUser({ email, password: originalPassword });
  console.log(`  account ready, userId=${userId}`);

  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    DemonstratePasswordReset.forUser(userId, email, newPassword)
  );

  console.log(
    '\n  demo complete — forgot-password, expired-link, and happy-path reset shown'
  );
}
