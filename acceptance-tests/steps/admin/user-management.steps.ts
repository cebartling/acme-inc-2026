import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { UsersPage } from '../../pages/admin/users.page.js';
import { TestUsers, User } from '../../support/test-data.js';

Given('I am on the admin users page', async function (this: CustomWorld) {
  const usersPage = new UsersPage(this.page);
  await usersPage.navigate();
});

Given('a user {string} exists', async function (this: CustomWorld, email: string) {
  this.setTestData('existingUser', email);
});

When('I click add user', async function (this: CustomWorld) {
  const usersPage = new UsersPage(this.page);
  await usersPage.clickAddUser();
});

When('I fill in the user details', async function (this: CustomWorld) {
  const usersPage = new UsersPage(this.page);
  const user = TestUsers.customer();
  this.setTestData('newUser', user);
  await usersPage.fillUserForm(user, user.password);
});

When('I fill in user details with:', async function (this: CustomWorld, dataTable: any) {
  const usersPage = new UsersPage(this.page);
  const data = dataTable.rowsHash();

  const user: User = {
    email: data['Email'],
    firstName: data['First Name'],
    lastName: data['Last Name'],
    password: data['Password'],
    role: data['Role'] as 'customer' | 'admin',
  };

  this.setTestData('newUser', user);
  await usersPage.fillUserForm(user, user.password);
});

When('I save the user', async function (this: CustomWorld) {
  const usersPage = new UsersPage(this.page);
  await usersPage.saveUser();
});

When('I cancel the user form', async function (this: CustomWorld) {
  const usersPage = new UsersPage(this.page);
  await usersPage.cancelUserForm();
});

When('I edit user {string}', async function (this: CustomWorld, email: string) {
  const usersPage = new UsersPage(this.page);
  await usersPage.editUser(email);
});

When('I delete user {string}', async function (this: CustomWorld, email: string) {
  const usersPage = new UsersPage(this.page);
  await usersPage.deleteUser(email);
});

When('I toggle active status for user {string}', async function (
  this: CustomWorld,
  email: string
) {
  const usersPage = new UsersPage(this.page);
  await usersPage.toggleUserActive(email);
});

When('I search for user {string}', async function (this: CustomWorld, query: string) {
  const usersPage = new UsersPage(this.page);
  await usersPage.searchUsers(query);
});

When('I filter users by role {string}', async function (this: CustomWorld, role: string) {
  const usersPage = new UsersPage(this.page);
  await usersPage.filterByRole(role as 'all' | 'customer' | 'admin');
});

When('I filter users by status {string}', async function (this: CustomWorld, status: string) {
  const usersPage = new UsersPage(this.page);
  await usersPage.filterByStatus(status as 'all' | 'active' | 'inactive');
});

Then('I should see the user in the list', async function (this: CustomWorld) {
  const usersPage = new UsersPage(this.page);
  const user = this.getTestData<User>('newUser');
  if (!user) {
    throw new Error('No user data found');
  }
  const isInList = await usersPage.isUserInList(user.email);
  expect(isInList).toBe(true);
});

Then('I should see user {string} in the list', async function (this: CustomWorld, email: string) {
  const usersPage = new UsersPage(this.page);
  const isInList = await usersPage.isUserInList(email);
  expect(isInList).toBe(true);
});

Then('I should not see user {string} in the list', async function (
  this: CustomWorld,
  email: string
) {
  const usersPage = new UsersPage(this.page);
  const isInList = await usersPage.isUserInList(email);
  expect(isInList).toBe(false);
});

Then('I should see {int} users', async function (this: CustomWorld, count: number) {
  const usersPage = new UsersPage(this.page);
  const actualCount = await usersPage.getUserCount();
  expect(actualCount).toBe(count);
});

Then('the user {string} should have role {string}', async function (
  this: CustomWorld,
  email: string,
  expectedRole: string
) {
  const usersPage = new UsersPage(this.page);
  const actualRole = await usersPage.getUserRole(email);
  expect(actualRole.toLowerCase()).toBe(expectedRole.toLowerCase());
});

Then('the user modal should be visible', async function (this: CustomWorld) {
  const usersPage = new UsersPage(this.page);
  await expect(usersPage.userModal).toBeVisible();
});

Then('the user modal should be hidden', async function (this: CustomWorld) {
  const usersPage = new UsersPage(this.page);
  await expect(usersPage.userModal).not.toBeVisible();
});
