import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const demosRoot = resolve(here, '..');

export const config = {
  customerAppUrl: process.env.CUSTOMER_APP_URL ?? 'http://localhost:7600',
  identityApiUrl: process.env.IDENTITY_API_URL ?? 'http://localhost:10300',
  testApiKey: process.env.TEST_API_KEY ?? 'test-api-key-for-acceptance-tests',
  demoEmail: process.env.DEMO_EMAIL ?? 'demo@acme.test',
  demoPassword: process.env.DEMO_PASSWORD ?? 'DemoPass123!',
  slowMoMs: Number(process.env.DEMO_SLOWMO_MS ?? 250),
  viewport: { width: 1280, height: 800 },
  recordingsDir: resolve(demosRoot, 'recordings'),
  preflightTimeoutMs: 3000,
};
