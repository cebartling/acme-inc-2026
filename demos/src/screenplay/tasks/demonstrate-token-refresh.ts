import { Duration, Interaction, Task, the, Wait } from '@serenity-js/core';
import { BrowseTheWeb } from '@serenity-js/web';
import { config } from '../../config.ts';

interface CookieSnapshot {
  value: string;
  domain: string;
  path: string;
  secure: boolean;
  sameSite: 'Strict' | 'Lax' | 'None';
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

const SignInViaUI = (email: string, password: string) =>
  Interaction.where(the`#actor signs in via the customer UI`, async (actor) => {
    const page = await BrowseTheWeb.as(actor).currentPage();
    const nativePage = await (page as any).nativePage();

    await nativePage.goto(`${config.customerAppUrl}/signin`);
    await nativePage.getByRole('heading', { name: /welcome back/i }).waitFor();
    await nativePage.getByRole('textbox', { name: 'Email' }).fill(email);
    await nativePage.getByRole('textbox', { name: 'Email' }).blur();
    await nativePage.waitForTimeout(400);
    await nativePage.locator('input[id="password"]').fill(password);
    await nativePage.locator('input[id="password"]').blur();
    await nativePage.waitForTimeout(400);
    await nativePage.getByRole('button', { name: /sign in/i }).click();
    await nativePage.waitForURL((url: URL) => !url.pathname.endsWith('/signin'), {
      timeout: 15000,
    });
    console.log('  signed in — refresh_token cookie should now be set');
  });

export const DemonstrateTokenRefresh = {
  forUser: (email: string, password: string) =>
    Task.where(
      the`#actor demonstrates token refresh and reuse detection`,

      SignInViaUI(email, password),
      Wait.for(Duration.ofMilliseconds(2500)),

      Interaction.where(
        the`#actor performs token rotation and reuse detection`,
        async (actor) => {
          const page = await BrowseTheWeb.as(actor).currentPage();
          const nativePage = await (page as any).nativePage();
          const context = nativePage.context();

          // Step 2 — Capture original refresh_token
          const cookies = await context.cookies(
            `${config.identityApiUrl}/api/v1/auth/refresh`
          );
          const originalCookie = cookies.find(
            (c: any) => c.name === 'refresh_token'
          ) as CookieSnapshot | undefined;
          if (!originalCookie) {
            throw new Error('No refresh_token cookie present after signin');
          }
          const originalFamily = readTokenFamily(originalCookie.value);
          console.log(`  original refresh_token tokenFamily: ${originalFamily}`);

          // Step 3 — Happy-path refresh
          console.log('  round 1: POST /api/v1/auth/refresh (happy path rotation)');
          const round1 = await nativePage.evaluate(
            async (url: string) => {
              const res = await fetch(url, { method: 'POST', credentials: 'include' });
              let body: unknown = null;
              try {
                body = await res.json();
              } catch {}
              return { status: res.status, body };
            },
            `${config.identityApiUrl}/api/v1/auth/refresh`
          );
          console.log(`    HTTP ${round1.status} ${JSON.stringify(round1.body)}`);

          const rotatedCookies = await context.cookies(
            `${config.identityApiUrl}/api/v1/auth/refresh`
          );
          const rotatedCookie = rotatedCookies.find(
            (c: any) => c.name === 'refresh_token'
          ) as CookieSnapshot | undefined;
          if (!rotatedCookie) {
            throw new Error('refresh_token cookie missing after rotation');
          }
          const rotatedFamily = readTokenFamily(rotatedCookie.value);
          console.log(`    new tokenFamily: ${rotatedFamily}`);
          if (rotatedFamily === originalFamily) {
            throw new Error('tokenFamily did not change — rotation did not happen');
          }
          await nativePage.waitForTimeout(3000);

          // Step 4 — Replay stale token (reuse detection)
          console.log(
            '  round 2: replay the original (now-stale) refresh_token cookie'
          );
          await context.clearCookies({ name: 'refresh_token' });
          await context.addCookies([
            {
              name: 'refresh_token',
              value: originalCookie.value,
              domain: originalCookie.domain,
              path: originalCookie.path,
              httpOnly: true,
              secure: originalCookie.secure,
              sameSite: originalCookie.sameSite,
            },
          ]);
          await nativePage.waitForTimeout(1000);

          const round2 = await nativePage.evaluate(
            async (url: string) => {
              const res = await fetch(url, { method: 'POST', credentials: 'include' });
              let body: unknown = null;
              try {
                body = await res.json();
              } catch {}
              return { status: res.status, body };
            },
            `${config.identityApiUrl}/api/v1/auth/refresh`
          );
          console.log(`    HTTP ${round2.status} ${JSON.stringify(round2.body)}`);
          if (
            round2.status !== 401 ||
            (round2.body as any)?.error !== 'TOKEN_REUSE_DETECTED'
          ) {
            throw new Error(
              `expected 401 TOKEN_REUSE_DETECTED, got HTTP ${round2.status} ${JSON.stringify(round2.body)}`
            );
          }

          const afterReuse = await context.cookies(
            `${config.identityApiUrl}/api/v1/auth/refresh`
          );
          const remaining = afterReuse.find(
            (c: any) => c.name === 'refresh_token'
          );
          console.log(
            remaining
              ? `    WARNING: refresh_token cookie still present`
              : '    refresh_token cookie cleared (Max-Age=0)'
          );
          await nativePage.waitForTimeout(3000);

          // Step 5 — Show the post-cleanup UI state
          console.log(
            '  navigating to /signin?logout=true to show the cleanup banner'
          );
          await nativePage.goto(`${config.customerAppUrl}/signin?logout=true`);
          await nativePage
            .getByText(/you have been signed out/i)
            .waitFor({ timeout: 5000 });
          await nativePage.waitForTimeout(3000);

          console.log(
            '  demo complete — rotation + OWASP reuse-detection shown'
          );
        }
      )
    ),
};
