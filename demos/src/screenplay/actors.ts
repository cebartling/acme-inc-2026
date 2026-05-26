import { Cast } from '@serenity-js/core';
import { BrowseTheWebWithPlaywright } from '@serenity-js/playwright';
import type { Browser, BrowserContextOptions } from 'playwright';

export function createDemoCast(
  browser: Browser,
  contextOptions?: BrowserContextOptions
): Cast {
  return Cast.where((actor) =>
    actor.whoCan(BrowseTheWebWithPlaywright.using(browser, contextOptions))
  );
}
