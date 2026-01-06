import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

Given('I am on the customer home page', async function (this: CustomWorld) {
  await this.page.goto(this.getCustomerAppUrl());
});

Given('I am on the admin dashboard', async function (this: CustomWorld) {
  await this.page.goto(this.getAdminAppUrl());
});

Given('I am on the login page', async function (this: CustomWorld) {
  await this.page.goto(`${this.getCustomerAppUrl()}/login`);
});

Given('I am on the admin login page', async function (this: CustomWorld) {
  await this.page.goto(`${this.getAdminAppUrl()}/login`);
});

When('I navigate to {string}', async function (this: CustomWorld, path: string) {
  const baseUrl = path.startsWith('/admin')
    ? this.getAdminAppUrl()
    : this.getCustomerAppUrl();
  await this.page.goto(`${baseUrl}${path}`);
});

When('I click the {string} link', async function (this: CustomWorld, linkText: string) {
  await this.page.getByRole('link', { name: linkText }).click();
});

When('I click the {string} button', async function (this: CustomWorld, buttonText: string) {
  await this.page.getByRole('button', { name: buttonText }).click();
});

When('I go back to the previous page', async function (this: CustomWorld) {
  await this.page.goBack();
});

When('I refresh the page', async function (this: CustomWorld) {
  await this.page.reload();
});

Then('I should be on the {string} page', async function (this: CustomWorld, pageName: string) {
  const urlPatterns: Record<string, RegExp> = {
    home: /\/$/,
    login: /\/login/,
    register: /\/register/,
    products: /\/products/,
    cart: /\/cart/,
    checkout: /\/checkout/,
    dashboard: /\/dashboard/,
    orders: /\/orders/,
    users: /\/users/,
  };

  const pattern = urlPatterns[pageName.toLowerCase()];
  if (pattern) {
    await expect(this.page).toHaveURL(pattern);
  }
});

Then('the page title should be {string}', async function (this: CustomWorld, title: string) {
  await expect(this.page).toHaveTitle(title);
});

Then('I should see the heading {string}', async function (this: CustomWorld, heading: string) {
  await expect(this.page.getByRole('heading', { name: heading })).toBeVisible();
});

Then('I should see the text {string}', async function (this: CustomWorld, text: string) {
  await expect(this.page.getByText(text)).toBeVisible();
});

Then('I should not see the text {string}', async function (this: CustomWorld, text: string) {
  await expect(this.page.getByText(text)).not.toBeVisible();
});

Then('the URL should contain {string}', async function (this: CustomWorld, urlPart: string) {
  await expect(this.page).toHaveURL(new RegExp(urlPart));
});

Then('I should be redirected to the home page', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(new RegExp(`${this.getCustomerAppUrl()}/?$`), { timeout: 10000 });
});
