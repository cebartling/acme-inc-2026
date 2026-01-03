import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { OrdersPage } from '../../pages/admin/orders.page.js';

Given('I am on the admin orders page', async function (this: CustomWorld) {
  const ordersPage = new OrdersPage(this.page);
  await ordersPage.navigate();
});

Given('an order {string} exists with status {string}', async function (
  this: CustomWorld,
  orderId: string,
  status: string
) {
  this.setTestData('existingOrder', { id: orderId, status });
});

When('I search for order {string}', async function (this: CustomWorld, orderId: string) {
  const ordersPage = new OrdersPage(this.page);
  await ordersPage.searchOrders(orderId);
});

When('I filter orders by status {string}', async function (this: CustomWorld, status: string) {
  const ordersPage = new OrdersPage(this.page);
  await ordersPage.filterByStatus(
    status as 'all' | 'pending' | 'processing' | 'shipped' | 'delivered' | 'cancelled'
  );
});

When('I view order {string} details', async function (this: CustomWorld, orderId: string) {
  const ordersPage = new OrdersPage(this.page);
  await ordersPage.viewOrderDetails(orderId);
});

When('I update the order status to {string}', async function (this: CustomWorld, status: string) {
  const ordersPage = new OrdersPage(this.page);
  await ordersPage.updateOrderStatus(
    status as 'pending' | 'processing' | 'shipped' | 'delivered' | 'cancelled'
  );
});

When('I close the order details', async function (this: CustomWorld) {
  const ordersPage = new OrdersPage(this.page);
  await ordersPage.closeOrderDetail();
});

When('I export orders', async function (this: CustomWorld) {
  const ordersPage = new OrdersPage(this.page);
  await ordersPage.exportOrders();
});

When('I refresh the orders list', async function (this: CustomWorld) {
  const ordersPage = new OrdersPage(this.page);
  await ordersPage.refreshOrders();
});

Then('I should see {int} orders', async function (this: CustomWorld, count: number) {
  const ordersPage = new OrdersPage(this.page);
  const actualCount = await ordersPage.getOrderCount();
  expect(actualCount).toBe(count);
});

Then('I should see order {string} in the list', async function (
  this: CustomWorld,
  orderId: string
) {
  const ordersPage = new OrdersPage(this.page);
  const orderIds = await ordersPage.getOrderIds();
  expect(orderIds).toContain(orderId);
});

Then('the order {string} status should be {string}', async function (
  this: CustomWorld,
  orderId: string,
  expectedStatus: string
) {
  const ordersPage = new OrdersPage(this.page);
  const actualStatus = await ordersPage.getOrderStatus(orderId);
  expect(actualStatus.toLowerCase()).toBe(expectedStatus.toLowerCase());
});

Then('the order detail modal should be visible', async function (this: CustomWorld) {
  const ordersPage = new OrdersPage(this.page);
  await expect(ordersPage.orderDetailModal).toBeVisible();
});

Then('the order total should be displayed', async function (this: CustomWorld) {
  const ordersPage = new OrdersPage(this.page);
  await expect(ordersPage.orderTotal).toBeVisible();
});

Then('the customer information should be displayed', async function (this: CustomWorld) {
  const ordersPage = new OrdersPage(this.page);
  await expect(ordersPage.customerInfo).toBeVisible();
});

Then('the shipping address should be displayed', async function (this: CustomWorld) {
  const ordersPage = new OrdersPage(this.page);
  await expect(ordersPage.shippingAddress).toBeVisible();
});
