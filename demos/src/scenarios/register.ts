import type { Page } from 'playwright';
import { config } from '../config.ts';
import { saveRegisteredAccount } from '../state.ts';

// The customer-frontend /register route currently console.logs and navigates
// to "/" — it does not call POST /api/v1/users/register. Until that is wired
// up, the demo also creates and activates the account via the identity API
// directly so the resulting account is real and can be signed into. Remove
// this backstop once the UI submits to the backend.
async function registerViaApi(email: string, password: string): Promise<void> {
  const registerRes = await fetch(`${config.identityApiUrl}/api/v1/users/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email,
      password,
      firstName: 'Demo',
      lastName: 'User',
      tosAccepted: true,
      tosAcceptedAt: new Date().toISOString(),
      marketingOptIn: false,
    }),
  });
  if (!registerRes.ok) {
    throw new Error(
      `Identity API registration failed: ${registerRes.status} ${await registerRes.text()}`
    );
  }
  const { userId } = (await registerRes.json()) as { userId: string };

  // New accounts are PENDING_VERIFICATION; signin will fail until verified.
  // Use the test-only endpoint to fetch the verification token, then verify.
  const tokenRes = await fetch(
    `${config.identityApiUrl}/api/v1/test/users/${userId}/verification-token`,
    { headers: { 'X-Test-Api-Key': config.testApiKey } }
  );
  if (!tokenRes.ok) {
    throw new Error(
      `Fetching verification token failed: ${tokenRes.status} ${await tokenRes.text()}`
    );
  }
  const { token } = (await tokenRes.json()) as { token: string };

  // The verify endpoint ALWAYS returns 302 — success goes to /login?verified=true
  // or /login?already_verified=true; failures go to /verify/resend?error=…. We
  // must inspect the Location header to tell the cases apart.
  const verifyRes = await fetch(
    `${config.identityApiUrl}/api/v1/users/verify?token=${encodeURIComponent(token)}`,
    { redirect: 'manual' }
  );
  if (verifyRes.status >= 400) {
    throw new Error(
      `Email verification failed: ${verifyRes.status} ${await verifyRes.text()}`
    );
  }
  const location = verifyRes.headers.get('location') ?? '';
  const verified = /[?&](verified|already_verified)=true(?:&|$)/.test(location);
  if (!verified) {
    throw new Error(`Email verification did not succeed; identity service redirected to ${location}`);
  }
}

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

  await registerViaApi(email, password);
  await saveRegisteredAccount({ email, password });
}
