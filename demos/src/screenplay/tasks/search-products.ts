import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import { BrowseTheWeb } from '@serenity-js/web';
import { config } from '../../config.ts';

const NavigateToSearch = Interaction.where(
  the`#actor navigates to the search page`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.goto(`${config.customerAppUrl}/search`);
    await nativePage.getByTestId('searchPage').waitFor({ timeout: 5000 });
  }
);

const SearchFor = (query: string) =>
  Interaction.where(the`#actor searches for "${query}"`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    const searchPage = nativePage.getByTestId('searchPage');
    const input = searchPage.getByTestId('searchInput');
    await input.clear();
    await input.fill(query);
    await nativePage.waitForTimeout(300);
    await searchPage.getByTestId('searchSubmitButton').click();
  });

const WaitForResults = Interaction.where(
  the`#actor waits for search results to load`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.getByTestId('searchResultsGrid').waitFor({ timeout: 8000 });
    const count = await nativePage.getByTestId('searchResultCount').textContent();
    console.log(`  results: ${count}`);
  }
);

const WaitForEmptyState = Interaction.where(
  the`#actor sees the empty state`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.getByTestId('searchEmptyState').waitFor({ timeout: 8000 });
    console.log('  no results found — empty state displayed');
  }
);

const ClickSpellingSuggestion = Interaction.where(
  the`#actor clicks the spelling suggestion`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    const suggestion = nativePage.getByTestId('searchSpellingSuggestion');
    const text = await suggestion.textContent();
    console.log(`  clicking spelling suggestion: ${text}`);
    await suggestion.click();
  }
);

const ChangeSortTo = (optionValue: string, label: string) =>
  Interaction.where(the`#actor changes sort to "${label}"`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    console.log(`  changing sort to: ${label}`);
    await nativePage.getByTestId('searchSortSelector').selectOption(optionValue);
  });

const ClearSearchInput = Interaction.where(
  the`#actor clears the search input`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    console.log('  clearing search input via × button');
    const searchPage = nativePage.getByTestId('searchPage');
    await searchPage.getByTestId('searchClearButton').click();
  }
);

export const SearchProducts = {
  withQuery: (query: string) =>
    Task.where(
      the`#actor searches for products matching "${query}"`,
      NavigateToSearch,
      Wait.for(Duration.ofMilliseconds(500)),
      SearchFor(query),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(2000))
    ),

  demonstrateFullFlow: () =>
    Task.where(
      the`#actor demonstrates the complete product search experience`,

      // Scene 1: Basic search with results
      NavigateToSearch,
      Wait.for(Duration.ofMilliseconds(800)),
      Interaction.where(the`#actor announces scene 1`, async () => {
        console.log('\n  scene 1: search for "widget" — expect results grid');
      }),
      SearchFor('widget'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 2: Sort by price
      Interaction.where(the`#actor announces scene 2`, async () => {
        console.log('\n  scene 2: sort results by price (low to high)');
      }),
      ChangeSortTo('price_asc', 'Price: Low to High'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 3: Sort by price descending
      Interaction.where(the`#actor announces scene 3`, async () => {
        console.log('\n  scene 3: sort results by price (high to low)');
      }),
      ChangeSortTo('price_desc', 'Price: High to Low'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 4: Misspelled query → spelling suggestion
      Interaction.where(the`#actor announces scene 4`, async () => {
        console.log('\n  scene 4: search for "widgit" (typo) — expect spelling suggestion');
      }),
      SearchFor('widgit'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForEmptyState,
      Wait.for(Duration.ofMilliseconds(2000)),
      ClickSpellingSuggestion,
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 5: Clear input
      Interaction.where(the`#actor announces scene 5`, async () => {
        console.log('\n  scene 5: clear search input using × button');
      }),
      ClearSearchInput,
      Wait.for(Duration.ofMilliseconds(2000))
    ),
};
