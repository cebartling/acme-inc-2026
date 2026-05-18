import { useState } from "react";
import { createFileRoute, Link, useSearch } from "@tanstack/react-router";
import { z } from "zod";
import { ApiError, identityApi } from "@/services/api";

const reactivateSearchSchema = z.object({
  email: z.string().email().optional(),
});

export const Route = createFileRoute("/reactivate")({
  component: ReactivatePage,
  validateSearch: reactivateSearchSchema,
});

function ReactivatePage() {
  const search = useSearch({ from: "/reactivate" });
  const [email, setEmail] = useState(search.email ?? "");
  const [password, setPassword] = useState("");
  const [status, setStatus] = useState<"idle" | "pending" | "sent" | "error">(
    "idle",
  );
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setStatus("pending");
    setError(null);
    try {
      await identityApi.reactivateAccount(email, password);
      setStatus("sent");
      setPassword("");
    } catch (err) {
      setStatus("error");
      if (err instanceof ApiError) {
        const data = err.data as { message?: string } | undefined;
        setError(data?.message ?? "Unable to submit reactivation request.");
      } else {
        setError("Unable to submit reactivation request.");
      }
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">
            Reactivate your account
          </h1>
          <p className="text-gray-400">
            Confirm your credentials and we&apos;ll email you a reactivation
            link.
          </p>
        </div>

        {status === "sent" ? (
          <div
            className="p-4 bg-green-50 dark:bg-green-950/50 border border-green-200 dark:border-green-800 rounded-md text-sm text-green-800 dark:text-green-200"
            role="status"
            aria-live="polite"
            data-testid="reactivate-sent"
          >
            <p className="font-medium">Check your inbox.</p>
            <p className="mt-1">
              If an eligible account exists for {email}, a reactivation email
              has been sent. The link expires in 24 hours.
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
            data-testid="reactivate-form"
          >
            <div>
              <label
                htmlFor="reactivate-email"
                className="block text-sm font-medium text-gray-200 mb-1"
              >
                Email
              </label>
              <input
                id="reactivate-email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-slate-900 text-white border border-slate-700 focus:border-amber-500 focus:outline-none focus:ring-1 focus:ring-amber-500"
                data-testid="reactivate-email-input"
              />
            </div>
            <div>
              <label
                htmlFor="reactivate-password"
                className="block text-sm font-medium text-gray-200 mb-1"
              >
                Password
              </label>
              <input
                id="reactivate-password"
                type="password"
                required
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-slate-900 text-white border border-slate-700 focus:border-amber-500 focus:outline-none focus:ring-1 focus:ring-amber-500"
                data-testid="reactivate-password-input"
              />
            </div>

            {error && (
              <p
                className="text-sm text-red-400"
                role="alert"
                data-testid="reactivate-error"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={status === "pending"}
              className="w-full inline-flex items-center justify-center px-3 py-2 rounded-md bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
              data-testid="reactivate-submit"
            >
              {status === "pending" ? "Sending…" : "Send reactivation email"}
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
