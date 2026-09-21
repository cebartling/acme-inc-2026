import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { SearchPage } from '../../pages/customer/search.page.js';

/**
 * Step definitions for US-0004-09 (search service resilience).
 *
 * The search service is stubbed at the network layer with page.route, following the
 * pattern in customer-profile-loading.steps.ts. Requests are counted so scenarios can
 * assert the circuit breaker stops calling a service it knows is down — a bypass is
 * only observable as the absence of a request.
 *
 * Searches after the first are client-side navigations. The breaker lives in module
 * scope, so a full page reload would reset it and no scenario could ever reach the
 * five-failure threshold.
 */

const SEARCH_ROUTE = '**/api/v1/search';

/** Slower than the client's 2s deadline, fast enough to keep scenarios short. */
const SLOW_RESPONSE_MS = 6000;

/** The client reopens the circuit 30s after it opens; allow a margin. */
const RESET_WINDOW_MS = 31000;

interface SearchStub {
  count: number;
  mode: 'fail' | 'slow' | 'healthy';
}

function getSearchPage(world: CustomWorld): SearchPage {
  return new SearchPage(world.page);
}

function getStub(world: CustomWorld): SearchStub {
  const stub = world.getTestData<SearchStub>('searchStub');
  if (!stub) {
    throw new Error('Search service stub was not installed — add a Given step for it first');
  }
  return stub;
}

/**
 * Installs the search stub once per scenario. Later steps flip `mode` on the same
 * object, so recovery does not need a second route registration.
 */
async function installSearchStub(world: CustomWorld, mode: SearchStub['mode']): Promise<void> {
  const existing = world.getTestData<SearchStub>('searchStub');
  if (existing) {
    existing.mode = mode;
    return;
  }

  const stub: SearchStub = { count: 0, mode };
  world.setTestData('searchStub', stub);

  await world.page.route(SEARCH_ROUTE, async (route) => {
    stub.count++;

    if (stub.mode === 'fail') {
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'SERVICE_UNAVAILABLE',
          message: 'Search service is unavailable',
        }),
      });
      return;
    }

    if (stub.mode === 'slow') {
      await new Promise((resolve) => setTimeout(resolve, SLOW_RESPONSE_MS));
    }

    try {
      await route.continue();
    } catch {
      // Expected in 'slow' mode: the client abandons the request at its 2s deadline, and
      // the scenario may finish before this handler wakes up. Continuing a request the
      // browser already aborted — or one whose page is gone — rejects, and an unhandled
      // rejection here would take the Cucumber worker down mid-run.
    }
  });
}

/**
 * Runs one search from wherever we currently are (header bar or search page).
 *
 * Deliberately settles the page before typing and confirms the input actually holds the
 * new term. SearchBar takes `defaultValue={q}`, so filling while the previous navigation
 * is still rendering lets React reset the field to the old query — the search then
 * re-submits the previous term, React Query serves it from cache, and no request is made.
 * That failure looks exactly like the circuit breaker bypassing, so the guard has to be
 * here rather than in the assertions.
 */
async function runSearch(world: CustomWorld, query: string): Promise<void> {
  const page = getSearchPage(world);

  // Wait until the input mirrors the query currently in the URL. SearchBar is a
  // controlled input with `useEffect(() => setValue(defaultValue), [defaultValue])`,
  // so that effect is still pending right after a navigation — typing before it runs
  // means it overwrites what we typed with the previous term.
  const currentQuery = new URL(world.page.url()).searchParams.get('q') ?? '';
  await expect.poll(() => page.getCurrentSearchInputValue(), { timeout: 10000 }).toBe(currentQuery);

  await page.enterSearchQuery(query);
  await expect.poll(() => page.getCurrentSearchInputValue(), { timeout: 5000 }).toBe(query);

  await page.submitSearch();
  await world.page.waitForURL((url) => url.searchParams.get('q') === query, {
    timeout: 10000,
  });
}

// -----------------------------------------------------------------------------
// Given
// -----------------------------------------------------------------------------

Given('the search service is unavailable', async function (this: CustomWorld) {
  await installSearchStub(this, 'fail');
});

Given('the search service is responding slowly', async function (this: CustomWorld) {
  await installSearchStub(this, 'slow');
});

// -----------------------------------------------------------------------------
// When
// -----------------------------------------------------------------------------

When('the search service recovers', async function (this: CustomWorld) {
  getStub(this).mode = 'healthy';
});

When('I search for {string}', async function (this: CustomWorld, query: string) {
  await runSearch(this, query);
  await this.page.waitForLoadState('networkidle');
});

/**
 * Each search uses a distinct term on purpose: React Query caches by query key, so
 * repeating a term would serve the cached failure without issuing a request and the
 * breaker would never see five failures.
 */
When('I run {int} failing searches', async function (this: CustomWorld, times: number) {
  const stub = getStub(this);

  for (let i = 1; i <= times; i++) {
    const expected = stub.count + 1;
    await runSearch(this, `outage-probe-${i}`);
    await expect.poll(() => stub.count, { timeout: 10000 }).toBeGreaterThanOrEqual(expected);
  }

  await getSearchPage(this).unavailableBanner.waitFor({ state: 'visible', timeout: 10000 });
});

When(
  'I wait for the circuit breaker reset window',
  { timeout: RESET_WINDOW_MS + 15000 },
  async function (this: CustomWorld) {
    await this.page.waitForTimeout(RESET_WINDOW_MS);
  }
);

When('I click the search retry button', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await page.clickRetrySearch();
  await this.page.waitForLoadState('networkidle');
});

When('I select the fallback category {string}', async function (this: CustomWorld, name: string) {
  const page = getSearchPage(this);
  await page.selectFallbackCategory(name);
  await page.categoryProductsGrid.waitFor({ state: 'visible', timeout: 10000 });
});

When('I open the first fallback product', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await page.openFirstFallbackProduct();
  await this.page.waitForURL(/\/products\//, { timeout: 10000 });
});

// -----------------------------------------------------------------------------
// Then
// -----------------------------------------------------------------------------

Then('I should see the search unavailable banner', async function (this: CustomWorld) {
  await expect(getSearchPage(this).unavailableBanner).toBeVisible({ timeout: 10000 });
});

Then('I should not see the search unavailable banner', async function (this: CustomWorld) {
  await expect(getSearchPage(this).unavailableBanner).toBeHidden({ timeout: 10000 });
});

Then(
  'I should see the search unavailable banner within {int} seconds',
  async function (this: CustomWorld, seconds: number) {
    await expect(getSearchPage(this).unavailableBanner).toBeVisible({
      timeout: seconds * 1000,
    });
  }
);

Then('the banner should say {string}', async function (this: CustomWorld, text: string) {
  await expect(getSearchPage(this).unavailableBanner).toContainText(text);
});

Then('I should see the search retry button', async function (this: CustomWorld) {
  await expect(getSearchPage(this).retryButton).toBeVisible();
});

Then('I should see the category browsing fallback', async function (this: CustomWorld) {
  await expect(getSearchPage(this).categoryFallback).toBeVisible({ timeout: 10000 });
});

Then('I should see at least one fallback category', async function (this: CustomWorld) {
  const count = await getSearchPage(this).getFallbackCategoryCount();
  expect(count).toBeGreaterThan(0);
});

Then('I should not see the search results grid', async function (this: CustomWorld) {
  await expect(getSearchPage(this).resultsGrid).toBeHidden();
});

Then('I should see fallback products', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.categoryProductsGrid).toBeVisible();
  expect(await page.getFallbackProductCount()).toBeGreaterThan(0);
});

/**
 * Auto-waiting variant of "I should see product cards in the results".
 *
 * The shared step counts cards immediately, which is fine after a normal search but
 * races here: the recovery probe resolves just after the page reaches networkidle, so
 * the grid may not have rendered on the first tick.
 */
Then('I should see search results', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.resultsGrid).toBeVisible({ timeout: 10000 });
  await expect(page.resultCards.first()).toBeVisible({ timeout: 10000 });
  expect(await page.getResultCardCount()).toBeGreaterThan(0);
});

Then('I should be on a product detail page', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(/\/products\/.+/);
});

Then(
  'the search service should have received {int} requests',
  async function (this: CustomWorld, expected: number) {
    expect(getStub(this).count).toBe(expected);
  }
);

/**
 * Proves a bypass: the count must be unchanged after an action that would otherwise
 * have issued a request. Waits first, so a late request still fails the assertion.
 */
Then(
  'the search service should still have received {int} requests',
  async function (this: CustomWorld, expected: number) {
    await this.page.waitForTimeout(1000);
    expect(getStub(this).count).toBe(expected);
  }
);

/**
 * Checks for a crash page rather than for role="alert": the outage banner is an alert
 * by design, so the generic "no error message" step would contradict AC-0004-09-03.
 */
Then('I should not see an error page', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.pageContainer).toBeVisible();

  const body = await this.page.locator('body').innerText();
  expect(body).not.toMatch(/something went wrong|unhandled|application error|500 internal/i);
});

Then('the search input should still be usable', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.pageSearchInput).toBeVisible();
  await expect(page.pageSearchInput).toBeEditable();
});
