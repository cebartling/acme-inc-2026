import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import {
  useCustomerStore,
  useCustomerProfile,
  useCustomerLoading,
  useCustomerError,
} from "./customer.store";
import { customerApi } from "@/services/api";
import type { CustomerProfile } from "@/services/api";

// Mock the API
vi.mock("@/services/api", () => ({
  customerApi: {
    getCurrentCustomer: vi.fn(),
  },
}));

describe("useCustomerStore", () => {
  const mockProfile: CustomerProfile = {
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

  beforeEach(() => {
    // Clear persisted state and reset store
    localStorage.removeItem("customer-storage");
    act(() => {
      useCustomerStore.getState().clearProfile();
    });
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("initial state", () => {
    it("has null profile initially", () => {
      const { result } = renderHook(() => useCustomerStore());
      expect(result.current.profile).toBeNull();
    });

    it("is not loading initially", () => {
      const { result } = renderHook(() => useCustomerStore());
      expect(result.current.isLoading).toBe(false);
    });

    it("has no error initially", () => {
      const { result } = renderHook(() => useCustomerStore());
      expect(result.current.error).toBeNull();
    });
  });

  describe("fetchProfile", () => {
    it("sets loading to true when fetching starts", async () => {
      vi.mocked(customerApi.getCurrentCustomer).mockImplementation(
        () => new Promise(() => {}) // Never resolves
      );

      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.fetchProfile();
      });

      expect(result.current.isLoading).toBe(true);
      expect(result.current.error).toBeNull();
    });

    it("sets profile and loading to false on success", async () => {
      vi.mocked(customerApi.getCurrentCustomer).mockResolvedValueOnce(
        mockProfile
      );

      const { result } = renderHook(() => useCustomerStore());

      await act(async () => {
        await result.current.fetchProfile();
      });

      expect(result.current.profile).toEqual(mockProfile);
      expect(result.current.isLoading).toBe(false);
      expect(result.current.error).toBeNull();
    });

    it("sets error and loading to false on failure", async () => {
      const errorMessage = "Network error";
      vi.mocked(customerApi.getCurrentCustomer).mockRejectedValueOnce(
        new Error(errorMessage)
      );

      const { result } = renderHook(() => useCustomerStore());

      await act(async () => {
        try {
          await result.current.fetchProfile();
        } catch {
          // Expected to throw
        }
      });

      expect(result.current.profile).toBeNull();
      expect(result.current.isLoading).toBe(false);
      expect(result.current.error).toBe(errorMessage);
    });

    it("re-throws error to allow callers to handle", async () => {
      const error = new Error("API Error");
      vi.mocked(customerApi.getCurrentCustomer).mockRejectedValueOnce(error);

      const { result } = renderHook(() => useCustomerStore());

      await expect(
        act(async () => {
          await result.current.fetchProfile();
        })
      ).rejects.toThrow("API Error");
    });

    it("clears previous error on new fetch", async () => {
      const { result } = renderHook(() => useCustomerStore());

      // Set initial error
      act(() => {
        result.current.setError("Previous error");
      });
      expect(result.current.error).toBe("Previous error");

      // Fetch should clear error
      vi.mocked(customerApi.getCurrentCustomer).mockResolvedValueOnce(
        mockProfile
      );

      await act(async () => {
        await result.current.fetchProfile();
      });

      expect(result.current.error).toBeNull();
    });
  });

  describe("setProfile", () => {
    it("sets profile data", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setProfile(mockProfile);
      });

      expect(result.current.profile).toEqual(mockProfile);
    });

    it("sets loading to false", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setLoading(true);
        result.current.setProfile(mockProfile);
      });

      expect(result.current.isLoading).toBe(false);
    });

    it("clears error", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setError("Some error");
        result.current.setProfile(mockProfile);
      });

      expect(result.current.error).toBeNull();
    });
  });

  describe("clearProfile", () => {
    it("clears profile data", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setProfile(mockProfile);
        result.current.clearProfile();
      });

      expect(result.current.profile).toBeNull();
    });

    it("clears error", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setError("Some error");
        result.current.clearProfile();
      });

      expect(result.current.error).toBeNull();
    });

    it("does not change loading state", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setLoading(true);
        result.current.clearProfile();
      });

      expect(result.current.isLoading).toBe(true);
    });
  });

  describe("setLoading", () => {
    it("sets loading to true", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setLoading(true);
      });

      expect(result.current.isLoading).toBe(true);
    });

    it("sets loading to false", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setLoading(true);
        result.current.setLoading(false);
      });

      expect(result.current.isLoading).toBe(false);
    });
  });

  describe("setError", () => {
    it("sets error message", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setError("Something went wrong");
      });

      expect(result.current.error).toBe("Something went wrong");
    });

    it("sets loading to false", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setLoading(true);
        result.current.setError("Error");
      });

      expect(result.current.isLoading).toBe(false);
    });

    it("can clear error by passing null", () => {
      const { result } = renderHook(() => useCustomerStore());

      act(() => {
        result.current.setError("Error");
        result.current.setError(null);
      });

      expect(result.current.error).toBeNull();
    });
  });

  describe("persistence", () => {
    it("persists profile to localStorage", async () => {
      vi.mocked(customerApi.getCurrentCustomer).mockResolvedValueOnce(
        mockProfile
      );

      await act(async () => {
        await useCustomerStore.getState().fetchProfile();
      });

      const stored = localStorage.getItem("customer-storage");
      expect(stored).not.toBeNull();

      const parsed = JSON.parse(stored!);
      expect(parsed.state.profile).toEqual(mockProfile);
    });

    it("does not persist loading state", () => {
      act(() => {
        useCustomerStore.getState().setLoading(true);
      });

      const stored = localStorage.getItem("customer-storage");
      if (stored) {
        const parsed = JSON.parse(stored);
        expect(parsed.state.isLoading).toBeUndefined();
      }
    });

    it("does not persist error state", () => {
      act(() => {
        useCustomerStore.getState().setError("Error");
      });

      const stored = localStorage.getItem("customer-storage");
      if (stored) {
        const parsed = JSON.parse(stored);
        expect(parsed.state.error).toBeUndefined();
      }
    });
  });
});

describe("selector hooks", () => {
  const mockProfile: CustomerProfile = {
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

  beforeEach(() => {
    localStorage.removeItem("customer-storage");
    act(() => {
      useCustomerStore.getState().clearProfile();
    });
  });

  describe("useCustomerProfile", () => {
    it("returns null when no profile loaded", () => {
      const { result } = renderHook(() => useCustomerProfile());
      expect(result.current).toBeNull();
    });

    it("returns profile when loaded", () => {
      act(() => {
        useCustomerStore.getState().setProfile(mockProfile);
      });

      const { result } = renderHook(() => useCustomerProfile());
      expect(result.current).toEqual(mockProfile);
    });
  });

  describe("useCustomerLoading", () => {
    it("returns false by default", () => {
      const { result } = renderHook(() => useCustomerLoading());
      expect(result.current).toBe(false);
    });

    it("returns true when loading", () => {
      act(() => {
        useCustomerStore.getState().setLoading(true);
      });

      const { result } = renderHook(() => useCustomerLoading());
      expect(result.current).toBe(true);
    });
  });

  describe("useCustomerError", () => {
    it("returns null by default", () => {
      const { result } = renderHook(() => useCustomerError());
      expect(result.current).toBeNull();
    });

    it("returns error message when set", () => {
      act(() => {
        useCustomerStore.getState().setError("Failed to load");
      });

      const { result } = renderHook(() => useCustomerError());
      expect(result.current).toBe("Failed to load");
    });
  });
});
