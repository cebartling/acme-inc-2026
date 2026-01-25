import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { generateSync } from 'otplib';
import { CustomWorld } from '../../support/world.js';

interface DeviceTrustInfo {
  id: string;
  deviceName: string;
  createdAt: string;
  lastUsedAt: string;
  expiresAt: string;
  ipAddress: string;
  isCurrent: boolean;
}

// ============================================================================
// Helper Functions
// ============================================================================

function extractCookieValue(cookieHeader: string | string[], cookieName: string): string | null {
  const cookies = Array.isArray(cookieHeader) ? cookieHeader : [cookieHeader];

  for (const cookie of cookies) {
    if (cookie.startsWith(`${cookieName}=`)) {
      const value = cookie.split(';')[0].substring(cookieName.length + 1);
      return value;
    }
  }

  return null;
}

function getCookieAttributes(cookieHeader: string | string[], cookieName: string): Map<string, string> {
  const cookies = Array.isArray(cookieHeader) ? cookieHeader : [cookieHeader];
  const attributes = new Map<string, string>();

  for (const cookie of cookies) {
    if (cookie.startsWith(`${cookieName}=`)) {
      const parts = cookie.split(';').map(p => p.trim());

      for (let i = 1; i < parts.length; i++) {
        const [key, value] = parts[i].split('=');
        attributes.set(key.toLowerCase(), value || 'true');
      }

      break;
    }
  }

  return attributes;
}

// ============================================================================
// GIVEN Steps - Setup
// ============================================================================

Given('the Redis cache is available', async function (this: CustomWorld) {
  // Redis availability is checked by the Identity service
  // This step is a no-op placeholder for BDD readability
  await this.attach('Redis cache availability assumed', 'text/plain');
});

Given('the user {string} has TOTP MFA enabled', async function (this: CustomWorld, email: string) {
  const userId = this.getTestData<string>(`testUserId_${email}`);

  if (!userId) {
    throw new Error(`Test user ${email} not found. User must be created in Background.`);
  }

  // Actually enable TOTP MFA via test API
  const totpSecret = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';
  const response = await this.identityApiClient.post(
    `/api/v1/test/users/${userId}/enable-mfa`,
    { totpSecret }
  );

  expect(response.status).toBe(200);
  this.setTestData('totpEnabled', true);
  this.setTestData('totpSecret', totpSecret);

  await this.attach(`Enabled TOTP MFA for user ${email}`, 'text/plain');
});

Given('I have an active session for {string}', async function (this: CustomWorld, email: string) {
  const actualEmail = this.getTestData<string>('testUserEmail') || email;
  const password = this.getTestData<string>('userPassword') || 'ValidP@ss123!';

  // Sign in
  const signinResponse = await this.identityApiClient.post('/api/v1/auth/signin', {
    email: actualEmail,
    password,
    rememberMe: false,
    deviceFingerprint: 'fp_test_session',
  });

  expect(signinResponse.status).toBe(200);

  // If MFA required, complete it
  if (signinResponse.data.status === 'MFA_REQUIRED') {
    const mfaToken = signinResponse.data.mfaToken;
    const totpSecret = this.getTestData<string>('totpSecret') || 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';
    const totpCode = generateSync({ secret: totpSecret });

    const mfaResponse = await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
      mfaToken,
      code: totpCode,
      method: 'TOTP',
      rememberDevice: false,
    });

    expect(mfaResponse.status).toBe(200);

    // Store cookies for authenticated requests
    const setCookieHeaders = mfaResponse.headers['set-cookie'];
    if (setCookieHeaders) {
      const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
      this.setTestData('access_token', accessToken);
    }
  } else {
    // Direct success - store cookies
    const setCookieHeaders = signinResponse.headers['set-cookie'];
    if (setCookieHeaders) {
      const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
      this.setTestData('access_token', accessToken);
    }
  }
});

Given('the user has a valid device trust from a previous signin', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');
  const deviceFingerprint = 'fp_test_device_123';
  const userAgent = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36';

  if (!userId) {
    throw new Error('Test user must be created before creating device trust');
  }

  // Create device trust via test API
  const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
    `/api/v1/test/users/${userId}/device-trusts`,
    {
      deviceFingerprint,
      userAgent,
      ipAddress: '192.168.1.100',
    }
  );

  expect(response.status).toBe(200);
  expect(response.data.deviceTrustId).toBeTruthy();

  this.setTestData('deviceTrustId', response.data.deviceTrustId);
  this.setTestData('deviceFingerprint', deviceFingerprint);
  this.setTestData('deviceUserAgent', userAgent);
});

Given('the user has an expired device trust', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');
  const deviceFingerprint = 'fp_expired_device';
  const userAgent = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)';

  if (!userId) {
    throw new Error('Test user must be created before creating device trust');
  }

  // Create device trust with 0 TTL (already expired)
  const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
    `/api/v1/test/users/${userId}/device-trusts`,
    {
      deviceFingerprint,
      userAgent,
      ipAddress: '192.168.1.100',
      ttlSeconds: 0,
    }
  );

  expect(response.status).toBe(200);

  this.setTestData('deviceTrustId', response.data.deviceTrustId);
  this.setTestData('deviceFingerprint', deviceFingerprint);
  this.setTestData('deviceUserAgent', userAgent);
});

Given('the user has {int} active device trusts', async function (this: CustomWorld, count: number) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('Test user must be created before creating device trusts');
  }

  const deviceTrustIds: string[] = [];

  for (let i = 0; i < count; i++) {
    const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
      `/api/v1/test/users/${userId}/device-trusts`,
      {
        deviceFingerprint: `fp_device_${i}`,
        userAgent: `Mozilla/5.0 (Device ${i})`,
        ipAddress: '192.168.1.100',
      }
    );

    expect(response.status).toBe(200);
    deviceTrustIds.push(response.data.deviceTrustId);
  }

  this.setTestData('deviceTrustIds', deviceTrustIds);
  this.setTestData('deviceTrustCount', count);
});

Given('the user has a valid device trust with fingerprint {string}', async function (this: CustomWorld, fingerprint: string) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('Test user must be created before creating device trust');
  }

  const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
    `/api/v1/test/users/${userId}/device-trusts`,
    {
      deviceFingerprint: fingerprint,
      userAgent: 'Mozilla/5.0 (Test)',
      ipAddress: '192.168.1.100',
    }
  );

  expect(response.status).toBe(200);

  this.setTestData('deviceTrustId', response.data.deviceTrustId);
  this.setTestData('deviceFingerprint', fingerprint);
});

Given('the user has a valid device trust with user agent {string}', async function (this: CustomWorld, userAgent: string) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('Test user must be created before creating device trust');
  }

  const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
    `/api/v1/test/users/${userId}/device-trusts`,
    {
      deviceFingerprint: 'fp_test',
      userAgent,
      ipAddress: '192.168.1.100',
    }
  );

  expect(response.status).toBe(200);

  this.setTestData('deviceTrustId', response.data.deviceTrustId);
  this.setTestData('deviceUserAgent', userAgent);
});

Given('the user has a device trust with ID {string}', async function (this: CustomWorld, deviceTrustId: string) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('Test user must be created before creating device trust');
  }

  const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
    `/api/v1/test/users/${userId}/device-trusts`,
    {
      deviceFingerprint: 'fp_test',
      userAgent: 'Mozilla/5.0 (Test)',
      ipAddress: '192.168.1.100',
      deviceTrustId, // Specify the ID
    }
  );

  expect(response.status).toBe(200);

  this.setTestData('deviceTrustId', deviceTrustId);
});

Given('the user {string} has a device trust with ID {string}', async function (this: CustomWorld, email: string, deviceTrustId: string) {
  const userId = this.getTestData<string>(`testUserId_${email}`);

  if (!userId) {
    throw new Error(`Test user ${email} must be created before creating device trust`);
  }

  const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
    `/api/v1/test/users/${userId}/device-trusts`,
    {
      deviceFingerprint: 'fp_test',
      userAgent: 'Mozilla/5.0 (Test)',
      ipAddress: '192.168.1.100',
      deviceTrustId,
    }
  );

  expect(response.status).toBe(200);
});

Given('the user has a valid device trust created {int} days ago', async function (this: CustomWorld, daysAgo: number) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('Test user must be created before creating device trust');
  }

  const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
    `/api/v1/test/users/${userId}/device-trusts`,
    {
      deviceFingerprint: 'fp_old_device',
      userAgent: 'Mozilla/5.0 (Old Device)',
      ipAddress: '192.168.1.100',
      createdDaysAgo: daysAgo,
    }
  );

  expect(response.status).toBe(200);

  this.setTestData('deviceTrustId', response.data.deviceTrustId);
  this.setTestData('deviceFingerprint', 'fp_old_device');
  this.setTestData('deviceUserAgent', 'Mozilla/5.0 (Old Device)');
});

Given('the user has a valid device trust created from IP {string}', async function (this: CustomWorld, ipAddress: string) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('Test user must be created before creating device trust');
  }

  const response = await this.identityApiClient.post<{ deviceTrustId: string }>(
    `/api/v1/test/users/${userId}/device-trusts`,
    {
      deviceFingerprint: 'fp_test',
      userAgent: 'Mozilla/5.0 (Test)',
      ipAddress,
    }
  );

  expect(response.status).toBe(200);

  this.setTestData('deviceTrustId', response.data.deviceTrustId);
  this.setTestData('deviceFingerprint', 'fp_test');
  this.setTestData('deviceUserAgent', 'Mozilla/5.0 (Test)');
  this.setTestData('originalIpAddress', ipAddress);
});

Given('I have an active session for {string} with device trust {string}', async function (this: CustomWorld, email: string, deviceTrustId: string) {
  // First create a regular session
  await this.attach(`Creating session for ${email}`, 'text/plain');

  const actualEmail = this.getTestData<string>('testUserEmail') || email;

  // Sign in and complete MFA
  const signinResponse = await this.identityApiClient.post('/api/v1/auth/signin', {
    email: actualEmail,
    password: this.getTestData('userPassword') || 'ValidP@ss123!',
    rememberMe: false,
    deviceFingerprint: 'fp_test',
  });

  expect(signinResponse.status).toBe(200);

  if (signinResponse.data.status === 'MFA_REQUIRED') {
    const mfaToken = signinResponse.data.mfaToken;
    const totpSecret = this.getTestData<string>('totpSecret') || 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';
    const totpCode = generateSync({ secret: totpSecret });

    const mfaResponse = await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
      mfaToken,
      code: totpCode,
      method: 'TOTP',
      deviceFingerprint: 'fp_test',
      rememberDevice: false,
    });

    expect(mfaResponse.status).toBe(200);

    // Store cookies for authenticated requests
    const setCookieHeaders = mfaResponse.headers['set-cookie'];
    if (setCookieHeaders) {
      const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
      this.setTestData('access_token', accessToken);
    }
  } else {
    // Direct success - store cookies
    const setCookieHeaders = signinResponse.headers['set-cookie'];
    if (setCookieHeaders) {
      const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
      this.setTestData('access_token', accessToken);
    }
  }

  // Store the device trust ID for current device indicator
  this.setTestData('currentDeviceTrustId', deviceTrustId);
  this.setTestData('deviceTrustId', deviceTrustId);
});

// ============================================================================
// WHEN Steps - Actions
// ============================================================================

When('I verify MFA with the correct TOTP code and rememberDevice set to true', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');
  const totpSecret = this.getTestData<string>('totpSecret') || 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';
  const deviceFingerprint = this.getTestData<string>('deviceFingerprint') || 'fp_test_device';

  if (!mfaToken) {
    throw new Error('MFA token not found. Did you call signin first?');
  }

  const totpCode = generateSync({ secret: totpSecret });

  const response = await this.identityApiClient.post(
    '/api/v1/auth/mfa/verify',
    {
      mfaToken,
      code: totpCode,
      method: 'TOTP',
      deviceFingerprint,
      rememberDevice: true,
    }
  );

  this.setTestData('lastResponse', response);

  // Extract Set-Cookie headers for cookie verification steps
  if (response.headers['set-cookie']) {
    const setCookieHeaders = Array.isArray(response.headers['set-cookie'])
      ? response.headers['set-cookie']
      : [response.headers['set-cookie']];
    this.setTestData('setCookieHeaders', setCookieHeaders);

    // Extract and store access_token for authenticated requests
    const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
    if (accessToken) {
      this.setTestData('access_token', accessToken);
    }
  }
});

When('I verify MFA with the correct TOTP code and rememberDevice set to false', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');
  const totpSecret = this.getTestData<string>('totpSecret') || 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';

  if (!mfaToken) {
    throw new Error('MFA token not found. Did you call signin first?');
  }

  const totpCode = generateSync({ secret: totpSecret });

  const response = await this.identityApiClient.post(
    '/api/v1/auth/mfa/verify',
    {
      mfaToken,
      code: totpCode,
      method: 'TOTP',
      rememberDevice: false,
    }
  );

  this.setTestData('lastResponse', response);
});

When('I signin with email {string} and password {string} with the device trust cookie', async function (this: CustomWorld, email: string, password: string) {
  const actualEmail = this.getTestData<string>('testUserEmail') || email;
  const deviceTrustId = this.getTestData<string>('deviceTrustId');
  const deviceFingerprint = this.getTestData<string>('deviceFingerprint');
  const userAgent = this.getTestData<string>('deviceUserAgent');

  const response = await this.identityApiClient.post(
    '/api/v1/auth/signin',
    {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint,
    },
    {
      headers: {
        'User-Agent': userAgent || 'Mozilla/5.0 (Test)',
        'Cookie': `device_trust=${deviceTrustId}`,
      }
    }
  );

  this.setTestData('lastResponse', response);

  // Extract Set-Cookie headers for cookie verification steps
  if (response.headers['set-cookie']) {
    const setCookieHeaders = Array.isArray(response.headers['set-cookie'])
      ? response.headers['set-cookie']
      : [response.headers['set-cookie']];
    this.setTestData('setCookieHeaders', setCookieHeaders);
  }
});

When('I signin with email {string} and password {string} with the expired device trust cookie', async function (this: CustomWorld, email: string, password: string) {
  const actualEmail = this.getTestData<string>('testUserEmail') || email;
  const deviceTrustId = this.getTestData<string>('deviceTrustId');
  const deviceFingerprint = this.getTestData<string>('deviceFingerprint');

  const response = await this.identityApiClient.post(
    '/api/v1/auth/signin',
    {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint,
    },
    {
      headers: {
        'Cookie': `device_trust=${deviceTrustId}`,
      }
    }
  );

  this.setTestData('lastResponse', response);

  if (response.data.status === 'MFA_REQUIRED') {
    this.setTestData('mfaToken', response.data.mfaToken);
  }
});

When('I signin with email {string} and password {string} from a new device', async function (this: CustomWorld, email: string, password: string) {
  const actualEmail = this.getTestData<string>('testUserEmail') || email;

  const response = await this.identityApiClient.post(
    '/api/v1/auth/signin',
    {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint: `fp_new_device_${Date.now()}`,
    }
  );

  this.setTestData('lastResponse', response);

  if (response.data.status === 'MFA_REQUIRED') {
    this.setTestData('mfaToken', response.data.mfaToken);
  }
});

When('I signin with email {string} and password {string} with fingerprint {string}', async function (this: CustomWorld, email: string, password: string, fingerprint: string) {
  const actualEmail = this.getTestData<string>('testUserEmail') || email;
  const deviceTrustId = this.getTestData<string>('deviceTrustId');

  const response = await this.identityApiClient.post(
    '/api/v1/auth/signin',
    {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint: fingerprint,
    },
    {
      headers: {
        'Cookie': `device_trust=${deviceTrustId}`,
      }
    }
  );

  this.setTestData('lastResponse', response);

  if (response.data.status === 'MFA_REQUIRED') {
    this.setTestData('mfaToken', response.data.mfaToken);
  }
});

When('I signin with email {string} and password {string} with user agent {string}', async function (this: CustomWorld, email: string, password: string, userAgent: string) {
  const actualEmail = this.getTestData<string>('testUserEmail') || email;
  const deviceTrustId = this.getTestData<string>('deviceTrustId');
  const deviceFingerprint = this.getTestData<string>('deviceFingerprint') || `fp_${Date.now()}`;

  const headers: Record<string, string> = {
    'User-Agent': userAgent,
  };

  if (deviceTrustId) {
    headers['Cookie'] = `device_trust=${deviceTrustId}`;
  }

  const response = await this.identityApiClient.post(
    '/api/v1/auth/signin',
    {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint,
    },
    { headers }
  );

  this.setTestData('lastResponse', response);
  this.setTestData('deviceFingerprint', deviceFingerprint);

  if (response.data.status === 'MFA_REQUIRED') {
    this.setTestData('mfaToken', response.data.mfaToken);
  }
});

When('I signin with email {string} and password {string} from IP {string}', async function (this: CustomWorld, email: string, password: string, ipAddress: string) {
  const actualEmail = this.getTestData<string>('testUserEmail') || email;
  const deviceTrustId = this.getTestData<string>('deviceTrustId');
  const deviceFingerprint = this.getTestData<string>('deviceFingerprint');
  const userAgent = this.getTestData<string>('deviceUserAgent');

  const response = await this.identityApiClient.post(
    '/api/v1/auth/signin',
    {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint,
    },
    {
      headers: {
        'User-Agent': userAgent,
        'Cookie': `device_trust=${deviceTrustId}`,
        'X-Forwarded-For': ipAddress,
      }
    }
  );

  this.setTestData('lastResponse', response);
});

When('I signin with email {string} and password {string}', async function (this: CustomWorld, email: string, password: string) {
  const userAgent = this.getTestData<string>('testUserAgent') || 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36';

  // Use the actual email from test data (which may have a timestamp suffix for uniqueness)
  const actualEmail = this.getTestData<string>('testUserEmail') || email;

  // Use a unique device fingerprint for each signin to ensure MFA is always triggered
  // This prevents issues where a previously trusted device would bypass MFA
  const deviceFingerprint = `fp_test_signin_${Date.now()}_${Math.random().toString(36).substring(7)}`;

  const response = await this.identityApiClient.post(
    '/api/v1/auth/signin',
    {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint,
    },
    {
      'User-Agent': userAgent,
    }
  );

  this.setTestData('lastResponse', response);
  this.setTestData('deviceFingerprint', deviceFingerprint);

  if (response.data.status === 'MFA_REQUIRED') {
    this.setTestData('mfaToken', response.data.mfaToken);
  }
});

When('I request GET {string}', async function (this: CustomWorld, path: string) {
  const accessToken = this.getTestData<string>('access_token');

  const headers: Record<string, string> = {};
  if (accessToken) {
    headers['Cookie'] = `access_token=${accessToken}`;
  }

  const deviceTrustId = this.getTestData<string>('deviceTrustId');
  if (deviceTrustId) {
    headers['Cookie'] = headers['Cookie']
      ? `${headers['Cookie']}; device_trust=${deviceTrustId}`
      : `device_trust=${deviceTrustId}`;
  }

  const response = await this.identityApiClient.get(path, { headers });

  this.setTestData('lastResponse', response);
});

When('I request GET {string} without authentication', async function (this: CustomWorld, path: string) {
  try {
    const response = await this.identityApiClient.get(path);
    this.setTestData('lastResponse', response);
  } catch (error: unknown) {
    // Axios throws on non-2xx status codes
    if (error && typeof error === 'object' && 'response' in error) {
      this.setTestData('lastResponse', (error as { response: unknown }).response);
    } else {
      throw error;
    }
  }
});

When('I request DELETE {string}', async function (this: CustomWorld, path: string) {
  const accessToken = this.getTestData<string>('access_token');

  const headers: Record<string, string> = {};
  if (accessToken) {
    headers['Cookie'] = `access_token=${accessToken}`;
  }

  try {
    const response = await this.identityApiClient.delete(path, { headers });
    this.setTestData('lastResponse', response);
  } catch (error: unknown) {
    // Axios throws on non-2xx status codes
    if (error && typeof error === 'object' && 'response' in error) {
      this.setTestData('lastResponse', (error as { response: unknown }).response);
    } else {
      throw error;
    }
  }
});

When('I change my password from {string} to {string}', async function (this: CustomWorld, oldPassword: string, newPassword: string) {
  const accessToken = this.getTestData<string>('access_token');

  const headers: Record<string, string> = {};
  if (accessToken) {
    headers['Cookie'] = `access_token=${accessToken}`;
  }

  try {
    const response = await this.identityApiClient.post(
      '/api/v1/auth/change-password',
      {
        currentPassword: oldPassword,
        newPassword,
      },
      { headers }
    );

    this.setTestData('lastResponse', response);
    this.setTestData('passwordChangeSuccess', true);
  } catch (error: unknown) {
    // Password change endpoint may not be implemented yet
    if (error && typeof error === 'object' && 'response' in error) {
      this.setTestData('lastResponse', (error as { response: unknown }).response);
      this.setTestData('passwordChangeSuccess', false);
    } else {
      throw error;
    }
  }
});

// ============================================================================
// THEN Steps - Assertions
// ============================================================================

Then('the response should not set a cookie named {string}', function (this: CustomWorld, cookieName: string) {
  const response = this.getTestData<{ status: number; headers: Record<string, unknown> }>('lastResponse');

  if (!response) {
    throw new Error('No response found. Did you make an API call?');
  }

  const setCookieHeader = response.headers['set-cookie'];

  if (!setCookieHeader) {
    return; // No cookies at all - that's fine
  }

  const cookieValue = extractCookieValue(setCookieHeader, cookieName);

  expect(cookieValue).toBeNull();
});

Then('the {word} cookie should have HttpOnly flag', function (this: CustomWorld, cookieName: string) {
  const response = this.getTestData<{ headers: Record<string, unknown> }>('lastResponse');
  const setCookieHeader = response!.headers['set-cookie'];

  const attributes = getCookieAttributes(setCookieHeader, cookieName);

  expect(attributes.has('httponly')).toBe(true);
});

Then('the {word} cookie should have Secure flag', function (this: CustomWorld, cookieName: string) {
  const response = this.getTestData<{ headers: Record<string, unknown> }>('lastResponse');
  const setCookieHeader = response!.headers['set-cookie'];

  const attributes = getCookieAttributes(setCookieHeader, cookieName);

  expect(attributes.has('secure')).toBe(true);
});

Then('the {word} cookie should have SameSite=Strict', function (this: CustomWorld, cookieName: string) {
  const response = this.getTestData<{ headers: Record<string, unknown> }>('lastResponse');
  const setCookieHeader = response!.headers['set-cookie'];

  const attributes = getCookieAttributes(setCookieHeader, cookieName);

  expect(attributes.get('samesite')).toBe('Strict');
});

Then('the {word} cookie should have Path={string}', function (this: CustomWorld, cookieName: string, expectedPath: string) {
  const response = this.getTestData<{ headers: Record<string, unknown> }>('lastResponse');
  const setCookieHeader = response!.headers['set-cookie'];

  const attributes = getCookieAttributes(setCookieHeader, cookieName);

  expect(attributes.get('path')).toBe(expectedPath);
});

Then('the {word} cookie should have Max-Age={int}', function (this: CustomWorld, cookieName: string, expectedMaxAge: number) {
  const response = this.getTestData<{ headers: Record<string, unknown> }>('lastResponse');
  const setCookieHeader = response!.headers['set-cookie'];

  const attributes = getCookieAttributes(setCookieHeader, cookieName);

  expect(attributes.get('max-age')).toBe(expectedMaxAge.toString());
});

Then('the device trust token should be stored in Redis', async function (this: CustomWorld) {
  const deviceTrustToken = this.getTestData<string>('device_trust_value');

  if (!deviceTrustToken) {
    throw new Error('Device trust token not found in test data');
  }

  // Verify via test API that the trust exists in Redis
  const response = await this.identityApiClient.get(`/api/v1/test/device-trusts/${deviceTrustToken}`);

  expect(response.status).toBe(200);
  expect(response.data.id).toBe(deviceTrustToken);
});

Then('the device trust should have a TTL of approximately {int} seconds', async function (this: CustomWorld, expectedTtl: number) {
  const deviceTrustToken = this.getTestData<string>('device_trust_value');

  if (!deviceTrustToken) {
    throw new Error('Device trust token not found in test data');
  }

  const response = await this.identityApiClient.get(`/api/v1/test/device-trusts/${deviceTrustToken}/ttl`);

  expect(response.status).toBe(200);

  const actualTtl = response.data.ttl;
  const tolerance = 10; // Allow 10 seconds tolerance

  expect(actualTtl).toBeGreaterThanOrEqual(expectedTtl - tolerance);
  expect(actualTtl).toBeLessThanOrEqual(expectedTtl + tolerance);
});

Then('MFA verification should not be required', function (this: CustomWorld) {
  const response = this.getTestData<{ data: { status: string } }>('lastResponse');

  expect(response!.data.status).toBe('SUCCESS');
});

Then('MFA verification should be required', function (this: CustomWorld) {
  const response = this.getTestData<{ data: { status: string } }>('lastResponse');

  expect(response!.data.status).toBe('MFA_REQUIRED');
});

Then('the user should have exactly {int} device trusts in Redis', async function (this: CustomWorld, expectedCount: number) {
  const userId = this.getTestData<string>('testUserId');

  const response = await this.identityApiClient.get(`/api/v1/test/users/${userId}/device-trusts`);

  expect(response.status).toBe(200);
  expect(response.data.devices).toHaveLength(expectedCount);
});

Then('the user should have {int} device trusts in Redis', async function (this: CustomWorld, expectedCount: number) {
  const userId = this.getTestData<string>('testUserId');

  const response = await this.identityApiClient.get(`/api/v1/test/users/${userId}/device-trusts`);

  expect(response.status).toBe(200);
  expect(response.data.devices).toHaveLength(expectedCount);
});

Then('the oldest device trust should have been evicted', async function (this: CustomWorld) {
  const oldDeviceTrustIds = this.getTestData<string[]>('deviceTrustIds') || [];

  // The first device trust should be the oldest (created first)
  if (oldDeviceTrustIds.length > 0) {
    const oldestDeviceTrustId = oldDeviceTrustIds[0];

    const response = await this.identityApiClient.get(`/api/v1/test/device-trusts/${oldestDeviceTrustId}`);

    expect(response.status).toBe(404);
  }
});

Then('a DeviceRevoked event should be published with reason {word}', async function (this: CustomWorld, reason: string) {
  const userId = this.getTestData<string>('testUserId');

  // Wait a bit for async event publishing
  await new Promise(resolve => setTimeout(resolve, 500));

  const response = await this.identityApiClient.get(`/api/v1/test/events/DeviceRevoked?userId=${userId}`);

  expect(response.status).toBe(200);
  expect(response.data.events).toBeDefined();
  expect(response.data.events.length).toBeGreaterThan(0);

  const latestEvent = response.data.events[response.data.events.length - 1];
  expect(latestEvent.payload.reason).toBe(reason);
});

Then('{int} DeviceRevoked events should be published with reason {word}', async function (this: CustomWorld, count: number, reason: string) {
  const userId = this.getTestData<string>('testUserId');

  // Wait a bit for async event publishing
  await new Promise(resolve => setTimeout(resolve, 500));

  const response = await this.identityApiClient.get(`/api/v1/test/events/DeviceRevoked?userId=${userId}`);

  expect(response.status).toBe(200);

  const eventsWithReason = response.data.events.filter((e: { payload: { reason: string } }) => e.payload.reason === reason);
  expect(eventsWithReason.length).toBeGreaterThanOrEqual(count);
});

Then('a DeviceRemembered event should be published', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');

  // Wait a bit for async event publishing
  await new Promise(resolve => setTimeout(resolve, 500));

  const response = await this.identityApiClient.get(`/api/v1/test/events/DeviceRemembered?userId=${userId}`);

  expect(response.status).toBe(200);
  expect(response.data.events).toBeDefined();
  expect(response.data.events.length).toBeGreaterThan(0);

  this.setTestData('latestDeviceRememberedEvent', response.data.events[response.data.events.length - 1]);
});

Then('the DeviceRemembered event should include {word}', function (this: CustomWorld, field: string) {
  const event = this.getTestData<{ payload: Record<string, unknown> }>('latestDeviceRememberedEvent');

  expect(event).toBeDefined();
  expect(event!.payload[field]).toBeDefined();
});

Then('the response should contain exactly {int} devices', function (this: CustomWorld, expectedCount: number) {
  const response = this.getTestData<{ data: { devices: unknown[] } }>('lastResponse');

  expect(response!.data.devices).toHaveLength(expectedCount);
});

Then('each device should include {word}', function (this: CustomWorld, field: string) {
  const response = this.getTestData<{ data: { devices: Record<string, unknown>[] } }>('lastResponse');

  response!.data.devices.forEach(device => {
    expect(device[field]).toBeDefined();
  });
});

Then('the device trust {string} should not exist in Redis', async function (this: CustomWorld, deviceTrustId: string) {
  const response = await this.identityApiClient.get(`/api/v1/test/device-trusts/${deviceTrustId}`);

  expect(response.status).toBe(404);
});

Then('the device trust {string} should still exist in Redis', async function (this: CustomWorld, deviceTrustId: string) {
  const response = await this.identityApiClient.get(`/api/v1/test/device-trusts/${deviceTrustId}`);

  expect(response.status).toBe(200);
});

Then('the device trust lastUsedAt should be updated to now', async function (this: CustomWorld) {
  const deviceTrustId = this.getTestData<string>('deviceTrustId');

  const response = await this.identityApiClient.get(`/api/v1/test/device-trusts/${deviceTrustId}`);

  expect(response.status).toBe(200);

  const lastUsedAt = new Date(response.data.lastUsedAt);
  const now = new Date();
  const diffSeconds = Math.abs((now.getTime() - lastUsedAt.getTime()) / 1000);

  // Should be updated within the last 5 seconds
  expect(diffSeconds).toBeLessThan(5);
});

Then('the first device should have deviceName {string}', function (this: CustomWorld, expectedName: string) {
  const response = this.getTestData<{ data: { devices: DeviceTrustInfo[] } }>('lastResponse');

  expect(response!.data.devices[0].deviceName).toBe(expectedName);
});

Then('the device {string} should have isCurrent set to true', function (this: CustomWorld, deviceTrustId: string) {
  const response = this.getTestData<{ data: { devices: DeviceTrustInfo[] } }>('lastResponse');

  const device = response!.data.devices.find(d => d.id === deviceTrustId);
  expect(device).toBeDefined();
  expect(device!.isCurrent).toBe(true);
});

Then('all other devices should have isCurrent set to false', function (this: CustomWorld) {
  const response = this.getTestData<{ data: { devices: DeviceTrustInfo[] } }>('lastResponse');
  const currentDeviceTrustId = this.getTestData<string>('currentDeviceTrustId');

  const otherDevices = response!.data.devices.filter(d => d.id !== currentDeviceTrustId);

  otherDevices.forEach(device => {
    expect(device.isCurrent).toBe(false);
  });
});

Then('the response should contain {string} status', function (this: CustomWorld, expectedStatus: string) {
  const response = this.getTestData<{ data: { status: string } }>('lastResponse');

  if (!response) {
    throw new Error('No response found. Did you make an API call?');
  }

  expect(response.data.status).toBe(expectedStatus);
});

Then('the password change should succeed', function (this: CustomWorld) {
  const success = this.getTestData<boolean>('passwordChangeSuccess');

  if (success === false) {
    throw new Error('Password change endpoint returned an error. Feature may not be implemented yet.');
  }

  expect(success).toBe(true);
});
