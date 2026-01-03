import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ProductListPage } from '../../pages/customer/product-list.page.js';
import { ProductDetailPage } from '../../pages/customer/product-detail.page.js';
import { HomePage } from '../../pages/customer/home.page.js';

Given('I am on the products page', async function (this: CustomWorld) {
  const productListPage = new ProductListPage(this.page);
  await productListPage.navigate();
});

Given('I am viewing product {string}', async function (this: CustomWorld, productId: string) {
  const productDetailPage = new ProductDetailPage(this.page);
  await productDetailPage.navigateToProduct(productId);
});

When('I search for {string}', async function (this: CustomWorld, searchTerm: string) {
  const homePage = new HomePage(this.page);
  await homePage.searchProducts(searchTerm);
});

When('I click on product {string}', async function (this: CustomWorld, productName: string) {
  const productListPage = new ProductListPage(this.page);
  await productListPage.clickProduct(productName);
});

When('I filter by category {string}', async function (this: CustomWorld, category: string) {
  const productListPage = new ProductListPage(this.page);
  await productListPage.filterByCategory(category);
});

When('I filter by price range {int} to {int}', async function (
  this: CustomWorld,
  min: number,
  max: number
) {
  const productListPage = new ProductListPage(this.page);
  await productListPage.filterByPriceRange(min, max);
});

When('I sort products by {string}', async function (this: CustomWorld, sortOption: string) {
  const productListPage = new ProductListPage(this.page);
  await productListPage.sortBy(sortOption);
});

When('I click load more products', async function (this: CustomWorld) {
  const productListPage = new ProductListPage(this.page);
  await productListPage.loadMore();
});

Then('I should see {int} products', async function (this: CustomWorld, count: number) {
  const productListPage = new ProductListPage(this.page);
  const actualCount = await productListPage.getProductCount();
  expect(actualCount).toBe(count);
});

Then('I should see products matching {string}', async function (
  this: CustomWorld,
  searchTerm: string
) {
  const productListPage = new ProductListPage(this.page);
  const productNames = await productListPage.getProductNames();
  const hasMatch = productNames.some((name) =>
    name.toLowerCase().includes(searchTerm.toLowerCase())
  );
  expect(hasMatch).toBe(true);
});

Then('I should see no products found', async function (this: CustomWorld) {
  const productListPage = new ProductListPage(this.page);
  const hasNoResults = await productListPage.hasNoResults();
  expect(hasNoResults).toBe(true);
});

Then('I should see the product details', async function (this: CustomWorld) {
  const productDetailPage = new ProductDetailPage(this.page);
  await expect(productDetailPage.productName).toBeVisible();
  await expect(productDetailPage.productPrice).toBeVisible();
  await expect(productDetailPage.productDescription).toBeVisible();
});

Then('the product name should be {string}', async function (
  this: CustomWorld,
  expectedName: string
) {
  const productDetailPage = new ProductDetailPage(this.page);
  const actualName = await productDetailPage.getName();
  expect(actualName).toBe(expectedName);
});

Then('the product price should be {float}', async function (
  this: CustomWorld,
  expectedPrice: number
) {
  const productDetailPage = new ProductDetailPage(this.page);
  const actualPrice = await productDetailPage.getPrice();
  expect(actualPrice).toBe(expectedPrice);
});

Then('the product should be in stock', async function (this: CustomWorld) {
  const productDetailPage = new ProductDetailPage(this.page);
  const isInStock = await productDetailPage.isInStock();
  expect(isInStock).toBe(true);
});

Then('the product should be out of stock', async function (this: CustomWorld) {
  const productDetailPage = new ProductDetailPage(this.page);
  const isInStock = await productDetailPage.isInStock();
  expect(isInStock).toBe(false);
});

Then('the add to cart button should be disabled', async function (this: CustomWorld) {
  const productDetailPage = new ProductDetailPage(this.page);
  const isEnabled = await productDetailPage.isAddToCartEnabled();
  expect(isEnabled).toBe(false);
});

Then('products should be sorted by price ascending', async function (this: CustomWorld) {
  const productListPage = new ProductListPage(this.page);
  const prices = await productListPage.getProductPrices();
  const sortedPrices = [...prices].sort((a, b) => a - b);
  expect(prices).toEqual(sortedPrices);
});

Then('products should be sorted by price descending', async function (this: CustomWorld) {
  const productListPage = new ProductListPage(this.page);
  const prices = await productListPage.getProductPrices();
  const sortedPrices = [...prices].sort((a, b) => b - a);
  expect(prices).toEqual(sortedPrices);
});
