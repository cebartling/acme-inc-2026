import { When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { SearchPage } from '../../pages/customer/search.page.js';

function getSearchPage(world: CustomWorld): SearchPage {
  return new SearchPage(world.page);
}

When('I type {string} in the header search bar', async function (this: CustomWorld, text: string) {
  const page = getSearchPage(this);
  await page.typeInSearchBar(text);
  // Wait for debounce + network
  await this.page.waitForTimeout(500);
});

When('I click the first autocomplete suggestion', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await page.clickFirstAutocompleteSuggestion();
  await this.page.waitForLoadState('networkidle');
});

When('I press the Escape key', async function (this: CustomWorld) {
  await this.page.keyboard.press('Escape');
});

Then('I should see the autocomplete dropdown', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await page.waitForAutocomplete();
  await expect(page.autocompleteDropdown).toBeVisible();
});

Then('I should not see the autocomplete dropdown', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  await expect(page.autocompleteDropdown).not.toBeVisible();
});

Then('the autocomplete dropdown should contain suggestions', async function (this: CustomWorld) {
  const page = getSearchPage(this);
  const count = await page.getAutocompleteSuggestionCount();
  expect(count).toBeGreaterThan(0);
});
