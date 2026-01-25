import { create } from "zustand";
import { persist } from "zustand/middleware";
import { customerApi } from "@/services/api";
import type { CustomerProfile } from "@/services/api";

interface CustomerStore {
  // State
  profile: CustomerProfile | null;
  isLoading: boolean;
  error: string | null;

  // Actions
  fetchProfile: () => Promise<void>;
  setProfile: (profile: CustomerProfile) => void;
  clearProfile: () => void;
  setLoading: (isLoading: boolean) => void;
  setError: (error: string | null) => void;
}

/**
 * Customer store for managing customer profile state.
 *
 * This store:
 * - Fetches and caches customer profile data from the Customer Service
 * - Persists profile to localStorage for fast initial load
 * - Provides loading and error states for UI
 * - Clears on logout
 */
export const useCustomerStore = create<CustomerStore>()(
  persist(
    (set) => ({
      // Initial state
      profile: null,
      isLoading: false,
      error: null,

      // Actions
      fetchProfile: async () => {
        set({ isLoading: true, error: null });
        try {
          const profile = await customerApi.getCurrentCustomer();
          set({ profile, isLoading: false, error: null });
        } catch (err) {
          const error =
            err instanceof Error ? err.message : "Failed to load customer profile";
          set({ error, isLoading: false, profile: null });
          throw err; // Re-throw to allow callers to handle
        }
      },

      setProfile: (profile) => set({ profile, isLoading: false, error: null }),

      clearProfile: () => set({ profile: null, error: null }),

      setLoading: (isLoading) => set({ isLoading }),

      setError: (error) => set({ error, isLoading: false }),
    }),
    {
      name: "customer-storage",
      partialize: (state) => ({
        // Only persist the profile, not loading/error states
        profile: state.profile,
      }),
    }
  )
);

// Selector hooks for convenience
export const useCustomerProfile = () =>
  useCustomerStore((state) => state.profile);
export const useCustomerLoading = () =>
  useCustomerStore((state) => state.isLoading);
export const useCustomerError = () => useCustomerStore((state) => state.error);
