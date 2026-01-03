import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';
import { Product } from '../../support/test-data.js';

export class ProductsPage extends BasePage {
  // Locators
  readonly pageTitle: Locator;
  readonly addProductButton: Locator;
  readonly searchInput: Locator;
  readonly categoryFilter: Locator;
  readonly stockFilter: Locator;
  readonly productsTable: Locator;
  readonly productRows: Locator;
  readonly pagination: Locator;
  readonly bulkActionsDropdown: Locator;
  readonly selectAllCheckbox: Locator;

  // Modal Locators
  readonly productModal: Locator;
  readonly productNameInput: Locator;
  readonly productDescriptionInput: Locator;
  readonly productPriceInput: Locator;
  readonly productCategorySelect: Locator;
  readonly productSkuInput: Locator;
  readonly productQuantityInput: Locator;
  readonly productInStockCheckbox: Locator;
  readonly saveProductButton: Locator;
  readonly cancelButton: Locator;
  readonly deleteConfirmButton: Locator;

  constructor(page: Page) {
    super(page);
    this.pageTitle = page.getByRole('heading', { name: 'Products' });
    this.addProductButton = page.getByRole('button', { name: 'Add product' });
    this.searchInput = page.getByPlaceholder('Search products...');
    this.categoryFilter = page.getByLabel('Category');
    this.stockFilter = page.getByLabel('Stock status');
    this.productsTable = page.getByTestId('products-table');
    this.productRows = page.getByTestId('product-row');
    this.pagination = page.getByTestId('pagination');
    this.bulkActionsDropdown = page.getByLabel('Bulk actions');
    this.selectAllCheckbox = page.getByLabel('Select all');

    // Modal
    this.productModal = page.getByTestId('product-modal');
    this.productNameInput = page.getByLabel('Product name');
    this.productDescriptionInput = page.getByLabel('Description');
    this.productPriceInput = page.getByLabel('Price');
    this.productCategorySelect = page.getByLabel('Category');
    this.productSkuInput = page.getByLabel('SKU');
    this.productQuantityInput = page.getByLabel('Quantity');
    this.productInStockCheckbox = page.getByLabel('In stock');
    this.saveProductButton = page.getByRole('button', { name: 'Save' });
    this.cancelButton = page.getByRole('button', { name: 'Cancel' });
    this.deleteConfirmButton = page.getByRole('button', { name: 'Delete' });
  }

  get url(): string {
    return `${config.baseUrl.admin}/products`;
  }

  async getProductCount(): Promise<number> {
    return this.productRows.count();
  }

  async searchProducts(query: string): Promise<void> {
    await this.fill(this.searchInput, query);
    await this.page.keyboard.press('Enter');
  }

  async filterByCategory(category: string): Promise<void> {
    await this.selectOption(this.categoryFilter, category);
  }

  async filterByStock(status: 'all' | 'in-stock' | 'out-of-stock'): Promise<void> {
    await this.selectOption(this.stockFilter, status);
  }

  async clickAddProduct(): Promise<void> {
    await this.click(this.addProductButton);
  }

  async fillProductForm(product: Product): Promise<void> {
    await this.fill(this.productNameInput, product.name);
    await this.fill(this.productDescriptionInput, product.description);
    await this.fill(this.productPriceInput, product.price.toString());
    await this.selectOption(this.productCategorySelect, product.category);
    await this.fill(this.productSkuInput, product.sku);
    if (product.quantity !== undefined) {
      await this.fill(this.productQuantityInput, product.quantity.toString());
    }
    if (product.inStock) {
      await this.checkCheckbox(this.productInStockCheckbox);
    } else {
      await this.uncheckCheckbox(this.productInStockCheckbox);
    }
  }

  async saveProduct(): Promise<void> {
    await this.click(this.saveProductButton);
  }

  async cancelProductForm(): Promise<void> {
    await this.click(this.cancelButton);
  }

  async createProduct(product: Product): Promise<void> {
    await this.clickAddProduct();
    await this.fillProductForm(product);
    await this.saveProduct();
  }

  async editProduct(productName: string): Promise<void> {
    await this.productRows
      .filter({ hasText: productName })
      .getByRole('button', { name: 'Edit' })
      .click();
  }

  async deleteProduct(productName: string): Promise<void> {
    await this.productRows
      .filter({ hasText: productName })
      .getByRole('button', { name: 'Delete' })
      .click();
    await this.click(this.deleteConfirmButton);
  }

  async getProductNames(): Promise<string[]> {
    return this.productRows.locator('[data-testid="product-name"]').allTextContents();
  }

  async isProductInList(productName: string): Promise<boolean> {
    const names = await this.getProductNames();
    return names.includes(productName);
  }

  async selectProduct(productName: string): Promise<void> {
    await this.productRows.filter({ hasText: productName }).getByRole('checkbox').check();
  }

  async bulkDelete(): Promise<void> {
    await this.selectOption(this.bulkActionsDropdown, 'delete');
    await this.click(this.deleteConfirmButton);
  }
}
