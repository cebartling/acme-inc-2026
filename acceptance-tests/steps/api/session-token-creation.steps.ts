import { Given, When, Then } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { generateSync } from 'otplib';
import { CustomWorld } from '../../support/world.js';

interface JWTHeader {
  alg: string;
  typ: string;
  kid?: string;
}

interface JWTPayload {
  sub?: string;
  email?: string;
  roles?: string[];
  sessionId?: string;
  tokenFamily?: string;
  iat?: number;
  exp?: number;
  iss?: string;
  aud?: string;
  [key: string]: unknown;
}

interface SessionData {
  sessionId?: string;
  userId?: string;
  deviceId?: string;
  ipAddress?: string;
  userAgent?: string;
  createdAt?: string;
  expiresAt?: string;
  [key: string]: unknown;
}

// ============================================================================
// Helper Functions
// ============================================================================

function decodeJWT(token: string): { header: JWTHeader; payload: JWTPayload } {
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Invalid JWT format');
  }

  const header = JSON.parse(Buffer.from(parts[0], 'base64url').toString());
  const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString());

  return { header, payload };
}

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

function getCookieAttributes(
  cookieHeader: string | string[],
  cookieName: string
): Map<string, string> {
  const cookies = Array.isArray(cookieHeader) ? cookieHeader : [cookieHeader];
  const attributes = new Map<string, string>();

  for (const cookie of cookies) {
    if (cookie.startsWith(`${cookieName}=`)) {
      const parts = cookie.split(';').map((p) => p.trim());

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

Given('the user has TOTP MFA enabled', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('Test user must be created before enabling MFA');
  }

  // Use a valid base32 encoded secret
  const totpSecret = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';

  const response = await this.identityApiClient.post<{
    userId: string;
    mfaEnabled: boolean;
    totpEnabled: boolean;
  }>(`/api/v1/test/users/${userId}/enable-mfa`, { totpSecret });

  if (response.status !== 200) {
    throw new Error(`Failed to enable MFA for user: ${response.status}`);
  }

  this.setTestData('totpEnabled', true);
  this.setTestData('totpSecret', totpSecret);

  // Validate that the secret was properly stored
  const storedSecret = this.getTestData<string>('totpSecret');
  if (storedSecret !== totpSecret) {
    throw new Error('TOTP secret was not properly stored in test data');
  }
});

Given(
  'the user has {int} active sessions',
  async function (this: CustomWorld, sessionCount: number) {
    const userId = this.getTestData<string>('testUserId');

    if (!userId) {
      throw new Error('Test user must be created before creating sessions');
    }

    // Create multiple sessions via test endpoint
    console.log(`Creating ${sessionCount} sessions for user ${userId}`);
    console.log('Request body:', JSON.stringify({ count: sessionCount }));

    const response = await this.identityApiClient.post(
      `/api/v1/test/users/${userId}/create-sessions`,
      { count: sessionCount }
    );

    if (response.status !== 200) {
      console.error('Session creation failed:', response.status);
      console.error('Response data:', JSON.stringify(response.data));
      throw new Error(
        `Failed to create sessions: ${response.status} - ${JSON.stringify(response.data)}`
      );
    }

    this.setTestData('existingSessionCount', sessionCount);
  }
);

Given(
  'the user has {int} active sessions from different devices',
  async function (this: CustomWorld, sessionCount: number) {
    const userId = this.getTestData<string>('testUserId');

    if (!userId) {
      throw new Error('Test user must be created before creating sessions');
    }

    // Create sessions with different device IDs
    const response = await this.identityApiClient.post(
      `/api/v1/test/users/${userId}/create-sessions`,
      { count: sessionCount, differentDevices: true }
    );

    if (response.status !== 200) {
      throw new Error(`Failed to create sessions from different devices: ${response.status}`);
    }

    this.setTestData('existingSessionCount', sessionCount);
  }
);

// ============================================================================
// WHEN Steps - Actions
// ============================================================================

When(
  'I complete signin and MFA verification for {string}',
  async function (this: CustomWorld, email: string) {
    // Use the actual email that was created (may be unique)
    const actualEmail = this.getTestData<string>('testUserEmail') || email;
    const password = this.getTestData<string>('testUserPassword') || 'ValidP@ss123!';
    const totpSecret = this.getTestData<string>('totpSecret');

    if (!totpSecret) {
      throw new Error('TOTP secret must be set before MFA verification');
    }

    // Step 1: Signin to get MFA token
    const signinResponse = await this.identityApiClient.post('/api/v1/auth/signin', {
      email: actualEmail,
      password,
      rememberMe: false,
    });

    expect(signinResponse.status).toBe(200);

    const mfaToken = signinResponse.data.mfaToken;
    this.setTestData('mfaToken', mfaToken);

    // Step 2: Verify MFA with TOTP code
    const totpCode = generateSync({ secret: totpSecret });

    const verifyResponse = await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
      mfaToken,
      code: totpCode,
      method: 'TOTP',
      rememberDevice: false,
    });

    this.setLastResponse(verifyResponse);

    // Store cookies and tokens for verification
    const setCookieHeaders = verifyResponse.headers['set-cookie'];
    if (setCookieHeaders && setCookieHeaders.length > 0) {
      this.setTestData('setCookieHeaders', setCookieHeaders);

      // Automatically extract and store token values for convenience
      const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
      if (accessToken) {
        this.setTestData('access_token_value', accessToken);
      }

      const refreshToken = extractCookieValue(setCookieHeaders, 'refresh_token');
      if (refreshToken) {
        this.setTestData('refresh_token_value', refreshToken);
      }
    }
  }
);

When(
  'I complete signin and MFA verification for {string} from a new device',
  async function (this: CustomWorld, email: string) {
    // Use the actual email that was created (may be unique)
    const actualEmail = this.getTestData<string>('testUserEmail') || email;
    const password = this.getTestData<string>('testUserPassword') || 'ValidP@ss123!';
    const totpSecret = this.getTestData<string>('totpSecret');

    if (!totpSecret) {
      throw new Error('TOTP secret must be set before MFA verification');
    }

    // Generate a unique device fingerprint
    const deviceFingerprint = `fp_${Date.now()}`;

    // Step 1: Signin with device fingerprint
    const signinResponse = await this.identityApiClient.post('/api/v1/auth/signin', {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint,
    });

    const mfaToken = signinResponse.data.mfaToken;

    // Step 2: Verify MFA
    const totpCode = generateSync({ secret: totpSecret });

    const verifyResponse = await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
      mfaToken,
      code: totpCode,
      method: 'TOTP',
      rememberDevice: false,
      deviceFingerprint,
    });

    this.setLastResponse(verifyResponse);

    // Store cookies and tokens for verification
    const setCookieHeaders = verifyResponse.headers['set-cookie'];
    if (setCookieHeaders && setCookieHeaders.length > 0) {
      this.setTestData('setCookieHeaders', setCookieHeaders);

      // Automatically extract and store token values for convenience
      const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
      if (accessToken) {
        this.setTestData('access_token_value', accessToken);
      }

      const refreshToken = extractCookieValue(setCookieHeaders, 'refresh_token');
      if (refreshToken) {
        this.setTestData('refresh_token_value', refreshToken);
      }
    }
  }
);

When(
  'I complete signin and MFA verification for {string} with device fingerprint {string}',
  async function (this: CustomWorld, email: string, deviceFingerprint: string) {
    // Use the actual email that was created (may be unique)
    const actualEmail = this.getTestData<string>('testUserEmail') || email;
    const password = this.getTestData<string>('testUserPassword') || 'ValidP@ss123!';
    const totpSecret = this.getTestData<string>('totpSecret');

    if (!totpSecret) {
      throw new Error('TOTP secret must be set before MFA verification');
    }

    // Step 1: Signin with device fingerprint
    const signinResponse = await this.identityApiClient.post('/api/v1/auth/signin', {
      email: actualEmail,
      password,
      rememberMe: false,
      deviceFingerprint,
    });

    const mfaToken = signinResponse.data.mfaToken;

    // Step 2: Verify MFA with device fingerprint
    const totpCode = generateSync({ secret: totpSecret });

    const verifyResponse = await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
      mfaToken,
      code: totpCode,
      method: 'TOTP',
      rememberDevice: false,
      deviceFingerprint,
    });

    this.setLastResponse(verifyResponse);

    // Store cookies and tokens for verification
    const setCookieHeaders = verifyResponse.headers['set-cookie'];
    if (setCookieHeaders && setCookieHeaders.length > 0) {
      this.setTestData('setCookieHeaders', setCookieHeaders);

      // Automatically extract and store token values for convenience
      const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
      if (accessToken) {
        this.setTestData('access_token_value', accessToken);
      }

      const refreshToken = extractCookieValue(setCookieHeaders, 'refresh_token');
      if (refreshToken) {
        this.setTestData('refresh_token_value', refreshToken);
      }
    }

    this.setTestData('deviceFingerprint', deviceFingerprint);
  }
);

When(
  'I complete signin and SMS MFA verification for {string}',
  async function (this: CustomWorld, email: string) {
    // Use the actual email that was created (may be unique)
    const actualEmail = this.getTestData<string>('testUserEmail') || email;
    const password = this.getTestData<string>('testUserPassword') || 'ValidP@ss123!';

    // Step 1: Signin to get MFA token
    const signinResponse = await this.identityApiClient.post('/api/v1/auth/signin', {
      email: actualEmail,
      password,
      rememberMe: false,
    });

    expect(signinResponse.status).toBe(200);

    const mfaToken = signinResponse.data.mfaToken;
    this.setTestData('mfaToken', mfaToken);

    // Step 2: Get the SMS code from test endpoint
    const userId = this.getTestData<string>('testUserId');
    const codeResponse = await this.identityApiClient.get(`/api/v1/test/users/${userId}/sms-code`);
    const smsCode = codeResponse.data.code;

    // Step 3: Verify MFA with SMS code
    const verifyResponse = await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
      mfaToken,
      code: smsCode,
      method: 'SMS',
      rememberDevice: false,
    });

    this.setLastResponse(verifyResponse);

    // Store cookies and tokens for verification
    const setCookieHeaders = verifyResponse.headers['set-cookie'];
    if (setCookieHeaders && setCookieHeaders.length > 0) {
      this.setTestData('setCookieHeaders', setCookieHeaders);

      // Automatically extract and store token values for convenience
      const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
      if (accessToken) {
        this.setTestData('access_token_value', accessToken);
      }

      const refreshToken = extractCookieValue(setCookieHeaders, 'refresh_token');
      if (refreshToken) {
        this.setTestData('refresh_token_value', refreshToken);
      }
    }
  }
);

When(
  'I measure the response time for {int} MFA verification requests',
  async function (this: CustomWorld, requestCount: number) {
    const password = this.getTestData<string>('testUserPassword') || 'ValidP@ss123!';
    const totpSecret = this.getTestData<string>('totpSecret');
    const email = this.getTestData<string>('testUserEmail');

    if (!totpSecret) {
      throw new Error(
        'TOTP secret must be set before running performance test. Current value: ' + totpSecret
      );
    }
    if (!email) {
      throw new Error('Email must be set before running performance test. Current value: ' + email);
    }

    const responseTimes: number[] = [];

    for (let i = 0; i < requestCount; i++) {
      // Get MFA token
      const signinResponse = await this.identityApiClient.post('/api/v1/auth/signin', {
        email,
        password,
        rememberMe: false,
      });

      const mfaToken = signinResponse.data.mfaToken;
      const totpCode = generateSync({ secret: totpSecret });

      // Measure verification time
      const startTime = Date.now();

      await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
        mfaToken,
        code: totpCode,
        method: 'TOTP',
        rememberDevice: false,
      });

      const endTime = Date.now();
      responseTimes.push(endTime - startTime);
    }

    // Calculate p95
    responseTimes.sort((a, b) => a - b);
    const p95Index = Math.ceil(requestCount * 0.95) - 1;
    const p95ResponseTime = responseTimes[p95Index];

    this.setTestData('p95ResponseTime', p95ResponseTime);
  }
);

// ============================================================================
// THEN Steps - Cookie Assertions
// ============================================================================

Then(
  'the response should set a secure cookie named {string}',
  function (this: CustomWorld, cookieName: string) {
    const setCookieHeaders = this.getTestData<string[]>('setCookieHeaders');

    if (!setCookieHeaders) {
      throw new Error('No Set-Cookie headers found in response');
    }

    const cookieValue = extractCookieValue(setCookieHeaders, cookieName);

    expect(cookieValue).toBeTruthy();
    expect(cookieValue).not.toBe('');

    this.setTestData(`${cookieName}_value`, cookieValue);
  }
);

Then(
  'the {word} token cookie should have HttpOnly flag',
  function (this: CustomWorld, tokenType: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const setCookieHeaders = this.getTestData<string[]>('setCookieHeaders');

    if (!setCookieHeaders) {
      throw new Error('No Set-Cookie headers found');
    }

    const attributes = getCookieAttributes(setCookieHeaders, cookieName);
    expect(attributes.has('httponly')).toBe(true);
  }
);

Then(
  'the {word} token cookie should have Secure flag',
  function (this: CustomWorld, tokenType: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const setCookieHeaders = this.getTestData<string[]>('setCookieHeaders');

    if (!setCookieHeaders) {
      throw new Error('No Set-Cookie headers found');
    }

    const attributes = getCookieAttributes(setCookieHeaders, cookieName);
    expect(attributes.has('secure')).toBe(true);
  }
);

Then(
  'the {word} token cookie should have SameSite=Strict',
  function (this: CustomWorld, tokenType: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const setCookieHeaders = this.getTestData<string[]>('setCookieHeaders');

    if (!setCookieHeaders) {
      throw new Error('No Set-Cookie headers found');
    }

    const attributes = getCookieAttributes(setCookieHeaders, cookieName);
    expect(attributes.get('samesite')?.toLowerCase()).toBe('strict');
  }
);

Then(
  'the {word} token cookie should have Path={string}',
  function (this: CustomWorld, tokenType: string, path: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const setCookieHeaders = this.getTestData<string[]>('setCookieHeaders');

    if (!setCookieHeaders) {
      throw new Error('No Set-Cookie headers found');
    }

    const attributes = getCookieAttributes(setCookieHeaders, cookieName);
    expect(attributes.get('path')).toBe(path);
  }
);

Then(
  'the {word} token cookie should have Max-Age={int}',
  function (this: CustomWorld, tokenType: string, maxAge: number) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const setCookieHeaders = this.getTestData<string[]>('setCookieHeaders');

    if (!setCookieHeaders) {
      throw new Error('No Set-Cookie headers found');
    }

    const attributes = getCookieAttributes(setCookieHeaders, cookieName);
    const actualMaxAge = parseInt(attributes.get('max-age') || '0', 10);

    expect(actualMaxAge).toBe(maxAge);
  }
);

Then(
  'the response should set exactly {int} cookies',
  function (this: CustomWorld, expectedCount: number) {
    const setCookieHeaders = this.getTestData<string[]>('setCookieHeaders');

    if (!setCookieHeaders) {
      expect(expectedCount).toBe(0);
      return;
    }

    expect(setCookieHeaders.length).toBe(expectedCount);
  }
);

// ============================================================================
// THEN Steps - JWT Token Verification
// ============================================================================

Then(
  'the {word} token JWT should include a {string} header',
  function (this: CustomWorld, tokenType: string, headerName: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);

    if (!tokenValue) {
      throw new Error(`Token ${cookieName} not found`);
    }

    const { header } = decodeJWT(tokenValue);
    expect(header[headerName as keyof JWTHeader]).toBeDefined();

    this.setTestData(`${tokenType}_jwt_header`, header);
  }
);

Then(
  'the {string} header should match pattern {string}',
  function (this: CustomWorld, headerName: string, pattern: string) {
    const header =
      this.getTestData<JWTHeader>('access_jwt_header') ||
      this.getTestData<JWTHeader>('refresh_jwt_header');

    if (!header) {
      throw new Error('JWT header not found');
    }

    const headerValue = header[headerName as keyof JWTHeader];
    expect(headerValue).toBeDefined();
    expect(String(headerValue)).toMatch(new RegExp(pattern));
  }
);

Then(
  'the {word} token JWT should include {string} claim',
  function (this: CustomWorld, tokenType: string, claimName: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);

    if (!tokenValue) {
      throw new Error(`Token ${cookieName} not found`);
    }

    const { payload } = decodeJWT(tokenValue);
    expect(payload[claimName]).toBeDefined();

    this.setTestData(`${tokenType}_jwt_payload`, payload);
  }
);

Then(
  "the {word} token JWT should include claim {string} with the user's UUID",
  function (this: CustomWorld, tokenType: string, claimName: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);
    const userId = this.getTestData<string>('testUserId');

    if (!tokenValue || !userId) {
      throw new Error('Token or userId not found');
    }

    const { payload } = decodeJWT(tokenValue);
    expect(payload[claimName]).toBe(userId);
  }
);

Then(
  'the {word} token JWT should include claim {string} with value {string}',
  function (this: CustomWorld, tokenType: string, claimName: string, expectedValue: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);

    if (!tokenValue) {
      throw new Error(`Token ${cookieName} not found`);
    }

    const { payload } = decodeJWT(tokenValue);

    // Special handling for email claim - use actual stored email if checking email
    let actualExpectedValue = expectedValue;
    if (claimName === 'email') {
      const storedEmail = this.getTestData<string>('testUserEmail');
      if (storedEmail) {
        actualExpectedValue = storedEmail;
      }
    }

    expect(payload[claimName]).toBe(actualExpectedValue);
  }
);

Then(
  'the {word} token JWT should include claim {string} containing {string}',
  function (this: CustomWorld, tokenType: string, claimName: string, expectedValue: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);

    if (!tokenValue) {
      throw new Error(`Token ${cookieName} not found`);
    }

    const { payload } = decodeJWT(tokenValue);
    const claimValue = payload[claimName];

    if (Array.isArray(claimValue)) {
      expect(claimValue).toContain(expectedValue);
    } else {
      expect(String(claimValue)).toContain(expectedValue);
    }
  }
);

Then(
  'the {word} token JWT should not include claim {string}',
  function (this: CustomWorld, tokenType: string, claimName: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);

    if (!tokenValue) {
      throw new Error(`Token ${cookieName} not found`);
    }

    const { payload } = decodeJWT(tokenValue);
    expect(payload[claimName]).toBeUndefined();
  }
);

Then(
  'the {word} token JWT should include claim {string}',
  function (this: CustomWorld, tokenType: string, claimName: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);

    if (!tokenValue) {
      throw new Error(`Token ${cookieName} not found`);
    }

    const { payload } = decodeJWT(tokenValue);
    expect(payload[claimName]).toBeDefined();
  }
);

Then(
  'the {word} token should expire in {int} seconds',
  function (this: CustomWorld, tokenType: string, expectedExpiry: number) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);

    if (!tokenValue) {
      throw new Error(`Token ${cookieName} not found`);
    }

    const { payload } = decodeJWT(tokenValue);

    if (!payload.iat || !payload.exp) {
      throw new Error('Token missing iat or exp claims');
    }

    const actualExpiry = payload.exp - payload.iat;

    // Allow 1 second tolerance
    expect(Math.abs(actualExpiry - expectedExpiry)).toBeLessThanOrEqual(1);
  }
);

Then(
  'the {word} token JWT header should have {string} value {string}',
  function (this: CustomWorld, tokenType: string, headerName: string, expectedValue: string) {
    const cookieName = `${tokenType.toLowerCase()}_token`;
    const tokenValue = this.getTestData<string>(`${cookieName}_value`);

    if (!tokenValue) {
      throw new Error(`Token ${cookieName} not found`);
    }

    const { header } = decodeJWT(tokenValue);
    expect(header[headerName as keyof JWTHeader]).toBe(expectedValue);
  }
);

Then(
  'the tokenFamily should match pattern {string}',
  function (this: CustomWorld, pattern: string) {
    const payload = this.getTestData<JWTPayload>('refresh_jwt_payload');

    if (!payload || !payload.tokenFamily) {
      throw new Error('Token family not found in refresh token payload');
    }

    expect(String(payload.tokenFamily)).toMatch(new RegExp(pattern));
  }
);

// ============================================================================
// THEN Steps - Redis Session Verification
// ============================================================================

Then('a session should be created in Redis for the user', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('User ID not found');
  }

  // Query test endpoint to check Redis session
  const response = await this.identityApiClient.get(`/api/v1/test/users/${userId}/sessions`);

  expect(response.status).toBe(200);
  expect(response.data.sessions).toBeDefined();
  expect(response.data.sessions.length).toBeGreaterThan(0);

  // Store the most recent session
  const latestSession = response.data.sessions[response.data.sessions.length - 1];
  this.setTestData('latestSession', latestSession);
});

Then('the session should include {word}', function (this: CustomWorld, fieldName: string) {
  const session = this.getTestData<SessionData>('latestSession');

  if (!session) {
    throw new Error('No session data found');
  }

  expect(session[fieldName]).toBeDefined();
  expect(session[fieldName]).not.toBe('');
});

Then(
  'the session TTL in Redis should be approximately {int} seconds',
  async function (this: CustomWorld, expectedTtl: number) {
    const userId = this.getTestData<string>('testUserId');
    const session = this.getTestData<SessionData>('latestSession');

    if (!userId || !session || !session.sessionId) {
      throw new Error('User ID or session not found');
    }

    // Get TTL from test endpoint
    const response = await this.identityApiClient.get(
      `/api/v1/test/sessions/${session.sessionId}/ttl`
    );

    expect(response.status).toBe(200);

    const actualTtl = response.data.ttl;

    // Allow 60 second tolerance
    expect(Math.abs(actualTtl - expectedTtl)).toBeLessThanOrEqual(60);
  }
);

Then(
  'the user should have exactly {int} active sessions',
  async function (this: CustomWorld, expectedCount: number) {
    const userId = this.getTestData<string>('testUserId');

    if (!userId) {
      throw new Error('User ID not found');
    }

    const response = await this.identityApiClient.get(`/api/v1/test/users/${userId}/sessions`);

    expect(response.status).toBe(200);
    expect(response.data.sessions.length).toBe(expectedCount);
  }
);

// ============================================================================
// THEN Steps - Kafka Event Verification
// ============================================================================

Then('a SessionCreated event should be published to Kafka', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('User ID not found');
  }

  // Wait a bit for async event publishing
  await new Promise((resolve) => setTimeout(resolve, 500));

  // Check for event via test endpoint
  const response = await this.identityApiClient.get(
    `/api/v1/test/events/SessionCreated?userId=${userId}`
  );

  expect(response.status).toBe(200);
  expect(response.data.events).toBeDefined();
  expect(response.data.events.length).toBeGreaterThan(0);

  const latestEvent = response.data.events[response.data.events.length - 1];
  this.setTestData('latestSessionCreatedEvent', latestEvent);
});

Then('a UserLoggedIn event should be published to Kafka', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('User ID not found');
  }

  // Wait for async event publishing
  await new Promise((resolve) => setTimeout(resolve, 500));

  const response = await this.identityApiClient.get(
    `/api/v1/test/events/UserLoggedIn?userId=${userId}`
  );

  expect(response.status).toBe(200);
  expect(response.data.events).toBeDefined();
  expect(response.data.events.length).toBeGreaterThan(0);

  const latestEvent = response.data.events[response.data.events.length - 1];
  this.setTestData('latestUserLoggedInEvent', latestEvent);
});

Then('a UserLoggedIn event should be published', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('User ID not found');
  }

  // Wait for async event publishing and transaction commit
  await new Promise((resolve) => setTimeout(resolve, 1000));

  const response = await this.identityApiClient.get(
    `/api/v1/test/events/UserLoggedIn?userId=${userId}`
  );

  expect(response.status).toBe(200);
  expect(response.data.events).toBeDefined();
  expect(response.data.events.length).toBeGreaterThan(0);

  const latestEvent = response.data.events[response.data.events.length - 1];
  this.setTestData('latestUserLoggedInEvent', latestEvent);
});

Then('a SessionInvalidated event should be published', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('User ID not found');
  }

  // Wait for async event publishing and transaction commit
  await new Promise((resolve) => setTimeout(resolve, 1000));

  const response = await this.identityApiClient.get(
    `/api/v1/test/events/SessionInvalidated?userId=${userId}`
  );

  expect(response.status).toBe(200);
  expect(response.data.events).toBeDefined();
  expect(response.data.events.length).toBeGreaterThan(0);

  const latestEvent = response.data.events[response.data.events.length - 1];
  this.setTestData('latestSessionInvalidatedEvent', latestEvent);
});

Then('no SessionInvalidated event should be published', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');
  const existingCount = this.getTestData<number>('sessionInvalidatedEventCount') || 0;

  if (!userId) {
    throw new Error('User ID not found');
  }

  // Wait a bit
  await new Promise((resolve) => setTimeout(resolve, 500));

  const response = await this.identityApiClient.get(
    `/api/v1/test/events/SessionInvalidated?userId=${userId}`
  );

  expect(response.status).toBe(200);

  // Check that no new events were published
  const currentCount = response.data.events?.length || 0;
  expect(currentCount).toBe(existingCount);
});

Then('the event should contain {word}', function (this: CustomWorld, fieldName: string) {
  const event =
    this.getTestData('latestSessionCreatedEvent') ||
    this.getTestData('latestUserLoggedInEvent') ||
    this.getTestData('latestSessionInvalidatedEvent');

  if (!event) {
    throw new Error('No event data found');
  }

  expect(event[fieldName]).toBeDefined();
});

Then(
  'the event should contain {string} with value {string}',
  function (this: CustomWorld, fieldName: string, expectedValue: string) {
    const event =
      this.getTestData('latestSessionCreatedEvent') ||
      this.getTestData('latestUserLoggedInEvent') ||
      this.getTestData('latestSessionInvalidatedEvent') ||
      this.getTestData('latestAuthenticationFailedEvent');

    if (!event) {
      throw new Error('No event data found');
    }

    // Parse the nested payload structure
    let eventData = event;
    if (event.payload?.value) {
      const parsed = JSON.parse(event.payload.value);
      eventData = parsed.payload || parsed;
    }

    expect(eventData[fieldName]).toBeDefined();

    if (expectedValue === 'true' || expectedValue === 'false') {
      expect(eventData[fieldName]).toBe(expectedValue === 'true');
    } else {
      expect(String(eventData[fieldName])).toBe(expectedValue);
    }
  }
);

Then(
  'the event should contain {string} with value true',
  function (this: CustomWorld, fieldName: string) {
    const event =
      this.getTestData('latestSessionCreatedEvent') ||
      this.getTestData('latestUserLoggedInEvent') ||
      this.getTestData('latestSessionInvalidatedEvent');

    if (!event) {
      throw new Error('No event data found');
    }

    expect(event[fieldName]).toBe(true);
  }
);

Then(
  'the invalidated session reason should be {string}',
  function (this: CustomWorld, expectedReason: string) {
    const event = this.getTestData('latestSessionInvalidatedEvent');

    if (!event) {
      throw new Error('No SessionInvalidated event found');
    }

    expect(event.reason).toBe(expectedReason);
  }
);

// ============================================================================
// THEN Steps - Security & Performance
// ============================================================================

Then(
  'the response body should not contain {string}',
  function (this: CustomWorld, searchString: string) {
    const response = this.getLastResponse();

    if (!response) {
      throw new Error('No response found');
    }

    const body = JSON.stringify(response.data);
    expect(body.toLowerCase()).not.toContain(searchString.toLowerCase());
  }
);

Then('the response body should not contain any JWT token strings', function (this: CustomWorld) {
  const response = this.getLastResponse();

  if (!response) {
    throw new Error('No response found');
  }

  const body = JSON.stringify(response.data);

  // JWT tokens are three base64url-encoded parts separated by dots
  const jwtPattern = /[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}/;

  expect(jwtPattern.test(body)).toBe(false);
});

Then(
  'the application logs should not contain any JWT token strings',
  async function (this: CustomWorld) {
    // Query test endpoint to check recent logs
    const response = await this.identityApiClient.get('/api/v1/test/logs/recent?lines=100');

    expect(response.status).toBe(200);

    const logs = response.data.logs || '';

    // Check for JWT pattern in logs
    const jwtPattern = /[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}/;

    expect(jwtPattern.test(logs)).toBe(false);
  }
);

Then(
  'the 95th percentile response time should be less than {int}ms',
  function (this: CustomWorld, maxResponseTime: number) {
    const p95ResponseTime = this.getTestData<number>('p95ResponseTime');

    if (p95ResponseTime === undefined) {
      throw new Error('p95 response time not measured');
    }

    expect(p95ResponseTime).toBeLessThan(maxResponseTime);
  }
);
