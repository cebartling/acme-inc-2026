import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class CartPage extends BasePage {
  // Locators
  readonly cartItems: Locator;
  readonly emptyCartMessage: Locator;
  readonly subtotal: Locator;
  readonly tax: Locator;
  readonly total: Locator;
  readonly checkoutButton: Locator;
  readonly continueShoppingLink: Locator;
  readonly promoCodeInput: Locator;
  readonly applyPromoButton: Locator;
  readonly promoDiscount: Locator;
  readonly promoError: Locator;

  constructor(page: Page) {
    super(page);
    this.cartItems = page.getByTestId('cart-item');
    this.emptyCartMessage = page.getByTestId('empty-cart');
    this.subtotal = page.getByTestId('subtotal');
    this.tax = page.getByTestId('tax');
    this.total = page.getByTestId('total');
    this.checkoutButton = page.getByRole('button', { name: 'Proceed to checkout' });
    this.continueShoppingLink = page.getByRole('link', { name: 'Continue shopping' });
    this.promoCodeInput = page.getByLabel('Promo code');
    this.applyPromoButton = page.getByRole('button', { name: 'Apply' });
    this.promoDiscount = page.getByTestId('promo-discount');
    this.promoError = page.getByTestId('promo-error');
  }

  get url(): string {
    return `${config.baseUrl.customer}/cart`;
  }

  async getItemCount(): Promise<number> {
    return this.cartItems.count();
  }

  async isEmpty(): Promise<boolean> {
    return this.isVisible(this.emptyCartMessage);
  }

  async getItemQuantity(index: number): Promise<number> {
    const quantityInput = this.cartItems.nth(index).getByLabel('Quantity');
    const value = await quantityInput.inputValue();
    return parseInt(value, 10);
  }

  async updateItemQuantity(index: number, quantity: number): Promise<void> {
    const quantityInput = this.cartItems.nth(index).getByLabel('Quantity');
    await quantityInput.fill(quantity.toString());
    await this.page.keyboard.press('Enter');
  }

  async removeItem(index: number): Promise<void> {
    await this.cartItems.nth(index).getByRole('button', { name: 'Remove' }).click();
  }

  async getSubtotal(): Promise<number> {
    const text = await this.getText(this.subtotal);
    return parseFloat(text.replace(/[^0-9.]/g, ''));
  }

  async getTax(): Promise<number> {
    const text = await this.getText(this.tax);
    return parseFloat(text.replace(/[^0-9.]/g, ''));
  }

  async getTotal(): Promise<number> {
    const text = await this.getText(this.total);
    return parseFloat(text.replace(/[^0-9.]/g, ''));
  }

  async applyPromoCode(code: string): Promise<void> {
    await this.fill(this.promoCodeInput, code);
    await this.click(this.applyPromoButton);
  }

  async getPromoDiscount(): Promise<number> {
    const text = await this.getText(this.promoDiscount);
    return parseFloat(text.replace(/[^0-9.]/g, ''));
  }

  async hasPromoError(): Promise<boolean> {
    return this.isVisible(this.promoError);
  }

  async proceedToCheckout(): Promise<void> {
    await this.click(this.checkoutButton);
  }

  async continueShopping(): Promise<void> {
    await this.click(this.continueShoppingLink);
  }

  async getItemNames(): Promise<string[]> {
    return this.cartItems.locator('[data-testid="item-name"]').allTextContents();
  }
}
