// Direct identity-service calls used by demos to set up real, verified
// accounts without going through the UI. The customer-frontend /register
// route currently console.logs and navigates to "/" — it does not call
// POST /api/v1/users/register — so demos that need a sign-in-able account
// hit the API directly as a backstop. Remove these helpers once the UI
// submits to the backend.
import { config } from './config.ts';

export interface RegisterAndVerifyParams {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface RegisterAndVerifyResult {
  userId: string;
}

export async function registerAndVerifyUser(
  params: RegisterAndVerifyParams
): Promise<RegisterAndVerifyResult> {
  const { email, password, firstName = 'Demo', lastName = 'User' } = params;

  const registerRes = await fetch(`${config.identityApiUrl}/api/v1/users/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email,
      password,
      firstName,
      lastName,
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
    throw new Error(
      `Email verification did not succeed; identity service redirected to ${location}`
    );
  }

  return { userId };
}

// Issues a password-reset token for a given user via the test-only fixture
// endpoint, bypassing the normal "send an email" flow so demos can drive the
// reset-password UI without a real mail relay.
export async function getPasswordResetToken(userId: string): Promise<string> {
  const res = await fetch(`${config.identityApiUrl}/api/v1/test/password-reset-tokens`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Test-Api-Key': config.testApiKey,
    },
    body: JSON.stringify({ userId }),
  });
  if (!res.ok) {
    throw new Error(
      `Fetching password-reset token failed: ${res.status} ${await res.text()}`
    );
  }
  const { token } = (await res.json()) as { token: string };
  return token;
}

export type UserStatus =
  | 'PENDING_VERIFICATION'
  | 'ACTIVE'
  | 'SUSPENDED'
  | 'DEACTIVATED'
  | 'LOCKED';

// Drives a user directly into the requested status via the test-only
// POST /api/v1/test/users/{userId}/status endpoint. The inactive-account
// demo uses this to flip one throwaway account through PENDING_VERIFICATION,
// SUSPENDED, and DEACTIVATED without re-registering each time.
export async function setUserStatus(userId: string, status: UserStatus): Promise<void> {
  const res = await fetch(`${config.identityApiUrl}/api/v1/test/users/${userId}/status`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Test-Api-Key': config.testApiKey,
    },
    body: JSON.stringify({ status }),
  });
  if (!res.ok) {
    throw new Error(
      `Setting user status to ${status} failed: ${res.status} ${await res.text()}`
    );
  }
}
