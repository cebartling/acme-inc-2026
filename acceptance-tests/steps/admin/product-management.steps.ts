import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { ProductsPage } from '../../pages/admin/products.page.js';
import { TestProducts, Product } from '../../support/test-data.js';

Given('I am on the admin products page', async function (this: CustomWorld) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.navigate();
});

Given('a product {string} exists', async function (this: CustomWorld, productName: string) {
  // Store product info for later verification
  this.setTestData('existingProduct', productName);
});

When('I click add product', async function (this: CustomWorld) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.clickAddProduct();
});

When('I fill in the product details', async function (this: CustomWorld) {
  const productsPage = new ProductsPage(this.page);
  const product = TestProducts.simple();
  this.setTestData('newProduct', product);
  await productsPage.fillProductForm(product);
});

When('I fill in product details with:', async function (this: CustomWorld, dataTable: any) {
  const productsPage = new ProductsPage(this.page);
  const data = dataTable.rowsHash();

  const product: Product = {
    name: data['Name'],
    description: data['Description'],
    price: parseFloat(data['Price']),
    category: data['Category'],
    sku: data['SKU'],
    inStock: data['In Stock'] === 'true',
    quantity: parseInt(data['Quantity'], 10),
  };

  this.setTestData('newProduct', product);
  await productsPage.fillProductForm(product);
});

When('I save the product', async function (this: CustomWorld) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.saveProduct();
});

When('I cancel the product form', async function (this: CustomWorld) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.cancelProductForm();
});

When('I edit product {string}', async function (this: CustomWorld, productName: string) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.editProduct(productName);
});

When('I delete product {string}', async function (this: CustomWorld, productName: string) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.deleteProduct(productName);
});

When('I search for product {string}', async function (this: CustomWorld, query: string) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.searchProducts(query);
});

When('I filter products by category {string}', async function (
  this: CustomWorld,
  category: string
) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.filterByCategory(category);
});

When('I filter products by stock status {string}', async function (
  this: CustomWorld,
  status: string
) {
  const productsPage = new ProductsPage(this.page);
  await productsPage.filterByStock(status as 'all' | 'in-stock' | 'out-of-stock');
});

Then('I should see the product in the list', async function (this: CustomWorld) {
  const productsPage = new ProductsPage(this.page);
  const product = this.getTestData<Product>('newProduct');
  if (!product) {
    throw new Error('No product data found');
  }
  const isInList = await productsPage.isProductInList(product.name);
  expect(isInList).toBe(true);
});

Then('I should see product {string} in the list', async function (
  this: CustomWorld,
  productName: string
) {
  const productsPage = new ProductsPage(this.page);
  const isInList = await productsPage.isProductInList(productName);
  expect(isInList).toBe(true);
});

Then('I should not see product {string} in the list', async function (
  this: CustomWorld,
  productName: string
) {
  const productsPage = new ProductsPage(this.page);
  const isInList = await productsPage.isProductInList(productName);
  expect(isInList).toBe(false);
});

Then('I should see {int} products in the admin list', async function (
  this: CustomWorld,
  count: number
) {
  const productsPage = new ProductsPage(this.page);
  const actualCount = await productsPage.getProductCount();
  expect(actualCount).toBe(count);
});

Then('the product modal should be visible', async function (this: CustomWorld) {
  const productsPage = new ProductsPage(this.page);
  await expect(productsPage.productModal).toBeVisible();
});

Then('the product modal should be hidden', async function (this: CustomWorld) {
  const productsPage = new ProductsPage(this.page);
  await expect(productsPage.productModal).not.toBeVisible();
});
