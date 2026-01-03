import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class DashboardPage extends BasePage {
  // Locators
  readonly welcomeMessage: Locator;
  readonly totalOrdersCard: Locator;
  readonly totalRevenueCard: Locator;
  readonly totalCustomersCard: Locator;
  readonly totalProductsCard: Locator;
  readonly recentOrdersTable: Locator;
  readonly lowStockAlerts: Locator;
  readonly salesChart: Locator;
  readonly sidebarNav: Locator;
  readonly userMenu: Locator;
  readonly logoutButton: Locator;

  constructor(page: Page) {
    super(page);
    this.welcomeMessage = page.getByTestId('welcome-message');
    this.totalOrdersCard = page.getByTestId('total-orders');
    this.totalRevenueCard = page.getByTestId('total-revenue');
    this.totalCustomersCard = page.getByTestId('total-customers');
    this.totalProductsCard = page.getByTestId('total-products');
    this.recentOrdersTable = page.getByTestId('recent-orders');
    this.lowStockAlerts = page.getByTestId('low-stock-alerts');
    this.salesChart = page.getByTestId('sales-chart');
    this.sidebarNav = page.getByTestId('sidebar-nav');
    this.userMenu = page.getByTestId('user-menu');
    this.logoutButton = page.getByRole('button', { name: 'Logout' });
  }

  get url(): string {
    return `${config.baseUrl.admin}/dashboard`;
  }

  async getTotalOrders(): Promise<number> {
    const text = await this.getText(this.totalOrdersCard.locator('[data-testid="metric-value"]'));
    return parseInt(text.replace(/,/g, ''), 10);
  }

  async getTotalRevenue(): Promise<number> {
    const text = await this.getText(this.totalRevenueCard.locator('[data-testid="metric-value"]'));
    return parseFloat(text.replace(/[^0-9.]/g, ''));
  }

  async getTotalCustomers(): Promise<number> {
    const text = await this.getText(this.totalCustomersCard.locator('[data-testid="metric-value"]'));
    return parseInt(text.replace(/,/g, ''), 10);
  }

  async getTotalProducts(): Promise<number> {
    const text = await this.getText(this.totalProductsCard.locator('[data-testid="metric-value"]'));
    return parseInt(text.replace(/,/g, ''), 10);
  }

  async navigateToProducts(): Promise<void> {
    await this.sidebarNav.getByRole('link', { name: 'Products' }).click();
  }

  async navigateToOrders(): Promise<void> {
    await this.sidebarNav.getByRole('link', { name: 'Orders' }).click();
  }

  async navigateToUsers(): Promise<void> {
    await this.sidebarNav.getByRole('link', { name: 'Users' }).click();
  }

  async logout(): Promise<void> {
    await this.click(this.userMenu);
    await this.click(this.logoutButton);
  }

  async getLowStockProductCount(): Promise<number> {
    return this.lowStockAlerts.locator('[data-testid="low-stock-item"]').count();
  }

  async getRecentOrdersCount(): Promise<number> {
    return this.recentOrdersTable.locator('tbody tr').count();
  }
}
