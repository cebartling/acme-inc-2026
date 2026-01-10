import {
  Before,
  After,
  BeforeStep,
  AfterStep,
  Status,
  setDefaultTimeout,
} from '@cucumber/cucumber';
import { config } from '../playwright.config.js';
import { CustomWorld } from './world.js';

// Set default timeout to 30 seconds for all steps
setDefaultTimeout(30000);

// UI Tests: Launch browser and create context for @customer or @admin tagged scenarios
Before({ tags: '@customer or @admin' }, async function (this: CustomWorld) {
  await this.launchBrowser();
  await this.createContext();
});

// API Tests: Only initialize API clients for @api tagged scenarios (no browser needed)
Before({ tags: '@api and not @customer and not @admin' }, async function (this: CustomWorld) {
  this.initializeApiClients();
});

// Navigate to customer app for @customer scenarios (except @registration which navigates itself)
Before({ tags: '@customer and not @registration' }, async function (this: CustomWorld) {
  await this.page.goto(this.getCustomerAppUrl());
});

// Navigate to admin app for @admin scenarios
Before({ tags: '@admin' }, async function (this: CustomWorld) {
  await this.page.goto(this.getAdminAppUrl());
});

After(async function (this: CustomWorld, scenario) {
  // Only handle browser cleanup for UI tests
  if (this.page) {
    // Take screenshot on failure
    if (scenario.result?.status === Status.FAILED && config.screenshot.onFailure) {
      const screenshot = await this.page.screenshot({
        fullPage: config.screenshot.fullPage,
      });

      await this.attach(screenshot, 'image/png');
      await this.attach(`Failed at URL: ${this.page.url()}`, 'text/plain');
    }

    // Cleanup browser context
    await this.closeContext();
    await this.closeBrowser();
  }

  // Clear test data for all tests
  this.clearTestData();
});

BeforeStep(async function (this: CustomWorld) {
  // Optional: Add step-level setup if needed
});

AfterStep(async function (this: CustomWorld, _step) {
  // Optional: Take screenshot after each step for debugging (only for UI tests)
  if (process.env.SCREENSHOT_EACH_STEP === 'true' && this.page) {
    const screenshot = await this.page.screenshot();
    await this.attach(screenshot, 'image/png');
  }
});
