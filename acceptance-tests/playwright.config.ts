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
    customer: process.env.CUSTOMER_APP_URL || 'http://localhost:3000',
    admin: process.env.ADMIN_APP_URL || 'http://localhost:3001',
  },
  apiUrl: process.env.API_URL || 'http://localhost:10300',
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
