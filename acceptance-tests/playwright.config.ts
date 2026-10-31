import { LaunchOptions } from '@playwright/test';

const isHeaded = process.env.HEADED === 'true';

export const browserOptions: LaunchOptions = {
  headless: !isHeaded,
  slowMo: isHeaded ? 50 : 0,
  args: ['--disable-gpu', '--no-sandbox', '--disable-setuid-sandbox'],
};

export const config = {
  browser: (process.env.BROWSER || 'chromium') as 'chromium' | 'firefox' | 'webkit',
  baseUrl: {
    customer: process.env.CUSTOMER_APP_URL || 'http://localhost:7600',
    admin: process.env.ADMIN_APP_URL || 'http://localhost:5174',
  },
  identityApiUrl: process.env.IDENTITY_API_URL || 'http://localhost:10300',
  customerApiUrl: process.env.CUSTOMER_API_URL || 'http://localhost:10301',
  notificationApiUrl: process.env.NOTIFICATION_API_URL || 'http://localhost:10302',
  cartApiUrl: process.env.CART_API_URL || 'http://localhost:10304',
  orderApiUrl: process.env.ORDER_API_URL || 'http://localhost:10305',
  // The order service's secret for cart calls (must match ACME_ORDER_SERVICE_TOKEN, PIN-382)
  orderServiceToken: process.env.ACME_ORDER_SERVICE_TOKEN || 'local-dev-order-service-token',
  // Test API key for accessing test endpoints (must match identity.test.api-key in backend)
  testApiKey: process.env.TEST_API_KEY || 'test-api-key-for-acceptance-tests',
  customerServiceToken:
    process.env.ACME_CUSTOMER_SERVICE_TOKEN || 'local-dev-customer-service-token',
  timeout: {
    default: 30000,
    navigation: 60000,
    action: 15000,
  },
  screenshot: {
    onFailure: true,
    fullPage: true,
  },
  video: {
    enabled: process.env.RECORD_VIDEO === 'true',
    dir: './reports/videos',
  },
  trace: {
    enabled: process.env.TRACE === 'true',
    dir: './reports/traces',
  },
};
