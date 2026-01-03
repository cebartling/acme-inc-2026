import {
  Before,
  After,
  BeforeAll,
  AfterAll,
  BeforeStep,
  AfterStep,
  Status,
} from '@cucumber/cucumber';
import { chromium, Browser } from '@playwright/test';
import { config, browserOptions } from '../playwright.config.js';
import { CustomWorld } from './world.js';

let sharedBrowser: Browser | null = null;

BeforeAll(async function () {
  // Launch shared browser instance for all scenarios
  sharedBrowser = await chromium.launch(browserOptions);
});

AfterAll(async function () {
  // Close shared browser after all scenarios
  if (sharedBrowser) {
    await sharedBrowser.close();
    sharedBrowser = null;
  }
});

Before(async function (this: CustomWorld) {
  // Create new context and page for each scenario
  await this.launchBrowser();
  await this.createContext();
});

Before({ tags: '@customer' }, async function (this: CustomWorld) {
  // Navigate to customer app for customer-tagged scenarios
  await this.page.goto(this.getCustomerAppUrl());
});

Before({ tags: '@admin' }, async function (this: CustomWorld) {
  // Navigate to admin app for admin-tagged scenarios
  await this.page.goto(this.getAdminAppUrl());
});

After(async function (this: CustomWorld, scenario) {
  // Take screenshot on failure
  if (scenario.result?.status === Status.FAILED && config.screenshot.onFailure) {
    const screenshot = await this.page.screenshot({
      fullPage: config.screenshot.fullPage,
    });

    await this.attach(screenshot, 'image/png');

    // Also attach the page URL and any console errors
    await this.attach(`Failed at URL: ${this.page.url()}`, 'text/plain');
  }

  // Cleanup context
  await this.closeContext();
  await this.closeBrowser();

  // Clear test data
  this.clearTestData();
});

BeforeStep(async function (this: CustomWorld) {
  // Optional: Add step-level setup if needed
});

AfterStep(async function (this: CustomWorld, _step) {
  // Optional: Take screenshot after each step for debugging
  if (process.env.SCREENSHOT_EACH_STEP === 'true') {
    const screenshot = await this.page.screenshot();
    await this.attach(screenshot, 'image/png');
  }
});
