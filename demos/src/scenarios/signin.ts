import type { Page } from 'playwright';
import { config } from '../config.ts';
import { loadRegisteredAccount } from '../state.ts';

export default async function signin(page: Page): Promise<void> {
  const registered = await loadRegisteredAccount();
  const email = registered?.email ?? config.demoEmail;
  const password = registered?.password ?? config.demoPassword;

  if (registered) {
    console.log(`  using account from last register run: ${email}`);
  } else {
    console.log(`  using DEMO_EMAIL env default: ${email}`);
  }

  await page.goto(`${config.customerAppUrl}/signin`);
  await page.getByRole('heading', { name: /welcome back/i }).waitFor();

  const emailInput = page.getByRole('textbox', { name: 'Email' });
  const passwordInput = page.locator('input[id="password"]');

  await emailInput.fill(email);
  await emailInput.blur();
  await page.waitForTimeout(400);
  await passwordInput.fill(password);
  await passwordInput.blur();
  await page.waitForTimeout(600);

  await page.getByRole('button', { name: /sign in/i }).click();

  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), {
    timeout: 15000,
  });
}
