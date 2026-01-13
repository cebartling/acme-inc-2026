import { describe, it, expect, beforeEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import {
  useAuthStore,
  useUser,
  useCustomerId,
  useUserId,
  useIsAuthenticated,
  useIsAuthLoading,
} from "./auth.store";

describe("useAuthStore", () => {
  beforeEach(() => {
    // Clear persisted state and reset store
    localStorage.removeItem("auth-storage");
    act(() => {
      useAuthStore.getState().clearUser();
    });
  });

  describe("initial state", () => {
    it("has null user initially", () => {
      const { result } = renderHook(() => useAuthStore());
      expect(result.current.user).toBeNull();
    });

    it("is not authenticated initially", () => {
      const { result } = renderHook(() => useAuthStore());
      expect(result.current.isAuthenticated).toBe(false);
    });

    it("is not loading after clearUser", () => {
      const { result } = renderHook(() => useAuthStore());
      expect(result.current.isLoading).toBe(false);
    });
  });

  describe("setUser", () => {
    const testUser = {
      userId: "user-123",
      customerId: "customer-456",
      email: "test@example.com",
      firstName: "John",
      lastName: "Doe",
    };

    it("sets user data", () => {
      const { result } = renderHook(() => useAuthStore());
      act(() => {
        result.current.setUser(testUser);
      });
      expect(result.current.user).toEqual(testUser);
    });

    it("sets isAuthenticated to true", () => {
      const { result } = renderHook(() => useAuthStore());
      act(() => {
        result.current.setUser(testUser);
      });
      expect(result.current.isAuthenticated).toBe(true);
    });

    it("sets isLoading to false", () => {
      const { result } = renderHook(() => useAuthStore());
      act(() => {
        result.current.setLoading(true);
        result.current.setUser(testUser);
      });
      expect(result.current.isLoading).toBe(false);
    });
  });

  describe("clearUser", () => {
    const testUser = {
      userId: "user-123",
      customerId: "customer-456",
      email: "test@example.com",
      firstName: "John",
      lastName: "Doe",
    };

    it("clears user data", () => {
      const { result } = renderHook(() => useAuthStore());
      act(() => {
        result.current.setUser(testUser);
        result.current.clearUser();
      });
      expect(result.current.user).toBeNull();
    });

    it("sets isAuthenticated to false", () => {
      const { result } = renderHook(() => useAuthStore());
      act(() => {
        result.current.setUser(testUser);
        result.current.clearUser();
      });
      expect(result.current.isAuthenticated).toBe(false);
    });

    it("sets isLoading to false", () => {
      const { result } = renderHook(() => useAuthStore());
      act(() => {
        result.current.setLoading(true);
        result.current.clearUser();
      });
      expect(result.current.isLoading).toBe(false);
    });
  });

  describe("setLoading", () => {
    it("sets loading to true", () => {
      const { result } = renderHook(() => useAuthStore());
      act(() => {
        result.current.setLoading(true);
      });
      expect(result.current.isLoading).toBe(true);
    });

    it("sets loading to false", () => {
      const { result } = renderHook(() => useAuthStore());
      act(() => {
        result.current.setLoading(true);
        result.current.setLoading(false);
      });
      expect(result.current.isLoading).toBe(false);
    });
  });

  describe("persistence", () => {
    const testUser = {
      userId: "user-123",
      customerId: "customer-456",
      email: "test@example.com",
      firstName: "John",
      lastName: "Doe",
    };

    it("persists user to localStorage", () => {
      act(() => {
        useAuthStore.getState().setUser(testUser);
      });

      const stored = localStorage.getItem("auth-storage");
      expect(stored).not.toBeNull();

      const parsed = JSON.parse(stored!);
      expect(parsed.state.user).toEqual(testUser);
      expect(parsed.state.isAuthenticated).toBe(true);
    });

    it("does not persist isLoading state", () => {
      act(() => {
        useAuthStore.getState().setLoading(true);
      });

      const stored = localStorage.getItem("auth-storage");
      if (stored) {
        const parsed = JSON.parse(stored);
        expect(parsed.state.isLoading).toBeUndefined();
      }
    });
  });
});

describe("selector hooks", () => {
  const testUser = {
    userId: "user-123",
    customerId: "customer-456",
    email: "test@example.com",
    firstName: "John",
    lastName: "Doe",
  };

  beforeEach(() => {
    localStorage.removeItem("auth-storage");
    act(() => {
      useAuthStore.getState().clearUser();
    });
  });

  describe("useUser", () => {
    it("returns null when not authenticated", () => {
      const { result } = renderHook(() => useUser());
      expect(result.current).toBeNull();
    });

    it("returns user when authenticated", () => {
      act(() => {
        useAuthStore.getState().setUser(testUser);
      });
      const { result } = renderHook(() => useUser());
      expect(result.current).toEqual(testUser);
    });
  });

  describe("useCustomerId", () => {
    it("returns undefined when not authenticated", () => {
      const { result } = renderHook(() => useCustomerId());
      expect(result.current).toBeUndefined();
    });

    it("returns customerId when authenticated", () => {
      act(() => {
        useAuthStore.getState().setUser(testUser);
      });
      const { result } = renderHook(() => useCustomerId());
      expect(result.current).toBe("customer-456");
    });
  });

  describe("useUserId", () => {
    it("returns undefined when not authenticated", () => {
      const { result } = renderHook(() => useUserId());
      expect(result.current).toBeUndefined();
    });

    it("returns userId when authenticated", () => {
      act(() => {
        useAuthStore.getState().setUser(testUser);
      });
      const { result } = renderHook(() => useUserId());
      expect(result.current).toBe("user-123");
    });
  });

  describe("useIsAuthenticated", () => {
    it("returns false when not authenticated", () => {
      const { result } = renderHook(() => useIsAuthenticated());
      expect(result.current).toBe(false);
    });

    it("returns true when authenticated", () => {
      act(() => {
        useAuthStore.getState().setUser(testUser);
      });
      const { result } = renderHook(() => useIsAuthenticated());
      expect(result.current).toBe(true);
    });
  });

  describe("useIsAuthLoading", () => {
    it("returns false by default after clearUser", () => {
      const { result } = renderHook(() => useIsAuthLoading());
      expect(result.current).toBe(false);
    });

    it("returns true when loading", () => {
      act(() => {
        useAuthStore.getState().setLoading(true);
      });
      const { result } = renderHook(() => useIsAuthLoading());
      expect(result.current).toBe(true);
    });
  });
});
