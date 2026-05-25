import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class SearchPage extends BasePage {
  readonly searchInput: Locator;
  readonly searchSubmitButton: Locator;
  readonly searchClearButton: Locator;
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

    this.searchInput = page.getByTestId('searchInput');
    this.searchSubmitButton = page.getByTestId('searchSubmitButton');
    this.searchClearButton = page.getByTestId('searchClearButton');
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
    await this.fill(this.searchInput, query);
  }

  async submitSearch(): Promise<void> {
    await this.click(this.searchSubmitButton);
  }

  async clearSearch(): Promise<void> {
    await this.click(this.searchClearButton);
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
    return await this.searchInput.inputValue();
  }

  async getResultCardCount(): Promise<number> {
    return await this.resultCards.count();
  }
}
