import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * Authenticated user information from the identity service.
 */
interface AuthUser {
  userId: string;
  customerId: string;
  email: string;
  firstName: string;
  lastName: string;
}

interface AuthState {
  // Auth state
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  // Actions
  setUser: (user: AuthUser) => void;
  clearUser: () => void;
  setLoading: (isLoading: boolean) => void;
}

/**
 * Auth store for managing authenticated user state.
 *
 * In a production app, this would be populated by:
 * 1. OAuth/OIDC callback after successful authentication
 * 2. Session restoration on app load
 * 3. API call to get user info after token validation
 *
 * For now, this provides the infrastructure for components to access
 * the authenticated user's information including their customer ID.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      isLoading: true,

      setUser: (user) =>
        set({
          user,
          isAuthenticated: true,
          isLoading: false,
        }),

      clearUser: () =>
        set({
          user: null,
          isAuthenticated: false,
          isLoading: false,
        }),

      setLoading: (isLoading) => set({ isLoading }),
    }),
    {
      name: "auth-storage",
      partialize: (state) => ({
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);

// Selector hooks for common selections
export const useUser = () => useAuthStore((state) => state.user);
export const useCustomerId = () =>
  useAuthStore((state) => state.user?.customerId);
export const useUserId = () => useAuthStore((state) => state.user?.userId);
export const useIsAuthenticated = () =>
  useAuthStore((state) => state.isAuthenticated);
export const useIsAuthLoading = () => useAuthStore((state) => state.isLoading);
