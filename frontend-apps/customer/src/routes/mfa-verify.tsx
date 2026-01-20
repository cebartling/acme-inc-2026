import { useState, useEffect } from "react";
import {
  createFileRoute,
  useNavigate,
  useSearch,
} from "@tanstack/react-router";
import { z } from "zod";
import { MfaVerificationForm } from "@/components/mfa";
import { useAuthStore } from "@/stores/auth.store";
import { identityApi, ApiError } from "@/services/api";

/**
 * State for MFA verification.
 */
interface MfaState {
  mfaToken: string;
  email: string;
  redirect?: string;
}

const mfaVerifySearchSchema = z.object({
  token: z.string().optional(),
  email: z.string().optional(),
  redirect: z.string().optional(),
});

export const Route = createFileRoute("/mfa-verify")({
  component: MfaVerifyPage,
  validateSearch: mfaVerifySearchSchema,
});

function MfaVerifyPage() {
  const navigate = useNavigate();
  const search = useSearch({ from: "/mfa-verify" });
  const setUser = useAuthStore((state) => state.setUser);
  const [error, setError] = useState<string | undefined>(undefined);
  const [remainingAttempts, setRemainingAttempts] = useState<number | undefined>(undefined);
  const [isExpired, setIsExpired] = useState(false);

  // Get MFA state from search params or session storage
  const [mfaState, setMfaState] = useState<MfaState | null>(null);

  useEffect(() => {
    // Try to get MFA state from search params first
    if (search.token && search.email) {
      setMfaState({
        mfaToken: search.token,
        email: search.email,
        redirect: search.redirect,
      });
      return;
    }

    // Fall back to session storage
    const storedState = sessionStorage.getItem("mfaState");
    if (storedState) {
      try {
        setMfaState(JSON.parse(storedState));
      } catch {
        // Invalid state, redirect to signin
        navigate({ to: "/signin" });
      }
    } else {
      // No MFA state, redirect to signin
      navigate({ to: "/signin" });
    }
  }, [search.token, search.email, search.redirect, navigate]);

  const handleSubmit = async (code: string, rememberDevice: boolean) => {
    if (!mfaState) return;

    setError(undefined);
    setRemainingAttempts(undefined);

    try {
      const response = await identityApi.verifyMfa({
        mfaToken: mfaState.mfaToken,
        code,
        method: "TOTP",
        rememberDevice,
      });

      // Clear MFA state from session storage
      sessionStorage.removeItem("mfaState");

      // Store user info
      setUser({
        userId: response.userId,
        customerId: response.userId,
        email: mfaState.email,
        firstName: "User",
        lastName: "",
      });

      // Navigate to redirect URL or dashboard
      const redirectTo = mfaState.redirect || "/dashboard";
      navigate({ to: redirectTo });
    } catch (err) {
      if (err instanceof ApiError) {
        const errorData = err.data as {
          error?: string;
          message?: string;
          remainingAttempts?: number;
        };

        // Check for expired challenge
        if (errorData?.error === "MFA_EXPIRED" || errorData?.error === "INVALID_MFA_TOKEN") {
          setIsExpired(true);
          setError("Your verification session has expired. Please sign in again.");
          // Clear MFA state
          sessionStorage.removeItem("mfaState");
          return;
        }

        // Handle invalid code with remaining attempts
        if (errorData?.remainingAttempts !== undefined) {
          setRemainingAttempts(errorData.remainingAttempts);

          if (errorData.remainingAttempts === 0) {
            setIsExpired(true);
            setError("Too many failed attempts. Please sign in again.");
            sessionStorage.removeItem("mfaState");
            return;
          }
        }

        setError(errorData?.message || "Invalid verification code. Please try again.");
      } else {
        setError("An unexpected error occurred. Please try again.");
      }
      console.error("MFA verification error:", err);
    }
  };

  if (!mfaState) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4 flex items-center justify-center">
        <div className="text-white">Loading...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">Verify Your Identity</h1>
          <p className="text-gray-400">
            We need to verify it&apos;s really you
          </p>
        </div>

        <MfaVerificationForm
          onSubmit={handleSubmit}
          error={error}
          remainingAttempts={remainingAttempts}
          disabled={isExpired}
        />
      </div>
    </div>
  );
}
