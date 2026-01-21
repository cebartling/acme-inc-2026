import { useState, useEffect, useCallback } from "react";
import {
  createFileRoute,
  useNavigate,
  useSearch,
} from "@tanstack/react-router";
import { z } from "zod";
import { SigninForm } from "@/components/signin";
import { useAuthStore } from "@/stores/auth.store";
import { identityApi, ApiError } from "@/services/api";
import type { SigninFormData } from "@/schemas/signin.schema";

/**
 * State for account lockout information.
 */
interface LockoutState {
  isLocked: boolean;
  remainingSeconds: number;
  lockedUntil: string;
  passwordResetUrl?: string;
}

/**
 * Validates that a redirect URL is safe (internal path only).
 * Prevents open redirect attacks by rejecting:
 * - Absolute URLs (http://, https://, etc.)
 * - Protocol-relative URLs (//example.com)
 * - URLs with encoded characters that could bypass validation
 */
function isValidRedirectUrl(url: string): boolean {
  // Must start with a single forward slash (relative path)
  if (!url.startsWith("/")) return false;

  // Reject protocol-relative URLs (//example.com)
  if (url.startsWith("//")) return false;

  // Reject URLs containing protocol indicators
  if (url.includes("://")) return false;

  // Reject URLs with encoded slashes or colons that could bypass checks
  const decoded = decodeURIComponent(url);
  if (decoded.startsWith("//") || decoded.includes("://")) return false;

  return true;
}

const signinSearchSchema = z.object({
  redirect: z
    .string()
    .optional()
    .refine((val) => !val || isValidRedirectUrl(val), {
      message: "Invalid redirect URL",
    }),
  logout: z.string().optional(),
});

export const Route = createFileRoute("/signin")({
  component: SigninPage,
  validateSearch: signinSearchSchema,
});

function SigninPage() {
  const navigate = useNavigate();
  const search = useSearch({ from: "/signin" });
  const setUser = useAuthStore((state) => state.setUser);
  const [error, setError] = useState<string | undefined>(undefined);
  const [lockout, setLockout] = useState<LockoutState | null>(null);

  // Show logout message if redirected after logout
  const logoutMessage =
    search.logout === "true" ? "You have been signed out." : undefined;

  /**
   * Formats remaining seconds as "Xm Ys" or "Xs" for display.
   */
  const formatRemainingTime = useCallback((seconds: number): string => {
    if (seconds <= 0) return "0s";
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes > 0) {
      return `${minutes}m ${remainingSeconds}s`;
    }
    return `${remainingSeconds}s`;
  }, []);

  // Countdown timer for lockout
  useEffect(() => {
    if (!lockout?.isLocked || lockout.remainingSeconds <= 0) {
      return;
    }

    const timer = setInterval(() => {
      setLockout((prev) => {
        if (!prev || prev.remainingSeconds <= 1) {
          // Lockout has expired
          return null;
        }
        return {
          ...prev,
          remainingSeconds: prev.remainingSeconds - 1,
        };
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [lockout?.isLocked, lockout?.lockedUntil]);

  const handleSubmit = async (data: SigninFormData) => {
    setError(undefined);
    setLockout(null);

    try {
      // Call Identity Service API
      const response = await identityApi.signin({
        email: data.email,
        password: data.password,
        rememberMe: data.rememberMe,
      });

      if (response.status === "MFA_REQUIRED") {
        // Store MFA state in sessionStorage for the verification page.
        //
        // Security considerations:
        // - The mfaToken is a short-lived credential (5 minutes TTL) that can only
        //   be used to complete MFA verification, not to authenticate directly.
        // - sessionStorage is automatically cleared when the tab/window closes,
        //   limiting the exposure window.
        // - sessionStorage is isolated per tab (unlike localStorage), preventing
        //   cross-tab access.
        // - The token is cleared immediately after successful verification or
        //   on expiry/lockout errors in the mfa-verify route.
        //
        // Residual risks:
        // - XSS attacks could read the token from sessionStorage. This is mitigated
        //   by the short TTL and the fact that XSS would likely have access to the
        //   authenticated session anyway.
        // - Browser extensions with host permissions could read the storage.
        //
        // Alternative approaches considered:
        // - URL parameters: More visible in browser history and logs.
        // - In-memory only: Would break on page refresh during MFA flow.
        // - Encrypted storage: Key management adds complexity without significant
        //   benefit given the short token lifetime.
        sessionStorage.setItem(
          "mfaState",
          JSON.stringify({
            mfaToken: response.mfaToken,
            email: data.email,
            redirect: search.redirect,
            mfaMethods: response.mfaMethods,
          })
        );

        // Navigate to MFA verification page
        navigate({ to: "/mfa-verify" });
        return;
      }

      // Successful signin - store user info
      // Note: The signin API only returns userId. In a full implementation,
      // we would fetch additional user profile data from a separate endpoint.
      // For now, we use the email from the form and placeholder values.
      setUser({
        userId: response.userId,
        customerId: response.userId, // TODO: Get from user profile API
        email: data.email,
        firstName: "User", // TODO: Get from user profile API
        lastName: "", // TODO: Get from user profile API
      });

      // Navigate to redirect URL or dashboard (with validation as defense-in-depth)
      const redirectTo =
        search.redirect && isValidRedirectUrl(search.redirect)
          ? search.redirect
          : "/dashboard";
      navigate({ to: redirectTo });
    } catch (err) {
      // Handle API errors
      if (err instanceof ApiError) {
        const errorData = err.data as {
          error?: string;
          message?: string;
          remainingAttempts?: number;
          lockoutRemainingSeconds?: number;
          lockedUntil?: string;
          passwordResetUrl?: string;
        };

        // Check for account lockout (HTTP 423)
        if (err.status === 423 || errorData?.error === "ACCOUNT_LOCKED") {
          setLockout({
            isLocked: true,
            remainingSeconds: errorData?.lockoutRemainingSeconds ?? 0,
            lockedUntil: errorData?.lockedUntil ?? "",
            passwordResetUrl: errorData?.passwordResetUrl,
          });
          return;
        }

        // Handle invalid credentials with remaining attempts
        if (errorData?.remainingAttempts !== undefined && errorData.remainingAttempts > 0) {
          setError(`${errorData.message || "Invalid email or password."} (${errorData.remainingAttempts} attempts remaining)`);
        } else {
          setError(errorData?.message || "Invalid email or password. Please try again.");
        }
      } else {
        setError("An unexpected error occurred. Please try again.");
      }
      console.error("Signin error:", err);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">Welcome Back</h1>
          <p className="text-gray-400">Sign in to your ACME account</p>
        </div>

        {logoutMessage && (
          <div
            className="mb-6 p-3 text-sm text-green-600 bg-green-50 dark:bg-green-950/50 border border-green-200 dark:border-green-800 rounded-md"
            role="status"
            aria-live="polite"
          >
            {logoutMessage}
          </div>
        )}

        {lockout?.isLocked && (
          <div
            className="mb-6 p-4 bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 rounded-md"
            role="alert"
            aria-live="assertive"
            data-testid="lockout-message"
          >
            <div className="flex items-start">
              <svg
                className="h-5 w-5 text-red-400 mt-0.5 mr-3 flex-shrink-0"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 20 20"
                fill="currentColor"
                aria-hidden="true"
              >
                <path
                  fillRule="evenodd"
                  d="M10 1a4.5 4.5 0 00-4.5 4.5V9H5a2 2 0 00-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 00-2-2h-.5V5.5A4.5 4.5 0 0010 1zm3 8V5.5a3 3 0 10-6 0V9h6z"
                  clipRule="evenodd"
                />
              </svg>
              <div className="flex-1">
                <h3 className="text-sm font-medium text-red-800 dark:text-red-200">
                  Account Locked
                </h3>
                <div className="mt-1 text-sm text-red-700 dark:text-red-300">
                  <p>
                    Your account has been temporarily locked due to too many failed
                    signin attempts.
                  </p>
                  {lockout.remainingSeconds > 0 && (
                    <p className="mt-2 font-medium" data-testid="lockout-countdown">
                      Please try again in{" "}
                      <span className="tabular-nums">
                        {formatRemainingTime(lockout.remainingSeconds)}
                      </span>
                    </p>
                  )}
                  {lockout.passwordResetUrl && (
                    <p className="mt-3">
                      <a
                        href={lockout.passwordResetUrl}
                        className="font-medium text-red-800 dark:text-red-200 underline hover:text-red-600 dark:hover:text-red-100"
                        data-testid="password-reset-link"
                      >
                        Reset your password
                      </a>{" "}
                      to unlock your account immediately.
                    </p>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        <SigninForm onSubmit={handleSubmit} error={error} isDisabled={lockout?.isLocked} />
      </div>
    </div>
  );
}
