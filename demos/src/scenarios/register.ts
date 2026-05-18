import type { Page } from 'playwright';
import { config } from '../config.ts';
import { registerAndVerifyUser } from '../identity-api.ts';
import { saveRegisteredAccount } from '../state.ts';

export default async function register(page: Page): Promise<void> {
  const stamp = Date.now();
  const email = `demo-${stamp}@acme.test`;
  const password = config.demoPassword;

  await page.goto(`${config.customerAppUrl}/register`);
  await page.getByRole('heading', { name: 'Welcome to ACME' }).waitFor();

  await page.getByRole('textbox', { name: 'Email' }).fill(email);
  await page.waitForTimeout(400);
  await page.locator('input[name="password"]').fill(password);
  await page.locator('input[name="confirmPassword"]').fill(password);
  await page.waitForTimeout(400);
  await page.getByLabel('First Name').fill('Demo');
  await page.getByLabel('Last Name').fill('User');
  await page.waitForTimeout(400);

  await page.getByLabel(/I accept the Terms of Service/).click();
  await page.getByLabel(/I accept the Privacy Policy/).click();
  await page.waitForTimeout(400);

  await page.getByRole('button', { name: 'Create Account' }).click();

  await page.waitForURL((url) => !url.pathname.endsWith('/register'), {
    timeout: 15000,
  });

  await registerAndVerifyUser({ email, password });
  await saveRegisteredAccount({ email, password });
}
