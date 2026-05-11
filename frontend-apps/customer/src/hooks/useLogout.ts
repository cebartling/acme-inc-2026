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
        // Order matters: navigate FIRST so /dashboard unmounts before its
        // auth guard sees isAuthenticated=false and races us with its own
        // `navigate({ to: "/signin" })` (no search), which would otherwise
        // overwrite our redirect and strip the `logout` query param.
        // Wrap navigate so a router-side failure still lets the client
        // cleanup below complete -- a stuck "Loading..." spinner with
        // stale auth state is worse than a missed redirect.
        try {
          await navigate({ to: "/signin", search: { logout: true } });
        } catch {
          // Best-effort; cleanup still runs below.
        }
        useAuthStore.getState().clearUser();
        useCustomerStore.getState().clearProfile();
        trackEvent("logout", {
          source: "WEB",
          logoutType: allDevices ? "all" : "single",
        });
        setIsLoading(false);
      }
    },
    [navigate],
  );

  return { logout, isLoading };
}
