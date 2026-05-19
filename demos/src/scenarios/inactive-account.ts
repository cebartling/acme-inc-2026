import type { Page } from 'playwright';
import { config } from '../config.ts';
import { registerAndVerifyUser, setUserStatus } from '../identity-api.ts';

// Walks the audience through the inactive-account UX added in US-0003-11:
//   - PENDING_VERIFICATION: card prompts the customer to check their inbox
//     and offers a "Resend verification email" CTA, which goes into a
//     60-second cooldown after a successful resend
//   - SUSPENDED: card directs the customer to contact support, with a
//     mailto: link to support@acme.com
//   - DEACTIVATED: card explains the account is deactivated and offers a
//     "Reactivate Account" link to the self-service /reactivate page
//   - The /reactivate page accepts the customer's credentials and confirms
//     a reactivation email is on its way (the response is deliberately
//     uniform regardless of state, to prevent account enumeration).
//
// The demo registers ONE throwaway account, verifies it, then flips its
// status through PENDING_VERIFICATION → SUSPENDED → DEACTIVATED via the
// test-only status endpoint so the audience sees all three card variants
// in a single run.
export default async function inactiveAccount(page: Page): Promise<void> {
  const stamp = Date.now();
  const email = `demo-inactive-${stamp}@acme.test`;
  const password = config.demoPassword;

  console.log(`  registering throwaway account ${email}...`);
  const { userId } = await registerAndVerifyUser({ email, password });

  const emailInput = page.getByRole('textbox', { name: 'Email' });
  const passwordInput = page.locator('input[id="password"]');
  const submitButton = page.getByRole('button', { name: /sign in/i });
  const inactiveCard = page.getByTestId('inactive-account-message');

  async function attemptSignin(): Promise<void> {
    await page.goto(`${config.customerAppUrl}/signin`);
    await page.getByRole('heading', { name: /welcome back/i }).waitFor();
    await emailInput.fill(email);
    await emailInput.blur();
    await page.waitForTimeout(300);
    await passwordInput.fill(password);
    await passwordInput.blur();
    await page.waitForTimeout(400);
    await submitButton.click();
  }

  // ---- Variant 1: PENDING_VERIFICATION + resend cooldown ----
  console.log('  variant 1: PENDING_VERIFICATION — "check your inbox" card');
  await setUserStatus(userId, 'PENDING_VERIFICATION');
  await attemptSignin();
  await inactiveCard.waitFor({ timeout: 5000 });
  await page.waitForTimeout(2500);

  const resendButton = page.getByTestId('resend-verification-button');
  console.log('  clicking "Resend verification email" — expect 60s cooldown');
  await resendButton.click();
  await page.getByTestId('resend-verification-success').waitFor({ timeout: 5000 });
  // Linger so viewers can see the success badge AND the disabled-with-countdown button.
  await page.waitForTimeout(3500);

  // ---- Variant 2: SUSPENDED + contact-support CTA ----
  console.log('  variant 2: SUSPENDED — contact-support card');
  await setUserStatus(userId, 'SUSPENDED');
  await attemptSignin();
  await inactiveCard.waitFor({ timeout: 5000 });
  await page.getByTestId('contact-support-link').hover();
  await page.waitForTimeout(2500);
  await page.getByTestId('support-email-link').hover();
  await page.waitForTimeout(2000);

  // ---- Variant 3: DEACTIVATED + self-service reactivation ----
  console.log('  variant 3: DEACTIVATED — reactivation CTA');
  await setUserStatus(userId, 'DEACTIVATED');
  await attemptSignin();
  await inactiveCard.waitFor({ timeout: 5000 });
  await page.waitForTimeout(2000);

  console.log('  clicking "Reactivate Account" — navigates to /reactivate');
  await page.getByTestId('reactivate-account-link').click();
  await page.waitForURL((url) => url.pathname.endsWith('/reactivate'), {
    timeout: 5000,
  });
  await page.getByTestId('reactivate-form').waitFor({ timeout: 5000 });

  // The reactivate form prefills the email from the ?email= search param,
  // but in this demo we click the link from the inactive-account card which
  // already carries it. Fill any missing fields, then submit.
  const reactivateEmail = page.getByTestId('reactivate-email-input');
  const currentValue = await reactivateEmail.inputValue();
  if (!currentValue) {
    await reactivateEmail.fill(email);
  }
  await page.waitForTimeout(500);
  await page.getByTestId('reactivate-password-input').fill(password);
  await page.waitForTimeout(700);

  await page.getByTestId('reactivate-submit').click();
  await page.getByTestId('reactivate-sent').waitFor({ timeout: 8000 });
  // Hold on the confirmation banner so the audience reads it.
  await page.waitForTimeout(3000);

  console.log('  demo complete — three card variants + reactivation flow shown');
}
