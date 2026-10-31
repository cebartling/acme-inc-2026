import { useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { ApiError } from "@/services/api";
import { useIsAuthenticated, useIsAuthLoading } from "@/stores/auth.store";

/**
 * Sends a signed-out visitor to sign-in, and back to `redirect` afterwards. A 401 that reaches
 * the page counts too: the store can say signed in when the browser has no access cookie, which
 * the order service answers with `SIGN_IN_REQUIRED`.
 *
 * @returns whether the visitor is being redirected, so the page can render nothing
 */
export function useRedirectWhenSignedOut(
  redirect: string,
  error: unknown,
): boolean {
  const navigate = useNavigate();
  const isAuthenticated = useIsAuthenticated();
  const isAuthLoading = useIsAuthLoading();
  const signedOut =
    (!isAuthLoading && !isAuthenticated) ||
    (error instanceof ApiError && error.status === 401);

  useEffect(() => {
    if (signedOut) {
      navigate({ to: "/signin", search: { redirect } });
    }
  }, [signedOut, redirect, navigate]);

  return signedOut;
}
