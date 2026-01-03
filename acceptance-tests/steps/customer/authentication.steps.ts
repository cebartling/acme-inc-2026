import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { LoginPage } from '../../pages/customer/login.page.js';
import { HomePage } from '../../pages/customer/home.page.js';
import { TestUsers } from '../../support/test-data.js';

Given('I am a registered customer', async function (this: CustomWorld) {
  const user = TestUsers.existingCustomer();
  this.setTestData('currentUser', user);
});

Given('I am logged in as a customer', async function (this: CustomWorld) {
  const user = TestUsers.existingCustomer();
  this.setTestData('currentUser', user);

  const loginPage = new LoginPage(this.page);
  await loginPage.navigate();
  await loginPage.login(user.email, user.password);

  // Wait for redirect to home page
  await this.page.waitForURL(/\/$/);
});

When('I enter my email {string}', async function (this: CustomWorld, email: string) {
  await this.page.getByLabel('Email').fill(email);
});

When('I enter my password {string}', async function (this: CustomWorld, password: string) {
  await this.page.getByLabel('Password').fill(password);
});

When('I submit the login form', async function (this: CustomWorld) {
  await this.page.getByRole('button', { name: 'Log in' }).click();
});

When('I login with email {string} and password {string}', async function (
  this: CustomWorld,
  email: string,
  password: string
) {
  const loginPage = new LoginPage(this.page);
  await loginPage.login(email, password);
});

When('I login with valid credentials', async function (this: CustomWorld) {
  const user = this.getTestData<{ email: string; password: string }>('currentUser');
  if (!user) {
    throw new Error('No user data found. Did you set up the user in a previous step?');
  }

  const loginPage = new LoginPage(this.page);
  await loginPage.login(user.email, user.password);
});

When('I click the logout button', async function (this: CustomWorld) {
  await this.page.getByTestId('user-menu').click();
  await this.page.getByRole('button', { name: 'Logout' }).click();
});

When('I check the remember me checkbox', async function (this: CustomWorld) {
  await this.page.getByLabel('Remember me').check();
});

Then('I should be logged in', async function (this: CustomWorld) {
  const homePage = new HomePage(this.page);
  const isLoggedIn = await homePage.isUserLoggedIn();
  expect(isLoggedIn).toBe(true);
});

Then('I should be logged out', async function (this: CustomWorld) {
  const homePage = new HomePage(this.page);
  const isLoggedIn = await homePage.isUserLoggedIn();
  expect(isLoggedIn).toBe(false);
});

Then('I should see a login error', async function (this: CustomWorld) {
  const loginPage = new LoginPage(this.page);
  const hasError = await loginPage.isErrorDisplayed();
  expect(hasError).toBe(true);
});

Then('I should see the error {string}', async function (this: CustomWorld, errorMessage: string) {
  const loginPage = new LoginPage(this.page);
  const actualError = await loginPage.getErrorMessage();
  expect(actualError).toContain(errorMessage);
});

Then('I should be redirected to the home page', async function (this: CustomWorld) {
  await expect(this.page).toHaveURL(/\/$/);
});

Then('I should see my user menu', async function (this: CustomWorld) {
  await expect(this.page.getByTestId('user-menu')).toBeVisible();
});
