import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

/** The `/cart` page (US-0004-07). Tests use a single cart line. */
export class CartPage extends BasePage {
  readonly lines: Locator;
  readonly quantityInput: Locator;
  readonly increaseButton: Locator;
  readonly unitPrice: Locator;
  readonly lineTotal: Locator;
  readonly lineMessage: Locator;
  readonly removeButton: Locator;
  readonly subtotal: Locator;
  readonly estimatedTotal: Locator;
  readonly emptyState: Locator;
  readonly continueShopping: Locator;

  constructor(page: Page) {
    super(page);
    this.lines = page.getByTestId('cartLineItem');
    this.quantityInput = page.getByTestId('lineQuantityInput');
    this.increaseButton = page.getByTestId('increaseQuantity');
    this.unitPrice = page.getByTestId('lineUnitPrice');
    this.lineTotal = page.getByTestId('lineTotal');
    this.lineMessage = page.getByTestId('lineMessage');
    this.removeButton = page.getByTestId('removeLine');
    this.subtotal = page.getByTestId('cartSubtotal');
    this.estimatedTotal = page.getByTestId('cartEstimatedTotal');
    this.emptyState = page.getByTestId('cartEmptyState');
    this.continueShopping = page.getByTestId('continueShopping');
  }

  get url(): string {
    return `${config.baseUrl.customer}/cart`;
  }

  /** Types a quantity and commits it with Enter, as a customer would. */
  async typeQuantity(quantity: number): Promise<void> {
    await this.quantityInput.fill(String(quantity));
    await this.quantityInput.press('Enter');
  }
}
