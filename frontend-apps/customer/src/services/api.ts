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
const PRODUCT_SERVICE_URL =
  import.meta.env.VITE_PRODUCT_SERVICE_URL || "http://localhost:10303";

/**
 * Custom error class for API errors with status code and response data.
 */
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public data?: Record<string, unknown>,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

// =============================================================================
// Token-refresh interceptor state (PIN-92 / US-0003-12)
//
// When the identity service rejects a request with 401 + TOKEN_EXPIRED, we
// transparently call POST /api/v1/auth/refresh (which sets new HttpOnly
// auth cookies) and retry the original request. Concurrent expired
// requests must share a single refresh: anything that arrives while a
// refresh is in flight queues onto `refreshWaiters` and retries after the
// refresh resolves.
//
// State lives at module scope because there's no AuthProvider in the
// customer app. The `__resetRefreshStateForTests` export is a deliberate
// test seam — vitest calls it in `beforeEach` so module-level state
// doesn't leak between cases.
// =============================================================================

const REFRESH_URL = `${IDENTITY_SERVICE_URL}/api/v1/auth/refresh`;

let isRefreshing = false;
let refreshWaiters: Array<{
  resolve: () => void;
  reject: (err: unknown) => void;
}> = [];

/**
 * Test-only: reset module-level refresh state between vitest cases. NOT
 * used by application code.
 */
export function __resetRefreshStateForTests(): void {
  isRefreshing = false;
  refreshWaiters = [];
}

interface RefreshAwareRequestInit extends RequestInit {
  /** Internal flag set when apiRequest is retrying after a successful refresh. */
  __isRetry?: boolean;
  /** Internal flag set on the refresh-call itself so it never triggers a nested refresh. */
  __skipRefresh?: boolean;
}

/**
 * Waits in line behind the in-flight refresh. Resolves when the refresh
 * succeeds (caller should re-invoke apiRequest with __isRetry=true) or
 * rejects with the refresh error if it fails.
 */
function waitForRefresh(): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    refreshWaiters.push({ resolve, reject });
  });
}

/**
 * Flushes the waiter queue. Called from both success and failure paths
 * inside the active refresh.
 */
function flushWaiters(error?: unknown): void {
  const waiters = refreshWaiters;
  refreshWaiters = [];
  for (const waiter of waiters) {
    if (error !== undefined) {
      waiter.reject(error);
    } else {
      waiter.resolve();
    }
  }
}

/**
 * Best-effort: clear local auth state and bounce the user to /signin
 * after a refresh failure. Stores and window are imported lazily to
 * avoid a static-import cycle (auth.store imports api types).
 */
async function handleRefreshFailure(): Promise<void> {
  // Dynamic imports avoid a static cycle (stores import API types). The
  // catch branches log a warning rather than swallowing silently — a
  // bundler quirk or future store rename that breaks the import path
  // would otherwise leak stale Zustand state past the redirect and
  // flash an authenticated UI to a signed-out user.
  try {
    const { useAuthStore } = await import("@/stores/auth.store");
    useAuthStore.getState().clearUser();
  } catch (err) {
    console.warn("refresh-cleanup: failed to clear auth store", err);
  }
  try {
    const { useCustomerStore } = await import("@/stores/customer.store");
    useCustomerStore.getState().clearProfile();
  } catch (err) {
    console.warn("refresh-cleanup: failed to clear customer store", err);
  }
  if (typeof window !== "undefined") {
    window.location.href = "/signin?logout=true";
  }
}

/**
 * Makes a fetch request with standard headers and error handling.
 *
 * Intercepts 401 + { error: "TOKEN_EXPIRED" }: single-flights a
 * `POST /api/v1/auth/refresh` and retries the original request once.
 * Concurrent expired requests share the same refresh. On any failure
 * the auth store is cleared and the browser is redirected to
 * `/signin?logout=true`.
 */
async function apiRequest<T>(
  url: string,
  options: RefreshAwareRequestInit = {},
): Promise<T> {
  const { __isRetry, __skipRefresh, ...fetchOptions } = options;
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...fetchOptions.headers,
  };

  const response = await fetch(url, {
    ...fetchOptions,
    headers,
  });

  const contentType = response.headers.get("content-type");
  const isJson = contentType?.includes("application/json");

  if (response.ok) {
    return isJson ? response.json() : (null as T);
  }

  const errorData = isJson ? await response.json() : null;

  // Token-refresh interception: only on 401 + TOKEN_EXPIRED, only when
  // this isn't itself the refresh call, and only once per original
  // request. The path-suffix check is defense-in-depth backing up
  // __skipRefresh — using `endsWith` instead of strict URL equality means
  // the guard survives variations in base URL (proxy paths, trailing
  // slashes) that would otherwise silently regress the loop-prevention
  // invariant.
  if (
    response.status === 401 &&
    errorData?.error === "TOKEN_EXPIRED" &&
    !__skipRefresh &&
    !__isRetry &&
    !url.endsWith("/api/v1/auth/refresh")
  ) {
    if (isRefreshing) {
      // Another request is already refreshing. Queue up; once the
      // refresh resolves we retry our original request.
      await waitForRefresh();
      return apiRequest<T>(url, { ...options, __isRetry: true });
    }

    isRefreshing = true;
    try {
      await apiRequest<unknown>(REFRESH_URL, {
        method: "POST",
        credentials: "include",
        __skipRefresh: true,
      });
      flushWaiters();
      return apiRequest<T>(url, { ...options, __isRetry: true });
    } catch (refreshError) {
      flushWaiters(refreshError);
      await handleRefreshFailure();
      throw refreshError;
    } finally {
      isRefreshing = false;
    }
  }

  throw new ApiError(
    errorData?.error || errorData?.message || `HTTP ${response.status}`,
    response.status,
    errorData,
  );
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
    userId: string,
  ): Promise<PreferencesResponse> {
    return apiRequest<PreferencesResponse>(
      `${CUSTOMER_SERVICE_URL}/api/v1/customers/${customerId}/preferences`,
      {
        method: "GET",
        headers: {
          "X-User-Id": userId,
        },
      },
    );
  },

  /**
   * Updates a customer's preferences.
   */
  async updatePreferences(
    customerId: string,
    userId: string,
    preferences: UpdatePreferencesRequest,
  ): Promise<PreferencesResponse> {
    return apiRequest<PreferencesResponse>(
      `${CUSTOMER_SERVICE_URL}/api/v1/customers/${customerId}/preferences`,
      {
        method: "PUT",
        headers: {
          "X-User-Id": userId,
        },
        body: JSON.stringify(preferences),
      },
    );
  },

  /**
   * Gets a customer's profile completeness breakdown.
   */
  async getProfileCompleteness(
    customerId: string,
  ): Promise<ProfileCompletenessResponse> {
    return apiRequest<ProfileCompletenessResponse>(
      `${CUSTOMER_SERVICE_URL}/api/v1/customers/${customerId}/profile/completeness`,
      {
        method: "GET",
      },
    );
  },

  /**
   * Gets the current authenticated customer's profile.
   *
   * This is called after signin to load the customer profile into the frontend.
   * The backend caches this in Redis with a 5-minute TTL for performance.
   *
   * @returns The customer profile with all fields.
   * @throws ApiError on failure (401 if not authenticated, 404 if customer not found).
   */
  async getCurrentCustomer(): Promise<CustomerProfile> {
    return apiRequest<CustomerProfile>(
      `${CUSTOMER_SERVICE_URL}/api/v1/customers/me`,
      {
        method: "GET",
        credentials: "include", // Include cookies for authentication
      },
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

/**
 * Full customer profile response from /api/v1/customers/me.
 *
 * This is loaded after signin and includes:
 * - Customer identification (ID, number, name, email)
 * - Profile data (date of birth, gender, locale, timezone, currency)
 * - Preferences (communication, privacy, display settings)
 * - Profile completeness percentage
 * - Activity tracking (last activity timestamp)
 */
export interface CustomerProfile {
  customerId: string;
  userId: string;
  customerNumber: string;
  name: {
    firstName: string;
    lastName: string;
    displayName: string;
  };
  email: {
    address: string;
    verified: boolean;
  };
  phone: {
    countryCode: string | null;
    number: string;
    verified: boolean;
  } | null;
  status: string;
  type: string;
  profile: {
    dateOfBirth: string | null;
    gender: string | null;
    preferredLocale: string;
    timezone: string;
    preferredCurrency: string;
  };
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
  profileCompleteness: number;
  registeredAt: string;
  lastActivityAt: string;
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
  supportEmail?: string;
  lockedUntil?: string;
  deactivatedAt?: string;
  reactivationAvailable?: boolean;
  resendAvailableIn?: number;
}

/**
 * Response from the verification-email resend endpoint.
 */
export interface ResendVerificationResponse {
  message: string;
  requestsRemaining?: number;
}

/**
 * Response from the account-reactivation request endpoint.
 */
export interface ReactivateAccountResponse {
  message: string;
}

/**
 * Response from the password-reset request endpoint. Always 200 with a
 * generic message regardless of whether the email maps to an account.
 */
export interface PasswordResetResponse {
  message: string;
}

/**
 * Response from the validate-reset-token endpoint on success.
 */
export interface PasswordResetTokenValidResponse {
  valid: true;
  expiresIn: number;
}

/**
 * Response from the validate-reset-token endpoint when the token is
 * invalid, expired, or already used.
 */
export interface PasswordResetTokenErrorResponse {
  error: "INVALID_RESET_TOKEN";
  message: string;
  requestNewUrl: string;
}

/**
 * Per-rule check returned when a new password does not satisfy the
 * password-strength requirements.
 */
export interface PasswordRequirement {
  rule: string;
  met: boolean;
  detail: string;
}

/**
 * Response from the confirm-password-reset endpoint on success.
 */
export interface PasswordResetConfirmResponse {
  message: string;
  sessionsInvalidated: number;
  deviceTrustsRevoked: number;
}

/**
 * Response from the confirm-password-reset endpoint when the submitted
 * password fails one or more requirement checks.
 */
export interface PasswordRequirementsErrorResponse {
  error: "PASSWORD_REQUIREMENTS_NOT_MET";
  message: string;
  requirements: PasswordRequirement[];
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
 * Response type for single-session logout.
 */
export interface LogoutResponse {
  status: "SUCCESS";
  message: string;
}

/**
 * Response type for logout-all-devices.
 */
export interface LogoutAllResponse {
  status: "SUCCESS";
  message: string;
  sessionsInvalidated: number;
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
      },
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
      },
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
      },
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
      },
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
      },
    );
  },

  /**
   * Revokes all trusted devices for the authenticated user.
   *
   * @throws ApiError on failure (401 if not authenticated).
   */
  async revokeAllDevices(): Promise<void> {
    return apiRequest<void>(`${IDENTITY_SERVICE_URL}/api/v1/auth/devices`, {
      method: "DELETE",
      credentials: "include", // Include cookies for authentication
    });
  },

  /**
   * Signs the current session out.
   *
   * The server clears all auth cookies in the response regardless of token
   * validity, so this call is safe to make even when not authenticated.
   */
  async logout(): Promise<LogoutResponse> {
    return apiRequest<LogoutResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/logout`,
      {
        method: "POST",
        credentials: "include",
      },
    );
  },

  /**
   * Signs the user out of every active session.
   *
   * Requires a valid access_token cookie.
   *
   * @throws ApiError 401 if not authenticated.
   */
  async logoutAll(): Promise<LogoutAllResponse> {
    return apiRequest<LogoutAllResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/logout/all`,
      {
        method: "POST",
        credentials: "include",
      },
    );
  },

  /**
   * Resends the verification email for a customer with a PENDING_VERIFICATION
   * account. The backend rate-limits resends; success is reported uniformly
   * regardless of whether the email maps to a real account, to prevent
   * enumeration.
   */
  async resendVerification(email: string): Promise<ResendVerificationResponse> {
    return apiRequest<ResendVerificationResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/users/verify/resend`,
      {
        method: "POST",
        body: JSON.stringify({ email }),
      },
    );
  },

  /**
   * Requests reactivation of a deactivated account.
   *
   * The backend always returns 200 with a generic message regardless of
   * whether the email is known or whether the password is correct — this
   * is intentional to prevent enumeration. When everything lines up, the
   * customer receives a reactivation email.
   */
  async reactivateAccount(
    email: string,
    password: string,
  ): Promise<ReactivateAccountResponse> {
    return apiRequest<ReactivateAccountResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/reactivate`,
      {
        method: "POST",
        body: JSON.stringify({ email, password }),
      },
    );
  },

  /**
   * Initiates a password reset for the given email. Always resolves to
   * the same generic message regardless of whether the email is known —
   * the backend rate-limits to 3/hour per email and returns 200 in
   * either case to prevent enumeration.
   */
  async requestPasswordReset(email: string): Promise<PasswordResetResponse> {
    return apiRequest<PasswordResetResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/password-reset`,
      {
        method: "POST",
        body: JSON.stringify({ email }),
      },
    );
  },

  /**
   * Validates a password-reset token without consuming it. Rejects (via
   * ApiError) if the token is invalid, expired, or already used.
   */
  async validatePasswordResetToken(
    token: string,
  ): Promise<PasswordResetTokenValidResponse> {
    return apiRequest<PasswordResetTokenValidResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/password-reset/${encodeURIComponent(token)}`,
      { method: "GET" },
    );
  },

  /**
   * Completes a password reset. On success the account's sessions and
   * device trusts are all invalidated; the response payload reports
   * how many of each were affected.
   */
  async confirmPasswordReset(
    token: string,
    newPassword: string,
  ): Promise<PasswordResetConfirmResponse> {
    return apiRequest<PasswordResetConfirmResponse>(
      `${IDENTITY_SERVICE_URL}/api/v1/auth/password-reset/confirm`,
      {
        method: "POST",
        body: JSON.stringify({ token, newPassword }),
      },
    );
  },
};

// ---------------------------------------------------------------------------
// Product Service types
// ---------------------------------------------------------------------------

export interface ProductSummary {
  id: string;
  slug: string;
  name: string;
  price: number;
  category: string | null;
}

export interface SearchFilters {
  categories?: string[];
  priceMin?: number;
  priceMax?: number;
}

export interface SearchRequest {
  query: string;
  page: number;
  pageSize: number;
  sort: "relevance" | "price_asc" | "price_desc" | "newest";
  filters: SearchFilters;
}

export interface SearchFacets {
  categories: Record<string, number>;
}

export interface SearchResponse {
  query: string;
  totalResults: number;
  page: number;
  pageSize: number;
  totalPages: number;
  results: ProductSummary[];
  facets: SearchFacets;
  spellingSuggestion: string | null;
  executionTimeMs: number;
}

export interface AutocompleteSuggestion {
  type: "product" | "category" | "query";
  text: string;
  productId?: string;
  productSlug?: string;
  imageUrl?: string | null;
  categorySlug?: string;
}

export interface AutocompleteResponse {
  query: string;
  suggestions: AutocompleteSuggestion[];
}

export const productApi = {
  async search(request: SearchRequest): Promise<SearchResponse> {
    return apiRequest<SearchResponse>(`${PRODUCT_SERVICE_URL}/api/v1/search`, {
      method: "POST",
      body: JSON.stringify(request),
      credentials: "include",
    });
  },

  async autocomplete(query: string, limit = 8): Promise<AutocompleteResponse> {
    return apiRequest<AutocompleteResponse>(
      `${PRODUCT_SERVICE_URL}/api/v1/search/autocomplete?q=${encodeURIComponent(query)}&limit=${limit}`,
      {
        method: "GET",
        credentials: "include",
      },
    );
  },
};
