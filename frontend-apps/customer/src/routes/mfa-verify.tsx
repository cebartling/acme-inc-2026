import { useState, useEffect } from "react";
import {
  createFileRoute,
  useNavigate,
  useSearch,
  Link,
} from "@tanstack/react-router";
import { z } from "zod";
import { AlertCircle } from "lucide-react";
import { MfaVerificationForm } from "@/components/mfa";
import { useAuthStore } from "@/stores/auth.store";
import { identityApi, ApiError } from "@/services/api";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

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
  const [stateChecked, setStateChecked] = useState(false);
  const [missingState, setMissingState] = useState(false);

  useEffect(() => {
    // Prevent re-checking if already done
    if (stateChecked) return;

    // Try to get MFA state from search params first
    if (search.token && search.email) {
      setMfaState({
        mfaToken: search.token,
        email: search.email,
        redirect: search.redirect,
      });
      setStateChecked(true);
      return;
    }

    // Fall back to session storage
    const storedState = sessionStorage.getItem("mfaState");
    if (storedState) {
      try {
        const parsed = JSON.parse(storedState);
        if (parsed.mfaToken && parsed.email) {
          setMfaState(parsed);
        } else {
          setMissingState(true);
        }
      } catch {
        // Invalid JSON in storage
        sessionStorage.removeItem("mfaState");
        setMissingState(true);
      }
    } else {
      // No MFA state available
      setMissingState(true);
    }
    setStateChecked(true);
  }, [search.token, search.email, search.redirect, stateChecked]);

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

      // Store user info from response
      setUser({
        userId: response.userId,
        customerId: response.userId,
        email: response.email,
        firstName: response.firstName,
        lastName: response.lastName,
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

  // Show loading while checking for MFA state
  if (!stateChecked) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4 flex items-center justify-center">
        <div className="text-white">Loading...</div>
      </div>
    );
  }

  // Show error state if no valid MFA state was found
  if (missingState || !mfaState) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4 flex items-center justify-center">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <div className="mx-auto mb-4 w-12 h-12 bg-red-100 dark:bg-red-900/30 rounded-full flex items-center justify-center">
              <AlertCircle className="w-6 h-6 text-red-600 dark:text-red-400" />
            </div>
            <CardTitle>Session Expired</CardTitle>
            <CardDescription>
              Your verification session is missing or has expired.
              Please sign in again to continue.
            </CardDescription>
          </CardHeader>
          <CardContent className="text-center text-sm text-muted-foreground">
            <p>
              This can happen if you refreshed the page, your session timed out,
              or you navigated directly to this page.
            </p>
          </CardContent>
          <CardFooter className="flex justify-center">
            <Button asChild>
              <Link to="/signin">Back to Sign In</Link>
            </Button>
          </CardFooter>
        </Card>
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
