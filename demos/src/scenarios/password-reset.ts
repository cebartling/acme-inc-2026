import type { Page } from 'playwright';
import { config } from '../config.ts';
import { registerAndVerifyUser, getPasswordResetToken } from '../identity-api.ts';

// Walks an audience through the password-reset flow added in US-0003-13
// (PIN-93). Three scenes in a single browser run:
//
//   1. Forgot-password page — customer enters their email, the UI shows
//      the generic "check your inbox" confirmation (no enumeration leak).
//
//   2. Expired-link page — navigating to /reset-password?token=<bad-token>
//      shows the "invalid or expired" error card with a "Request a new link"
//      CTA back to /forgot-password.
//
//   3. Happy-path reset — the test fixture API issues a real, valid token
//      for the throwaway account; the demo opens /reset-password?token=<real>
//      which validates the token, shows the new-password form, submits a
//      strong password, and lands on the "Password updated" success banner
//      with a "Sign in now" link. The demo then signs in with the NEW
//      password to prove end-to-end correctness.
//
// The demo uses the test-only POST /api/v1/test/password-reset-tokens
// endpoint to mint a token without touching a real mail relay, which is the
// same mechanism used by the acceptance-test suite.
export default async function passwordReset(page: Page): Promise<void> {
  const stamp = Date.now();
  const email = `demo-reset-${stamp}@acme.test`;
  const originalPassword = config.demoPassword;
  const newPassword = 'NewSecure@Pass1!';

  // -----------------------------------------------------------------------
  // Setup — register + verify a throwaway account
  // -----------------------------------------------------------------------
  console.log(`  registering throwaway account ${email}...`);
  const { userId } = await registerAndVerifyUser({ email, password: originalPassword });
  console.log(`  account ready, userId=${userId}`);

  // -----------------------------------------------------------------------
  // Scene 1 — /forgot-password: "check your inbox" confirmation
  // -----------------------------------------------------------------------
  console.log('\n  scene 1: /forgot-password — submit email, see generic confirmation');
  await page.goto(`${config.customerAppUrl}/forgot-password`);
  await page.getByRole('heading', { name: /forgot your password/i }).waitFor();
  await page.waitForTimeout(1000);

  const emailInput = page.getByTestId('forgot-password-email-input');
  await emailInput.fill(email);
  await emailInput.blur();
  await page.waitForTimeout(600);

  await page.getByTestId('forgot-password-submit').click();

  // The identity service returns 200 regardless of whether the email exists;
  // the UI always shows the same generic confirmation.
  await page.getByTestId('forgot-password-sent').waitFor({ timeout: 8000 });
  console.log('  confirmation banner visible');
  await page.waitForTimeout(3000);

  // -----------------------------------------------------------------------
  // Scene 2 — /reset-password?token=bad: expired / invalid link card
  // -----------------------------------------------------------------------
  console.log('\n  scene 2: expired reset link — "invalid or expired" error card');
  await page.goto(
    `${config.customerAppUrl}/reset-password?token=rst_this_token_is_obviously_invalid`
  );
  // The page validates the token on mount; invalid tokens surface the error card.
  await page.getByTestId('reset-password-expired').waitFor({ timeout: 8000 });
  console.log('  expired-link card visible');
  await page.waitForTimeout(2500);

  // Hover over the "Request a new link" CTA to show it's active.
  await page.getByRole('link', { name: /request a new link/i }).hover();
  await page.waitForTimeout(1500);

  // -----------------------------------------------------------------------
  // Scene 3 — happy-path reset using a real fixture token
  // -----------------------------------------------------------------------
  console.log('\n  scene 3: happy-path reset with a valid fixture token');

  // Mint a real token via the test fixture endpoint (no email required).
  const resetToken = await getPasswordResetToken(userId);
  console.log(`  reset token obtained: ${resetToken.slice(0, 20)}…`);

  await page.goto(
    `${config.customerAppUrl}/reset-password?token=${encodeURIComponent(resetToken)}`
  );

  // Token validation runs on mount; wait for the form to appear.
  await page.getByTestId('reset-password-form').waitFor({ timeout: 8000 });
  console.log('  reset-password form ready');
  await page.waitForTimeout(1000);

  // Fill new password
  const newPasswordInput = page.getByTestId('reset-password-new-input');
  const confirmInput = page.getByTestId('reset-password-confirm-input');

  await newPasswordInput.fill(newPassword);
  await newPasswordInput.blur();
  await page.waitForTimeout(500);

  await confirmInput.fill(newPassword);
  await confirmInput.blur();
  await page.waitForTimeout(600);

  // Submit
  await page.getByTestId('reset-password-submit').click();

  // "Password updated" success banner — auto-redirects to /signin after 3 s.
  await page.getByTestId('reset-password-success').waitFor({ timeout: 8000 });
  console.log('  "Password updated" banner visible');
  await page.waitForTimeout(2000);

  // Hover the "Sign in now" link so the audience sees it, then let the
  // auto-redirect take over.
  await page.getByTestId('reset-password-signin-now').hover();
  await page.waitForTimeout(1500);

  // Wait for the 3-second auto-redirect to /signin.
  await page.waitForURL((url) => url.pathname.endsWith('/signin'), {
    timeout: 6000,
  });
  console.log('  auto-redirected to /signin');
  await page.waitForTimeout(1000);

  // -----------------------------------------------------------------------
  // Bonus — sign in with the NEW password to prove end-to-end correctness
  // -----------------------------------------------------------------------
  console.log('\n  bonus: signing in with the new password to prove the reset worked');
  await page.getByRole('textbox', { name: 'Email' }).fill(email);
  await page.getByRole('textbox', { name: 'Email' }).blur();
  await page.waitForTimeout(400);
  await page.locator('input[id="password"]').fill(newPassword);
  await page.locator('input[id="password"]').blur();
  await page.waitForTimeout(400);
  await page.getByRole('button', { name: /sign in/i }).click();

  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), {
    timeout: 15000,
  });
  console.log('  signed in successfully with the new password');
  await page.waitForTimeout(2500);

  console.log('\n  demo complete — forgot-password, expired-link, and happy-path reset shown');
}
