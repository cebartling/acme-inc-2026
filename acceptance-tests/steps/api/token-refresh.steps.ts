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
