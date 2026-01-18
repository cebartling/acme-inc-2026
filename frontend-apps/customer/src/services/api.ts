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
      }
    );
  },
};
