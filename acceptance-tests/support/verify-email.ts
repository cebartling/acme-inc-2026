import { ApiClient } from './api-client.js';

/**
 * Verifies a newly registered user's email, the way test setup activates a user.
 *
 * Fetches the token from identity's test endpoint, then calls `GET /api/v1/users/verify`.
 * Verify always answers with a redirect: `…/login?verified=true` or `?already_verified=true`
 * on success, `…/verify/resend?error=…` on failure. The redirect is not followed (it points
 * at FRONTEND_BASE_URL, and following it can hang, PIN-332); its Location tells the cases
 * apart. Any failure throws, naming the user, so setup fails where it went wrong rather
 * than at a later sign-in. Mirrors `demos/src/identity-api.ts`.
 */
export async function verifyEmail(identityApiClient: ApiClient, userId: string): Promise<void> {
  const tokenResponse = await identityApiClient.get<{ token?: string }>(
    `/api/v1/test/users/${userId}/verification-token`
  );
  const token = tokenResponse.data?.token;
  if (tokenResponse.status !== 200 || !token) {
    throw new Error(
      `Could not get a verification token for user ${userId}: ${tokenResponse.status} ${JSON.stringify(tokenResponse.data)}`
    );
  }

  const verifyResponse = await identityApiClient.get<unknown>(
    `/api/v1/users/verify?token=${encodeURIComponent(token)}`,
    { redirect: 'manual' }
  );
  const location = String(verifyResponse.headers['location'] ?? '');
  if (!/[?&](verified|already_verified)=true(?:&|$)/.test(location)) {
    throw new Error(
      `Email verification failed for user ${userId}: ${verifyResponse.status}, redirected to "${location}"`
    );
  }
}
