import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class SearchPage extends BasePage {
  readonly headerSearchInput: Locator;
  readonly headerSearchSubmit: Locator;

  readonly pageContainer: Locator;
  readonly pageSearchInput: Locator;
  readonly pageSearchClearButton: Locator;
  readonly pageSearchSubmitButton: Locator;
  readonly resultCount: Locator;
  readonly resultsGrid: Locator;
  readonly resultCards: Locator;
  readonly emptyState: Locator;
  readonly spellingSuggestion: Locator;
  readonly pagination: Locator;
  readonly nextPageButton: Locator;
  readonly previousPageButton: Locator;
  readonly sortSelector: Locator;

  constructor(page: Page) {
    super(page);

    // Header search bar (visible on all pages)
    const header = page.locator('header');
    this.headerSearchInput = header.getByTestId('searchInput');
    this.headerSearchSubmit = header.getByTestId('searchSubmitButton');

    // Search page container (only on /search route)
    this.pageContainer = page.getByTestId('searchPage');
    this.pageSearchInput = this.pageContainer.getByTestId('searchInput');
    this.pageSearchClearButton = this.pageContainer.getByTestId('searchClearButton');
    this.pageSearchSubmitButton = this.pageContainer.getByTestId('searchSubmitButton');

    // Results (always on search page)
    this.resultCount = page.getByTestId('searchResultCount');
    this.resultsGrid = page.getByTestId('searchResultsGrid');
    this.resultCards = page.getByTestId('searchResultCard');
    this.emptyState = page.getByTestId('searchEmptyState');
    this.spellingSuggestion = page.getByTestId('searchSpellingSuggestion');
    this.pagination = page.getByTestId('searchPagination');
    this.nextPageButton = page.getByRole('button', { name: /next/i });
    this.previousPageButton = page.getByRole('button', { name: /previous/i });
    this.sortSelector = page.getByTestId('searchSortSelector');
  }

  get url(): string {
    return `${config.baseUrl.customer}/search`;
  }

  async enterSearchQuery(query: string): Promise<void> {
    const onSearchPage = await this.pageContainer.isVisible().catch(() => false);
    const input = onSearchPage ? this.pageSearchInput : this.headerSearchInput;
    await this.fill(input, query);
  }

  async submitSearch(): Promise<void> {
    const onSearchPage = await this.pageContainer.isVisible().catch(() => false);
    const button = onSearchPage ? this.pageSearchSubmitButton : this.headerSearchSubmit;
    await this.click(button);
  }

  async clearSearch(): Promise<void> {
    await this.click(this.pageSearchClearButton);
  }

  async selectSortOption(optionLabel: string): Promise<void> {
    await this.sortSelector.selectOption({ label: optionLabel });
  }

  async clickNextPage(): Promise<void> {
    await this.click(this.nextPageButton);
  }

  async clickSpellingSuggestion(): Promise<void> {
    await this.click(this.spellingSuggestion);
  }

  async getInputValue(): Promise<string> {
    return await this.pageSearchInput.inputValue();
  }

  async getResultCardCount(): Promise<number> {
    return await this.resultCards.count();
  }
}
