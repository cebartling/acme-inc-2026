import type { Page } from 'playwright';
import { config } from '../config.ts';
import { registerAndVerifyUser } from '../identity-api.ts';

// Walks the audience through the invalid-credentials UX added in US-0003-10:
//   - first failure shows the default error banner with a remaining-attempts hint
//   - successive failures decrement the counter
//   - when only 2 attempts remain, the banner switches to its urgent variant
//     (thicker border, bolder copy, "Reset Password" CTA)
//   - the password field is cleared and refocused after each failure, while
//     the email field retains its value
//
// The demo intentionally stops before the 5th attempt to avoid tripping the
// account-lockout flow — that has its own demo story.
//
// IMPORTANT: the identity service deliberately omits `remainingAttempts` from
// its 401 response when the email isn't a registered user — this is a
// username-enumeration mitigation. To make the counter actually tick down,
// the demo registers and verifies a fresh account first, then attempts
// failures against it.
export default async function invalidCredentials(page: Page): Promise<void> {
  const stamp = Date.now();
  const email = `demo-invalid-${stamp}@acme.test`;
  const password = config.demoPassword;
  const wrongPassword = 'WrongPassword123!';

  console.log(`  registering throwaway account ${email}...`);
  await registerAndVerifyUser({ email, password });

  await page.goto(`${config.customerAppUrl}/signin`);
  await page.getByRole('heading', { name: /welcome back/i }).waitFor();

  const emailInput = page.getByRole('textbox', { name: 'Email' });
  const passwordInput = page.locator('input[id="password"]');
  const submitButton = page.getByRole('button', { name: /sign in/i });
  const errorBanner = page.getByTestId('signin-error-banner');
  const remainingAttempts = page.getByTestId('signin-remaining-attempts');

  await emailInput.fill(email);
  await emailInput.blur();
  await page.waitForTimeout(400);

  // ---- Attempt 1: default banner, "4 attempts remaining" ----
  console.log('  attempt 1: submitting wrong password (expect 4 remaining)...');
  await passwordInput.fill(wrongPassword);
  await passwordInput.blur();
  await page.waitForTimeout(400);
  await submitButton.click();

  await errorBanner.waitFor({ timeout: 5000 });
  await remainingAttempts.waitFor({ timeout: 5000 });
  // Let the audience read the banner and notice the empty/refocused password.
  await page.waitForTimeout(2000);

  // ---- Attempt 2: still default variant, "3 attempts remaining" ----
  console.log('  attempt 2: submitting wrong password (expect 3 remaining)...');
  await passwordInput.fill(wrongPassword);
  await passwordInput.blur();
  await page.waitForTimeout(400);
  await submitButton.click();

  await remainingAttempts.waitFor({ timeout: 5000 });
  await page.waitForTimeout(2000);

  // ---- Attempt 3: urgent variant kicks in at 2 remaining ----
  console.log('  attempt 3: submitting wrong password (urgent variant, 2 remaining)...');
  await passwordInput.fill(wrongPassword);
  await passwordInput.blur();
  await page.waitForTimeout(400);
  await submitButton.click();

  await remainingAttempts.waitFor({ timeout: 5000 });
  // Linger longer on the urgent state so viewers can see the styling change
  // and the "Reset Password" call-to-action.
  await page.waitForTimeout(3000);

  // Hover the reset link so it's obvious where the recovery path lives.
  await page.getByTestId('signin-error-reset-link').hover();
  await page.waitForTimeout(1500);

  console.log('  demo complete — stopping before lockout threshold');
}
