/**
 * API service for making requests to backend services.
 *
 * This provides a centralized place for API configuration and error handling.
 */

// API base URLs - these would typically come from environment variables
const CUSTOMER_SERVICE_URL =
  import.meta.env.VITE_CUSTOMER_SERVICE_URL || "http://localhost:8080";

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
