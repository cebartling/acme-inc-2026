import type { BrowserContext, Page } from 'playwright';
import { config } from '../config.ts';
import { registerAndVerifyUser } from '../identity-api.ts';

// Walks an audience through the token-refresh + reuse-detection plumbing
// added in US-0003-12 (PIN-92), driven through the customer UI:
//
//   1. Register + verify a throwaway account, sign in via the UI. The
//      identity service issues an access_token + refresh_token cookie
//      (HttpOnly, Secure, Path-scoped).
//   2. Inspect the original refresh_token JWT's tokenFamily claim.
//   3. From the *customer app's browser context* (so cookies and CORS
//      behave like the real app), POST /api/v1/auth/refresh — observe
//      the response { status: SUCCESS, expiresIn: 900 } and confirm the
//      tokenFamily claim on the new refresh_token differs.
//   4. Replay the OLD refresh_token cookie (saved in step 2) against the
//      same endpoint. Per OWASP guidance, this fires reuse detection:
//      every session for the user is invalidated, a TokenReuseDetected
//      event is published to Kafka, and the response is
//      401 { error: "TOKEN_REUSE_DETECTED" } with all auth cookies cleared.
//   5. Navigate to /signin?logout=true to show the post-failure UX —
//      the same banner the frontend interceptor would surface if a
//      refresh failed mid-navigation.
//
// What the demo deliberately does NOT show: the full "expired access
// token mid-navigation → transparent refresh → original request
// retried" loop. That requires customer-service to return 401 with
// `error: "TOKEN_EXPIRED"` (it currently emits a generic 401) plus a
// way to mint an expired access-token JWT for tests. Both are flagged
// as @wip placeholders in features/customer/token-refresh-ui.feature
// and called out in PR #73's description. The frontend interceptor
// behavior is covered by 5 Vitest cases in src/services/api.test.ts.
export default async function tokenRefresh(page: Page): Promise<void> {
  const stamp = Date.now();
  const email = `demo-token-refresh-${stamp}@acme.test`;
  const password = config.demoPassword;

  console.log(`  registering throwaway account ${email}...`);
  await registerAndVerifyUser({ email, password });

  // -------------------------------------------------------------------
  // Step 1 — Sign in via the customer UI
  // -------------------------------------------------------------------
  await page.goto(`${config.customerAppUrl}/signin`);
  await page.getByRole('heading', { name: /welcome back/i }).waitFor();
  await page.getByRole('textbox', { name: 'Email' }).fill(email);
  await page.getByRole('textbox', { name: 'Email' }).blur();
  await page.waitForTimeout(400);
  await page.locator('input[id="password"]').fill(password);
  await page.locator('input[id="password"]').blur();
  await page.waitForTimeout(400);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL((url) => !url.pathname.endsWith('/signin'), {
    timeout: 15000,
  });
  console.log('  signed in — refresh_token cookie should now be set');

  // Linger so the audience sees the post-signin landing page.
  await page.waitForTimeout(2500);

  // -------------------------------------------------------------------
  // Step 2 — Capture the original refresh_token + tokenFamily claim
  // -------------------------------------------------------------------
  const context = page.context();
  const originalRefreshCookie = await readRefreshCookie(context);
  if (!originalRefreshCookie) {
    throw new Error('No refresh_token cookie present after signin');
  }
  const originalFamily = readTokenFamily(originalRefreshCookie.value);
  console.log(`  original refresh_token tokenFamily: ${originalFamily}`);

  // -------------------------------------------------------------------
  // Step 3 — Happy-path refresh: POST /auth/refresh from the browser
  // -------------------------------------------------------------------
  console.log('  round 1: POST /api/v1/auth/refresh (happy path rotation)');
  const round1 = await postRefreshFromBrowser(page);
  console.log(`    HTTP ${round1.status} ${JSON.stringify(round1.body)}`);

  const rotatedRefreshCookie = await readRefreshCookie(context);
  if (!rotatedRefreshCookie) {
    throw new Error('refresh_token cookie missing after rotation');
  }
  const rotatedFamily = readTokenFamily(rotatedRefreshCookie.value);
  console.log(`    new tokenFamily: ${rotatedFamily}`);
  if (rotatedFamily === originalFamily) {
    throw new Error('tokenFamily did not change — rotation did not happen');
  }
  // Hold so the audience sees the SUCCESS response in the terminal alongside
  // the still-rendered customer-app page.
  await page.waitForTimeout(3000);

  // -------------------------------------------------------------------
  // Step 4 — Reuse: replay the OLD refresh_token cookie
  // -------------------------------------------------------------------
  console.log('  round 2: replay the original (now-stale) refresh_token cookie');
  await context.clearCookies({ name: 'refresh_token' });
  await context.addCookies([
    {
      name: 'refresh_token',
      value: originalRefreshCookie.value,
      domain: originalRefreshCookie.domain,
      path: originalRefreshCookie.path,
      httpOnly: true,
      secure: originalRefreshCookie.secure,
      sameSite: originalRefreshCookie.sameSite,
    },
  ]);
  await page.waitForTimeout(1000);

  const round2 = await postRefreshFromBrowser(page);
  console.log(`    HTTP ${round2.status} ${JSON.stringify(round2.body)}`);
  if (round2.status !== 401 || round2.body?.error !== 'TOKEN_REUSE_DETECTED') {
    throw new Error(
      `expected 401 TOKEN_REUSE_DETECTED, got HTTP ${round2.status} ${JSON.stringify(round2.body)}`
    );
  }
  // The reuse path also clears cookies — confirm no refresh_token remains.
  const afterReuse = await readRefreshCookie(context);
  console.log(
    afterReuse
      ? `    WARNING: refresh_token cookie still present (value=${afterReuse.value.slice(0, 12)}…)`
      : '    refresh_token cookie cleared (Max-Age=0)'
  );
  await page.waitForTimeout(3000);

  // -------------------------------------------------------------------
  // Step 5 — Show the post-cleanup UI state
  // -------------------------------------------------------------------
  console.log('  navigating to /signin?logout=true to show the cleanup banner');
  await page.goto(`${config.customerAppUrl}/signin?logout=true`);
  await page.getByText(/you have been signed out/i).waitFor({ timeout: 5000 });
  await page.waitForTimeout(3000);

  console.log('  demo complete — rotation + OWASP reuse-detection shown');
}

// ---------- helpers ----------

interface CookieSnapshot {
  value: string;
  domain: string;
  path: string;
  secure: boolean;
  sameSite: 'Strict' | 'Lax' | 'None';
}

async function readRefreshCookie(context: BrowserContext): Promise<CookieSnapshot | null> {
  // Path is scoped to /api/v1/auth/refresh, so query against that URL to make
  // sure the browser-side view of the cookie matches what would actually be
  // sent on a refresh request.
  const cookies = await context.cookies(`${config.identityApiUrl}/api/v1/auth/refresh`);
  const match = cookies.find((c) => c.name === 'refresh_token');
  if (!match) return null;
  return {
    value: match.value,
    domain: match.domain,
    path: match.path,
    secure: match.secure,
    sameSite: match.sameSite,
  };
}

function readTokenFamily(jwt: string): string {
  const parts = jwt.split('.');
  if (parts.length !== 3) return '<not a JWT>';
  try {
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString());
    return typeof payload.tokenFamily === 'string' ? payload.tokenFamily : '<no tokenFamily>';
  } catch {
    return '<could not decode>';
  }
}

interface RefreshResult {
  status: number;
  body: { status?: string; expiresIn?: number; error?: string; message?: string } | null;
}

async function postRefreshFromBrowser(page: Page): Promise<RefreshResult> {
  return page.evaluate(async (url) => {
    const res = await fetch(url, { method: 'POST', credentials: 'include' });
    let body: unknown = null;
    try {
      body = await res.json();
    } catch {
      // ignore non-JSON bodies
    }
    return { status: res.status, body: body as RefreshResult['body'] };
  }, `${config.identityApiUrl}/api/v1/auth/refresh`);
}
