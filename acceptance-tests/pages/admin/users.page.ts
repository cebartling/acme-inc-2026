import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';
import { User } from '../../support/test-data.js';

export class UsersPage extends BasePage {
  // Locators
  readonly pageTitle: Locator;
  readonly addUserButton: Locator;
  readonly searchInput: Locator;
  readonly roleFilter: Locator;
  readonly statusFilter: Locator;
  readonly usersTable: Locator;
  readonly userRows: Locator;
  readonly pagination: Locator;

  // User Modal Locators
  readonly userModal: Locator;
  readonly emailInput: Locator;
  readonly firstNameInput: Locator;
  readonly lastNameInput: Locator;
  readonly passwordInput: Locator;
  readonly roleSelect: Locator;
  readonly activeCheckbox: Locator;
  readonly saveUserButton: Locator;
  readonly cancelButton: Locator;
  readonly deleteConfirmButton: Locator;

  constructor(page: Page) {
    super(page);
    this.pageTitle = page.getByRole('heading', { name: 'Users' });
    this.addUserButton = page.getByRole('button', { name: 'Add user' });
    this.searchInput = page.getByPlaceholder('Search users...');
    this.roleFilter = page.getByLabel('Role');
    this.statusFilter = page.getByLabel('Status');
    this.usersTable = page.getByTestId('users-table');
    this.userRows = page.getByTestId('user-row');
    this.pagination = page.getByTestId('pagination');

    // Modal
    this.userModal = page.getByTestId('user-modal');
    this.emailInput = page.getByLabel('Email');
    this.firstNameInput = page.getByLabel('First name');
    this.lastNameInput = page.getByLabel('Last name');
    this.passwordInput = page.getByLabel('Password');
    this.roleSelect = page.getByLabel('Role');
    this.activeCheckbox = page.getByLabel('Active');
    this.saveUserButton = page.getByRole('button', { name: 'Save' });
    this.cancelButton = page.getByRole('button', { name: 'Cancel' });
    this.deleteConfirmButton = page.getByRole('button', { name: 'Delete' });
  }

  get url(): string {
    return `${config.baseUrl.admin}/users`;
  }

  async getUserCount(): Promise<number> {
    return this.userRows.count();
  }

  async searchUsers(query: string): Promise<void> {
    await this.fill(this.searchInput, query);
    await this.page.keyboard.press('Enter');
  }

  async filterByRole(role: 'all' | 'customer' | 'admin'): Promise<void> {
    await this.selectOption(this.roleFilter, role);
  }

  async filterByStatus(status: 'all' | 'active' | 'inactive'): Promise<void> {
    await this.selectOption(this.statusFilter, status);
  }

  async clickAddUser(): Promise<void> {
    await this.click(this.addUserButton);
  }

  async fillUserForm(user: User, password?: string): Promise<void> {
    await this.fill(this.emailInput, user.email);
    await this.fill(this.firstNameInput, user.firstName);
    await this.fill(this.lastNameInput, user.lastName);
    if (password) {
      await this.fill(this.passwordInput, password);
    }
    if (user.role) {
      await this.selectOption(this.roleSelect, user.role);
    }
  }

  async saveUser(): Promise<void> {
    await this.click(this.saveUserButton);
  }

  async cancelUserForm(): Promise<void> {
    await this.click(this.cancelButton);
  }

  async createUser(user: User): Promise<void> {
    await this.clickAddUser();
    await this.fillUserForm(user, user.password);
    await this.saveUser();
  }

  async editUser(email: string): Promise<void> {
    await this.userRows.filter({ hasText: email }).getByRole('button', { name: 'Edit' }).click();
  }

  async deleteUser(email: string): Promise<void> {
    await this.userRows.filter({ hasText: email }).getByRole('button', { name: 'Delete' }).click();
    await this.click(this.deleteConfirmButton);
  }

  async toggleUserActive(email: string): Promise<void> {
    await this.userRows
      .filter({ hasText: email })
      .getByRole('button', { name: /Activate|Deactivate/ })
      .click();
  }

  async getUserEmails(): Promise<string[]> {
    return this.userRows.locator('[data-testid="user-email"]').allTextContents();
  }

  async isUserInList(email: string): Promise<boolean> {
    const emails = await this.getUserEmails();
    return emails.includes(email);
  }

  async getUserRole(email: string): Promise<string> {
    return (await this.userRows
      .filter({ hasText: email })
      .locator('[data-testid="user-role"]')
      .textContent()) as string;
  }
}
