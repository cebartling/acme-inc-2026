import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { SignIn } from '../tasks/sign-in.ts';
import { config } from '../../config.ts';
import { loadRegisteredAccount } from '../../state.ts';

export const screenplay = true;

export default async function signin(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  const registered = await loadRegisteredAccount();
  const email = registered?.email ?? config.demoEmail;
  const password = registered?.password ?? config.demoPassword;

  if (registered) {
    console.log(`  using account from last register run: ${email}`);
  } else {
    console.log(`  using DEMO_EMAIL env default: ${email}`);
  }

  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    SignIn.withCredentials(email, password)
  );
}
