import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class ProductListPage extends BasePage {
  // Locators
  readonly productGrid: Locator;
  readonly productCards: Locator;
  readonly categoryFilter: Locator;
  readonly priceFilter: Locator;
  readonly sortDropdown: Locator;
  readonly searchResults: Locator;
  readonly noResultsMessage: Locator;
  readonly loadMoreButton: Locator;
  readonly productCount: Locator;

  constructor(page: Page) {
    super(page);
    this.productGrid = page.getByTestId('product-grid');
    this.productCards = page.getByTestId('product-card');
    this.categoryFilter = page.getByTestId('category-filter');
    this.priceFilter = page.getByTestId('price-filter');
    this.sortDropdown = page.getByLabel('Sort by');
    this.searchResults = page.getByTestId('search-results');
    this.noResultsMessage = page.getByTestId('no-results');
    this.loadMoreButton = page.getByRole('button', { name: 'Load more' });
    this.productCount = page.getByTestId('product-count');
  }

  get url(): string {
    return `${config.baseUrl.customer}/products`;
  }

  async getProductCount(): Promise<number> {
    return this.productCards.count();
  }

  async clickProduct(productName: string): Promise<void> {
    await this.page.getByRole('link', { name: productName }).click();
  }

  async clickProductByIndex(index: number): Promise<void> {
    await this.productCards.nth(index).click();
  }

  async filterByCategory(category: string): Promise<void> {
    await this.categoryFilter.getByLabel(category).check();
  }

  async filterByPriceRange(min: number, max: number): Promise<void> {
    await this.priceFilter.getByLabel('Min price').fill(min.toString());
    await this.priceFilter.getByLabel('Max price').fill(max.toString());
    await this.page.getByRole('button', { name: 'Apply' }).click();
  }

  async sortBy(option: string): Promise<void> {
    await this.selectOption(this.sortDropdown, option);
  }

  async getProductNames(): Promise<string[]> {
    return this.productCards.locator('[data-testid="product-name"]').allTextContents();
  }

  async getProductPrices(): Promise<number[]> {
    const priceTexts = await this.productCards.locator('[data-testid="product-price"]').allTextContents();
    return priceTexts.map((text) => parseFloat(text.replace(/[^0-9.]/g, '')));
  }

  async addToCartByIndex(index: number): Promise<void> {
    await this.productCards.nth(index).getByRole('button', { name: 'Add to cart' }).click();
  }

  async hasNoResults(): Promise<boolean> {
    return this.isVisible(this.noResultsMessage);
  }

  async loadMore(): Promise<void> {
    await this.click(this.loadMoreButton);
  }
}
