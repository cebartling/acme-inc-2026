import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { CustomWorld } from '../../support/world.js';

interface SigninSuccessResponse {
  status: 'SUCCESS';
  userId: string;
  expiresIn: number;
}

interface LogoutResponseBody {
  status?: string;
  message?: string;
  sessionsInvalidated?: number;
}

function extractCookieValue(
  cookies: string[] | string | undefined,
  cookieName: string,
): string | null {
  if (!cookies) return null;
  const list = Array.isArray(cookies) ? cookies : [cookies];
  for (const cookie of list) {
    if (cookie.startsWith(`${cookieName}=`)) {
      return cookie.split(';')[0].substring(cookieName.length + 1);
    }
  }
  return null;
}

function getCookieAttributes(
  cookies: string[] | string | undefined,
  cookieName: string,
): Map<string, string> {
  const attrs = new Map<string, string>();
  if (!cookies) return attrs;
  const list = Array.isArray(cookies) ? cookies : [cookies];
  for (const cookie of list) {
    if (cookie.startsWith(`${cookieName}=`)) {
      const parts = cookie.split(';').map((p) => p.trim());
      for (let i = 1; i < parts.length; i++) {
        const [key, value] = parts[i].split('=');
        attrs.set(key.toLowerCase(), value ?? 'true');
      }
      break;
    }
  }
  return attrs;
}

async function signinAndCaptureAccessToken(world: CustomWorld): Promise<string> {
  const email = world.getTestData<string>('testUserEmail');
  const password = world.getTestData<string>('testUserPassword');
  if (!email || !password) {
    throw new Error('Test user email/password not set up before signin');
  }

  const response = await world.identityApiClient.post<SigninSuccessResponse>(
    '/api/v1/auth/signin',
    { email, password, rememberMe: false },
  );

  expect(response.status).toBe(200);
  expect((response.data as SigninSuccessResponse).status).toBe('SUCCESS');

  const accessToken = extractCookieValue(response.headers['set-cookie'], 'access_token');
  if (!accessToken) {
    throw new Error('signin did not return an access_token cookie');
  }
  return accessToken;
}

// ============================================================================
// Given Steps
// ============================================================================

Given('the user is signed in', async function (this: CustomWorld) {
  const token = await signinAndCaptureAccessToken(this);
  this.setTestData('accessToken', token);
});

Given('the user is signed in {int} times', async function (this: CustomWorld, count: number) {
  let lastToken = '';
  for (let i = 0; i < count; i++) {
    lastToken = await signinAndCaptureAccessToken(this);
  }
  // Use the most recent token for the subsequent logout/all request
  this.setTestData('accessToken', lastToken);
});

// ============================================================================
// When Steps
// ============================================================================

When(
  'I post to the logout endpoint with the access token cookie',
  async function (this: CustomWorld) {
    const token = this.getTestData<string>('accessToken');
    if (!token) {
      throw new Error('No access token captured; sign in first');
    }

    const response = await this.identityApiClient.post<LogoutResponseBody>(
      '/api/v1/auth/logout',
      undefined,
      { headers: { Cookie: `access_token=${token}` } },
    );

    this.setTestData('lastResponse', response);
  },
);

When('I post to the logout endpoint without any cookie', async function (this: CustomWorld) {
  const response = await this.identityApiClient.post<LogoutResponseBody>(
    '/api/v1/auth/logout',
  );
  this.setTestData('lastResponse', response);
});

When(
  'I post to the logout endpoint with a malformed access token',
  async function (this: CustomWorld) {
    const response = await this.identityApiClient.post<LogoutResponseBody>(
      '/api/v1/auth/logout',
      undefined,
      { headers: { Cookie: 'access_token=not.a.real.jwt' } },
    );
    this.setTestData('lastResponse', response);
  },
);

When(
  'I post to the logout-all endpoint with the access token cookie',
  async function (this: CustomWorld) {
    const token = this.getTestData<string>('accessToken');
    if (!token) {
      throw new Error('No access token captured; sign in first');
    }

    const response = await this.identityApiClient.post<LogoutResponseBody>(
      '/api/v1/auth/logout/all',
      undefined,
      { headers: { Cookie: `access_token=${token}` } },
    );

    this.setTestData('lastResponse', response);
  },
);

When('I post to the logout-all endpoint without any cookie', async function (this: CustomWorld) {
  const response = await this.identityApiClient.post<LogoutResponseBody>(
    '/api/v1/auth/logout/all',
  );
  this.setTestData('lastResponse', response);
});

// ============================================================================
// Then Steps
// ============================================================================

Then('the logout API should respond with status {int}', function (
  this: CustomWorld,
  status: number,
) {
  const response = this.getTestData<{ status: number }>('lastResponse');
  expect(response?.status).toBe(status);
});

Then('the logout response status should be {string}', function (
  this: CustomWorld,
  expected: string,
) {
  const response = this.getTestData<{ data: LogoutResponseBody }>('lastResponse');
  expect(response?.data?.status).toBe(expected);
});

Then(
  'the logout-all response should report {int} sessions invalidated',
  function (this: CustomWorld, count: number) {
    const response = this.getTestData<{ data: LogoutResponseBody }>('lastResponse');
    expect(response?.data?.sessionsInvalidated).toBe(count);
  },
);

Then('the response should clear the {string} cookie', function (
  this: CustomWorld,
  cookieName: string,
) {
  const response =
    this.getTestData<{ headers: { 'set-cookie'?: string[] } }>('lastResponse');
  const cookies = response?.headers['set-cookie'];
  expect(cookies, `expected Set-Cookie headers on logout response`).toBeDefined();

  const value = extractCookieValue(cookies, cookieName);
  expect(value, `expected ${cookieName} to be present in Set-Cookie`).toBe('');

  const attrs = getCookieAttributes(cookies, cookieName);
  expect(attrs.get('max-age'), `expected ${cookieName} Max-Age=0`).toBe('0');
});

Then('the user should have {int} active sessions', async function (
  this: CustomWorld,
  expected: number,
) {
  const userId = this.getTestData<string>('testUserId');
  if (!userId) {
    throw new Error('testUserId not set');
  }

  const response = await this.identityApiClient.get<{ sessions: unknown[] }>(
    `/api/v1/test/users/${userId}/sessions`,
  );
  expect(response.status).toBe(200);
  const sessions = response.data.sessions ?? [];
  expect(sessions.length).toBe(expected);
});
