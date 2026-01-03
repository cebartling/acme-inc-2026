import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { CartPage } from '../../pages/customer/cart.page.js';
import { ProductDetailPage } from '../../pages/customer/product-detail.page.js';
import { HomePage } from '../../pages/customer/home.page.js';

Given('I have an empty cart', async function (this: CustomWorld) {
  // Cart is empty by default for a new session
  this.setTestData('cartItems', []);
});

Given('I have items in my cart', async function (this: CustomWorld) {
  // This would typically require API seeding or UI interactions
  // For now, we'll navigate to cart and verify
  const cartPage = new CartPage(this.page);
  await cartPage.navigate();
});

Given('I am on the cart page', async function (this: CustomWorld) {
  const cartPage = new CartPage(this.page);
  await cartPage.navigate();
});

When('I add the product to cart', async function (this: CustomWorld) {
  const productDetailPage = new ProductDetailPage(this.page);
  await productDetailPage.addToCart();
});

When('I add {int} items to cart', async function (this: CustomWorld, quantity: number) {
  const productDetailPage = new ProductDetailPage(this.page);
  await productDetailPage.addToCartWithQuantity(quantity);
});

When('I go to cart', async function (this: CustomWorld) {
  const homePage = new HomePage(this.page);
  await homePage.goToCart();
});

When('I update item {int} quantity to {int}', async function (
  this: CustomWorld,
  itemIndex: number,
  quantity: number
) {
  const cartPage = new CartPage(this.page);
  await cartPage.updateItemQuantity(itemIndex - 1, quantity); // Convert to 0-based index
});

When('I remove item {int} from cart', async function (this: CustomWorld, itemIndex: number) {
  const cartPage = new CartPage(this.page);
  await cartPage.removeItem(itemIndex - 1); // Convert to 0-based index
});

When('I apply promo code {string}', async function (this: CustomWorld, promoCode: string) {
  const cartPage = new CartPage(this.page);
  await cartPage.applyPromoCode(promoCode);
});

When('I proceed to checkout', async function (this: CustomWorld) {
  const cartPage = new CartPage(this.page);
  await cartPage.proceedToCheckout();
});

When('I click continue shopping', async function (this: CustomWorld) {
  const cartPage = new CartPage(this.page);
  await cartPage.continueShopping();
});

Then('I should see {int} items in my cart', async function (this: CustomWorld, count: number) {
  const cartPage = new CartPage(this.page);
  const actualCount = await cartPage.getItemCount();
  expect(actualCount).toBe(count);
});

Then('the cart badge should show {int}', async function (this: CustomWorld, count: number) {
  const homePage = new HomePage(this.page);
  const badgeCount = await homePage.getCartItemCount();
  expect(badgeCount).toBe(count);
});

Then('the cart should be empty', async function (this: CustomWorld) {
  const cartPage = new CartPage(this.page);
  const isEmpty = await cartPage.isEmpty();
  expect(isEmpty).toBe(true);
});

Then('the cart subtotal should be {float}', async function (
  this: CustomWorld,
  expectedSubtotal: number
) {
  const cartPage = new CartPage(this.page);
  const actualSubtotal = await cartPage.getSubtotal();
  expect(actualSubtotal).toBeCloseTo(expectedSubtotal, 2);
});

Then('the cart total should be {float}', async function (this: CustomWorld, expectedTotal: number) {
  const cartPage = new CartPage(this.page);
  const actualTotal = await cartPage.getTotal();
  expect(actualTotal).toBeCloseTo(expectedTotal, 2);
});

Then('I should see a promo discount of {float}', async function (
  this: CustomWorld,
  expectedDiscount: number
) {
  const cartPage = new CartPage(this.page);
  const actualDiscount = await cartPage.getPromoDiscount();
  expect(actualDiscount).toBeCloseTo(expectedDiscount, 2);
});

Then('I should see a promo code error', async function (this: CustomWorld) {
  const cartPage = new CartPage(this.page);
  const hasError = await cartPage.hasPromoError();
  expect(hasError).toBe(true);
});

Then('the item {int} quantity should be {int}', async function (
  this: CustomWorld,
  itemIndex: number,
  expectedQuantity: number
) {
  const cartPage = new CartPage(this.page);
  const actualQuantity = await cartPage.getItemQuantity(itemIndex - 1);
  expect(actualQuantity).toBe(expectedQuantity);
});

Then('I should see {string} in my cart', async function (this: CustomWorld, productName: string) {
  const cartPage = new CartPage(this.page);
  const itemNames = await cartPage.getItemNames();
  expect(itemNames).toContain(productName);
});
