import { Page, Locator, expect } from '@playwright/test';

export abstract class BasePage {
  protected page: Page;

  constructor(page: Page) {
    this.page = page;
  }

  abstract get url(): string;

  async navigate(): Promise<void> {
    await this.page.goto(this.url);
    await this.waitForPageLoad();
  }

  async waitForPageLoad(): Promise<void> {
    await this.page.waitForLoadState('domcontentloaded');
  }

  async waitForNetworkIdle(): Promise<void> {
    await this.page.waitForLoadState('networkidle');
  }

  async getTitle(): Promise<string> {
    return this.page.title();
  }

  async getCurrentUrl(): Promise<string> {
    return this.page.url();
  }

  async waitForElement(locator: Locator, timeout?: number): Promise<void> {
    await locator.waitFor({ state: 'visible', timeout });
  }

  async click(locator: Locator): Promise<void> {
    await locator.click();
  }

  async fill(locator: Locator, text: string): Promise<void> {
    await locator.fill(text);
  }

  async getText(locator: Locator): Promise<string> {
    return (await locator.textContent()) || '';
  }

  async isVisible(locator: Locator): Promise<boolean> {
    return locator.isVisible();
  }

  async isEnabled(locator: Locator): Promise<boolean> {
    return locator.isEnabled();
  }

  async selectOption(locator: Locator, value: string): Promise<void> {
    await locator.selectOption(value);
  }

  async checkCheckbox(locator: Locator): Promise<void> {
    if (!(await locator.isChecked())) {
      await locator.check();
    }
  }

  async uncheckCheckbox(locator: Locator): Promise<void> {
    if (await locator.isChecked()) {
      await locator.uncheck();
    }
  }

  async expectVisible(locator: Locator): Promise<void> {
    await expect(locator).toBeVisible();
  }

  async expectNotVisible(locator: Locator): Promise<void> {
    await expect(locator).not.toBeVisible();
  }

  async expectText(locator: Locator, text: string): Promise<void> {
    await expect(locator).toHaveText(text);
  }

  async expectToContainText(locator: Locator, text: string): Promise<void> {
    await expect(locator).toContainText(text);
  }

  async expectUrl(urlPattern: string | RegExp): Promise<void> {
    await expect(this.page).toHaveURL(urlPattern);
  }

  async takeScreenshot(name: string): Promise<Buffer> {
    return this.page.screenshot({ path: `reports/screenshots/${name}.png` });
  }
}
