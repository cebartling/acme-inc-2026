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

// Helper function to run async operations with timeout
async function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  operation: string
): Promise<T | null> {
  const timeoutPromise = new Promise<null>((resolve) => {
    setTimeout(() => {
      console.warn(`⚠️  Timeout after ${timeoutMs}ms: ${operation}`);
      resolve(null);
    }, timeoutMs);
  });

  return Promise.race([promise, timeoutPromise]);
}

// UI Tests: Launch browser and create context for @customer or @admin tagged scenarios
Before({ tags: '@customer or @admin' }, async function (this: CustomWorld) {
  await this.launchBrowser();
  await this.createContext();
});

// API Tests: Initialize API clients and create test session for @api tagged scenarios
Before({ tags: '@api and not @customer and not @admin' }, async function (this: CustomWorld) {
  this.initializeApiClients();
  await this.createTestSession();
});

// UI Tests: Also initialize API clients for @customer scenarios that need API setup
Before({ tags: '@customer' }, async function (this: CustomWorld) {
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

// After hook with reasonable timeout for cleanup
After({ timeout: 30000 }, async function (this: CustomWorld, scenario) {
  // Only handle browser cleanup for UI tests
  if (this.page) {
    try {
      // Take screenshot on failure
      if (scenario.result?.status === Status.FAILED && config.screenshot.onFailure) {
        await withTimeout(
          (async () => {
            const screenshot = await this.page.screenshot({
              fullPage: config.screenshot.fullPage,
            });
            await this.attach(screenshot, 'image/png');
            await this.attach(`Failed at URL: ${this.page.url()}`, 'text/plain');
          })(),
          5000,
          'Screenshot capture'
        );
      }
    } catch (error) {
      console.warn('⚠️  Failed to capture screenshot:', error);
    }

    // Cleanup browser context - short timeout since we don't want to wait long
    try {
      await withTimeout(this.closeContext(), 3000, 'Close browser context');
    } catch (error) {
      // Silently ignore - context will be cleaned up on process exit
    }

    // Cleanup browser - this is now a no-op but kept for consistency
    try {
      await withTimeout(this.closeBrowser(), 1000, 'Close browser');
    } catch (error) {
      // Silently ignore - browser will be cleaned up on process exit
    }
  }

  // Clean up test session (rolls back all created test data)
  // Only attempt if we have a test session to clean up
  if (this._testSessionId) {
    try {
      await withTimeout(this.cleanupTestSession(), 5000, 'Cleanup test session');
    } catch (error) {
      console.warn('⚠️  Failed to cleanup test session:', error);
    }
  }

  // Clear local test data
  try {
    this.clearTestData();
  } catch (error) {
    // Silently ignore
  }
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
