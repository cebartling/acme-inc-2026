import { World, IWorldOptions, setWorldConstructor } from '@cucumber/cucumber';
import { Browser, BrowserContext, Page, chromium, firefox, webkit } from '@playwright/test';
import { config, browserOptions } from '../playwright.config.js';
import { ApiClient } from './api-client.js';

export interface CustomWorldParameters {
  browser?: 'chromium' | 'firefox' | 'webkit';
}

export class CustomWorld extends World<CustomWorldParameters> {
  browser!: Browser;
  context!: BrowserContext;
  page!: Page;
  apiClient!: ApiClient;

  private testData: Map<string, unknown> = new Map();

  constructor(options: IWorldOptions<CustomWorldParameters>) {
    super(options);
  }

  async launchBrowser(): Promise<void> {
    const browserType = this.parameters.browser || config.browser;

    switch (browserType) {
      case 'firefox':
        this.browser = await firefox.launch(browserOptions);
        break;
      case 'webkit':
        this.browser = await webkit.launch(browserOptions);
        break;
      default:
        this.browser = await chromium.launch(browserOptions);
    }
  }

  async createContext(): Promise<void> {
    this.context = await this.browser.newContext({
      viewport: { width: 1280, height: 720 },
      ignoreHTTPSErrors: true,
      recordVideo: config.video.enabled
        ? { dir: config.video.dir, size: { width: 1280, height: 720 } }
        : undefined,
    });

    if (config.trace.enabled) {
      await this.context.tracing.start({ screenshots: true, snapshots: true });
    }

    this.page = await this.context.newPage();
    this.page.setDefaultTimeout(config.timeout.default);
    this.page.setDefaultNavigationTimeout(config.timeout.navigation);

    this.apiClient = new ApiClient(config.apiUrl);
  }

  async closeContext(): Promise<void> {
    if (config.trace.enabled && this.context) {
      await this.context.tracing.stop({
        path: `${config.trace.dir}/trace-${Date.now()}.zip`,
      });
    }

    if (this.page) {
      await this.page.close();
    }

    if (this.context) {
      await this.context.close();
    }
  }

  async closeBrowser(): Promise<void> {
    if (this.browser) {
      await this.browser.close();
    }
  }

  getCustomerAppUrl(): string {
    return config.baseUrl.customer;
  }

  getAdminAppUrl(): string {
    return config.baseUrl.admin;
  }

  setTestData<T>(key: string, value: T): void {
    this.testData.set(key, value);
  }

  getTestData<T>(key: string): T | undefined {
    return this.testData.get(key) as T | undefined;
  }

  clearTestData(): void {
    this.testData.clear();
  }
}

setWorldConstructor(CustomWorld);
