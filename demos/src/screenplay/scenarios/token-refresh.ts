import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { DemonstrateTokenRefresh } from '../tasks/demonstrate-token-refresh.ts';
import { config } from '../../config.ts';
import { registerAndVerifyUser } from '../../identity-api.ts';

export const screenplay = true;

export default async function tokenRefresh(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  const stamp = Date.now();
  const email = `demo-token-refresh-${stamp}@acme.test`;
  const password = config.demoPassword;

  console.log(`  registering throwaway account ${email}...`);
  await registerAndVerifyUser({ email, password });

  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    DemonstrateTokenRefresh.forUser(email, password)
  );
}
