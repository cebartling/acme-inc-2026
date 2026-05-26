import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { SearchProducts } from '../tasks/search-products.ts';

export const screenplay = true;

export default async function productSearch(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    SearchProducts.demonstrateFullFlow()
  );

  console.log('\n  demo complete — search, sort, spelling suggestion, and clear shown');
}
