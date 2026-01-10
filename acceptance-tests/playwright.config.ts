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
