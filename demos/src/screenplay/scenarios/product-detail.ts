import { actorCalled, engage } from '@serenity-js/core';
import type { Browser, BrowserContextOptions } from 'playwright';
import { createDemoCast } from '../actors.ts';
import { ProductDetail } from '../tasks/product-detail.ts';

export const screenplay = true;

export default async function productDetail(
  browser: Browser,
  contextOptions: BrowserContextOptions
): Promise<void> {
  engage(createDemoCast(browser, contextOptions));

  await actorCalled('Demo User').attemptsTo(
    ProductDetail.demonstrateFullFlow()
  );

  console.log('\n  demo complete — product detail page, availability badge, and related products navigation shown');
}
