import { useState, useEffect, useCallback } from "react";
import {
  createFileRoute,
  useNavigate,
  useSearch,
  Link,
} from "@tanstack/react-router";
import { z } from "zod";
import { AlertCircle, MessageSquare, Smartphone } from "lucide-react";
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
 * MFA method type.
 */
type MfaMethod = "TOTP" | "SMS";

/**
 * State for MFA verification.
 */
interface MfaState {
  mfaToken: string;
  email: string;
  redirect?: string;
  mfaMethods?: string[];
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

  // Current MFA method (default to TOTP, then SMS if TOTP not available)
  const [currentMethod, setCurrentMethod] = useState<MfaMethod>("TOTP");

  // SMS-specific state
  const [maskedPhone, setMaskedPhone] = useState<string | undefined>(undefined);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [isResending, setIsResending] = useState(false);

  // Determine available methods from mfaState
  const availableMethods = mfaState?.mfaMethods || [];
  const hasTOTP = availableMethods.includes("TOTP");
  const hasSMS = availableMethods.includes("SMS");

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

  // Set initial method based on available methods (runs after mfaState is set)
  useEffect(() => {
    if (!mfaState) return;

    const methods = mfaState.mfaMethods || [];
    if (methods.includes("TOTP")) {
      setCurrentMethod("TOTP");
    } else if (methods.includes("SMS")) {
      setCurrentMethod("SMS");
    }
  }, [mfaState]);

  // Resend cooldown timer
  useEffect(() => {
    if (resendCooldown <= 0) return;

    const timer = setInterval(() => {
      setResendCooldown((prev) => Math.max(0, prev - 1));
    }, 1000);

    return () => clearInterval(timer);
  }, [resendCooldown]);

  const handleResendCode = useCallback(async () => {
    if (!mfaState || isResending || resendCooldown > 0) return;

    setIsResending(true);
    setError(undefined);

    try {
      const response = await identityApi.resendMfaCode({
        mfaToken: mfaState.mfaToken,
        method: "SMS",
      });

      setMaskedPhone(response.maskedPhone);
      setResendCooldown(response.resendAvailableIn);
    } catch (err) {
      if (err instanceof ApiError) {
        const errorData = err.data as {
          error?: string;
          message?: string;
          resendAvailableIn?: number;
          retryAfter?: number;
        };

        if (errorData?.error === "MFA_EXPIRED" || errorData?.error === "INVALID_MFA_TOKEN") {
          setIsExpired(true);
          setError("Your verification session has expired. Please sign in again.");
          sessionStorage.removeItem("mfaState");
          return;
        }

        if (errorData?.resendAvailableIn) {
          setResendCooldown(errorData.resendAvailableIn);
        }

        if (errorData?.retryAfter) {
          setResendCooldown(errorData.retryAfter);
        }

        setError(errorData?.message || "Failed to resend code. Please try again.");
      } else {
        setError("An unexpected error occurred. Please try again.");
      }
    } finally {
      setIsResending(false);
    }
  }, [mfaState, isResending, resendCooldown]);

  const handleSubmit = async (code: string, rememberDevice: boolean) => {
    if (!mfaState) return;

    setError(undefined);
    setRemainingAttempts(undefined);

    try {
      const response = await identityApi.verifyMfa({
        mfaToken: mfaState.mfaToken,
        code,
        method: currentMethod,
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

  const handleSwitchMethod = (method: MfaMethod) => {
    setCurrentMethod(method);
    setError(undefined);
    setRemainingAttempts(undefined);
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

  // SMS-specific description with masked phone
  const isSmsMethod = currentMethod === "SMS";
  const smsDescription = maskedPhone
    ? `Enter the 6-digit code sent to ${maskedPhone}`
    : "Enter the 6-digit code sent to your phone";

  // SMS resend button as footer extra
  const smsFooterExtra = isSmsMethod ? (
    <div className="text-center">
      <Button
        variant="link"
        onClick={handleResendCode}
        disabled={isExpired || isResending || resendCooldown > 0}
        className="text-sm"
      >
        {isResending
          ? "Sending..."
          : resendCooldown > 0
          ? `Resend code in ${resendCooldown}s`
          : "Resend code"}
      </Button>
    </div>
  ) : undefined;

  // Method switcher component (only show if multiple methods available)
  const methodSwitcher = hasTOTP && hasSMS ? (
    <div className="flex justify-center gap-2 mb-6">
      <Button
        variant={currentMethod === "TOTP" ? "default" : "outline"}
        size="sm"
        onClick={() => handleSwitchMethod("TOTP")}
        disabled={isExpired}
      >
        <Smartphone className="w-4 h-4 mr-2" />
        Authenticator
      </Button>
      <Button
        variant={currentMethod === "SMS" ? "default" : "outline"}
        size="sm"
        onClick={() => handleSwitchMethod("SMS")}
        disabled={isExpired}
      >
        <MessageSquare className="w-4 h-4 mr-2" />
        SMS
      </Button>
    </div>
  ) : null;

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">Verify Your Identity</h1>
          <p className="text-gray-400">
            We need to verify it&apos;s really you
          </p>
        </div>

        {methodSwitcher}

        <MfaVerificationForm
          onSubmit={handleSubmit}
          error={error}
          remainingAttempts={remainingAttempts}
          disabled={isExpired}
          method={currentMethod}
          description={isSmsMethod ? smsDescription : undefined}
          footerExtra={smsFooterExtra}
        />
      </div>
    </div>
  );
}
