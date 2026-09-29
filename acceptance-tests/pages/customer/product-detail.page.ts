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
  readonly availabilityBadge: Locator;

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
    this.availabilityBadge = page.getByTestId('availabilityBadge');
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

  /**
   * Picks a color swatch. An out-of-stock one is still selectable (US-0004-10 AC-07), but it is
   * `aria-disabled`, which Playwright treats as not clickable, so the click is forced: it is a
   * real click, only the enabled check is skipped.
   */
  async selectColor(color: string): Promise<void> {
    await this.page.getByRole('radio', { name: `Color: ${color}` }).click({ force: true });
  }

  async addToCart(): Promise<void> {
    await this.addToCartButton.click();
  }
}
