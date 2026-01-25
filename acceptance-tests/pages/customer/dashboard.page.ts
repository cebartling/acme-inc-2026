import { Page, Locator } from '@playwright/test';
import { BasePage } from '../base.page.js';
import { config } from '../../playwright.config.js';

export class DashboardPage extends BasePage {
  // Dashboard locators
  readonly pageTitle: Locator;

  // Profile Completeness Widget locators
  readonly profileCompletenessWidget: Locator;
  readonly widgetTitle: Locator;
  readonly widgetDescription: Locator;
  readonly progressRing: Locator;
  readonly progressRingSvg: Locator;
  readonly progressCircle: Locator;
  readonly scorePercentage: Locator;
  readonly completeText: Locator;
  readonly nextActionBanner: Locator;
  readonly nextActionText: Locator;
  readonly completeNowButton: Locator;
  readonly completeProfileButton: Locator;
  readonly sectionBreakdown: Locator;
  readonly loadingSkeleton: Locator;
  readonly errorCard: Locator;
  readonly errorMessage: Locator;

  constructor(page: Page) {
    super(page);

    // Dashboard
    this.pageTitle = page.getByRole('heading', { name: 'Dashboard', level: 1 });

    // Widget container
    this.profileCompletenessWidget = page.getByTestId('profile-completeness-widget');

    // Widget header - CardTitle uses data-slot, not heading role
    this.widgetTitle = this.profileCompletenessWidget.locator('[data-slot="card-title"]');
    this.widgetDescription = this.profileCompletenessWidget.locator(
      '[data-slot="card-description"]'
    );

    // Progress ring
    this.progressRing = this.profileCompletenessWidget.locator('.relative.inline-flex');
    this.progressRingSvg = this.progressRing.locator('svg');
    this.progressCircle = this.progressRingSvg.locator('circle').nth(1); // Second circle is the progress
    this.scorePercentage = this.progressRing.locator('span.text-2xl');
    this.completeText = this.progressRing.locator('span.text-xs');

    // Next action banner
    this.nextActionBanner = this.profileCompletenessWidget.locator('.bg-cyan-900\\/30');
    this.nextActionText = this.nextActionBanner.locator('p.text-gray-300');
    this.completeNowButton = this.nextActionBanner.getByRole('link', { name: 'Complete Now' });

    // Complete profile button
    this.completeProfileButton = this.profileCompletenessWidget.getByRole('link', {
      name: 'Complete Your Profile',
    });

    // Section breakdown - look for container with section buttons
    this.sectionBreakdown = this.profileCompletenessWidget.locator(
      'div:has(button[type="button"][aria-expanded])'
    );

    // Loading and error states
    this.loadingSkeleton = this.profileCompletenessWidget.locator('.animate-pulse');
    this.errorCard = this.profileCompletenessWidget.locator('[class*="text-red-400"]').first();
    this.errorMessage = this.profileCompletenessWidget.locator('p.text-gray-400');
  }

  get url(): string {
    return `${config.baseUrl.customer}/dashboard`;
  }

  /**
   * Navigate to the dashboard page.
   */
  async goto(): Promise<void> {
    await this.page.goto(this.url);
    await this.waitForPageLoad();
  }

  /**
   * Wait for the widget to finish loading (either success or error state).
   */
  async waitForWidgetLoad(): Promise<void> {
    // Wait for loading state to disappear
    await this.loadingSkeleton.waitFor({ state: 'hidden', timeout: 15000 }).catch(() => {});
    // Wait for the widget container to be visible
    await this.profileCompletenessWidget.waitFor({ state: 'visible', timeout: 10000 });
  }

  /**
   * Wait for the widget to successfully load data (progress ring visible).
   */
  async waitForSuccessfulLoad(): Promise<void> {
    await this.waitForWidgetLoad();
    // Wait specifically for the progress ring to appear
    await this.progressRingSvg.waitFor({ state: 'visible', timeout: 15000 });
  }

  /**
   * Check if the profile completeness widget is visible.
   */
  async isWidgetVisible(): Promise<boolean> {
    return this.isVisible(this.profileCompletenessWidget);
  }

  /**
   * Check if the progress ring is visible.
   */
  async isProgressRingVisible(): Promise<boolean> {
    return this.isVisible(this.progressRingSvg);
  }

  /**
   * Get the displayed completeness score.
   */
  async getScore(): Promise<number> {
    const text = await this.getText(this.scorePercentage);
    return parseInt(text.replace('%', ''), 10);
  }

  /**
   * Get the progress ring color class.
   */
  async getProgressRingColor(): Promise<string> {
    const classList = await this.progressCircle.getAttribute('class');
    if (classList?.includes('stroke-green')) return 'green';
    if (classList?.includes('stroke-yellow')) return 'yellow';
    if (classList?.includes('stroke-red')) return 'red';
    return 'unknown';
  }

  /**
   * Get the widget title text.
   */
  async getWidgetTitle(): Promise<string> {
    return this.getText(this.widgetTitle);
  }

  /**
   * Get the widget description text.
   */
  async getWidgetDescription(): Promise<string> {
    return this.getText(this.widgetDescription);
  }

  /**
   * Check if the next action banner is visible.
   */
  async isNextActionBannerVisible(): Promise<boolean> {
    return this.isVisible(this.nextActionBanner);
  }

  /**
   * Get the next action text.
   */
  async getNextActionText(): Promise<string> {
    return this.getText(this.nextActionText);
  }

  /**
   * Click the "Complete Now" button in the next action banner.
   */
  async clickCompleteNow(): Promise<void> {
    await this.click(this.completeNowButton);
  }

  /**
   * Check if the "Complete Your Profile" button is visible.
   */
  async isCompleteProfileButtonVisible(): Promise<boolean> {
    return this.isVisible(this.completeProfileButton);
  }

  /**
   * Click the "Complete Your Profile" button.
   */
  async clickCompleteProfile(): Promise<void> {
    await this.click(this.completeProfileButton);
  }

  /**
   * Get all section names from the breakdown.
   */
  async getSectionNames(): Promise<string[]> {
    const sections = this.sectionBreakdown.locator('button[type="button"]');
    const names: string[] = [];
    const count = await sections.count();
    for (let i = 0; i < count; i++) {
      const sectionButton = sections.nth(i);
      const nameElement = sectionButton.locator('span.font-medium');
      const text = await nameElement.textContent();
      if (text) names.push(text.trim());
    }
    return names;
  }

  /**
   * Click on a section to expand/collapse it.
   */
  async clickSection(sectionName: string): Promise<void> {
    // Search for section button directly within the widget
    const sectionButton = this.profileCompletenessWidget.locator('button[type="button"]', {
      hasText: sectionName,
    });
    await sectionButton.click();
  }

  /**
   * Check if a section is expanded.
   */
  async isSectionExpanded(sectionName: string): Promise<boolean> {
    const sectionButton = this.sectionBreakdown.locator('button[type="button"]', {
      has: this.page.locator(`text="${sectionName}"`),
    });
    const ariaExpanded = await sectionButton.getAttribute('aria-expanded');
    return ariaExpanded === 'true';
  }

  /**
   * Get items displayed when a section is expanded.
   */
  async getExpandedSectionItems(sectionName: string): Promise<string[]> {
    // Find all item rows in the expanded section
    // Items are in a div with bg-gray-800/50 class that appears after clicking a section
    const itemsContainer = this.profileCompletenessWidget.locator('div.bg-gray-800\\/50');
    const items = itemsContainer.locator('.flex.items-center.gap-2.py-1');

    const names: string[] = [];
    const count = await items.count();
    for (let i = 0; i < count; i++) {
      const text = await items.nth(i).textContent();
      if (text) {
        // Extract just the item name (before any action text)
        const name = text.split(' - ')[0].trim();
        names.push(name);
      }
    }
    return names;
  }

  /**
   * Check if a section shows a checkmark (complete).
   */
  async isSectionComplete(sectionName: string): Promise<boolean> {
    // Search for section button directly within the widget
    const sectionButton = this.profileCompletenessWidget.locator('button[type="button"]', {
      hasText: sectionName,
    });
    // Check for the green checkmark background
    const checkmark = sectionButton.locator('span.rounded-full.bg-green-600');
    return this.isVisible(checkmark);
  }

  /**
   * Get the score displayed for a section.
   */
  async getSectionScore(sectionName: string): Promise<string> {
    // Search for section button directly within the widget
    const sectionButton = this.profileCompletenessWidget.locator('button[type="button"]', {
      hasText: sectionName,
    });
    // Get the percentage score text
    const scoreElement = sectionButton.locator('span.text-sm').filter({ hasText: '%' });
    return this.getText(scoreElement);
  }

  /**
   * Check if the widget is showing loading skeleton.
   */
  async isLoading(): Promise<boolean> {
    return this.isVisible(this.loadingSkeleton);
  }

  /**
   * Check if the widget is showing an error.
   */
  async hasError(): Promise<boolean> {
    return this.isVisible(this.errorCard);
  }

  /**
   * Get the error message text.
   */
  async getErrorMessage(): Promise<string> {
    return this.getText(this.errorMessage);
  }
}
