import { AlertTriangle } from "lucide-react";
import { useEffect, useState } from "react";
import { ApiError, identityApi } from "@/services/api";
import { trackInactiveAccountDisplayed } from "@/services/analytics";

export type InactiveAccountReason =
  | "PENDING_VERIFICATION"
  | "SUSPENDED"
  | "DEACTIVATED";

export interface InactiveAccountMessageProps {
  reason: InactiveAccountReason;
  /** Email the customer typed into the signin form. */
  email: string;
  /** Support URL, populated by backend for SUSPENDED / DEACTIVATED. */
  supportUrl?: string;
  /** Support email, populated by backend for SUSPENDED. */
  supportEmail?: string;
  /** Initial cooldown (seconds) for the resend button. */
  resendAvailableIn?: number;
  /** ISO-8601 timestamp when the account was deactivated. */
  deactivatedAt?: string;
}

const COOLDOWN_AFTER_RESEND_SECONDS = 300;

function formatDeactivationDate(iso: string): string {
  try {
    const date = new Date(iso);
    return date.toLocaleDateString(undefined, {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  } catch {
    return iso;
  }
}

function resolutionFor(
  reason: InactiveAccountReason,
): "RESEND_VERIFICATION" | "CONTACT_SUPPORT" | "REACTIVATE" {
  switch (reason) {
    case "PENDING_VERIFICATION":
      return "RESEND_VERIFICATION";
    case "SUSPENDED":
      return "CONTACT_SUPPORT";
    case "DEACTIVATED":
      return "REACTIVATE";
  }
}

export function InactiveAccountMessage({
  reason,
  email,
  supportUrl,
  supportEmail,
  resendAvailableIn = 0,
  deactivatedAt,
}: InactiveAccountMessageProps) {
  const [cooldown, setCooldown] = useState(resendAvailableIn);
  const [resendStatus, setResendStatus] = useState<
    "idle" | "pending" | "success" | "error"
  >("idle");
  const [resendError, setResendError] = useState<string | null>(null);

  useEffect(() => {
    trackInactiveAccountDisplayed({
      accountStatus: reason,
      resolutionOffered: resolutionFor(reason),
    });
  }, [reason]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((c) => (c <= 1 ? 0 : c - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  const handleResend = async () => {
    setResendStatus("pending");
    setResendError(null);
    try {
      await identityApi.resendVerification(email);
      setResendStatus("success");
      setCooldown(COOLDOWN_AFTER_RESEND_SECONDS);
    } catch (err) {
      setResendStatus("error");
      if (err instanceof ApiError) {
        const data = err.data as { retryAfter?: number; message?: string } | undefined;
        if (err.status === 429 && data?.retryAfter) {
          setCooldown(data.retryAfter);
        }
        setResendError(data?.message ?? "Unable to resend right now. Please try again later.");
      } else {
        setResendError("Unable to resend right now. Please try again later.");
      }
    }
  };

  return (
    <div
      className="mb-6 p-4 bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-800 rounded-md"
      role="alert"
      aria-live="assertive"
      data-testid="inactive-account-message"
      data-reason={reason}
    >
      <div className="flex items-start">
        <AlertTriangle
          className="h-5 w-5 text-amber-600 dark:text-amber-400 mt-0.5 mr-3 flex-shrink-0"
          aria-hidden="true"
        />
        <div className="flex-1 text-sm text-amber-900 dark:text-amber-100 space-y-3">
          <h2 className="text-base font-semibold">Account issue</h2>

          {reason === "PENDING_VERIFICATION" && (
            <>
              <p>Please verify your email address to continue.</p>
              <p className="text-xs text-amber-800/80 dark:text-amber-200/80">
                A verification email was sent to{" "}
                <span className="font-medium">{email}</span>.
              </p>
              <button
                type="button"
                onClick={handleResend}
                disabled={cooldown > 0 || resendStatus === "pending"}
                className="inline-flex items-center justify-center px-3 py-2 rounded-md bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
                data-testid="resend-verification-button"
              >
                {cooldown > 0
                  ? `Resend in ${cooldown}s`
                  : resendStatus === "pending"
                    ? "Sending…"
                    : "Resend verification email"}
              </button>
              {resendStatus === "success" && (
                <p
                  className="text-xs text-green-700 dark:text-green-300"
                  role="status"
                  data-testid="resend-verification-success"
                >
                  Verification email sent. Check your inbox.
                </p>
              )}
              {resendStatus === "error" && resendError && (
                <p
                  className="text-xs text-red-700 dark:text-red-300"
                  data-testid="resend-verification-error"
                >
                  {resendError}
                </p>
              )}
            </>
          )}

          {reason === "SUSPENDED" && (
            <>
              <p>Your account has been suspended.</p>
              <p>Please contact support for assistance.</p>
              <div className="flex flex-col gap-1">
                {supportUrl && (
                  <a
                    href={supportUrl}
                    className="inline-flex w-fit items-center justify-center px-3 py-2 rounded-md bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2"
                    data-testid="contact-support-link"
                  >
                    Contact Support
                  </a>
                )}
                {supportEmail && (
                  <p className="text-xs text-amber-800/80 dark:text-amber-200/80">
                    Or email{" "}
                    <a
                      href={`mailto:${supportEmail}`}
                      className="font-medium underline hover:no-underline"
                      data-testid="support-email-link"
                    >
                      {supportEmail}
                    </a>
                  </p>
                )}
              </div>
            </>
          )}

          {reason === "DEACTIVATED" && (
            <>
              <p>
                Your account was deactivated
                {deactivatedAt
                  ? ` on ${formatDeactivationDate(deactivatedAt)}`
                  : ""}
                .
              </p>
              <p>Would you like to reactivate your account?</p>
              <a
                href={`/reactivate?email=${encodeURIComponent(email)}`}
                className="inline-flex w-fit items-center justify-center px-3 py-2 rounded-md bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2"
                data-testid="reactivate-account-link"
              >
                Reactivate Account
              </a>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
