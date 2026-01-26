import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { ApiError, customerApi } from "./api";

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
              communication: { email: true, sms: false, push: false, marketing: false, frequency: "IMMEDIATE" },
              privacy: { shareDataWithPartners: false, allowAnalytics: true, allowPersonalization: true },
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
        })
      );
    });

    it("returns preferences response on success", async () => {
      const expectedResponse = {
        customerId,
        preferences: {
          communication: { email: true, sms: false, push: false, marketing: false, frequency: "IMMEDIATE" },
          privacy: { shareDataWithPartners: false, allowAnalytics: true, allowPersonalization: true },
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

      await expect(customerApi.getPreferences(customerId, userId)).rejects.toThrow(
        ApiError
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
        })
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
        })
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
              communication: { email: false, sms: false, push: false, marketing: false, frequency: "IMMEDIATE" },
              privacy: { shareDataWithPartners: false, allowAnalytics: false, allowPersonalization: true },
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
        })
      );
    });

    it("returns updated preferences on success", async () => {
      const expectedResponse = {
        customerId,
        preferences: {
          communication: { email: false, sms: true, push: false, marketing: false, frequency: "DAILY_DIGEST" },
          privacy: { shareDataWithPartners: false, allowAnalytics: false, allowPersonalization: true },
          display: { language: "es-ES", currency: "EUR", timezone: "Europe/Madrid" },
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
        preferences
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
        customerApi.updatePreferences(customerId, userId, preferences)
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
