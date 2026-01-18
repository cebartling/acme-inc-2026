import { useState } from "react";
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

  // Show logout message if redirected after logout
  const logoutMessage =
    search.logout === "true" ? "You have been signed out." : undefined;

  const handleSubmit = async (data: SigninFormData) => {
    setError(undefined);

    try {
      // Call Identity Service API
      const response = await identityApi.signin({
        email: data.email,
        password: data.password,
        rememberMe: data.rememberMe,
      });

      if (response.status === "MFA_REQUIRED") {
        // TODO: Navigate to MFA verification page
        // For now, show a message that MFA is not yet implemented
        setError("MFA verification is required but not yet implemented.");
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
        // Use the error message from the API response
        const errorData = err.data as { message?: string; remainingAttempts?: number };
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

        <SigninForm onSubmit={handleSubmit} error={error} />
      </div>
    </div>
  );
}
