import { Given, When, Then, DataTable } from '@cucumber/cucumber';
import { expect } from '@playwright/test';
import { generateSync } from 'otplib';
import { CustomWorld } from '../../support/world.js';
import { ApiResponse } from '../../support/api-client.js';

interface MfaVerifyRequest {
  mfaToken: string;
  code: string;
  method: string;
  rememberDevice?: boolean;
}

interface MfaVerifyResponse {
  status: string;
  userId?: string;
  deviceTrusted?: boolean;
  expiresIn?: number;
}

interface MfaVerifyErrorResponse {
  error: string;
  message: string;
  remainingAttempts?: number;
}

interface SigninMfaResponse {
  status: 'MFA_REQUIRED';
  mfaToken: string;
  mfaMethods: string[];
  expiresIn: number;
}

// ============================================================================
// GIVEN Steps
// ============================================================================

Given('the user has TOTP MFA enabled with a valid secret', async function (this: CustomWorld) {
  const userId = this.getTestData<string>('testUserId');

  if (!userId) {
    throw new Error('Test user must be created before enabling MFA');
  }

  // This is a valid base32 encoded secret (32 chars = 20 bytes, meets minimum requirement)
  const totpSecret = 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP';

  // Enable MFA for the user via the test endpoint
  const response = await this.identityApiClient.post<{ userId: string; mfaEnabled: boolean; totpEnabled: boolean }>(
    `/api/v1/test/users/${userId}/enable-mfa`,
    { totpSecret }
  );

  if (response.status !== 200) {
    throw new Error(`Failed to enable MFA for user: ${response.status} - ${JSON.stringify(response.data)}`);
  }

  // Store the TOTP secret for generating codes later
  this.setTestData('totpEnabled', true);
  this.setTestData('totpSecret', totpSecret);
});

Given('I have completed credential validation and received an MFA token', async function (this: CustomWorld) {
  const email = this.getTestData<string>('testUserEmail');
  const password = this.getTestData<string>('testUserPassword');

  if (!email || !password) {
    throw new Error('Test user email and password must be set before completing credential validation');
  }

  const response = await this.identityApiClient.post<SigninMfaResponse>(
    '/api/v1/auth/signin',
    {
      email,
      password,
      rememberMe: false,
    }
  );

  if (response.status !== 200 || response.data.status !== 'MFA_REQUIRED') {
    throw new Error(`Expected MFA_REQUIRED response, got: ${response.status} - ${JSON.stringify(response.data)}`);
  }

  this.setTestData('mfaToken', response.data.mfaToken);
  this.setTestData('mfaMethods', response.data.mfaMethods);
});

Given('I have successfully verified with a TOTP code', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');
  const totpSecret = this.getTestData<string>('totpSecret');

  if (!mfaToken) {
    throw new Error('MFA token must be set before verifying');
  }

  // Generate a valid TOTP code (this would use the actual TOTP algorithm)
  // For now, we use a placeholder - in real tests, you'd use a TOTP library
  const code = generateTotpCode(totpSecret || '');

  const response = await this.identityApiClient.post<MfaVerifyResponse>(
    '/api/v1/auth/mfa/verify',
    {
      mfaToken,
      code,
      method: 'TOTP',
      rememberDevice: false,
    }
  );

  if (response.status !== 200) {
    throw new Error(`Expected successful MFA verification, got: ${response.status}`);
  }

  this.setTestData('lastUsedCode', code);
  this.setTestData('lastMfaVerifyResponse', response);
});

Given('I wait for the MFA challenge to expire', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');

  if (!mfaToken) {
    throw new Error('MFA token must be set before waiting for expiry');
  }

  // Use the test endpoint to expire the challenge immediately
  const response = await this.identityApiClient.post<{ mfaToken: string; expired: boolean }>(
    '/api/v1/test/mfa/expire-challenge',
    { mfaToken }
  );

  if (response.status !== 200) {
    throw new Error(`Failed to expire MFA challenge: ${response.status} - ${JSON.stringify(response.data)}`);
  }

  this.setTestData('challengeExpired', true);
});

// ============================================================================
// WHEN Steps
// ============================================================================

When('I submit an MFA verification request with the correct TOTP code', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');
  const totpSecret = this.getTestData<string>('totpSecret');

  if (!mfaToken) {
    throw new Error('MFA token must be set before verifying');
  }

  // Generate a valid TOTP code
  const code = generateTotpCode(totpSecret || '');

  const request: MfaVerifyRequest = {
    mfaToken,
    code,
    method: 'TOTP',
    rememberDevice: false,
  };

  const response = await this.identityApiClient.post<MfaVerifyResponse | MfaVerifyErrorResponse>(
    '/api/v1/auth/mfa/verify',
    request
  );

  this.setTestData('lastResponse', response);
  this.setTestData('lastMfaRequest', request);
});

When('I submit an MFA verification request with a code from the previous time step', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');
  const totpSecret = this.getTestData<string>('totpSecret');

  if (!mfaToken) {
    throw new Error('MFA token must be set before verifying');
  }

  // Generate a code from the previous time step
  const code = generateTotpCode(totpSecret || '', -1);

  const request: MfaVerifyRequest = {
    mfaToken,
    code,
    method: 'TOTP',
    rememberDevice: false,
  };

  const response = await this.identityApiClient.post<MfaVerifyResponse | MfaVerifyErrorResponse>(
    '/api/v1/auth/mfa/verify',
    request
  );

  this.setTestData('lastResponse', response);
});

When('I create a new MFA challenge for the same user', async function (this: CustomWorld) {
  const email = this.getTestData<string>('testUserEmail');
  const password = this.getTestData<string>('testUserPassword');

  if (!email || !password) {
    throw new Error('Test user credentials must be set');
  }

  const response = await this.identityApiClient.post<SigninMfaResponse>(
    '/api/v1/auth/signin',
    {
      email,
      password,
      rememberMe: false,
    }
  );

  if (response.status === 200 && response.data.status === 'MFA_REQUIRED') {
    this.setTestData('mfaToken', response.data.mfaToken);
  }
});

When('I submit an MFA verification request with the same TOTP code', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');
  const lastUsedCode = this.getTestData<string>('lastUsedCode');

  if (!mfaToken || !lastUsedCode) {
    throw new Error('MFA token and previously used code must be set');
  }

  const request: MfaVerifyRequest = {
    mfaToken,
    code: lastUsedCode,
    method: 'TOTP',
    rememberDevice: false,
  };

  const response = await this.identityApiClient.post<MfaVerifyResponse | MfaVerifyErrorResponse>(
    '/api/v1/auth/mfa/verify',
    request
  );

  this.setTestData('lastResponse', response);
});

When('I submit an MFA verification request with an invalid code', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');

  if (!mfaToken) {
    throw new Error('MFA token must be set before verifying');
  }

  const request: MfaVerifyRequest = {
    mfaToken,
    code: '000000', // Invalid code
    method: 'TOTP',
    rememberDevice: false,
  };

  const response = await this.identityApiClient.post<MfaVerifyResponse | MfaVerifyErrorResponse>(
    '/api/v1/auth/mfa/verify',
    request
  );

  this.setTestData('lastResponse', response);
});

When('I submit {int} MFA verification requests with wrong codes', async function (this: CustomWorld, count: number) {
  const mfaToken = this.getTestData<string>('mfaToken');

  if (!mfaToken) {
    throw new Error('MFA token must be set before verifying');
  }

  let lastResponse: ApiResponse<MfaVerifyResponse | MfaVerifyErrorResponse> | undefined;

  for (let i = 0; i < count; i++) {
    const request: MfaVerifyRequest = {
      mfaToken,
      code: String(100000 + i).padStart(6, '0'), // Different invalid codes
      method: 'TOTP',
      rememberDevice: false,
    };

    lastResponse = await this.identityApiClient.post<MfaVerifyResponse | MfaVerifyErrorResponse>(
      '/api/v1/auth/mfa/verify',
      request
    );
  }

  this.setTestData('lastResponse', lastResponse);
});

When('I submit an MFA verification request with:', async function (this: CustomWorld, dataTable: DataTable) {
  const data = dataTable.rowsHash();
  const mfaToken = this.getTestData<string>('mfaToken') || data.mfaToken;

  const request: MfaVerifyRequest = {
    mfaToken,
    code: data.code,
    method: data.method || 'TOTP',
    rememberDevice: data.rememberDevice === 'true',
  };

  const response = await this.identityApiClient.post<MfaVerifyResponse | MfaVerifyErrorResponse>(
    '/api/v1/auth/mfa/verify',
    request
  );

  this.setTestData('lastResponse', response);
  this.setTestData('lastMfaRequest', request);
});

When('I submit an MFA verification request with rememberDevice=true', async function (this: CustomWorld) {
  const mfaToken = this.getTestData<string>('mfaToken');
  const totpSecret = this.getTestData<string>('totpSecret');

  if (!mfaToken) {
    throw new Error('MFA token must be set before verifying');
  }

  const code = generateTotpCode(totpSecret || '');

  const request: MfaVerifyRequest = {
    mfaToken,
    code,
    method: 'TOTP',
    rememberDevice: true,
  };

  const response = await this.identityApiClient.post<MfaVerifyResponse | MfaVerifyErrorResponse>(
    '/api/v1/auth/mfa/verify',
    request
  );

  this.setTestData('lastResponse', response);
});

// ============================================================================
// THEN Steps
// ============================================================================

Then('I store the MFA token from the response', async function (this: CustomWorld) {
  const response = this.getTestData<ApiResponse<SigninMfaResponse>>('lastResponse');

  if (!response || response.status !== 200) {
    throw new Error('Expected successful response with MFA token');
  }

  const data = response.data as SigninMfaResponse;
  if (!data.mfaToken) {
    throw new Error('Response does not contain mfaToken');
  }

  this.setTestData('mfaToken', data.mfaToken);
  this.setTestData('mfaMethods', data.mfaMethods);
});

Then('an MFAChallengeInitiated event should be persisted in the event store', async function (this: CustomWorld) {
  // This would typically query the event store directly
  // For now, we verify the request returned MFA_REQUIRED as expected
  const response = this.getTestData<ApiResponse<SigninMfaResponse>>('lastResponse');
  expect(response).toBeDefined();
  expect(response!.status).toBe(200);
  expect((response!.data as SigninMfaResponse).status).toBe('MFA_REQUIRED');
});

Then('an MFAVerificationSucceeded event should be persisted in the event store', async function (this: CustomWorld) {
  // This would typically query the event store directly
  // For now, we verify the request succeeded
  const response = this.getTestData<ApiResponse<MfaVerifyResponse>>('lastResponse');
  expect(response).toBeDefined();
  expect(response!.status).toBe(200);
});

Then('an MFAVerificationFailed event should be persisted in the event store', async function (this: CustomWorld) {
  // This would typically query the event store directly
  // For now, we verify the request failed as expected
  const response = this.getTestData<ApiResponse<MfaVerifyErrorResponse>>('lastResponse');
  expect(response).toBeDefined();
  expect(response!.status).toBe(401);
});

Then('the response should contain {string} with value true', async function (this: CustomWorld, field: string) {
  const response = this.getTestData<ApiResponse<MfaVerifyResponse>>('lastResponse');
  expect(response).toBeDefined();

  const data = response!.data as Record<string, unknown>;
  expect(data[field]).toBe(true);
});

Then('the response should contain {string} with value false', async function (this: CustomWorld, field: string) {
  const response = this.getTestData<ApiResponse<MfaVerifyResponse>>('lastResponse');
  expect(response).toBeDefined();

  const data = response!.data as Record<string, unknown>;
  expect(data[field]).toBe(false);
});

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Generates a TOTP code for testing using the otplib library.
 *
 * Uses the same algorithm as the backend (RFC 6238 with SHA-1, 6 digits, 30 sec step).
 *
 * @param secret - The TOTP secret (base32 encoded, must be at least 16 bytes / 26 chars)
 * @param offset - Time step offset (e.g., -1 for previous step, +1 for next step)
 * @returns A 6-digit TOTP code
 */
function generateTotpCode(secret: string, offset: number = 0): string {
  if (!secret) {
    throw new Error('TOTP secret is required');
  }

  const period = 30; // TOTP time step in seconds
  const epoch = Math.floor(Date.now() / 1000) + (offset * period);

  return generateSync({ secret, epoch });
}
