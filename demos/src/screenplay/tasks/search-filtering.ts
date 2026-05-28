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

const ToggleCategoryFilter = (category: string) =>
  Interaction.where(the`#actor toggles category filter "${category}"`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    console.log(`  clicking category filter: ${category}`);
    const checkbox = nativePage.locator(`#filter-${category}`);
    await checkbox.click();
  });

const ApplyPricePreset = (label: string) =>
  Interaction.where(the`#actor applies price preset "${label}"`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    console.log(`  applying price preset: ${label}`);
    const filterPanel = nativePage.getByTestId('filterPanel');
    await filterPanel.getByRole('button', { name: label }).click();
  });

const WaitForActiveFiltersBar = Interaction.where(
  the`#actor waits for the active filters bar`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    await nativePage.getByTestId('activeFiltersBar').waitFor({ timeout: 5000 });
    console.log('  active filters bar is visible');
  }
);

const RemoveFilterBadge = (filterLabel: string) =>
  Interaction.where(the`#actor removes the filter badge "${filterLabel}"`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    console.log(`  removing filter badge: ${filterLabel}`);
    await nativePage.getByRole('button', { name: `Remove filter: ${filterLabel}` }).click();
  });

const ClearAllFilters = Interaction.where(
  the`#actor clears all filters`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    console.log('  clicking Clear All Filters');
    await nativePage.getByRole('button', { name: 'Clear All Filters' }).click();
  }
);

const LogFilteredCount = Interaction.where(
  the`#actor checks filtered result count`,
  async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();
    const count = await nativePage.getByTestId('searchResultCount').textContent();
    console.log(`  filtered results: ${count}`);
  }
);

export const SearchFiltering = {
  demonstrateFullFlow: () =>
    Task.where(
      the`#actor demonstrates the search results filtering experience`,

      // Scene 1: Basic search to load results with the filter panel
      NavigateToSearch,
      Wait.for(Duration.ofMilliseconds(800)),
      Interaction.where(the`#actor announces scene 1`, async () => {
        console.log('\n  scene 1: search for "widget" — results and filter panel load');
      }),
      SearchFor('widget'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 2: Apply a category filter
      Interaction.where(the`#actor announces scene 2`, async () => {
        console.log('\n  scene 2: apply a category filter — results narrow');
      }),
      ToggleCategoryFilter('Electronics'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      WaitForActiveFiltersBar,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 3: Stack a price range filter on top
      Interaction.where(the`#actor announces scene 3`, async () => {
        console.log('\n  scene 3: apply a price range filter — $25–$50 preset');
      }),
      ApplyPricePreset('$25 – $50'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      LogFilteredCount,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 4: Remove the price filter badge individually
      Interaction.where(the`#actor announces scene 4`, async () => {
        console.log('\n  scene 4: remove the price filter badge — category filter stays');
      }),
      RemoveFilterBadge('Price: $25 – $50'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 5: Clear all filters
      Interaction.where(the`#actor announces scene 5`, async () => {
        console.log('\n  scene 5: clear all filters — full result set returns');
      }),
      ClearAllFilters,
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(2000))
    ),
};
