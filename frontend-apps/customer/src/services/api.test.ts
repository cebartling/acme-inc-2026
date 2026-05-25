import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  ApiError,
  customerApi,
  identityApi,
  productApi,
  __resetRefreshStateForTests,
} from "./api";

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
    const userId = "user-456";

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

      await customerApi.getPreferences(customerId, userId);

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining(`/api/v1/customers/${customerId}/preferences`),
        expect.objectContaining({
          method: "GET",
          headers: expect.objectContaining({
            "Content-Type": "application/json",
            "X-User-Id": userId,
          }),
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

      const result = await customerApi.getPreferences(customerId, userId);
      expect(result).toEqual(expectedResponse);
    });

    it("throws ApiError on non-OK response", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ error: "Customer not found" }),
      });

      await expect(
        customerApi.getPreferences(customerId, userId),
      ).rejects.toThrow(ApiError);
    });

    it("throws ApiError with correct status code", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 403,
        headers: new Headers({ "content-type": "application/json" }),
        json: () => Promise.resolve({ error: "Forbidden" }),
      });

      try {
        await customerApi.getPreferences(customerId, userId);
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
    const userId = "user-456";
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

      await customerApi.updatePreferences(customerId, userId, preferences);

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining(`/api/v1/customers/${customerId}/preferences`),
        expect.objectContaining({
          method: "PUT",
          headers: expect.objectContaining({
            "Content-Type": "application/json",
            "X-User-Id": userId,
          }),
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
        userId,
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
        customerApi.updatePreferences(customerId, userId, preferences),
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
        await customerApi.updatePreferences(customerId, userId, preferences);
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
        await customerApi.getPreferences("customer-123", "user-456");
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
        await customerApi.getPreferences("customer-123", "user-456");
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
        await customerApi.getPreferences("customer-123", "user-456");
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
        await customerApi.getPreferences("customer-123", "user-456");
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
      customerApi.getPreferences("c", "u"),
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
    // Mock window.location so we can observe the redirect without
    // actually navigating in the JSDOM environment.
    const originalLocation = window.location;
    let assignedHref: string | null = null;
    Object.defineProperty(window, "location", {
      configurable: true,
      value: {
        ...originalLocation,
        set href(value: string) {
          assignedHref = value;
        },
        get href() {
          return assignedHref ?? originalLocation.href;
        },
      },
    });

    // Mock the auth + customer stores so we can assert cleanup.
    const clearUser = vi.fn();
    const clearProfile = vi.fn();
    vi.doMock("@/stores/auth.store", () => ({
      useAuthStore: {
        getState: () => ({ clearUser }),
      },
    }));
    vi.doMock("@/stores/customer.store", () => ({
      useCustomerStore: {
        getState: () => ({ clearProfile }),
      },
    }));

    // 1) original request → 401
    // 2) /refresh → 401 TOKEN_EXPIRED (refresh itself rejected)
    mockFetch
      .mockResolvedValueOnce(tokenExpired())
      .mockResolvedValueOnce(tokenExpired());

    await expect(customerApi.getCurrentCustomer()).rejects.toBeInstanceOf(
      ApiError,
    );

    expect(clearUser).toHaveBeenCalledOnce();
    expect(clearProfile).toHaveBeenCalledOnce();
    expect(assignedHref).toBe("/signin?logout=true");

    // Restore.
    Object.defineProperty(window, "location", {
      configurable: true,
      value: originalLocation,
    });
    vi.doUnmock("@/stores/auth.store");
    vi.doUnmock("@/stores/customer.store");
  });

  it("401 with a non-TOKEN_EXPIRED error code does not trigger refresh", async () => {
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
      customerApi.getPreferences("c", "u"),
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
