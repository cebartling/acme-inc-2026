import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { RegisterNewAccount } from '../tasks/register-new-account.ts';

export const screenplay = true;

export default async function register(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  const stamp = Date.now();
  const email = `demo-${stamp}@acme.test`;

  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    RegisterNewAccount.withEmail(email)
  );
}
