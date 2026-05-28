import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { SearchFiltering } from '../tasks/search-filtering.ts';

export const screenplay = true;

export default async function searchFiltering(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    SearchFiltering.demonstrateFullFlow()
  );

  console.log('\n  demo complete — category filter, price range, active filter badges, and clear all shown');
}
