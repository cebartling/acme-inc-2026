/**
 * API service for making requests to backend services.
 *
 * This provides a centralized place for API configuration and error handling.
 */

// API base URLs - these would typically come from environment variables
const IDENTITY_SERVICE_URL =
  import.meta.env.VITE_IDENTITY_SERVICE_URL || "http://localhost:10300";
const CUSTOMER_SERVICE_URL =
  import.meta.env.VITE_CUSTOMER_SERVICE_URL || "http://localhost:10301";

/**
 * Custom error class for API errors with status code and response data.
 */
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public data?: Record<string, unknown>
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/**
 * Makes a fetch request with standard headers and error handling.
 */
async function apiRequest<T>(
  url: string,
  options: RequestInit = {}
): Promise<T> {
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...options.headers,
  };

  const response = await fetch(url, {
    ...options,
    headers,
  });

  // Handle non-JSON responses
  const contentType = response.headers.get("content-type");
  const isJson = contentType?.includes("application/json");

  if (!response.ok) {
    const errorData = isJson ? await response.json() : null;
    throw new ApiError(
      errorData?.error || errorData?.message || `HTTP ${response.status}`,
      response.status,
      errorData
    );
  }

  return isJson ? response.json() : (null as T);
}

/**
 * Customer Service API client.
 */
export const customerApi = {
  /**
   * Gets a customer's preferences.
   */
  async getPreferences(
    customerId: string,
    userId: string
  ): Promise<PreferencesResponse> {
    return apiRequest<PreferencesResponse>(
      `${CUSTOMER_SERVICE_URL}/api/v1/customers/${customerId}/preferences`,
      {
        method: "GET",
        headers: {
          "X-User-Id": userId,
        },
      }
    );
  },

  /**
   * Updates a customer's preferences.
   */
  async updatePreferences(
    customerId: string,
    userId: string,
    preferences: UpdatePreferencesRequest
  ): Promise<PreferencesResponse> {
    return apiRequest<PreferencesResponse>(
      `${CUSTOMER_SERVICE_URL}/api/v1/customers/${customerId}/preferences`,
      {
        method: "PUT",
        headers: {
          "X-User-Id": userId,
        },
        body: JSON.stringify(preferences),
      }
    );
  },

  /**
   * Gets a customer's profile completeness breakdown.
   */
  async getProfileCompleteness(
    customerId: string
  ): Promise<ProfileCompletenessResponse> {
    return apiRequest<ProfileCompletenessResponse>(
      `${CUSTOMER_SERVICE_URL}/api/v1/customers/${customerId}/profile/completeness`,
      {
        method: "GET",
      }
    );
  },
};

/**
 * Response type for preferences API.
 */
export interface PreferencesResponse {
  customerId: string;
  preferences: {
    communication: {
      email: boolean;
      sms: boolean;
      push: boolean;
      marketing: boolean;
      frequency: string;
    };
    privacy: {
      shareDataWithPartners: boolean;
      allowAnalytics: boolean;
      allowPersonalization: boolean;
    };
    display: {
      language: string;
      currency: string;
      timezone: string;
    };
  };
  updatedAt: string;
}

/**
 * Request type for updating preferences.
 */
export interface UpdatePreferencesRequest {
  communication?: {
    email?: boolean;
    sms?: boolean;
    push?: boolean;
    marketing?: boolean;
    frequency?: string;
  };
  privacy?: {
    shareDataWithPartners?: boolean;
    allowAnalytics?: boolean;
    allowPersonalization?: boolean;
  };
  display?: {
    language?: string;
    currency?: string;
    timezone?: string;
  };
}

/**
 * Response type for profile completeness API.
 */
export interface ProfileCompletenessResponse {
  customerId: string;
  overallScore: number;
  sections: ProfileCompletenessSection[];
  nextAction: ProfileCompletenessNextAction | null;
  updatedAt: string;
}

/**
 * A section in the profile completeness breakdown.
 */
export interface ProfileCompletenessSection {
  name: string;
  displayName: string;
  weight: number;
  score: number;
  isComplete: boolean;
  items: ProfileCompletenessItem[];
}

/**
 * An item within a profile completeness section.
 */
export interface ProfileCompletenessItem {
  name: string;
  complete: boolean;
  action?: string;
}

/**
 * The next recommended action to improve profile completeness.
 */
export interface ProfileCompletenessNextAction {
  section: string;
  action: string;
  url: string;
}

// =============================================================================
// Identity Service API
// =============================================================================

/**
 * Request type for signin API.
 */
export interface SigninRequest {
  email: string;
  password: string;
  rememberMe: boolean;
  deviceFingerprint?: string;
}

/**
 * Response type for successful signin.
 */
export interface SigninSuccessResponse {
  status: "SUCCESS";
  userId: string;
  expiresIn: number;
}

/**
 * Response type for MFA required signin.
 */
export interface SigninMfaResponse {
  status: "MFA_REQUIRED";
  mfaToken: string;
  mfaMethods: string[];
  expiresIn: number;
}

/**
 * Combined signin response type.
 */
export type SigninResponse = SigninSuccessResponse | SigninMfaResponse;

/**
 * Error response from signin API.
 */
export interface SigninErrorResponse {
  error: string;
  message: string;
  remainingAttempts?: number;
  reason?: string;
  supportUrl?: string;
  lockedUntil?: string;
}

/**
 * Request type for MFA verification.
 */
export interface MfaVerifyRequest {
  mfaToken: string;
  code: string;
  method: string;
  rememberDevice?: boolean;
}

/**
 * Response type for successful MFA verification.
 */
export interface MfaVerifyResponse {
  status: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  deviceTrusted: boolean;
  expiresIn: number;
}

/**
 * Error response from MFA verify API.
 */
export interface MfaVerifyErrorResponse {
  error: string;
  message: string;
  remainingAttempts?: number;
}

/**
 * Request type for MFA resend (SMS only).
 */
export interface MfaResendRequest {
  mfaToken: string;
  method: string;
}

/**
 * Response type for successful MFA resend.
 */
export interface MfaResendResponse {
  status: string;
  maskedPhone: string;
  expiresIn: number;
  resendAvailableIn: number;
}

/**
 * Error response from MFA resend API.
 */
export interface MfaResendErrorResponse {
  error: string;
  message: string;
  resendAvailableIn?: number;
  retryAfter?: number;
}

/**
 * Trusted device information.
 */
export interface TrustedDevice {
  id: string;
  deviceName: string;
  createdAt: string;
  lastUsedAt: string;
  expiresAt: string;
  ipAddress: string;
  isCurrent: boolean;
}

/**
 * Response type for trusted devices list.
 */
export interface DevicesResponse {
  devices: TrustedDevice[];
}

/**
 * Identity Service API client.
 */
export const identityApi = {
  /**
   * Authenticates a user with email and password.
   *
   * @param credentials - The user's signin credentials.
   * @returns The signin response on success.
   * @throws ApiError on authentication failure.
   */
  async signin(credentials: SigninRequest): Promise<SigninResponse> {
    return apiRequest<SigninResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/signin`,
      {
        method: "POST",
        body: JSON.stringify(credentials),
        credentials: "include", // Include cookies for device trust
      }
    );
  },

  /**
   * Verifies MFA code for a pending authentication.
   *
   * @param request - The MFA verification request.
   * @returns The verification response on success.
   * @throws ApiError on verification failure.
   */
  async verifyMfa(request: MfaVerifyRequest): Promise<MfaVerifyResponse> {
    return apiRequest<MfaVerifyResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/mfa/verify`,
      {
        method: "POST",
        body: JSON.stringify(request),
        credentials: "include", // Include cookies for device trust
      }
    );
  },

  /**
   * Resends an MFA code (SMS only).
   *
   * @param request - The MFA resend request.
   * @returns The resend response on success.
   * @throws ApiError on resend failure (rate limit, cooldown, etc.).
   */
  async resendMfaCode(request: MfaResendRequest): Promise<MfaResendResponse> {
    return apiRequest<MfaResendResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/mfa/resend`,
      {
        method: "POST",
        body: JSON.stringify(request),
      }
    );
  },

  /**
   * Gets all trusted devices for the authenticated user.
   *
   * @returns List of trusted devices.
   * @throws ApiError on failure (401 if not authenticated).
   */
  async getTrustedDevices(): Promise<DevicesResponse> {
    return apiRequest<DevicesResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/devices`,
      {
        method: "GET",
        credentials: "include", // Include cookies for authentication
      }
    );
  },

  /**
   * Revokes a single trusted device.
   *
   * @param deviceId - The device trust ID to revoke.
   * @throws ApiError on failure (401 if not authenticated, 404 if device not found).
   */
  async revokeDevice(deviceId: string): Promise<void> {
    return apiRequest<void>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/devices/${deviceId}`,
      {
        method: "DELETE",
        credentials: "include", // Include cookies for authentication
      }
    );
  },

  /**
   * Revokes all trusted devices for the authenticated user.
   *
   * @throws ApiError on failure (401 if not authenticated).
   */
  async revokeAllDevices(): Promise<void> {
    return apiRequest<void>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/devices`,
      {
        method: "DELETE",
        credentials: "include", // Include cookies for authentication
      }
    );
  },
};
