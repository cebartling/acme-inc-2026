import type { Page } from 'playwright';
import { config } from '../config.ts';
import { loadRegisteredAccount } from '../state.ts';

const logoutAll = process.env.DEMO_LOGOUT_ALL === 'true';

export default async function logout(page: Page): Promise<void> {
  const registered = await loadRegisteredAccount();
  const email = registered?.email ?? config.demoEmail;
  const password = registered?.password ?? config.demoPassword;

  if (registered) {
    console.log(`  using account from last register run: ${email}`);
  } else {
    console.log(`  using DEMO_EMAIL env default: ${email}`);
  }

  // ---- Step 1: sign in (mirrors demos/src/scenarios/signin.ts) ----
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

  // Pause so the audience can read the signed-in state.
  await page.waitForTimeout(1500);

  // ---- Step 2: open the user menu in the header ----
  console.log('  opening user menu...');
  await page.getByRole('button', { name: /open user menu/i }).click();
  await page.waitForTimeout(800);

  if (logoutAll) {
    // ---- Step 3a: sign out of every device via the confirm dialog ----
    console.log('  clicking Sign Out All Devices (DEMO_LOGOUT_ALL=true)...');
    await page.getByRole('menuitem', { name: /sign out all devices/i }).click();
    await page.getByRole('alertdialog').waitFor({ timeout: 5000 });
    await page.waitForTimeout(1200);
    await page.getByRole('button', { name: /^sign out all$/i }).click();
  } else {
    // ---- Step 3b: single-session sign out ----
    console.log('  clicking Sign Out...');
    await page.getByRole('menuitem', { name: /^sign out$/i }).click();
  }

  // TanStack Router JSON-stringifies string search params, so the actual URL
  // is /signin?logout=%22true%22, not /signin?logout=true. Match on the param
  // name only.
  await page.waitForURL(/\/signin\?.*logout=/, { timeout: 10000 });
  await page.waitForTimeout(1500);
  console.log('  signed out, landed on /signin?logout=true');
}
