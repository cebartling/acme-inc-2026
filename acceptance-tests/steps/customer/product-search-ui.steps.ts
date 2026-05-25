import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { SearchPage } from '../../pages/customer/search.page.js';

let searchPage: SearchPage;

function getSearchPage(world: CustomWorld): SearchPage {
  if (!searchPage || searchPage['page'] !== world.page) {
    searchPage = new SearchPage(world.page);
  }
  return searchPage;
}

Given('I am on the home page', async function (this: CustomWorld) {
  await this.page.goto(this.getCustomerAppUrl());
  await this.page.waitForLoadState('domcontentloaded');
});

Given('there are more than 24 products matching {string}', async function (
  this: CustomWorld,
  _query: string,
) {
  // Seed data in V2 migration already contains >24 products with "product" in description.
  // This step is a precondition assertion — no action needed.
});

When('I enter a search query {string}', async function (this: CustomWorld, query: string) {
  const page = getSearchPage(this);
  await page.enterSearchQuery(query);
});

When('I submit the search', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await page.submitSearch();
  await this.page.waitForLoadState('networkidle');
});

When('I clear the search input', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await page.clearSearch();
});

When('I click the next page button', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await page.clickNextPage();
  await this.page.waitForLoadState('networkidle');
});

When('I select sort option {string}', async function (this: CustomWorld, optionLabel: string) {
  const page = getSearchPage(this);
  await page.selectSortOption(optionLabel);
  await this.page.waitForLoadState('networkidle');
});

When('I click the spelling suggestion link', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  const currentUrl = this.page.url();
  await page.clickSpellingSuggestion();
  await this.page.waitForURL((url) => url.toString() !== currentUrl, { timeout: 10000 });
  await this.page.waitForLoadState('networkidle');
});

Then('I should be on the search results page', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(/\/search/);
});

Then('I should see a result count above the product grid', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.resultCount).toBeVisible();
});

Then('the result count should contain {string}', async function (
  this: CustomWorld,
  text: string,
) {
  const page = getSearchPage(this);
  await expect(page.resultCount).toContainText(text);
});

Then('I should see product cards in the results', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  const count = await page.getResultCardCount();
  expect(count).toBeGreaterThan(0);
});

Then('I should see the empty search state', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.emptyState).toBeVisible();
});

Then('I should not see an error message', async function (this: CustomWorld) {
  const errorAlert = this.page.locator('[role="alert"]');
  await expect(errorAlert).not.toBeVisible();
});

Then('I should see pagination controls', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.pagination).toBeVisible();
});

Then('the search input should be empty', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  const value = await page.getInputValue();
  expect(value).toBe('');
});

Then('the search results should still be visible', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.resultsGrid).toBeVisible();
});

Then('I should see a spelling suggestion', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.spellingSuggestion).toBeVisible();
});

Then('a new search is executed', async function (this: CustomWorld) {
  // The URL should have changed to contain the suggested query (not "widgit")
  await expect(this.page).toHaveURL(/\/search\?q=(?!widgit)/);
  // Wait for either results or empty state to appear (loading is done)
  const page = getSearchPage(this);
  await expect(page.resultsGrid.or(page.emptyState)).toBeVisible({ timeout: 10000 });
});
