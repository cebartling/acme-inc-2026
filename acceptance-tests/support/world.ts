import { World, IWorldOptions, setWorldConstructor } from '@cucumber/cucumber';
import { Browser, BrowserContext, Page, chromium, firefox, webkit } from '@playwright/test';
import { config, browserOptions } from '../playwright.config.js';
import { ApiClient, ApiResponse } from './api-client.js';

export interface CustomWorldParameters {
  browser?: 'chromium' | 'firefox' | 'webkit';
}

export class CustomWorld extends World<CustomWorldParameters> {
  browser!: Browser;
  context!: BrowserContext;
  page!: Page;
  identityApiClient!: ApiClient;
  customerApiClient!: ApiClient;
  notificationApiClient!: ApiClient;

  private testData: Map<string, unknown> = new Map();
  private _testSessionId: string | null = null;

  constructor(options: IWorldOptions<CustomWorldParameters>) {
    super(options);
  }

  /**
   * Gets the current test session ID.
   */
  get testSessionId(): string | null {
    return this._testSessionId;
  }

  /**
   * Creates a new test session for tracking test data.
   * Call this at the start of each scenario.
   */
  async createTestSession(): Promise<string> {
    if (!this.identityApiClient) {
      this.initializeApiClients();
    }

    const response = await this.identityApiClient.post<{ sessionId: string }>(
      '/api/v1/test/sessions',
      {}
    );
    if (response.status === 200 && response.data.sessionId) {
      this._testSessionId = response.data.sessionId;
      return this._testSessionId;
    }
    throw new Error(`Failed to create test session: ${response.status}`);
  }

  /**
   * Registers a user with the current test session for cleanup.
   */
  async registerUserWithSession(userId: string, email: string): Promise<void> {
    if (!this._testSessionId) {
      return; // No session, skip registration
    }

    await this.identityApiClient.post(`/api/v1/test/sessions/${this._testSessionId}/users`, {
      userId,
      email,
    });
  }

  /**
   * Cleans up the test session, rolling back all created data.
   * Call this at the end of each scenario.
   */
  async cleanupTestSession(): Promise<void> {
    if (!this._testSessionId || !this.identityApiClient) {
      return;
    }

    try {
      await this.identityApiClient.delete(`/api/v1/test/sessions/${this._testSessionId}`);
    } catch (e) {
      // Ignore cleanup errors
    } finally {
      this._testSessionId = null;
    }
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

  initializeApiClients(): void {
    // Identity API client includes test API key for accessing test endpoints
    this.identityApiClient = new ApiClient(config.identityApiUrl, {
      'X-Test-Api-Key': config.testApiKey,
    });
    this.customerApiClient = new ApiClient(config.customerApiUrl);
    this.notificationApiClient = new ApiClient(config.notificationApiUrl);
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

    // Also initialize API clients for UI tests that may need them
    this.initializeApiClients();
  }

  async closeContext(): Promise<void> {
    // Stop tracing if enabled
    if (config.trace.enabled && this.context) {
      try {
        await this.context.tracing.stop({
          path: `${config.trace.dir}/trace-${Date.now()}.zip`,
        });
      } catch (error) {
        console.warn('Failed to stop tracing:', error);
      }
    }

    // Close page first
    if (this.page) {
      try {
        // Force close without waiting for pending operations
        await this.page.close({ runBeforeUnload: false });
        this.page = null;
      } catch (error) {
        console.warn('Failed to close page:', error);
      }
    }

    // Close context
    if (this.context) {
      try {
        await this.context.close();
        this.context = null;
      } catch (error) {
        console.warn('Failed to close context:', error);
      }
    }
  }

  async closeBrowser(): Promise<void> {
    if (this.browser) {
      try {
        await this.browser.close();
      } catch (error) {
        console.warn('Failed to close browser:', error);
      } finally {
        this.browser = null;
      }
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

  setLastResponse(response: ApiResponse<unknown>): void {
    this.setTestData('lastResponse', response);
  }

  getLastResponse<T = unknown>(): ApiResponse<T> | undefined {
    return this.getTestData<ApiResponse<T>>('lastResponse');
  }
}

setWorldConstructor(CustomWorld);
