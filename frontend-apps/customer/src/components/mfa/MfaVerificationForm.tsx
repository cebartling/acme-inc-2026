import { useState } from "react";
import { Loader2, Smartphone } from "lucide-react";
import { Link } from "@tanstack/react-router";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";

import { OtpInput } from "./OtpInput";

export interface MfaVerificationFormProps {
  /** Called when the user submits a code */
  onSubmit: (code: string, rememberDevice: boolean) => Promise<void>;
  /** Error message to display */
  error?: string;
  /** Number of remaining verification attempts */
  remainingAttempts?: number;
  /** Whether the form is disabled */
  disabled?: boolean;
}

/**
 * MFA verification form for TOTP code entry.
 *
 * Features:
 * - 6-digit OTP input
 * - Auto-submit on complete code entry
 * - Remember device checkbox
 * - Remaining attempts warning
 * - Help link
 */
export function MfaVerificationForm({
  onSubmit,
  error,
  remainingAttempts,
  disabled = false,
}: MfaVerificationFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [rememberDevice, setRememberDevice] = useState(false);

  const handleComplete = async (code: string) => {
    setIsSubmitting(true);
    try {
      await onSubmit(code, rememberDevice);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card className="w-full max-w-md mx-auto">
      <CardHeader className="text-center">
        <div className="mx-auto mb-4 w-12 h-12 bg-primary/10 rounded-full flex items-center justify-center">
          <Smartphone className="w-6 h-6 text-primary" />
        </div>
        <CardTitle>Two-Factor Authentication</CardTitle>
        <CardDescription>
          Enter the 6-digit code from your authenticator app
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {error && (
          <div
            className="p-3 text-sm text-red-600 bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 rounded-md"
            role="alert"
            aria-live="polite"
          >
            {error}
          </div>
        )}

        {remainingAttempts !== undefined && remainingAttempts <= 2 && (
          <div
            className="p-3 text-sm text-yellow-600 bg-yellow-50 dark:bg-yellow-950/50 border border-yellow-200 dark:border-yellow-800 rounded-md"
            role="alert"
            aria-live="polite"
            data-testid="remaining-attempts-warning"
          >
            {remainingAttempts === 1
              ? "1 attempt remaining"
              : `${remainingAttempts} attempts remaining`}
          </div>
        )}

        <div className="relative">
          <OtpInput
            length={6}
            onComplete={handleComplete}
            disabled={disabled || isSubmitting}
            autoFocus
            error={!!error}
          />

          {isSubmitting && (
            <div className="absolute inset-0 flex items-center justify-center bg-background/80">
              <Loader2 className="h-6 w-6 animate-spin text-primary" />
            </div>
          )}
        </div>

        <div className="flex items-center space-x-2 justify-center">
          <Checkbox
            id="rememberDevice"
            checked={rememberDevice}
            onCheckedChange={(checked) => setRememberDevice(!!checked)}
            disabled={disabled || isSubmitting}
          />
          <Label
            htmlFor="rememberDevice"
            className="text-sm font-medium leading-none cursor-pointer"
          >
            Remember this device for 30 days
          </Label>
        </div>
      </CardContent>
      <CardFooter className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground text-center">
          Open your authenticator app (Google Authenticator, Authy, etc.) to view
          your verification code.
        </p>
        <div className="text-center">
          <Link
            to="/signin"
            className="text-sm text-primary hover:underline"
          >
            Back to sign in
          </Link>
          {" | "}
          <a
            href="https://www.acme.com/help/mfa"
            target="_blank"
            rel="noopener noreferrer"
            className="text-sm text-primary hover:underline"
          >
            Having trouble?
          </a>
        </div>
      </CardFooter>
    </Card>
  );
}
