import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { CheckoutPage } from '../../pages/customer/checkout.page.js';
import { TestAddresses } from '../../support/test-data.js';

Given('I am on the checkout page', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  await checkoutPage.navigate();
});

When('I fill in my shipping address', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  const address = TestAddresses.valid();
  await checkoutPage.fillShippingAddress('Test', 'Customer', address);
});

When('I fill in shipping address with:', async function (this: CustomWorld, dataTable: any) {
  const checkoutPage = new CheckoutPage(this.page);
  const data = dataTable.rowsHash();

  await checkoutPage.fillShippingAddress(data['First Name'], data['Last Name'], {
    street: data['Street'],
    city: data['City'],
    state: data['State'],
    zipCode: data['Zip Code'],
    country: data['Country'],
  });
});

When('I use the same address for billing', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  await checkoutPage.useSameAddressForBilling(true);
});

When('I enter different billing address', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  await checkoutPage.useSameAddressForBilling(false);
  const address = TestAddresses.international();
  await checkoutPage.fillBillingAddress('Test', 'Customer', address);
});

When('I fill in my payment details', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  // Using test card numbers (these are typically provided by payment processors for testing)
  await checkoutPage.fillPaymentDetails('4111111111111111', '12/25', '123', 'Test Customer');
});

When('I fill in payment details with:', async function (this: CustomWorld, dataTable: any) {
  const checkoutPage = new CheckoutPage(this.page);
  const data = dataTable.rowsHash();

  await checkoutPage.fillPaymentDetails(
    data['Card Number'],
    data['Expiry'],
    data['CVC'],
    data['Name on Card']
  );
});

When('I place the order', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  await checkoutPage.placeOrder();
});

When('I go back to cart from checkout', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  await checkoutPage.backToCart();
});

Then('I should see the order confirmation', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  const isSuccessful = await checkoutPage.isOrderSuccessful();
  expect(isSuccessful).toBe(true);
});

Then('the order total should be {float}', async function (
  this: CustomWorld,
  expectedTotal: number
) {
  const checkoutPage = new CheckoutPage(this.page);
  const actualTotal = await checkoutPage.getOrderTotal();
  expect(actualTotal).toBeCloseTo(expectedTotal, 2);
});

Then('I should see a checkout error', async function (this: CustomWorld) {
  const checkoutPage = new CheckoutPage(this.page);
  const hasError = await checkoutPage.hasError();
  expect(hasError).toBe(true);
});

Then('the checkout error should say {string}', async function (
  this: CustomWorld,
  expectedError: string
) {
  const checkoutPage = new CheckoutPage(this.page);
  const actualError = await checkoutPage.getErrorMessage();
  expect(actualError).toContain(expectedError);
});

Then('I should be redirected to order confirmation', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(/\/order-confirmation/);
});

Then('I should see my order number', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('order-number')).toBeVisible();
});
