import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import { BrowseTheWeb } from '@serenity-js/web';
import { config } from '../../config.ts';

const NavigateToHome = Interaction.where(
  the`#actor navigates to the home page`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.goto(config.customerAppUrl);
    await nativePage.waitForLoadState('domcontentloaded');
  }
);

const TypeInSearchBar = (text: string) =>
  Interaction.where(the`#actor types "${text}" in the search bar`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    const header = nativePage.locator('header');
    const input = header.getByTestId('searchInput');
    await input.click();
    await input.fill('');
    await nativePage.keyboard.type(text, { delay: 80 });
  });

const ClearSearchBar = Interaction.where(
  the`#actor clears the search bar`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    const header = nativePage.locator('header');
    const input = header.getByTestId('searchInput');
    await input.click();
    await input.fill('');
  }
);

const WaitForAutocomplete = Interaction.where(
  the`#actor waits for the autocomplete dropdown`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    await nativePage.getByTestId('autocomplete-dropdown').waitFor({ state: 'visible', timeout: 5000 });
    const products = await nativePage.getByTestId('autocomplete-product-item').count();
    const categories = await nativePage.getByTestId('autocomplete-category-item').count();
    console.log(`  autocomplete: ${products} product(s), ${categories} category(ies)`);
  }
);

const WaitForNoAutocomplete = Interaction.where(
  the`#actor confirms autocomplete dropdown is closed`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    await nativePage.getByTestId('autocomplete-dropdown').waitFor({ state: 'hidden', timeout: 3000 });
    console.log('  autocomplete dropdown closed');
  }
);

const ClickProductSuggestion = Interaction.where(
  the`#actor clicks a product suggestion`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    const item = nativePage.getByTestId('autocomplete-product-item').first();
    const text = await item.textContent();
    console.log(`  clicking product suggestion: ${text}`);
    await item.click();
  }
);

const ClickCategorySuggestion = Interaction.where(
  the`#actor clicks a category suggestion`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    const item = nativePage.getByTestId('autocomplete-category-item').first();
    const text = await item.textContent();
    console.log(`  clicking category suggestion: ${text}`);
    await item.click();
  }
);

const PressEscape = Interaction.where(
  the`#actor presses Escape`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.keyboard.press('Escape');
  }
);

const WaitForSearchResults = Interaction.where(
  the`#actor waits for search results`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    await nativePage.getByTestId('searchResultsGrid').waitFor({ timeout: 8000 });
    const count = await nativePage.getByTestId('searchResultCount').textContent();
    console.log(`  results: ${count}`);
  }
);

export const SearchAutocomplete = {
  demonstrateFullFlow: () =>
    Task.where(
      the`#actor demonstrates the search autocomplete experience`,

      // Scene 1: Type "wid" — autocomplete shows Widget products
      NavigateToHome,
      Wait.for(Duration.ofMilliseconds(800)),
      Interaction.where(the`#actor announces scene 1`, async () => {
        console.log('\n  scene 1: type "wid" — autocomplete shows product suggestions');
      }),
      TypeInSearchBar('wid'),
      Wait.for(Duration.ofMilliseconds(500)),
      WaitForAutocomplete,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 2: Click a product suggestion — navigates to search results
      Interaction.where(the`#actor announces scene 2`, async () => {
        console.log('\n  scene 2: click a product suggestion — navigate to search results');
      }),
      ClickProductSuggestion,
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForSearchResults,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 3: Navigate back, type "ele" — category suggestion appears
      Interaction.where(the`#actor announces scene 3`, async () => {
        console.log('\n  scene 3: type "ele" — autocomplete shows category suggestion');
      }),
      NavigateToHome,
      Wait.for(Duration.ofMilliseconds(800)),
      TypeInSearchBar('ele'),
      Wait.for(Duration.ofMilliseconds(500)),
      WaitForAutocomplete,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 4: Press Escape — dropdown closes
      Interaction.where(the`#actor announces scene 4`, async () => {
        console.log('\n  scene 4: press Escape — autocomplete dropdown closes');
      }),
      PressEscape,
      Wait.for(Duration.ofMilliseconds(500)),
      WaitForNoAutocomplete,
      Wait.for(Duration.ofMilliseconds(2000)),

      // Scene 5: Click a category suggestion
      Interaction.where(the`#actor announces scene 5`, async () => {
        console.log('\n  scene 5: type "ele" again — click a category suggestion');
      }),
      ClearSearchBar,
      Wait.for(Duration.ofMilliseconds(500)),
      TypeInSearchBar('ele'),
      Wait.for(Duration.ofMilliseconds(500)),
      WaitForAutocomplete,
      Wait.for(Duration.ofMilliseconds(1500)),
      ClickCategorySuggestion,
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForSearchResults,
      Wait.for(Duration.ofMilliseconds(2500))
    ),
};
