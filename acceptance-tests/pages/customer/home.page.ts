import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class HomePage extends BasePage {
  // Locators
  readonly heroSection: Locator;
  readonly searchInput: Locator;
  readonly searchButton: Locator;
  readonly featuredProducts: Locator;
  readonly categoryLinks: Locator;
  readonly loginLink: Locator;
  readonly registerLink: Locator;
  readonly cartIcon: Locator;
  readonly cartBadge: Locator;
  readonly userMenu: Locator;

  constructor(page: Page) {
    super(page);
    this.heroSection = page.getByTestId('hero-section');
    this.searchInput = page.getByPlaceholder('Search products...');
    this.searchButton = page.getByRole('button', { name: 'Search' });
    this.featuredProducts = page.getByTestId('featured-products');
    this.categoryLinks = page.getByTestId('category-nav').getByRole('link');
    this.loginLink = page.getByRole('link', { name: 'Login' });
    this.registerLink = page.getByRole('link', { name: 'Register' });
    this.cartIcon = page.getByTestId('cart-icon');
    this.cartBadge = page.getByTestId('cart-badge');
    this.userMenu = page.getByTestId('user-menu');
  }

  get url(): string {
    return config.baseUrl.customer;
  }

  async searchProducts(query: string): Promise<void> {
    await this.fill(this.searchInput, query);
    await this.click(this.searchButton);
  }

  async clickCategory(categoryName: string): Promise<void> {
    await this.page.getByRole('link', { name: categoryName }).click();
  }

  async goToLogin(): Promise<void> {
    await this.click(this.loginLink);
  }

  async goToRegister(): Promise<void> {
    await this.click(this.registerLink);
  }

  async goToCart(): Promise<void> {
    await this.click(this.cartIcon);
  }

  async getCartItemCount(): Promise<number> {
    const text = await this.getText(this.cartBadge);
    return parseInt(text, 10) || 0;
  }

  async isUserLoggedIn(): Promise<boolean> {
    return this.isVisible(this.userMenu);
  }

  async getFeaturedProductNames(): Promise<string[]> {
    const products = this.featuredProducts.locator('[data-testid="product-name"]');
    return products.allTextContents();
  }
}
