import { useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { ApiError } from "@/services/api";
import { useIsAuthenticated, useIsAuthLoading } from "@/stores/auth.store";

/**
 * Sends a signed-out visitor to sign-in, and back to `redirect` afterwards. A 401 that reaches
 * the page counts too: the store can say signed in when the browser has no access cookie, which
 * the order service answers with `SIGN_IN_REQUIRED`. A refetch in flight settles the 401: a query
 * that holds data keeps its error until then, and the customer may have signed in since.
 *
 * The redirect replaces the page in the history, so Back doesn't land on it and bounce again.
 *
 * @returns whether the visitor is being redirected, so the page can render nothing
 */
export function useRedirectWhenSignedOut(
  redirect: string,
  query: { error: unknown; isFetching: boolean },
): boolean {
  const navigate = useNavigate();
  const isAuthenticated = useIsAuthenticated();
  const isAuthLoading = useIsAuthLoading();
  const signedOut =
    (!isAuthLoading && !isAuthenticated) ||
    (!query.isFetching &&
      query.error instanceof ApiError &&
      query.error.status === 401);

  useEffect(() => {
    if (signedOut) {
      navigate({ to: "/signin", search: { redirect }, replace: true });
    }
  }, [signedOut, redirect, navigate]);

  return signedOut;
}
