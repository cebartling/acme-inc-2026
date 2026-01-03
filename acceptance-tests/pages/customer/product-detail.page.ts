import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class ProductDetailPage extends BasePage {
  // Locators
  readonly productName: Locator;
  readonly productPrice: Locator;
  readonly productDescription: Locator;
  readonly productImage: Locator;
  readonly quantityInput: Locator;
  readonly addToCartButton: Locator;
  readonly buyNowButton: Locator;
  readonly stockStatus: Locator;
  readonly reviewSection: Locator;
  readonly reviewsList: Locator;
  readonly addReviewButton: Locator;
  readonly relatedProducts: Locator;
  readonly breadcrumb: Locator;

  constructor(page: Page) {
    super(page);
    this.productName = page.getByTestId('product-name');
    this.productPrice = page.getByTestId('product-price');
    this.productDescription = page.getByTestId('product-description');
    this.productImage = page.getByTestId('product-image');
    this.quantityInput = page.getByLabel('Quantity');
    this.addToCartButton = page.getByRole('button', { name: 'Add to cart' });
    this.buyNowButton = page.getByRole('button', { name: 'Buy now' });
    this.stockStatus = page.getByTestId('stock-status');
    this.reviewSection = page.getByTestId('review-section');
    this.reviewsList = page.getByTestId('reviews-list');
    this.addReviewButton = page.getByRole('button', { name: 'Write a review' });
    this.relatedProducts = page.getByTestId('related-products');
    this.breadcrumb = page.getByTestId('breadcrumb');
  }

  get url(): string {
    return `${config.baseUrl.customer}/products`;
  }

  async navigateToProduct(productId: string): Promise<void> {
    await this.page.goto(`${config.baseUrl.customer}/products/${productId}`);
    await this.waitForPageLoad();
  }

  async getName(): Promise<string> {
    return this.getText(this.productName);
  }

  async getPrice(): Promise<number> {
    const priceText = await this.getText(this.productPrice);
    return parseFloat(priceText.replace(/[^0-9.]/g, ''));
  }

  async getDescription(): Promise<string> {
    return this.getText(this.productDescription);
  }

  async setQuantity(quantity: number): Promise<void> {
    await this.quantityInput.fill(quantity.toString());
  }

  async addToCart(): Promise<void> {
    await this.click(this.addToCartButton);
  }

  async addToCartWithQuantity(quantity: number): Promise<void> {
    await this.setQuantity(quantity);
    await this.addToCart();
  }

  async buyNow(): Promise<void> {
    await this.click(this.buyNowButton);
  }

  async isInStock(): Promise<boolean> {
    const status = await this.getText(this.stockStatus);
    return status.toLowerCase().includes('in stock');
  }

  async isAddToCartEnabled(): Promise<boolean> {
    return this.isEnabled(this.addToCartButton);
  }

  async getReviewCount(): Promise<number> {
    return this.reviewsList.locator('[data-testid="review-item"]').count();
  }

  async clickRelatedProduct(index: number): Promise<void> {
    await this.relatedProducts.locator('[data-testid="product-card"]').nth(index).click();
  }
}
