import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class OrdersPage extends BasePage {
  // Locators
  readonly pageTitle: Locator;
  readonly searchInput: Locator;
  readonly statusFilter: Locator;
  readonly dateRangeFilter: Locator;
  readonly ordersTable: Locator;
  readonly orderRows: Locator;
  readonly pagination: Locator;
  readonly exportButton: Locator;
  readonly refreshButton: Locator;

  // Order Detail Modal
  readonly orderDetailModal: Locator;
  readonly orderIdDisplay: Locator;
  readonly orderStatusSelect: Locator;
  readonly orderItems: Locator;
  readonly customerInfo: Locator;
  readonly shippingAddress: Locator;
  readonly orderTotal: Locator;
  readonly updateStatusButton: Locator;
  readonly closeModalButton: Locator;

  constructor(page: Page) {
    super(page);
    this.pageTitle = page.getByRole('heading', { name: 'Orders' });
    this.searchInput = page.getByPlaceholder('Search orders...');
    this.statusFilter = page.getByLabel('Status');
    this.dateRangeFilter = page.getByTestId('date-range-filter');
    this.ordersTable = page.getByTestId('orders-table');
    this.orderRows = page.getByTestId('order-row');
    this.pagination = page.getByTestId('pagination');
    this.exportButton = page.getByRole('button', { name: 'Export' });
    this.refreshButton = page.getByRole('button', { name: 'Refresh' });

    // Modal
    this.orderDetailModal = page.getByTestId('order-detail-modal');
    this.orderIdDisplay = page.getByTestId('order-id');
    this.orderStatusSelect = page.getByLabel('Order status');
    this.orderItems = page.getByTestId('order-items');
    this.customerInfo = page.getByTestId('customer-info');
    this.shippingAddress = page.getByTestId('shipping-address');
    this.orderTotal = page.getByTestId('order-total');
    this.updateStatusButton = page.getByRole('button', { name: 'Update status' });
    this.closeModalButton = page.getByRole('button', { name: 'Close' });
  }

  get url(): string {
    return `${config.baseUrl.admin}/orders`;
  }

  async getOrderCount(): Promise<number> {
    return this.orderRows.count();
  }

  async searchOrders(query: string): Promise<void> {
    await this.fill(this.searchInput, query);
    await this.page.keyboard.press('Enter');
  }

  async filterByStatus(
    status: 'all' | 'pending' | 'processing' | 'shipped' | 'delivered' | 'cancelled'
  ): Promise<void> {
    await this.selectOption(this.statusFilter, status);
  }

  async viewOrderDetails(orderId: string): Promise<void> {
    await this.orderRows.filter({ hasText: orderId }).getByRole('button', { name: 'View' }).click();
  }

  async updateOrderStatus(
    status: 'pending' | 'processing' | 'shipped' | 'delivered' | 'cancelled'
  ): Promise<void> {
    await this.selectOption(this.orderStatusSelect, status);
    await this.click(this.updateStatusButton);
  }

  async closeOrderDetail(): Promise<void> {
    await this.click(this.closeModalButton);
  }

  async getOrderStatus(orderId: string): Promise<string> {
    return this.orderRows
      .filter({ hasText: orderId })
      .locator('[data-testid="order-status"]')
      .textContent() as Promise<string>;
  }

  async getOrderIds(): Promise<string[]> {
    return this.orderRows.locator('[data-testid="order-id"]').allTextContents();
  }

  async exportOrders(): Promise<void> {
    await this.click(this.exportButton);
  }

  async refreshOrders(): Promise<void> {
    await this.click(this.refreshButton);
  }

  async getOrderTotal(orderId: string): Promise<number> {
    const text = await this.orderRows
      .filter({ hasText: orderId })
      .locator('[data-testid="order-total"]')
      .textContent();
    return parseFloat((text || '0').replace(/[^0-9.]/g, ''));
  }
}
