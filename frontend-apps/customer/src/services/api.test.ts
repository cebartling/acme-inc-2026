import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  ApiError,
  TimeoutError,
  cartApi,
  categoryApi,
  customerApi,
  identityApi,
  inventoryApi,
  orderApi,
  productApi,
  __resetRefreshStateForTests,
} from "./api";
import {
  CircuitOpenError,
  searchCircuitBreaker,
  __resetCircuitBreakerForTests,
} from "@/lib/searchCircuitBreaker";

/** Settles only when aborted, standing in for a service that hangs. */
function rejectOnAbort(signal: AbortSignal | null | undefined) {
  return new Promise<never>((_resolve, reject) => {
    signal?.addEventListener("abort", () => {
      const abortError = new Error("Aborted");
      abortError.name = "AbortError";
      reject(abortError);
    });
  });
}

/**
 * Stands in for what a failed refresh touches: the auth and customer stores (which api.ts
 * imports lazily) and window.location, so a redirect is observed without navigating. Call
 * `restore` from a `finally`, so a failed assertion doesn't leak the stubs into later cases.
 */
function stubRefreshFailureEffects({ pathname = "/", search = "" } = {}) {
  const originalLocation = window.location;
  let assignedHref: string | null = null;
  Object.defineProperty(window, "location", {
    configurable: true,
    value: {
      ...originalLocation,
      pathname,
      search,
      set href(value: string) {
        assignedHref = value;
      },
      get href() {
        return assignedHref ?? originalLocation.href;
      },
    },
  });
  const clearUser = vi.fn();
  const clearProfile = vi.fn();
  vi.doMock("@/stores/auth.store", () => ({
    useAuthStore: { getState: () => ({ clearUser }) },
  }));
  vi.doMock("@/stores/customer.store", () => ({
    useCustomerStore: { getState: () => ({ clearProfile }) },
  }));

  return {
    clearUser,
    clearProfile,
    assignedHref: () => assignedHref,
    restore() {
      Object.defineProperty(window, "location", {
        configurable: true,
        value: originalLocation,
      });
      vi.doUnmock("@/stores/auth.store");
      vi.doUnmock("@/stores/customer.store");
    },
  };
}

describe("ApiError", () => {
  it("creates error with message and status", () => {
    const error = new ApiError("Not found", 404);
    expect(error.message).toBe("Not found");
    expect(error.status).toBe(404);
    expect(error.name).toBe("ApiError");
  });

  it("creates error with optional data", () => {
    const data = { field: "email", code: "INVALID" };
    const error = new ApiError("Validation failed", 400, data);
    expect(error.data).toEqual(data);
  });

  it("is instance of Error", () => {
    const error = new ApiError("Error", 500);
    expect(error).toBeInstanceOf(Error);
  });
});

describe("customerApi", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  describe("getPreferences", () => {
    const customerId = "customer-123";

    it("makes GET request with correct URL", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ "content-type": "application/json" }),
        json: () =>
          Promise.resolve({
            customerId,
            preferences: {
              communication: {
                email: true,
                sms: false,
                push: false,
                marketing: false,
                frequency: "IMMEDIATE",
              },
              privacy: {
                shareDataWithPartners: false,
                allowAnalytics: true,
                allowPersonalization: true,
              },
              display: { language: "en-US", currency: "USD", timezone: "UTC" },
            },
            updatedAt: "2024-01-15T10:00:00Z",
          }),
      });

      await customerApi.getPreferences(customerId);

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining(`/api/v1/customers/${customerId}/preferences`),
        expect.objectContaining({
          method: "GET",
          credentials: "include",
        }),
      );
    });

    it("returns preferences response on success", async () => {
      const expectedResponse = {
        customerId,
        preferences: {
          communication: {
            email: true,
            sms: false,
            push: false,
            marketing: false,
            frequency: "IMMEDIATE",
          },
          privacy: {
            shareDataWithPartners: false,
            allowAnalytics: true,
            allowPersonalization: true,
          },
          display: { language: "en-US", currency: "USD", timezone: "UTC" },
        },
        updatedAt: "2024-01-15T10:00:00Z",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve(expectedResponse),
      });

      const result = await customerApi.getPreferences(customerId);
      expect(result).toEqual(expectedResponse);
    });

    it("throws ApiError on non-OK response", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ error: "Customer not found" }),
      });

      await expect(customerApi.getPreferences(customerId)).rejects.toThrow(
        ApiError,
      );
    });

    it("throws ApiError with correct status code", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ error: "Forbidden" }),
      });

      try {
        await customerApi.getPreferences(customerId);
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).status).toBe(403);
      }
    });
  });

  describe("getCurrentCustomer", () => {
    it("makes GET request to /me endpoint", async () => {
      const mockProfile = {
        customerId: "customer-123",
        userId: "user-456",
        customerNumber: "ACME-202601-000001",
        name: {
          firstName: "John",
          lastName: "Doe",
          displayName: "John Doe",
        },
        email: {
          address: "john.doe@example.com",
          verified: true,
        },
        phone: null,
        status: "ACTIVE",
        type: "INDIVIDUAL",
        profile: {
          dateOfBirth: null,
          gender: null,
          preferredLocale: "en-US",
          timezone: "UTC",
          preferredCurrency: "USD",
        },
        preferences: {
          communication: {
            email: true,
            sms: false,
            push: false,
            marketing: false,
            frequency: "IMMEDIATE",
          },
          privacy: {
            shareDataWithPartners: false,
            allowAnalytics: true,
            allowPersonalization: true,
          },
          display: {
            language: "en-US",
            currency: "USD",
            timezone: "UTC",
          },
        },
        profileCompleteness: 25,
        registeredAt: "2024-01-15T10:00:00Z",
        lastActivityAt: "2024-01-15T10:00:00Z",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve(mockProfile),
      });

      await customerApi.getCurrentCustomer();

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/v1/customers/me"),
        expect.objectContaining({
          method: "GET",
          credentials: "include",
        }),
      );
    });

    it("returns customer profile on success", async () => {
      const mockProfile = {
        customerId: "customer-123",
        userId: "user-456",
        customerNumber: "ACME-202601-000001",
        name: {
          firstName: "Jane",
          lastName: "Smith",
          displayName: "Jane Smith",
        },
        email: {
          address: "jane.smith@example.com",
          verified: true,
        },
        phone: {
          countryCode: "+1",
          number: "5551234567",
          verified: true,
        },
        status: "ACTIVE",
        type: "INDIVIDUAL",
        profile: {
          dateOfBirth: "1990-05-15",
          gender: "female",
          preferredLocale: "en-US",
          timezone: "America/New_York",
          preferredCurrency: "USD",
        },
        preferences: {
          communication: {
            email: true,
            sms: true,
            push: false,
            marketing: true,
            frequency: "DAILY_DIGEST",
          },
          privacy: {
            shareDataWithPartners: false,
            allowAnalytics: true,
            allowPersonalization: true,
          },
          display: {
            language: "es-ES",
            currency: "EUR",
            timezone: "Europe/Madrid",
          },
        },
        profileCompleteness: 75,
        registeredAt: "2024-01-10T08:00:00Z",
        lastActivityAt: "2024-01-15T14:30:00Z",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve(mockProfile),
      });

      const result = await customerApi.getCurrentCustomer();

      expect(result).toEqual(mockProfile);
      expect(result.customerId).toBe("customer-123");
      expect(result.name.displayName).toBe("Jane Smith");
      expect(result.profileCompleteness).toBe(75);
    });

    it("includes credentials for authentication cookies", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({}),
      });

      await customerApi.getCurrentCustomer();

      expect(mockFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          credentials: "include",
        }),
      );
    });

    it("throws ApiError when not authenticated (401)", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: new Headers({ "content-type": "application/json" }),
        json: () =>
          Promise.resolve({
            error: "UNAUTHORIZED",
            message: "Not authenticated",
          }),
      });

      try {
        await customerApi.getCurrentCustomer();
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).status).toBe(401);
        expect((error as ApiError).message).toBe("UNAUTHORIZED");
      }
    });

    it("throws ApiError when customer not found (404)", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        headers: new Headers({ "content-type": "application/json" }),
        json: () =>
          Promise.resolve({
            error: "CUSTOMER_NOT_FOUND",
            message: "Customer profile not found for this user",
          }),
      });

      await expect(customerApi.getCurrentCustomer()).rejects.toThrow(ApiError);
    });

    it("includes all required profile fields in response", async () => {
      const completeProfile = {
        customerId: "customer-123",
        userId: "user-456",
        customerNumber: "ACME-202601-000001",
        name: {
          firstName: "John",
          lastName: "Doe",
          displayName: "John Doe",
        },
        email: {
          address: "john.doe@example.com",
          verified: true,
        },
        phone: {
          countryCode: "+1",
          number: "5551234567",
          verified: true,
        },
        status: "ACTIVE",
        type: "INDIVIDUAL",
        profile: {
          dateOfBirth: "1990-05-15",
          gender: "male",
          preferredLocale: "en-US",
          timezone: "America/New_York",
          preferredCurrency: "USD",
        },
        preferences: {
          communication: {
            email: true,
            sms: false,
            push: false,
            marketing: false,
            frequency: "IMMEDIATE",
          },
          privacy: {
            shareDataWithPartners: false,
            allowAnalytics: true,
            allowPersonalization: true,
          },
          display: {
            language: "en-US",
            currency: "USD",
            timezone: "UTC",
          },
        },
        profileCompleteness: 75,
        registeredAt: "2024-01-15T10:00:00Z",
        lastActivityAt: "2024-01-15T14:30:00Z",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve(completeProfile),
      });

      const result = await customerApi.getCurrentCustomer();

      // Verify all top-level fields are present
      expect(result).toHaveProperty("customerId");
      expect(result).toHaveProperty("userId");
      expect(result).toHaveProperty("customerNumber");
      expect(result).toHaveProperty("name");
      expect(result).toHaveProperty("email");
      expect(result).toHaveProperty("phone");
      expect(result).toHaveProperty("status");
      expect(result).toHaveProperty("type");
      expect(result).toHaveProperty("profile");
      expect(result).toHaveProperty("preferences");
      expect(result).toHaveProperty("profileCompleteness");
      expect(result).toHaveProperty("registeredAt");
      expect(result).toHaveProperty("lastActivityAt");

      // Verify nested structures
      expect(result.name).toHaveProperty("firstName");
      expect(result.name).toHaveProperty("lastName");
      expect(result.name).toHaveProperty("displayName");
      expect(result.email).toHaveProperty("address");
      expect(result.email).toHaveProperty("verified");
      expect(result.preferences).toHaveProperty("communication");
      expect(result.preferences).toHaveProperty("privacy");
      expect(result.preferences).toHaveProperty("display");
    });
  });

  describe("updatePreferences", () => {
    const customerId = "customer-123";
    const preferences = {
      communication: { email: false },
      privacy: { allowAnalytics: false },
    };

    it("makes PUT request with correct URL and body", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ "content-type": "application/json" }),
        json: () =>
          Promise.resolve({
            customerId,
            preferences: {
              communication: {
                email: false,
                sms: false,
                push: false,
                marketing: false,
                frequency: "IMMEDIATE",
              },
              privacy: {
                shareDataWithPartners: false,
                allowAnalytics: false,
                allowPersonalization: true,
              },
              display: { language: "en-US", currency: "USD", timezone: "UTC" },
            },
            updatedAt: "2024-01-15T10:00:00Z",
          }),
      });

      await customerApi.updatePreferences(customerId, preferences);

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining(`/api/v1/customers/${customerId}/preferences`),
        expect.objectContaining({
          method: "PUT",
          credentials: "include",
          body: JSON.stringify(preferences),
        }),
      );
    });

    it("returns updated preferences on success", async () => {
      const expectedResponse = {
        customerId,
        preferences: {
          communication: {
            email: false,
            sms: true,
            push: false,
            marketing: false,
            frequency: "DAILY_DIGEST",
          },
          privacy: {
            shareDataWithPartners: false,
            allowAnalytics: false,
            allowPersonalization: true,
          },
          display: {
            language: "es-ES",
            currency: "EUR",
            timezone: "Europe/Madrid",
          },
        },
        updatedAt: "2024-01-15T11:00:00Z",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve(expectedResponse),
      });

      const result = await customerApi.updatePreferences(
        customerId,
        preferences,
      );
      expect(result).toEqual(expectedResponse);
    });

    it("throws ApiError on validation failure", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        headers: new Headers({ "content-type": "application/json" }),
        json: () =>
          Promise.resolve({
            error: "VALIDATION_ERROR",
            message: "Invalid frequency value",
          }),
      });

      await expect(
        customerApi.updatePreferences(customerId, preferences),
      ).rejects.toThrow(ApiError);
    });

    it("throws ApiError on unauthorized access", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        headers: new Headers({ "content-type": "application/json" }),
        json: () =>
          Promise.resolve({
            error: "FORBIDDEN",
            message: "Not authorized to update this customer",
          }),
      });

      try {
        await customerApi.updatePreferences(customerId, preferences);
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).status).toBe(403);
        expect((error as ApiError).message).toBe("FORBIDDEN");
      }
    });
  });

  describe("error handling", () => {
    it("handles non-JSON error responses", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        headers: new Headers({ "content-type": "text/plain" }),
        json: () => Promise.reject(new Error("Not JSON")),
      });

      try {
        await customerApi.getPreferences("customer-123");
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).status).toBe(500);
        expect((error as ApiError).message).toBe("HTTP 500");
      }
    });

    it("uses error field from response for message", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ error: "INVALID_INPUT" }),
      });

      try {
        await customerApi.getPreferences("customer-123");
      } catch (error) {
        expect((error as ApiError).message).toBe("INVALID_INPUT");
      }
    });

    it("uses message field from response if error field is missing", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ message: "Something went wrong" }),
      });

      try {
        await customerApi.getPreferences("customer-123");
      } catch (error) {
        expect((error as ApiError).message).toBe("Something went wrong");
      }
    });

    it("includes response data in ApiError", async () => {
      const errorData = {
        error: "VALIDATION_ERROR",
        fields: { email: "Invalid format" },
      };

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve(errorData),
      });

      try {
        await customerApi.getPreferences("customer-123");
      } catch (error) {
        expect((error as ApiError).data).toEqual(errorData);
      }
    });
  });
});

// =============================================================================
// Token-refresh interceptor (PIN-92 / US-0003-12)
//
// apiRequest intercepts 401 + { error: "TOKEN_EXPIRED" }, calls
// POST /api/v1/auth/refresh, and re-tries the original request on success.
// These tests pin the contract down so the queue behavior, retry, and
// failure-path cleanup can't silently regress.
// =============================================================================

describe("token-refresh interceptor", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
    __resetRefreshStateForTests();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  // Helpers to keep the response-shape boilerplate out of the cases.
  const okJson = (body: unknown) => ({
    ok: true,
    status: 200,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve(body),
  });
  const errorJson = (status: number, body: unknown) => ({
    ok: false,
    status,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve(body),
  });
  const tokenExpired = () =>
    errorJson(401, {
      error: "TOKEN_EXPIRED",
      message: "Session expired. Please sign in again.",
    });

  it("on a single 401 TOKEN_EXPIRED, calls /refresh exactly once and retries the original request", async () => {
    const profile = { customerId: "customer-123", userId: "user-456" };

    // 1) original request → 401 TOKEN_EXPIRED
    // 2) /refresh → 200
    // 3) original request retry → 200
    mockFetch
      .mockResolvedValueOnce(tokenExpired())
      .mockResolvedValueOnce(okJson({ status: "SUCCESS", expiresIn: 900 }))
      .mockResolvedValueOnce(okJson(profile));

    const result = await customerApi.getCurrentCustomer();

    expect(result).toEqual(profile);
    expect(mockFetch).toHaveBeenCalledTimes(3);
    // The middle call hits the refresh endpoint.
    const [secondUrl, secondInit] = mockFetch.mock.calls[1];
    expect(secondUrl).toContain("/api/v1/auth/refresh");
    expect(secondInit).toMatchObject({
      method: "POST",
      credentials: "include",
    });
  });

  it("on a 401 INVALID_TOKEN, refreshes and retries, since the cookie can outlive its signing key (PIN-443)", async () => {
    const profile = { customerId: "customer-123", userId: "user-456" };
    mockFetch
      .mockResolvedValueOnce(
        errorJson(401, { error: "INVALID_TOKEN", code: "INVALID_TOKEN" }),
      )
      .mockResolvedValueOnce(okJson({ status: "SUCCESS", expiresIn: 900 }))
      .mockResolvedValueOnce(okJson(profile));

    await expect(customerApi.getCurrentCustomer()).resolves.toEqual(profile);

    expect(mockFetch).toHaveBeenCalledTimes(3);
    expect(mockFetch.mock.calls[1][0]).toContain("/api/v1/auth/refresh");
  });

  it("concurrent 401s share a single refresh, then each retries with the new tokens", async () => {
    const prefs = { customerId: "c", preferences: {}, updatedAt: "x" };
    const profile = { customerId: "c", userId: "u" };

    // Sequence (order matters because mockResolvedValueOnce is FIFO):
    //   1) first caller's request → 401
    //   2) second caller's request → 401
    //   3) /refresh (only the first caller fires this) → 200
    //   4) first caller retry → 200
    //   5) second caller retry → 200
    mockFetch
      .mockResolvedValueOnce(tokenExpired())
      .mockResolvedValueOnce(tokenExpired())
      .mockResolvedValueOnce(okJson({ status: "SUCCESS", expiresIn: 900 }))
      .mockResolvedValueOnce(okJson(profile))
      .mockResolvedValueOnce(okJson(prefs));

    const [profileResult, prefsResult] = await Promise.all([
      customerApi.getCurrentCustomer(),
      customerApi.getPreferences("c"),
    ]);

    expect(profileResult).toEqual(profile);
    expect(prefsResult).toEqual(prefs);

    // 2 initial 401s + 1 refresh + 2 retries = 5 fetch calls.
    expect(mockFetch).toHaveBeenCalledTimes(5);

    // Exactly one refresh call.
    const refreshCalls = mockFetch.mock.calls.filter((args) =>
      String(args[0]).includes("/api/v1/auth/refresh"),
    );
    expect(refreshCalls.length).toBe(1);
  });

  it("refresh failure clears auth stores, redirects to /signin?logout=true, and rejects the original request", async () => {
    const effects = stubRefreshFailureEffects();
    try {
      // 1) original request → 401
      // 2) /refresh → 401 TOKEN_EXPIRED (refresh itself rejected)
      mockFetch
        .mockResolvedValueOnce(tokenExpired())
        .mockResolvedValueOnce(tokenExpired());

      await expect(customerApi.getCurrentCustomer()).rejects.toBeInstanceOf(
        ApiError,
      );

      expect(effects.clearUser).toHaveBeenCalledOnce();
      expect(effects.clearProfile).toHaveBeenCalledOnce();
      expect(effects.assignedHref()).toBe("/signin?logout=true");
    } finally {
      effects.restore();
    }
  });

  it.each([
    ["/orders", "?page=3", "/signin?logout=true&redirect=%2Forders%3Fpage%3D3"],
    ["/cart", "", "/signin?logout=true&redirect=%2Fcart"],
  ])(
    "a refused refresh on %s%s comes back after sign-in",
    async (pathname, search, expected) => {
      const effects = stubRefreshFailureEffects({ pathname, search });
      try {
        mockFetch
          .mockResolvedValueOnce(tokenExpired())
          .mockResolvedValueOnce(tokenExpired());

        await expect(customerApi.getCurrentCustomer()).rejects.toBeInstanceOf(
          ApiError,
        );

        expect(effects.assignedHref()).toBe(expected);
      } finally {
        effects.restore();
      }
    },
  );

  it("a refused refresh still redirects when a page watching the stores left first", async () => {
    const effects = stubRefreshFailureEffects({ pathname: "/dashboard" });
    effects.clearUser.mockImplementation(() => {
      window.location.pathname = "/signin";
    });
    try {
      mockFetch
        .mockResolvedValueOnce(tokenExpired())
        .mockResolvedValueOnce(tokenExpired());

      await expect(customerApi.getCurrentCustomer()).rejects.toBeInstanceOf(
        ApiError,
      );

      expect(effects.assignedHref()).toBe(
        "/signin?logout=true&redirect=%2Fdashboard",
      );
    } finally {
      effects.restore();
    }
  });

  it.each([
    ["/signin", "?logout=true&redirect=%2Fcart"],
    ["/reset-password", "?token=secret"],
    ["/register", ""],
    ["/mfa-verify", ""],
  ])("a refused refresh on %s%s stays there", async (pathname, search) => {
    const effects = stubRefreshFailureEffects({ pathname, search });
    try {
      mockFetch
        .mockResolvedValueOnce(tokenExpired())
        .mockResolvedValueOnce(tokenExpired());

      await expect(customerApi.getCurrentCustomer()).rejects.toBeInstanceOf(
        ApiError,
      );

      expect(effects.clearUser).toHaveBeenCalledOnce();
      expect(effects.assignedHref()).toBeNull();
    } finally {
      effects.restore();
    }
  });

  it("holds a Web Lock across the refresh, so tabs can't refresh at once (PIN-443)", async () => {
    const refreshCalls = () =>
      mockFetch.mock.calls.filter(([url]) =>
        String(url).endsWith("/api/v1/auth/refresh"),
      ).length;
    let refreshCallsBeforeLock = -1;
    const request = vi.fn(
      async (_name: string, callback: () => Promise<unknown>) => {
        refreshCallsBeforeLock = refreshCalls();
        return callback();
      },
    );
    vi.stubGlobal("navigator", { ...navigator, locks: { request } });

    try {
      mockFetch
        .mockResolvedValueOnce(tokenExpired())
        .mockResolvedValueOnce(okJson({ status: "SUCCESS" }))
        .mockResolvedValueOnce(okJson({ id: "customer-1" }));

      await customerApi.getCurrentCustomer();

      expect(request).toHaveBeenCalledOnce();
      expect(request.mock.calls[0][0]).toBe("acme.auth.refresh");
      expect(refreshCallsBeforeLock).toBe(0);
      expect(refreshCalls()).toBe(1);
      expect(mockFetch).toHaveBeenCalledTimes(3);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("401 with any other error code does not trigger refresh", async () => {
    mockFetch.mockResolvedValueOnce(
      errorJson(401, { error: "UNAUTHORIZED", message: "Not authenticated" }),
    );

    await expect(customerApi.getCurrentCustomer()).rejects.toBeInstanceOf(
      ApiError,
    );

    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it("does not loop if the retry itself comes back 401 TOKEN_EXPIRED", async () => {
    // 1) original → 401
    // 2) /refresh → 200
    // 3) retry → 401 (still expired somehow)
    mockFetch
      .mockResolvedValueOnce(tokenExpired())
      .mockResolvedValueOnce(okJson({ status: "SUCCESS", expiresIn: 900 }))
      .mockResolvedValueOnce(tokenExpired());

    await expect(customerApi.getCurrentCustomer()).rejects.toBeInstanceOf(
      ApiError,
    );

    // Exactly 3 calls — no second refresh attempt.
    expect(mockFetch).toHaveBeenCalledTimes(3);
  });

  it("a parallel non-401 request resolves immediately without queueing on the refresh", async () => {
    // Locks the short-circuit invariant: when one request triggers a
    // refresh, another concurrent request that happens to return 200
    // must NOT be held up waiting for the refresh to complete. The
    // current interceptor handles this because the 200 path returns
    // before reaching the 401-detection branch — without a test, a
    // future re-ordering of the queueing logic could silently regress.
    const profile = { customerId: "c", userId: "u" };
    const prefs = { customerId: "c", preferences: {}, updatedAt: "x" };

    // Sequence:
    //   1) first caller's request → 401 (triggers refresh)
    //   2) second caller's request → 200 (short-circuits, no queue)
    //   3) /refresh → 200
    //   4) first caller retry → 200
    mockFetch
      .mockResolvedValueOnce(tokenExpired())
      .mockResolvedValueOnce(okJson(prefs))
      .mockResolvedValueOnce(okJson({ status: "SUCCESS", expiresIn: 900 }))
      .mockResolvedValueOnce(okJson(profile));

    const [profileResult, prefsResult] = await Promise.all([
      customerApi.getCurrentCustomer(),
      customerApi.getPreferences("c"),
    ]);

    expect(profileResult).toEqual(profile);
    expect(prefsResult).toEqual(prefs);
    // 1 initial 401 + 1 short-circuited 200 + 1 refresh + 1 retry = 4 calls.
    expect(mockFetch).toHaveBeenCalledTimes(4);

    // Exactly one /refresh call (no second one piggybacked by the 200 call).
    const refreshCalls = mockFetch.mock.calls.filter((args) =>
      String(args[0]).includes("/api/v1/auth/refresh"),
    );
    expect(refreshCalls.length).toBe(1);
  });
});

describe("identityApi password reset", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
    __resetRefreshStateForTests();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  const okJson = (body: unknown) => ({
    ok: true,
    status: 200,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve(body),
  });

  it("requestPasswordReset POSTs the email to /api/v1/auth/password-reset", async () => {
    mockFetch.mockResolvedValueOnce(
      okJson({ message: "If an account exists, …" }),
    );

    const result = await identityApi.requestPasswordReset("user@example.com");

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/auth/password-reset"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ email: "user@example.com" }),
      }),
    );
    expect(result.message).toMatch(/If an account exists/);
  });

  it("validatePasswordResetToken GETs the token-scoped URL and URL-encodes it", async () => {
    mockFetch.mockResolvedValueOnce(okJson({ valid: true, expiresIn: 3540 }));

    const result = await identityApi.validatePasswordResetToken("rst_abc def");

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/auth/password-reset/rst_abc%20def"),
      expect.objectContaining({ method: "GET" }),
    );
    expect(result).toEqual({ valid: true, expiresIn: 3540 });
  });

  it("confirmPasswordReset POSTs token and newPassword to /confirm", async () => {
    mockFetch.mockResolvedValueOnce(
      okJson({
        message: "Your password has been updated.",
        sessionsInvalidated: 2,
        deviceTrustsRevoked: 1,
      }),
    );

    const result = await identityApi.confirmPasswordReset(
      "rst_token",
      "NewSecureP@ss123",
    );

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/auth/password-reset/confirm"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          token: "rst_token",
          newPassword: "NewSecureP@ss123",
        }),
      }),
    );
    expect(result.sessionsInvalidated).toBe(2);
    expect(result.deviceTrustsRevoked).toBe(1);
  });

  it("confirmPasswordReset throws ApiError on PASSWORD_REQUIREMENTS_NOT_MET", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 400,
      headers: new Headers({ "content-type": "application/json" }),
      json: () =>
        Promise.resolve({
          error: "PASSWORD_REQUIREMENTS_NOT_MET",
          message: "Password does not meet requirements",
          requirements: [
            { rule: "MIN_LENGTH", met: true, detail: "At least 8 characters" },
            {
              rule: "UPPERCASE",
              met: false,
              detail: "At least one uppercase letter",
            },
          ],
        }),
    });

    await expect(
      identityApi.confirmPasswordReset("rst_token", "weak"),
    ).rejects.toBeInstanceOf(ApiError);
  });
});

describe("api.search", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
    __resetRefreshStateForTests();
    // productApi.search runs under a module-scoped breaker; without this, failures
    // from one case leak into the next and eventually trip the circuit.
    __resetCircuitBreakerForTests();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  const okJson = (body: unknown) => ({
    ok: true,
    status: 200,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve(body),
  });

  it("POSTs the search request to /api/v1/search", async () => {
    const searchResponse = {
      query: "widget",
      results: [],
      totalResults: 0,
      page: 1,
      pageSize: 24,
      totalPages: 0,
      spellingSuggestion: null,
      executionTimeMs: 5,
    };
    mockFetch.mockResolvedValueOnce(okJson(searchResponse));

    const request = {
      query: "widget",
      page: 1,
      pageSize: 24,
      sort: "relevance" as const,
      filters: {},
    };
    const result = await productApi.search(request);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/search"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(request),
      }),
    );
    expect(result.totalResults).toBe(0);
  });

  it("returns a typed SearchResponse with results", async () => {
    const product = {
      id: "prod-1",
      slug: "widget-pro",
      name: "Widget Pro",
      price: 29.99,
      category: "Widgets",
    };
    const searchResponse = {
      query: "widget",
      results: [product],
      totalResults: 1,
      page: 1,
      pageSize: 24,
      totalPages: 1,
      executionTimeMs: 12,
      spellingSuggestion: null,
    };
    mockFetch.mockResolvedValueOnce(okJson(searchResponse));

    const result = await productApi.search({
      query: "widget",
      page: 1,
      pageSize: 24,
      sort: "relevance" as const,
      filters: {},
    });
    expect(result.results).toHaveLength(1);
    expect(result.results[0].name).toBe("Widget Pro");
  });

  it("throws ApiError on non-OK response", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 500,
      headers: new Headers({ "content-type": "application/json" }),
      json: () => Promise.resolve({ message: "Internal Server Error" }),
    });

    await expect(
      productApi.search({
        query: "widget",
        page: 1,
        pageSize: 24,
        sort: "relevance" as const,
        filters: {},
      }),
    ).rejects.toBeInstanceOf(ApiError);
  });
});

describe("search circuit breaker integration", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
    __resetRefreshStateForTests();
    __resetCircuitBreakerForTests();
  });

  afterEach(() => {
    global.fetch = originalFetch;
    vi.useRealTimers();
  });

  const request = {
    query: "widget",
    page: 1,
    pageSize: 24,
    sort: "relevance" as const,
    filters: {},
  };

  const serverError = () => ({
    ok: false,
    status: 503,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve({ message: "Service Unavailable" }),
  });

  const search = () => productApi.search(request);

  async function failSearch(times: number) {
    for (let i = 0; i < times; i++) {
      mockFetch.mockResolvedValueOnce(serverError());
      await expect(search()).rejects.toBeInstanceOf(ApiError);
    }
  }

  it("sends the search request with a 2s timeout", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: () => Promise.resolve({ query: "widget", results: [] }),
    });

    await search();

    // The deadline is enforced via AbortController, so the signal is the observable part.
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/search"),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
  });

  it("opens the circuit after five consecutive 5xx responses", async () => {
    await failSearch(5);

    expect(searchCircuitBreaker.getState()).toBe("OPEN");
  });

  it("stays closed after four consecutive failures", async () => {
    await failSearch(4);

    expect(searchCircuitBreaker.getState()).toBe("CLOSED");
  });

  it("bypasses the network once the circuit is open", async () => {
    await failSearch(5);
    const callsBefore = mockFetch.mock.calls.length;

    await expect(search()).rejects.toBeInstanceOf(CircuitOpenError);

    expect(mockFetch).toHaveBeenCalledTimes(callsBefore);
  });

  it("does not count a 4xx toward opening the circuit", async () => {
    for (let i = 0; i < 6; i++) {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 400,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ message: "Bad Request" }),
      });
      await expect(search()).rejects.toBeInstanceOf(ApiError);
    }

    // A malformed request says nothing about the service's health.
    expect(searchCircuitBreaker.getState()).toBe("CLOSED");
    expect(searchCircuitBreaker.getFailureCount()).toBe(0);
  });

  it("counts a network-level rejection as a service failure", async () => {
    for (let i = 0; i < 5; i++) {
      mockFetch.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      await expect(search()).rejects.toBeInstanceOf(TypeError);
    }

    expect(searchCircuitBreaker.getState()).toBe("OPEN");
  });

  it("resets the failure count after a successful search", async () => {
    await failSearch(4);

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: () => Promise.resolve({ query: "widget", results: [] }),
    });
    await search();

    expect(searchCircuitBreaker.getFailureCount()).toBe(0);
    expect(searchCircuitBreaker.getState()).toBe("CLOSED");
  });

  it("times out a slow search and counts it as a failure", async () => {
    vi.useFakeTimers();

    mockFetch.mockImplementationOnce((_url: string, init: RequestInit) =>
      rejectOnAbort(init.signal),
    );

    // Attach the rejection handler before advancing timers. The abort fires inside
    // advanceTimersByTimeAsync, so asserting afterwards would leave the rejection
    // unhandled for a tick — which vitest reports as an unhandled error and exits
    // non-zero on, even with every test passing.
    const assertion = expect(search()).rejects.toBeInstanceOf(TimeoutError);
    await vi.advanceTimersByTimeAsync(2_000);
    await assertion;

    expect(searchCircuitBreaker.getFailureCount()).toBe(1);
  });

  it("times out a service that sends headers and then stalls mid-body", async () => {
    vi.useFakeTimers();

    // fetch resolves as soon as headers arrive, so a deadline cleared at that point
    // leaves the body read unguarded: the caller hangs forever, React Query never
    // settles, and the search page sits on its spinner instead of falling back.
    mockFetch.mockImplementationOnce((_url: string, init: RequestInit) =>
      Promise.resolve({
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => rejectOnAbort(init.signal),
      }),
    );

    const assertion = expect(search()).rejects.toBeInstanceOf(TimeoutError);
    await vi.advanceTimersByTimeAsync(2_000);
    await assertion;

    expect(searchCircuitBreaker.getFailureCount()).toBe(1);
  });

  it("clears the deadline once the body has been read", async () => {
    vi.useFakeTimers();

    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: () => Promise.resolve({ query: "widget", results: [] }),
    });

    await search();

    // A leaked timer would abort a later, unrelated request when it eventually fired.
    expect(vi.getTimerCount()).toBe(0);
  });
});

describe("categoryApi", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
    __resetRefreshStateForTests();
    __resetCircuitBreakerForTests();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  const okJson = (body: unknown) => ({
    ok: true,
    status: 200,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve(body),
  });

  it("GETs the category list", async () => {
    mockFetch.mockResolvedValueOnce(
      okJson({ categories: [{ name: "Electronics", productCount: 3 }] }),
    );

    const result = await categoryApi.listCategories();

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/api/v1/categories"),
      expect.objectContaining({ method: "GET" }),
    );
    expect(result.categories[0].name).toBe("Electronics");
  });

  it("GETs products in a category with pagination", async () => {
    mockFetch.mockResolvedValueOnce(
      okJson({
        category: "Electronics",
        totalResults: 0,
        page: 2,
        pageSize: 10,
        totalPages: 0,
        results: [],
      }),
    );

    await categoryApi.productsInCategory("Electronics", 2, 10);

    const [url] = mockFetch.mock.calls[0];
    expect(url).toContain("/api/v1/categories/Electronics/products");
    expect(url).toContain("page=2");
    expect(url).toContain("pageSize=10");
  });

  it("encodes a category name containing a space", async () => {
    mockFetch.mockResolvedValueOnce(
      okJson({
        category: "Home Goods",
        totalResults: 0,
        page: 1,
        pageSize: 24,
        totalPages: 0,
        results: [],
      }),
    );

    await categoryApi.productsInCategory("Home Goods");

    expect(mockFetch.mock.calls[0][0]).toContain("Home%20Goods");
  });

  it("stays available when the search circuit is open", async () => {
    // The fallback must not be guarded by the breaker it exists to work around.
    for (let i = 0; i < 5; i++) {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 503,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ message: "down" }),
      });
      await expect(
        productApi.search({
          query: "widget",
          page: 1,
          pageSize: 24,
          sort: "relevance" as const,
          filters: {},
        }),
      ).rejects.toBeInstanceOf(ApiError);
    }
    expect(searchCircuitBreaker.getState()).toBe("OPEN");

    mockFetch.mockResolvedValueOnce(
      okJson({ categories: [{ name: "Electronics", productCount: 3 }] }),
    );

    await expect(categoryApi.listCategories()).resolves.toEqual({
      categories: [{ name: "Electronics", productCount: 3 }],
    });
  });
});

describe("cartApi", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  const request = {
    variantId: "variant-1",
    quantity: 2,
    productSnapshot: {
      productId: "product-1",
      name: "ACME Gaming Mouse Pro",
      sku: "ACME-GM-PRO-BLK",
      variantName: "Black",
      imageUrl: null,
      attributes: { color: "Black" },
    },
  };

  it("POSTs the item with credentials so the session cookie is sent", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 201,
      headers: new Headers({ "content-type": "application/json" }),
      json: () =>
        Promise.resolve({
          id: "cart-1",
          items: [],
          summary: { itemCount: 2, subtotal: 139.98, currency: "USD" },
        }),
    });

    const cart = await cartApi.addItem(request);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/carts\/items$/),
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        body: JSON.stringify(request),
      }),
    );
    expect(cart.summary.itemCount).toBe(2);
  });

  it("surfaces the service's error message", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 422,
      headers: new Headers({ "content-type": "application/json" }),
      json: () =>
        Promise.resolve({
          error: "Maximum order quantity is 10 for this item",
        }),
    });

    await expect(cartApi.addItem(request)).rejects.toMatchObject({
      status: 422,
      message: "Maximum order quantity is 10 for this item",
    });
  });
});

describe("cartApi reads and updates", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  const cart = {
    id: "cart-1",
    items: [],
    summary: { itemCount: 0, subtotal: 0, currency: "USD" },
  };

  const jsonResponse = (status: number, body: unknown) => ({
    ok: status < 400,
    status,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve(body),
  });

  it("getCurrent resolves to null when the service answers 204", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
      headers: new Headers(),
      json: () => Promise.reject(new Error("no body")),
    });

    await expect(cartApi.getCurrent()).resolves.toBeNull();
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/carts\/current$/),
      expect.objectContaining({ method: "GET", credentials: "include" }),
    );
  });

  it("getCurrent returns the session's cart", async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(200, cart));

    await expect(cartApi.getCurrent()).resolves.toEqual(cart);
  });

  it("updateItem PATCHes the quantity with credentials", async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(200, cart));

    await cartApi.updateItem("cart-1", "line-1", 3);

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/carts\/cart-1\/items\/line-1$/),
      expect.objectContaining({
        method: "PATCH",
        credentials: "include",
        body: JSON.stringify({ quantity: 3 }),
      }),
    );
  });

  it("updateItem exposes maxQuantity from a 422", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse(422, {
        error: "Maximum order quantity is 10 for this item",
        maxQuantity: 10,
      }),
    );

    await expect(
      cartApi.updateItem("cart-1", "line-1", 11),
    ).rejects.toMatchObject({
      status: 422,
      data: { maxQuantity: 10 },
    });
  });

  it("merge POSTs with credentials and returns the merged cart", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse(200, {
        ...cart,
        mergeResult: { itemsMerged: 1, quantitiesAdjusted: [] },
      }),
    );

    const merged = await cartApi.merge();

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/carts\/merge$/),
      expect.objectContaining({ method: "POST", credentials: "include" }),
    );
    expect(merged?.mergeResult?.itemsMerged).toBe(1);
  });

  it("merge resolves to null when there was nothing to merge (204)", async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 204,
      headers: new Headers(),
      json: () => Promise.reject(new Error("no body")),
    });

    await expect(cartApi.merge()).resolves.toBeNull();
  });

  it("removeItem DELETEs with credentials", async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(200, cart));

    await cartApi.removeItem("cart-1", "line-1");

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/carts\/cart-1\/items\/line-1$/),
      expect.objectContaining({ method: "DELETE", credentials: "include" }),
    );
  });

  it("clearCart DELETEs every line with credentials", async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(200, cart));

    await cartApi.clearCart("cart-1");

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/carts\/cart-1\/items$/),
      expect.objectContaining({ method: "DELETE", credentials: "include" }),
    );
  });
});

describe("inventoryApi", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    global.fetch = originalFetch;
  });

  it("times out a stalled availability check (PIN-349)", async () => {
    vi.useFakeTimers();

    mockFetch.mockImplementationOnce((_url: string, init: RequestInit) =>
      rejectOnAbort(init.signal),
    );

    const check = inventoryApi.getAvailability("variant-1");
    let settled = false;
    check.catch(() => (settled = true));
    // Attach the rejection handler before the abort fires inside advanceTimersByTimeAsync.
    const assertion = expect(check).rejects.toBeInstanceOf(TimeoutError);

    // Not a moment before the 5s deadline
    await vi.advanceTimersByTimeAsync(4_999);
    expect(settled).toBe(false);

    await vi.advanceTimersByTimeAsync(1);
    await assertion;
  });
});

describe("checkout and orders (PIN-426)", () => {
  const mockFetch = vi.fn();
  const originalFetch = global.fetch;

  beforeEach(() => {
    global.fetch = mockFetch;
    mockFetch.mockReset();
    __resetRefreshStateForTests();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  const jsonResponse = (status: number, body: unknown) => ({
    ok: status < 400,
    status,
    headers: new Headers({ "content-type": "application/json" }),
    json: () => Promise.resolve(body),
  });

  const placed = {
    orderId: "order-1",
    orderNumber: "ACME-20261029-000001",
    status: "SUBMITTED",
    createdAt: "2026-10-29T12:00:00Z",
    summary: { subtotal: { amount: 69.99, currency: "USD" } },
  };

  it("startCheckout POSTs to the cart's checkout with credentials", async () => {
    const session = {
      checkoutSessionId: "session-1",
      cartId: "cart 1",
      status: "INITIATED",
      expiresAt: "2026-10-29T12:30:00Z",
      cart: { itemCount: 1, subtotal: 69.99, currency: "USD" },
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(200, session));

    await expect(cartApi.startCheckout("cart 1")).resolves.toEqual(session);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/carts\/cart%201\/checkout$/),
      expect.objectContaining({ method: "POST", credentials: "include" }),
    );
  });

  it("leaveCheckout DELETEs the cart's checkout with credentials", async () => {
    const cart = {
      id: "cart 1",
      items: [],
      summary: { itemCount: 0, subtotal: 0, currency: "USD" },
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(200, cart));

    await expect(cartApi.leaveCheckout("cart 1")).resolves.toEqual(cart);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/api\/v1\/carts\/cart%201\/checkout$/),
      expect.objectContaining({ method: "DELETE", credentials: "include" }),
    );
  });

  it("orderApi.place sends the session and the Idempotency-Key to the order service", async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(201, placed));

    await expect(orderApi.place("session-1", "key-1")).resolves.toEqual(placed);
    const [url, init] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:10305/api/v1/orders");
    expect(init).toMatchObject({
      method: "POST",
      credentials: "include",
      body: JSON.stringify({ checkoutSessionId: "session-1" }),
    });
    expect(init.headers).toMatchObject({ "Idempotency-Key": "key-1" });
  });

  it("orderApi.list asks the order service for one page of orders", async () => {
    const list = {
      orders: [],
      page: 2,
      pageSize: 10,
      totalResults: 0,
      totalPages: 0,
    };
    mockFetch.mockResolvedValueOnce(jsonResponse(200, list));

    await expect(orderApi.list(2, 10)).resolves.toEqual(list);
    const [url, init] = mockFetch.mock.calls[0];
    expect(url).toBe("http://localhost:10305/api/v1/orders?page=2&pageSize=10");
    expect(init).toMatchObject({ method: "GET", credentials: "include" });
  });

  it("orderApi.place refreshes on TOKEN_EXPIRED and retries with the same key", async () => {
    mockFetch
      .mockResolvedValueOnce(
        jsonResponse(401, { error: "TOKEN_EXPIRED", code: "TOKEN_EXPIRED" }),
      )
      .mockResolvedValueOnce(jsonResponse(200, { status: "SUCCESS" }))
      .mockResolvedValueOnce(jsonResponse(201, placed));

    await expect(orderApi.place("session-1", "key-1")).resolves.toEqual(placed);
    expect(mockFetch).toHaveBeenCalledTimes(3);
    expect(mockFetch.mock.calls[1][0]).toContain("/api/v1/auth/refresh");
    const [retryUrl, retryInit] = mockFetch.mock.calls[2];
    expect(retryUrl).toBe("http://localhost:10305/api/v1/orders");
    expect(retryInit.headers).toMatchObject({ "Idempotency-Key": "key-1" });
  });

  // PIN-438: a browser with no access cookie gets SIGN_IN_REQUIRED from the order service
  // rather than TOKEN_EXPIRED
  const signInRequired = () =>
    jsonResponse(401, {
      error: "Sign in to continue",
      code: "SIGN_IN_REQUIRED",
    });

  it("orderApi.place refreshes on SIGN_IN_REQUIRED and retries with the same key", async () => {
    mockFetch
      .mockResolvedValueOnce(signInRequired())
      .mockResolvedValueOnce(jsonResponse(200, { status: "SUCCESS" }))
      .mockResolvedValueOnce(jsonResponse(201, placed));

    await expect(orderApi.place("session-1", "key-1")).resolves.toEqual(placed);
    expect(mockFetch).toHaveBeenCalledTimes(3);
    expect(mockFetch.mock.calls[1][0]).toContain("/api/v1/auth/refresh");
    const [retryUrl, retryInit] = mockFetch.mock.calls[2];
    expect(retryUrl).toBe("http://localhost:10305/api/v1/orders");
    expect(retryInit.headers).toMatchObject({ "Idempotency-Key": "key-1" });
  });

  // What identity's refresh answers when there is no session to refresh
  const refreshRefused = () => jsonResponse(401, { error: "TOKEN_EXPIRED" });

  it("orderApi.place signs the customer out without a redirect when TOKEN_EXPIRED can't refresh", async () => {
    const effects = stubRefreshFailureEffects();
    try {
      mockFetch
        .mockResolvedValueOnce(
          jsonResponse(401, { error: "TOKEN_EXPIRED", code: "TOKEN_EXPIRED" }),
        )
        .mockResolvedValueOnce(refreshRefused());

      const error = await orderApi.place("session-1", "key-1").catch((e) => e);

      // The page, not the client, sends them to sign-in, so it can keep redirect=/cart
      expect(error).toBeInstanceOf(ApiError);
      expect(mockFetch).toHaveBeenCalledTimes(2);
      expect(effects.clearUser).toHaveBeenCalledOnce();
      expect(effects.clearProfile).toHaveBeenCalledOnce();
      expect(effects.assignedHref()).toBeNull();
    } finally {
      effects.restore();
    }
  });

  it("orderApi.place signs the customer out without a redirect when SIGN_IN_REQUIRED can't refresh", async () => {
    const effects = stubRefreshFailureEffects();
    try {
      mockFetch
        .mockResolvedValueOnce(signInRequired())
        .mockResolvedValueOnce(refreshRefused());

      const error = await orderApi.place("session-1", "key-1").catch((e) => e);

      // The page, not the client, sends them to sign-in, so it can keep redirect=/cart
      expect(error).toBeInstanceOf(ApiError);
      expect(error.status).toBe(401);
      expect(error.data.code).toBe("SIGN_IN_REQUIRED");
      // Why the refresh failed is kept, not swallowed
      expect(error.cause).toBeInstanceOf(ApiError);
      expect(error.cause.data.error).toBe("TOKEN_EXPIRED");
      expect(mockFetch).toHaveBeenCalledTimes(2);
      expect(effects.clearUser).toHaveBeenCalledOnce();
      expect(effects.clearProfile).toHaveBeenCalledOnce();
      expect(effects.assignedHref()).toBeNull();
    } finally {
      effects.restore();
    }
  });

  it("orderApi.place queued behind a failed refresh still rejects with SIGN_IN_REQUIRED", async () => {
    const effects = stubRefreshFailureEffects();
    try {
      // 1) customer profile → 401 TOKEN_EXPIRED, starts the refresh
      // 2) place → 401 SIGN_IN_REQUIRED, queues behind it  3) /refresh → 401
      mockFetch
        .mockResolvedValueOnce(jsonResponse(401, { error: "TOKEN_EXPIRED" }))
        .mockResolvedValueOnce(signInRequired())
        .mockResolvedValueOnce(refreshRefused());

      const [profileError, placeError] = await Promise.all([
        customerApi.getCurrentCustomer().catch((e) => e),
        orderApi.place("session-1", "key-1").catch((e) => e),
      ]);

      expect(mockFetch).toHaveBeenCalledTimes(3);
      expect(profileError).toBeInstanceOf(ApiError);
      expect(placeError).toBeInstanceOf(ApiError);
      expect(placeError.data.code).toBe("SIGN_IN_REQUIRED");
      expect(placeError.cause).toBeInstanceOf(ApiError);
    } finally {
      effects.restore();
    }
  });

  it("orderApi.get does not refresh on SIGN_IN_REQUIRED", async () => {
    const effects = stubRefreshFailureEffects();
    try {
      mockFetch.mockResolvedValueOnce(signInRequired());

      const error = await orderApi.get("order-1").catch((e) => e);

      expect(error).toBeInstanceOf(ApiError);
      expect(error.data.code).toBe("SIGN_IN_REQUIRED");
      expect(mockFetch).toHaveBeenCalledTimes(1);
      expect(effects.clearUser).not.toHaveBeenCalled();
      expect(effects.assignedHref()).toBeNull();
    } finally {
      effects.restore();
    }
  });

  it("a TOKEN_EXPIRED request queued behind a SIGN_IN_REQUIRED refresh that fails still redirects", async () => {
    const effects = stubRefreshFailureEffects();
    try {
      // 1) place → 401 SIGN_IN_REQUIRED, starts the refresh
      // 2) customer profile → 401 TOKEN_EXPIRED, queues behind it  3) /refresh → 401
      mockFetch
        .mockResolvedValueOnce(signInRequired())
        .mockResolvedValueOnce(jsonResponse(401, { error: "TOKEN_EXPIRED" }))
        .mockResolvedValueOnce(refreshRefused());

      const [placeError, profileError] = await Promise.all([
        orderApi.place("session-1", "key-1").catch((e) => e),
        customerApi.getCurrentCustomer().catch((e) => e),
      ]);

      expect(mockFetch).toHaveBeenCalledTimes(3);
      expect(placeError.data.code).toBe("SIGN_IN_REQUIRED");
      expect(profileError).toBeInstanceOf(ApiError);
      expect(effects.clearUser).toHaveBeenCalled();
      expect(effects.assignedHref()).toBe("/signin?logout=true");
    } finally {
      effects.restore();
    }
  });

  it("orderApi.place rejects with the order service's code", async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse(404, {
        error: "Checkout session not found",
        code: "CHECKOUT_SESSION_NOT_FOUND",
      }),
    );

    const error = await orderApi.place("session-1", "key-1").catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
    expect(error.data.code).toBe("CHECKOUT_SESSION_NOT_FOUND");
  });

  it("orderApi.get reads one order with credentials", async () => {
    const order = { ...placed, lineItems: [], pricing: placed.summary };
    mockFetch.mockResolvedValueOnce(jsonResponse(200, order));

    await expect(orderApi.get("order-1")).resolves.toEqual(order);
    expect(mockFetch).toHaveBeenCalledWith(
      "http://localhost:10305/api/v1/orders/order-1",
      expect.objectContaining({ method: "GET", credentials: "include" }),
    );
  });
});
