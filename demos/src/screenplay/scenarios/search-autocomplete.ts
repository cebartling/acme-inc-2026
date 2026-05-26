import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { SearchAutocomplete } from '../tasks/search-autocomplete.ts';

export const screenplay = true;

export default async function searchAutocomplete(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    SearchAutocomplete.demonstrateFullFlow()
  );

  console.log('\n  demo complete — autocomplete suggestions, selection, keyboard nav, and dismiss shown');
}
