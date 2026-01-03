import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';
import { Address } from '../../support/test-data.js';

export class CheckoutPage extends BasePage {
  // Shipping Address Locators
  readonly shippingFirstName: Locator;
  readonly shippingLastName: Locator;
  readonly shippingStreet: Locator;
  readonly shippingCity: Locator;
  readonly shippingState: Locator;
  readonly shippingZipCode: Locator;
  readonly shippingCountry: Locator;

  // Billing Address Locators
  readonly sameAsShippingCheckbox: Locator;
  readonly billingFirstName: Locator;
  readonly billingLastName: Locator;
  readonly billingStreet: Locator;
  readonly billingCity: Locator;
  readonly billingState: Locator;
  readonly billingZipCode: Locator;
  readonly billingCountry: Locator;

  // Payment Locators
  readonly cardNumber: Locator;
  readonly cardExpiry: Locator;
  readonly cardCvc: Locator;
  readonly cardholderName: Locator;

  // Order Summary Locators
  readonly orderItems: Locator;
  readonly orderSubtotal: Locator;
  readonly orderShipping: Locator;
  readonly orderTax: Locator;
  readonly orderTotal: Locator;

  // Action Locators
  readonly placeOrderButton: Locator;
  readonly backToCartLink: Locator;
  readonly errorMessage: Locator;
  readonly successMessage: Locator;

  constructor(page: Page) {
    super(page);

    // Shipping Address
    this.shippingFirstName = page.getByLabel('First name', { exact: true });
    this.shippingLastName = page.getByLabel('Last name', { exact: true });
    this.shippingStreet = page.getByTestId('shipping-street');
    this.shippingCity = page.getByTestId('shipping-city');
    this.shippingState = page.getByTestId('shipping-state');
    this.shippingZipCode = page.getByTestId('shipping-zip');
    this.shippingCountry = page.getByTestId('shipping-country');

    // Billing Address
    this.sameAsShippingCheckbox = page.getByLabel('Same as shipping address');
    this.billingFirstName = page.getByTestId('billing-first-name');
    this.billingLastName = page.getByTestId('billing-last-name');
    this.billingStreet = page.getByTestId('billing-street');
    this.billingCity = page.getByTestId('billing-city');
    this.billingState = page.getByTestId('billing-state');
    this.billingZipCode = page.getByTestId('billing-zip');
    this.billingCountry = page.getByTestId('billing-country');

    // Payment
    this.cardNumber = page.getByLabel('Card number');
    this.cardExpiry = page.getByLabel('Expiry date');
    this.cardCvc = page.getByLabel('CVC');
    this.cardholderName = page.getByLabel('Cardholder name');

    // Order Summary
    this.orderItems = page.getByTestId('order-item');
    this.orderSubtotal = page.getByTestId('order-subtotal');
    this.orderShipping = page.getByTestId('order-shipping');
    this.orderTax = page.getByTestId('order-tax');
    this.orderTotal = page.getByTestId('order-total');

    // Actions
    this.placeOrderButton = page.getByRole('button', { name: 'Place order' });
    this.backToCartLink = page.getByRole('link', { name: 'Back to cart' });
    this.errorMessage = page.getByTestId('checkout-error');
    this.successMessage = page.getByTestId('checkout-success');
  }

  get url(): string {
    return `${config.baseUrl.customer}/checkout`;
  }

  async fillShippingAddress(
    firstName: string,
    lastName: string,
    address: Address
  ): Promise<void> {
    await this.fill(this.shippingFirstName, firstName);
    await this.fill(this.shippingLastName, lastName);
    await this.fill(this.shippingStreet, address.street);
    await this.fill(this.shippingCity, address.city);
    await this.fill(this.shippingState, address.state);
    await this.fill(this.shippingZipCode, address.zipCode);
    await this.selectOption(this.shippingCountry, address.country);
  }

  async useSameAddressForBilling(same: boolean): Promise<void> {
    if (same) {
      await this.checkCheckbox(this.sameAsShippingCheckbox);
    } else {
      await this.uncheckCheckbox(this.sameAsShippingCheckbox);
    }
  }

  async fillBillingAddress(
    firstName: string,
    lastName: string,
    address: Address
  ): Promise<void> {
    await this.fill(this.billingFirstName, firstName);
    await this.fill(this.billingLastName, lastName);
    await this.fill(this.billingStreet, address.street);
    await this.fill(this.billingCity, address.city);
    await this.fill(this.billingState, address.state);
    await this.fill(this.billingZipCode, address.zipCode);
    await this.selectOption(this.billingCountry, address.country);
  }

  async fillPaymentDetails(
    cardNumber: string,
    expiry: string,
    cvc: string,
    name: string
  ): Promise<void> {
    await this.fill(this.cardNumber, cardNumber);
    await this.fill(this.cardExpiry, expiry);
    await this.fill(this.cardCvc, cvc);
    await this.fill(this.cardholderName, name);
  }

  async getOrderTotal(): Promise<number> {
    const text = await this.getText(this.orderTotal);
    return parseFloat(text.replace(/[^0-9.]/g, ''));
  }

  async placeOrder(): Promise<void> {
    await this.click(this.placeOrderButton);
  }

  async hasError(): Promise<boolean> {
    return this.isVisible(this.errorMessage);
  }

  async getErrorMessage(): Promise<string> {
    return this.getText(this.errorMessage);
  }

  async isOrderSuccessful(): Promise<boolean> {
    return this.isVisible(this.successMessage);
  }

  async backToCart(): Promise<void> {
    await this.click(this.backToCartLink);
  }
}
