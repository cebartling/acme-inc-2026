import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { DemonstrateInactiveAccount } from '../tasks/demonstrate-inactive-account.ts';
import { config } from '../../config.ts';
import { registerAndVerifyUser } from '../../identity-api.ts';

export const screenplay = true;

export default async function inactiveAccount(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  const stamp = Date.now();
  const email = `demo-inactive-${stamp}@acme.test`;
  const password = config.demoPassword;

  console.log(`  registering throwaway account ${email}...`);
  const { userId } = await registerAndVerifyUser({ email, password });

  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    DemonstrateInactiveAccount.forUser(userId, email, password)
  );

  console.log('  demo complete — three card variants + reactivation flow shown');
}
