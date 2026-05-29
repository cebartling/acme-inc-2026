import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import type { UsesAbilities } from '@serenity-js/core';
import { BrowseTheWeb } from '@serenity-js/web';
import { config } from '../../config.ts';

async function getNativePage(actor: UsesAbilities) {
  const page = await BrowseTheWeb.as(actor).currentPage();
  return (page as any).nativePage();
}

const NavigateToSearch = Interaction.where(
  the`#actor navigates to the search page`,
  async (actor) => {
    const nativePage = await getNativePage(actor);
    await nativePage.goto(`${config.customerAppUrl}/search`);
    await nativePage.getByTestId('searchPage').waitFor({ timeout: 5000 });
  }
);

const SearchFor = (query: string) =>
  Interaction.where(the`#actor searches for "${query}"`, async (actor) => {
    const nativePage = await getNativePage(actor);
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
    const nativePage = await getNativePage(actor);
    await nativePage.getByTestId('searchResultsGrid').waitFor({ timeout: 8000 });
    const count = await nativePage.getByTestId('searchResultCount').textContent();
    console.log(`  results: ${count}`);
  }
);

const ClickFirstResult = Interaction.where(
  the`#actor clicks the first search result card`,
  async (actor) => {
    const nativePage = await getNativePage(actor);
    const firstCard = nativePage.getByTestId('searchResultCard').first();
    const productName = await firstCard.getByRole('heading').textContent();
    console.log(`  clicking: ${productName}`);
    await firstCard.click();
  }
);

const WaitForProductDetail = Interaction.where(
  the`#actor waits for the product detail page to render`,
  async (actor) => {
    const nativePage = await getNativePage(actor);
    await nativePage.getByTestId('productDetailPage').waitFor({ timeout: 8000 });
    const name = await nativePage.getByTestId('productName').textContent();
    console.log(`  product detail loaded: ${name}`);
  }
);

const VerifyProductInfo = Interaction.where(
  the`#actor verifies product info is visible`,
  async (actor) => {
    const nativePage = await getNativePage(actor);
    const badge = nativePage.getByTestId('availabilityBadge');
    const availability = await badge.textContent();
    console.log(`  availability: ${availability?.trim()}`);
  }
);

const ClickRelatedProduct = Interaction.where(
  the`#actor clicks the first related product`,
  async (actor) => {
    const nativePage = await getNativePage(actor);
    const relatedSection = nativePage.getByRole('region', { name: 'Related Products' });
    const firstRelated = relatedSection.getByTestId('searchResultCard').first();
    const name = await firstRelated.getByRole('heading').textContent();
    console.log(`  navigating to related product: ${name}`);
    await firstRelated.click();
    await nativePage.getByTestId('productDetailPage').waitFor({ timeout: 8000 });
    const newName = await nativePage.getByTestId('productName').textContent();
    console.log(`  now viewing: ${newName}`);
  }
);

export const ProductDetail = {
  demonstrateFullFlow: () =>
    Task.where(
      the`#actor demonstrates the product detail page experience`,

      // Scene 1: Search and navigate to a product detail page
      NavigateToSearch,
      Wait.for(Duration.ofMilliseconds(800)),
      Interaction.where(the`#actor announces scene 1`, async () => {
        console.log('\n  scene 1: search for "widget" and click a product card');
      }),
      SearchFor('widget'),
      Wait.for(Duration.ofMilliseconds(1500)),
      WaitForResults,
      Wait.for(Duration.ofMilliseconds(1500)),
      ClickFirstResult,
      Wait.for(Duration.ofMilliseconds(1500)),

      // Scene 2: Verify the product detail page content
      Interaction.where(the`#actor announces scene 2`, async () => {
        console.log('\n  scene 2: product detail page renders with name, price, availability');
      }),
      WaitForProductDetail,
      VerifyProductInfo,
      Wait.for(Duration.ofMilliseconds(2500)),

      // Scene 3: Navigate to a related product
      Interaction.where(the`#actor announces scene 3`, async () => {
        console.log('\n  scene 3: click a related product to navigate to its detail page');
      }),
      ClickRelatedProduct,
      Wait.for(Duration.ofMilliseconds(2000))
    ),
};
