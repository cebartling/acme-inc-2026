import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

/**
 * Product detail page (`/products/{slug}`) and its Add to Cart form (US-0004-06).
 */
export class ProductDetailPage extends BasePage {
  readonly quantityInput: Locator;
  readonly addToCartButton: Locator;
  readonly confirmation: Locator;
  readonly error: Locator;
  readonly cartBadge: Locator;
  readonly cartBadgeCount: Locator;

  constructor(
    page: Page,
    private readonly slug: string
  ) {
    super(page);
    this.quantityInput = page.getByTestId('quantityInput');
    this.addToCartButton = page.getByTestId('addToCartButton');
    this.confirmation = page.getByTestId('addToCartConfirmation');
    this.error = page.getByTestId('addToCartError');
    this.cartBadge = page.getByTestId('cartBadge');
    this.cartBadgeCount = page.getByTestId('cartBadgeCount');
  }

  get url(): string {
    return `${config.baseUrl.customer}/products/${this.slug}`;
  }

  async open(): Promise<void> {
    await this.navigate();
    await this.page.getByTestId('productDetailPage').waitFor();
  }

  async setQuantity(quantity: number): Promise<void> {
    await this.quantityInput.fill(String(quantity));
  }

  async addToCart(): Promise<void> {
    await this.addToCartButton.click();
  }
}
