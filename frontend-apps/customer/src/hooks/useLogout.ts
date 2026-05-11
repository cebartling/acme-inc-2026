import { useCallback, useState } from "react";
import { useNavigate } from "@tanstack/react-router";

import { identityApi } from "@/services/api";
import { trackEvent } from "@/services/analytics";
import { useAuthStore } from "@/stores/auth.store";
import { useCustomerStore } from "@/stores/customer.store";

interface UseLogoutResult {
  logout: (allDevices?: boolean) => Promise<void>;
  isLoading: boolean;
}

/**
 * Logout mutation hook.
 *
 * Calls the identity service to invalidate the session(s), clears the
 * frontend auth and customer stores, emits a logout analytics event,
 * and navigates the user to the signin page with `?logout=true` so the
 * signin page can show the "You have been signed out" banner.
 *
 * Failures on the API call are intentionally swallowed: the user is
 * still logged out client-side and the cookies are cleared by the server
 * even on failure paths.
 */
export function useLogout(): UseLogoutResult {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);

  const logout = useCallback(
    async (allDevices: boolean = false) => {
      setIsLoading(true);
      try {
        if (allDevices) {
          await identityApi.logoutAll();
        } else {
          await identityApi.logout();
        }
      } catch {
        // Logout is best-effort on the server side; we still clear
        // client state and redirect to signin below.
      } finally {
        useAuthStore.getState().clearUser();
        useCustomerStore.getState().clearProfile();
        trackEvent("logout", {
          source: "WEB",
          logoutType: allDevices ? "all" : "single",
        });
        setIsLoading(false);
        await navigate({ to: "/signin", search: { logout: "true" } });
      }
    },
    [navigate],
  );

  return { logout, isLoading };
}
