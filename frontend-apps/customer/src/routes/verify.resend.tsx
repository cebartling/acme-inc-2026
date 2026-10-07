import { useState } from "react";
import { createFileRoute, Link, useSearch } from "@tanstack/react-router";
import { z } from "zod";
import { ApiError, identityApi } from "@/services/api";

// Identity redirects an expired or invalid verification link here (PIN-346).
// An unknown value is dropped rather than breaking the page.
const verifyResendSearchSchema = z.object({
  error: z.enum(["expired", "invalid"]).optional().catch(undefined),
});

export const Route = createFileRoute("/verify/resend")({
  component: VerifyResendPage,
  validateSearch: verifyResendSearchSchema,
});

const LINK_MESSAGES = {
  expired: "Your verification link has expired. Request a new one.",
  invalid: "Invalid verification link. Request a new one.",
} as const;

function VerifyResendPage() {
  const { error: linkError } = useSearch({ from: "/verify/resend" });
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState<"idle" | "pending" | "sent" | "error">(
    "idle",
  );
  const [error, setError] = useState<string | null>(null);
  const [requestsRemaining, setRequestsRemaining] = useState<number | null>(
    null,
  );

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setStatus("pending");
    setError(null);
    try {
      const response = await identityApi.resendVerification(email);
      setRequestsRemaining(response.requestsRemaining ?? null);
      setStatus("sent");
    } catch (err) {
      setStatus("error");
      if (err instanceof ApiError) {
        const data = err.data as { message?: string } | undefined;
        setError(data?.message ?? "Unable to send a new verification link.");
      } else {
        setError("Unable to send a new verification link.");
      }
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">
            Verify your email
          </h1>
          <p className="text-gray-400">
            Enter your email and we&apos;ll send you a new verification link.
          </p>
        </div>

        {linkError && status !== "sent" && (
          <div
            className="mb-6 p-3 text-sm text-amber-700 bg-amber-50 dark:bg-amber-950/50 border border-amber-200 dark:border-amber-800 rounded-md"
            role="status"
            aria-live="polite"
            data-testid="verify-resend-link-error"
          >
            {LINK_MESSAGES[linkError]}
          </div>
        )}

        {status === "sent" ? (
          <div
            className="p-4 bg-green-50 dark:bg-green-950/50 border border-green-200 dark:border-green-800 rounded-md text-sm text-green-800 dark:text-green-200"
            role="status"
            aria-live="polite"
            data-testid="verify-resend-sent"
          >
            <p className="font-medium">Check your inbox.</p>
            <p className="mt-1">
              If an account with this email is waiting for verification, a new
              link has been sent.
            </p>
            {requestsRemaining !== null && (
              <p className="mt-1" data-testid="verify-resend-remaining">
                {requestsRemaining}{" "}
                {requestsRemaining === 1 ? "request" : "requests"} remaining
                this hour.
              </p>
            )}
            <p className="mt-3">
              <Link to="/signin" className="font-medium underline">
                Back to sign in
              </Link>
            </p>
          </div>
        ) : (
          <form
            onSubmit={handleSubmit}
            className="space-y-4 bg-slate-800/60 p-6 rounded-md border border-slate-700"
            data-testid="verify-resend-form"
          >
            <div>
              <label
                htmlFor="verify-resend-email"
                className="block text-sm font-medium text-gray-200 mb-1"
              >
                Email
              </label>
              <input
                id="verify-resend-email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-slate-900 text-white border border-slate-700 focus:border-amber-500 focus:outline-none focus:ring-1 focus:ring-amber-500"
                data-testid="verify-resend-email-input"
              />
            </div>

            {error && (
              <p
                className="text-sm text-red-400"
                role="alert"
                data-testid="verify-resend-error"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={status === "pending"}
              className="w-full inline-flex items-center justify-center px-3 py-2 rounded-md bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
              data-testid="verify-resend-submit"
            >
              {status === "pending" ? "Sending…" : "Send New Link"}
            </button>

            <p className="text-xs text-gray-400 text-center">
              You can request up to 3 links per hour.
            </p>

            <p className="text-xs text-gray-400 text-center">
              <Link to="/signin" className="underline hover:no-underline">
                Back to sign in
              </Link>
            </p>
          </form>
        )}
      </div>
    </div>
  );
}
