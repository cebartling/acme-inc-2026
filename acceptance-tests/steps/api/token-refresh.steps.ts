import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

// Local copies of the JWT/cookie helpers from session-token-creation.steps.ts.
// They were deliberately not exported (TypeScript file-scoped); duplicating
// them here keeps the token-refresh step file self-contained without forcing
// a refactor of a stable, working file.

interface JWTPayload {
  sub?: string;
  sessionId?: string;
  tokenFamily?: string;
  iat?: number;
  exp?: number;
  iss?: string;
  [key: string]: unknown;
}

function decodeJWTPayload(token: string): JWTPayload {
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Invalid JWT format');
  }
  return JSON.parse(Buffer.from(parts[1], 'base64url').toString()) as JWTPayload;
}

function extractCookieValue(
  cookieHeader: string | string[],
  cookieName: string
): string | null {
  const cookies = Array.isArray(cookieHeader) ? cookieHeader : [cookieHeader];
  for (const cookie of cookies) {
    if (cookie.startsWith(`${cookieName}=`)) {
      return cookie.split(';')[0].substring(cookieName.length + 1);
    }
  }
  return null;
}

// ============================================================================
// GIVEN — capture refresh-token state before refreshing
// ============================================================================

Given(
  "the server has rotated the current session's tokenFamily out of band",
  async function (this: CustomWorld) {
    // Pull sessionId from the access-token JWT claims (set by the signin/MFA
    // step). Calling the test-only rotate-family endpoint flips the session's
    // tokenFamily on the server while the test still holds the original
    // refresh-token cookie — exactly the condition reuse detection guards
    // against (a stolen, already-rotated refresh token).
    const accessToken = this.getTestData<string>('access_token_value');
    if (!accessToken) {
      throw new Error(
        'No access_token_value in test data — complete signin + MFA before rotating tokenFamily'
      );
    }
    const sessionId = decodeJWTPayload(accessToken).sessionId;
    if (!sessionId) {
      throw new Error('Access token has no sessionId claim');
    }

    const response = await this.identityApiClient.post(
      `/api/v1/test/sessions/${sessionId}/rotate-family`
    );
    if (response.status !== 200) {
      throw new Error(
        `Failed to rotate tokenFamily: ${response.status} ${JSON.stringify(response.data)}`
      );
    }
  }
);

Given(
  "I remember the current refresh token's tokenFamily claim",
  function (this: CustomWorld) {
    const refreshToken = this.getTestData<string>('refresh_token_value');
    if (!refreshToken) {
      throw new Error(
        'No refresh_token_value in test data — complete signin + MFA before remembering the tokenFamily'
      );
    }
    const payload = decodeJWTPayload(refreshToken);
    if (!payload.tokenFamily) {
      throw new Error('Refresh token does not carry a tokenFamily claim');
    }
    this.setTestData('rememberedTokenFamily', payload.tokenFamily);
    this.setTestData('rememberedRefreshToken', refreshToken);
  }
);

// ============================================================================
// WHEN — call the refresh endpoint
// ============================================================================

When(
  'I POST to {string} with the current refresh_token cookie',
  async function (this: CustomWorld, path: string) {
    const refreshToken = this.getTestData<string>('refresh_token_value');
    if (!refreshToken) {
      throw new Error(
        'No refresh_token_value in test data — complete signin + MFA before refreshing'
      );
    }

    const response = await this.identityApiClient.post<unknown>(
      path,
      undefined,
      { headers: { Cookie: `refresh_token=${refreshToken}` } }
    );

    this.setLastResponse(response);

    // Update the stored refresh_token_value to the new rotated value so any
    // follow-up steps (e.g. reuse-detection scenarios) work against the
    // freshly issued cookie. Keep the original value in
    // rememberedRefreshToken for negative-test scenarios.
    const setCookieHeaders = response.headers['set-cookie'];
    if (setCookieHeaders && setCookieHeaders.length > 0) {
      this.setTestData('setCookieHeaders', setCookieHeaders);
      const newAccess = extractCookieValue(setCookieHeaders, 'access_token');
      if (newAccess) this.setTestData('access_token_value', newAccess);
      const newRefresh = extractCookieValue(setCookieHeaders, 'refresh_token');
      if (newRefresh) this.setTestData('refresh_token_value', newRefresh);
    }
  }
);

// ============================================================================
// THEN — assertions specific to the refresh response
// ============================================================================

Then(
  "the new refresh token's tokenFamily claim should differ from the remembered tokenFamily",
  function (this: CustomWorld) {
    const remembered = this.getTestData<string>('rememberedTokenFamily');
    const current = this.getTestData<string>('refresh_token_value');
    if (!remembered) {
      throw new Error('rememberedTokenFamily not set — did you run the Given step?');
    }
    if (!current) {
      throw new Error('refresh_token_value not set after refresh');
    }
    const newFamily = decodeJWTPayload(current).tokenFamily;
    expect(newFamily).toBeDefined();
    expect(newFamily).not.toBe(remembered);
  }
);

interface RefreshResponseBody {
  status?: string;
  expiresIn?: number;
}

Then(
  'the refresh response status field should be {string}',
  function (this: CustomWorld, expected: string) {
    const response = this.getLastResponse<RefreshResponseBody>();
    expect(response?.data.status).toBe(expected);
  }
);

Then(
  'the refresh response expiresIn field should be {int}',
  function (this: CustomWorld, expected: number) {
    const response = this.getLastResponse<RefreshResponseBody>();
    expect(response?.data.expiresIn).toBe(expected);
  }
);

// "Cleared" means a Set-Cookie that immediately expires the cookie:
// empty value + Max-Age=0 (per RFC 6265 and AuthCookieBuilder.buildClearCookies).
function assertCookieCleared(
  cookieHeader: string | string[] | undefined,
  cookieName: string
): void {
  const cookies = cookieHeader
    ? Array.isArray(cookieHeader)
      ? cookieHeader
      : [cookieHeader]
    : [];
  const matching = cookies.filter((c) => c.startsWith(`${cookieName}=`));
  expect(
    matching.length,
    `expected a Set-Cookie clearing ${cookieName}, got: ${JSON.stringify(cookies)}`
  ).toBeGreaterThan(0);
  // The clear cookie has an empty value (cookieName=;...)
  const clearedValue = matching[0].split(';')[0].substring(cookieName.length + 1);
  expect(clearedValue).toBe('');
  // ...and Max-Age=0
  const maxAgePart = matching[0]
    .split(';')
    .map((p) => p.trim())
    .find((p) => p.toLowerCase().startsWith('max-age'));
  expect(maxAgePart?.toLowerCase()).toBe('max-age=0');
}

Then(
  'the access_token cookie should be cleared',
  function (this: CustomWorld) {
    const response = this.getLastResponse();
    assertCookieCleared(response?.headers['set-cookie'], 'access_token');
  }
);

Then(
  'the refresh_token cookie should be cleared',
  function (this: CustomWorld) {
    const response = this.getLastResponse();
    assertCookieCleared(response?.headers['set-cookie'], 'refresh_token');
  }
);
