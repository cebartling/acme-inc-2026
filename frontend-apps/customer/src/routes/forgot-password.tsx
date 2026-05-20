import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { ApiError, identityApi } from "@/services/api";

export const Route = createFileRoute("/forgot-password")({
  component: ForgotPasswordPage,
});

function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState<"idle" | "pending" | "sent" | "error">(
    "idle",
  );
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setStatus("pending");
    setError(null);
    try {
      await identityApi.requestPasswordReset(email);
      setStatus("sent");
    } catch (err) {
      setStatus("error");
      if (err instanceof ApiError) {
        const data = err.data as { message?: string } | undefined;
        setError(data?.message ?? "Unable to submit password reset request.");
      } else {
        setError("Unable to submit password reset request.");
      }
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">
            Forgot your password?
          </h1>
          <p className="text-gray-400">
            Enter your email and we&apos;ll send you a link to reset it.
          </p>
        </div>

        {status === "sent" ? (
          <div
            className="p-4 bg-green-50 dark:bg-green-950/50 border border-green-200 dark:border-green-800 rounded-md text-sm text-green-800 dark:text-green-200"
            role="status"
            aria-live="polite"
            data-testid="forgot-password-sent"
          >
            <p className="font-medium">Check your inbox.</p>
            <p className="mt-1">
              If an account exists with this email, a password reset link has
              been sent. The link expires in 1 hour.
            </p>
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
            data-testid="forgot-password-form"
          >
            <div>
              <label
                htmlFor="forgot-password-email"
                className="block text-sm font-medium text-gray-200 mb-1"
              >
                Email
              </label>
              <input
                id="forgot-password-email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-slate-900 text-white border border-slate-700 focus:border-amber-500 focus:outline-none focus:ring-1 focus:ring-amber-500"
                data-testid="forgot-password-email-input"
              />
            </div>

            {error && (
              <p
                className="text-sm text-red-400"
                role="alert"
                data-testid="forgot-password-error"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={status === "pending"}
              className="w-full inline-flex items-center justify-center px-3 py-2 rounded-md bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
              data-testid="forgot-password-submit"
            >
              {status === "pending" ? "Sending…" : "Send Reset Link"}
            </button>

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
