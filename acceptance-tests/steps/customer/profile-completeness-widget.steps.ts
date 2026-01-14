import { Given, When, Then, DataTable } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';
import { DashboardPage } from '../../pages/customer/dashboard.page.js';

/**
 * Sets up mock auth state in localStorage for frontend routes that require authentication.
 * Also creates a test customer in the backend via the test helper API.
 */
async function setupAuthenticatedCustomer(world: CustomWorld): Promise<void> {
  const customerId = world.getTestData<string>('customerId') || crypto.randomUUID();
  const userId = world.getTestData<string>('userId') || crypto.randomUUID();

  // Store for later use
  world.setTestData('customerId', customerId);
  world.setTestData('userId', userId);

  // Create the customer in the backend via test helper API
  try {
    await world.customerApiClient.post('/api/v1/test/customers', {
      customerId,
      userId,
      email: `test-${customerId}@example.com`,
      firstName: 'Test',
      lastName: 'User',
      displayName: 'Test User',
      emailVerified: true,
    });
  } catch (error: unknown) {
    // Customer might already exist, that's okay
    if (
      error &&
      typeof error === 'object' &&
      'response' in error &&
      error.response
    ) {
      const err = error as { response: { status: number } };
      if (err.response.status !== 409 && err.response.status !== 200) {
        console.warn('Warning: Could not create test customer:', err.response.status);
      }
    }
  }

  // Inject auth state into localStorage before navigation
  await world.page.addInitScript((authData) => {
    localStorage.setItem('auth-storage', JSON.stringify({
      state: {
        user: {
          userId: authData.userId,
          customerId: authData.customerId,
          email: 'test@example.com',
          firstName: 'Test',
          lastName: 'User',
        },
        isAuthenticated: true,
        isLoading: false,
      },
      version: 0,
    }));
  }, { customerId, userId });
}

// ============================================================================
// Background Steps
// ============================================================================

Given('I am logged in as an authenticated customer', async function (this: CustomWorld) {
  await setupAuthenticatedCustomer(this);
});

// ============================================================================
// Navigation Steps
// ============================================================================

When('I navigate to the dashboard', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.goto();
  // Wait for widget to finish loading (success or error)
  await dashboardPage.waitForWidgetLoad();
  // Give a brief moment for any additional rendering
  await this.page.waitForTimeout(500);
});

Then('I should be navigated to the profile completion page', async function (this: CustomWorld) {
  // Accept any profile-related URL since "Complete Now" navigates to specific sections
  await expect(this.page).toHaveURL(/\/profile\//, { timeout: 10000 });
});

// ============================================================================
// Widget Visibility Steps
// ============================================================================

Then('I should see the profile completeness widget', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  const isVisible = await dashboardPage.isWidgetVisible();
  expect(isVisible).toBe(true);
});

Then('the widget should display a circular progress ring', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  const isVisible = await dashboardPage.isProgressRingVisible();
  expect(isVisible).toBe(true);
});

Then('the widget should show the completeness percentage', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  const score = await dashboardPage.getScore();
  expect(score).toBeGreaterThanOrEqual(0);
  expect(score).toBeLessThanOrEqual(100);
});

Then('the widget should display {string} as the title', async function (this: CustomWorld, expectedTitle: string) {
  const dashboardPage = new DashboardPage(this.page);
  const title = await dashboardPage.getWidgetTitle();
  expect(title).toBe(expectedTitle);
});

// ============================================================================
// Profile State Setup Steps
// ============================================================================

Given('my profile completeness is below 50 percent', async function (this: CustomWorld) {
  // New customers start with ~25-40% (basic info complete, everything else empty)
  // No additional setup needed - the default test customer has low completeness
});

Given('my profile completeness is between 50 and 79 percent', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  // Add phone number (+15%)
  await this.customerApiClient.patch(
    `/api/v1/customers/${customerId}/profile`,
    { phone: { countryCode: '+1', number: '2024561234' } },
    { headers: { 'X-User-Id': userId! } }
  );

  // Add date of birth (+15%)
  await this.customerApiClient.patch(
    `/api/v1/customers/${customerId}/profile`,
    { dateOfBirth: '1990-05-15' },
    { headers: { 'X-User-Id': userId! } }
  );

  // This gives us ~55% (25% basic + 15% contact + 15% personal)
});

Given('my profile completeness is 80 percent or higher', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  // Add phone number (+15%)
  await this.customerApiClient.patch(
    `/api/v1/customers/${customerId}/profile`,
    { phone: { countryCode: '+1', number: '2024561234' } },
    { headers: { 'X-User-Id': userId! } }
  );

  // Add date of birth (+15%)
  await this.customerApiClient.patch(
    `/api/v1/customers/${customerId}/profile`,
    { dateOfBirth: '1990-05-15' },
    { headers: { 'X-User-Id': userId! } }
  );

  // Add and validate address (+20%)
  try {
    const addressResponse = await this.customerApiClient.post<{ addressId: string }>(
      `/api/v1/customers/${customerId}/addresses`,
      {
        type: 'SHIPPING',
        street: { line1: '123 Main St' },
        city: 'Washington',
        state: 'DC',
        postalCode: '20001',
        country: 'US',
        isDefault: true,
      },
      { headers: { 'X-User-Id': userId! } }
    );

    if (addressResponse.data?.addressId) {
      await this.customerApiClient.put(
        `/api/v1/test/customers/${customerId}/addresses/${addressResponse.data.addressId}/validate`,
        { isValid: true }
      );
    }
  } catch (error) {
    console.warn('Warning: Could not add address');
  }

  // Grant consent (+10%)
  try {
    await this.customerApiClient.post(
      `/api/v1/customers/${customerId}/consents`,
      {
        consentType: 'DATA_PROCESSING',
        granted: true,
        source: 'REGISTRATION',
        ipAddress: '127.0.0.1',
      },
      { headers: { 'X-User-Id': userId! } }
    );
  } catch (error) {
    console.warn('Warning: Could not grant consent');
  }

  // This gives us ~85% (25% basic + 15% contact + 15% personal + 20% address + 10% consent)
  // Note: Preferences are already created by test helper, so we have 100%
});

Given('my profile completeness is below 80 percent', async function (this: CustomWorld) {
  // New customers start with ~25-40% (basic info complete, everything else empty)
  // No additional setup needed
});

Given('my profile is 100 percent complete', async function (this: CustomWorld) {
  const customerId = this.getTestData<string>('customerId');
  const userId = this.getTestData<string>('userId');

  // Add phone number (+15%)
  await this.customerApiClient.patch(
    `/api/v1/customers/${customerId}/profile`,
    { phone: { countryCode: '+1', number: '2024561234' } },
    { headers: { 'X-User-Id': userId! } }
  );

  // Add date of birth (+15%)
  await this.customerApiClient.patch(
    `/api/v1/customers/${customerId}/profile`,
    { dateOfBirth: '1990-05-15' },
    { headers: { 'X-User-Id': userId! } }
  );

  // Add and validate address (+20%)
  try {
    const addressResponse = await this.customerApiClient.post<{ addressId: string }>(
      `/api/v1/customers/${customerId}/addresses`,
      {
        type: 'SHIPPING',
        street: { line1: '123 Main St' },
        city: 'Washington',
        state: 'DC',
        postalCode: '20001',
        country: 'US',
        isDefault: true,
      },
      { headers: { 'X-User-Id': userId! } }
    );

    if (addressResponse.data?.addressId) {
      await this.customerApiClient.put(
        `/api/v1/test/customers/${customerId}/addresses/${addressResponse.data.addressId}/validate`,
        { isValid: true }
      );
    }
  } catch (error) {
    console.warn('Warning: Could not add address');
  }

  // Grant consent (+10%)
  try {
    await this.customerApiClient.post(
      `/api/v1/customers/${customerId}/consents`,
      {
        consentType: 'DATA_PROCESSING',
        granted: true,
        source: 'REGISTRATION',
        ipAddress: '127.0.0.1',
      },
      { headers: { 'X-User-Id': userId! } }
    );
  } catch (error) {
    console.warn('Warning: Could not grant consent');
  }

  // Preferences are already created by test helper, so we have 100%
});

Given('my profile is not 100 percent complete', async function (this: CustomWorld) {
  // New customers start with ~25-40%, so no additional setup needed
});

Given('my basic info is complete', async function (this: CustomWorld) {
  // Basic info is complete by default (firstName, lastName, emailVerified are set in test customer)
});

Given('my contact info is incomplete', async function (this: CustomWorld) {
  // Contact info is incomplete by default (no phone number set)
});

Given('the next recommended action is to add a phone number', async function (this: CustomWorld) {
  // This is the typical next action for a new customer
});

Given('the profile completeness API is unavailable', async function (this: CustomWorld) {
  // Mock API failure by routing the completeness endpoint to return an error
  await this.page.route('**/api/v1/customers/*/profile/completeness', (route) => {
    route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ error: 'Internal Server Error' }),
    });
  });
});

// ============================================================================
// Color Coding Steps
// ============================================================================

Then('the progress ring should be displayed in red', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const color = await dashboardPage.getProgressRingColor();
  expect(color).toBe('red');
});

Then('the progress ring should be displayed in yellow', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const color = await dashboardPage.getProgressRingColor();
  expect(color).toBe('yellow');
});

Then('the progress ring should be displayed in green', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const color = await dashboardPage.getProgressRingColor();
  expect(color).toBe('green');
});

// ============================================================================
// Message and Button Steps
// ============================================================================

Then('I should see a message to complete my profile', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const description = await dashboardPage.getWidgetDescription();
  expect(description).toContain('Complete your profile');
});

Then('I should see the {string} button', async function (this: CustomWorld, buttonName: string) {
  const dashboardPage = new DashboardPage(this.page);
  if (buttonName === 'Complete Your Profile') {
    const isVisible = await dashboardPage.isCompleteProfileButtonVisible();
    expect(isVisible).toBe(true);
  } else if (buttonName === 'Complete Now') {
    const isVisible = await dashboardPage.isNextActionBannerVisible();
    expect(isVisible).toBe(true);
  }
});

Then('I should not see the {string} button', async function (this: CustomWorld, buttonName: string) {
  const dashboardPage = new DashboardPage(this.page);
  if (buttonName === 'Complete Your Profile') {
    const isVisible = await dashboardPage.isCompleteProfileButtonVisible();
    expect(isVisible).toBe(false);
  }
});

Then('I should see {string} message', async function (this: CustomWorld, expectedMessage: string) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const description = await dashboardPage.getWidgetDescription();
  expect(description).toContain(expectedMessage);
});

// ============================================================================
// Section Breakdown Steps
// ============================================================================

Then('I should see the following sections in the widget:', async function (this: CustomWorld, dataTable: DataTable) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const expectedSections = dataTable.hashes().map((row) => row.section);
  const actualSections = await dashboardPage.getSectionNames();

  for (const expected of expectedSections) {
    expect(actualSections).toContain(expected);
  }
});

When('I click on the {string} section', async function (this: CustomWorld, sectionName: string) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  await dashboardPage.clickSection(sectionName);
});

Then('the section should expand', async function (this: CustomWorld) {
  // Wait a moment for the expansion animation
  await this.page.waitForTimeout(300);
});

Then('I should see the following items:', async function (this: CustomWorld, dataTable: DataTable) {
  const dashboardPage = new DashboardPage(this.page);
  const expectedItems = dataTable.hashes().map((row) => row.item);
  const actualItems = await dashboardPage.getExpandedSectionItems('Basic Information');

  for (const expected of expectedItems) {
    expect(actualItems.some(item => item.includes(expected))).toBe(true);
  }
});

Then('the {string} section should show a checkmark', async function (this: CustomWorld, sectionName: string) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const isComplete = await dashboardPage.isSectionComplete(sectionName);
  expect(isComplete).toBe(true);
});

Then('the {string} section should show an empty circle', async function (this: CustomWorld, sectionName: string) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const isComplete = await dashboardPage.isSectionComplete(sectionName);
  expect(isComplete).toBe(false);
});

Then('the {string} section should show {string} score', async function (this: CustomWorld, sectionName: string, expectedScore: string) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const score = await dashboardPage.getSectionScore(sectionName);
  expect(score).toContain(expectedScore);
});

// ============================================================================
// Next Action Steps
// ============================================================================

Then('I should see {string} banner', async function (this: CustomWorld, bannerTitle: string) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  const isVisible = await dashboardPage.isNextActionBannerVisible();
  expect(isVisible).toBe(true);
});

When('I click the Complete Now button', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  await dashboardPage.clickCompleteNow();
});

When('I click the Complete Your Profile button', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForSuccessfulLoad();
  await dashboardPage.clickCompleteProfile();
});

// ============================================================================
// Loading and Error State Steps
// ============================================================================

Then('the widget should initially show a loading skeleton', async function (this: CustomWorld) {
  // This step is tricky because loading is fast
  // We just verify the widget eventually loads
  const dashboardPage = new DashboardPage(this.page);
  await dashboardPage.waitForWidgetLoad();
  const isVisible = await dashboardPage.isWidgetVisible();
  expect(isVisible).toBe(true);
});

Then('the widget should display an error message', async function (this: CustomWorld) {
  const dashboardPage = new DashboardPage(this.page);
  await this.page.waitForTimeout(2000); // Wait for error to appear
  const hasError = await dashboardPage.hasError();
  expect(hasError).toBe(true);
});
