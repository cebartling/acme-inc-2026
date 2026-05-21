import { useEffect, useState } from "react";
import {
  createFileRoute,
  Link,
  useNavigate,
  useSearch,
} from "@tanstack/react-router";
import { z } from "zod";
import {
  ApiError,
  identityApi,
  type PasswordRequirement,
} from "@/services/api";

const resetPasswordSearchSchema = z.object({
  token: z.string().min(1),
});

export const Route = createFileRoute("/reset-password")({
  component: ResetPasswordPage,
  validateSearch: resetPasswordSearchSchema,
});

type Phase =
  | "validating"
  | "form"
  | "submitting"
  | "done"
  | "expired"
  | "error";

function ResetPasswordPage() {
  const { token } = useSearch({ from: "/reset-password" });
  const navigate = useNavigate();

  const [phase, setPhase] = useState<Phase>("validating");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [requirements, setRequirements] = useState<
    PasswordRequirement[] | null
  >(null);
  // Incrementing this triggers a fresh token-validation attempt (retry).
  const [validationKey, setValidationKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await identityApi.validatePasswordResetToken(token);
        if (!cancelled) setPhase("form");
      } catch (err) {
        if (!cancelled) {
          // Only show "expired" when the API explicitly says the token is
          // invalid. Network failures, 5xx responses, etc. show a retryable
          // error state instead of misleading the user into thinking their
          // link is gone.
          if (err instanceof ApiError) {
            const data = err.data as { error?: string } | undefined;
            setPhase(
              data?.error === "INVALID_RESET_TOKEN" ? "expired" : "error",
            );
          } else {
            setPhase("error");
          }
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token, validationKey]);

  useEffect(() => {
    if (phase !== "done") return;
    const handle = setTimeout(() => {
      navigate({ to: "/signin" });
    }, 3000);
    return () => clearTimeout(handle);
  }, [phase, navigate]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setRequirements(null);

    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }

    setPhase("submitting");
    try {
      await identityApi.confirmPasswordReset(token, password);
      setPhase("done");
    } catch (err) {
      setPhase("form");
      if (err instanceof ApiError) {
        const data = err.data as
          | {
              error?: string;
              message?: string;
              requirements?: PasswordRequirement[];
            }
          | undefined;
        if (
          data?.error === "PASSWORD_REQUIREMENTS_NOT_MET" &&
          data.requirements
        ) {
          setRequirements(data.requirements);
          setError(data.message ?? "Password does not meet requirements.");
        } else if (data?.error === "INVALID_RESET_TOKEN") {
          setPhase("expired");
        } else {
          setError(data?.message ?? "Unable to reset password.");
        }
      } else {
        setError("Unable to reset password.");
      }
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">
            Set a new password
          </h1>
        </div>

        {phase === "validating" && (
          <p
            className="text-center text-gray-300"
            data-testid="reset-password-validating"
          >
            Checking your reset link…
          </p>
        )}

        {phase === "expired" && (
          <div
            className="p-4 bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 rounded-md text-sm text-red-800 dark:text-red-200"
            role="alert"
            data-testid="reset-password-expired"
          >
            <p className="font-medium">
              This password reset link is invalid or has expired.
            </p>
            <p className="mt-3">
              <Link to="/forgot-password" className="font-medium underline">
                Request a new link
              </Link>
            </p>
          </div>
        )}

        {phase === "error" && (
          <div
            className="p-4 bg-yellow-50 dark:bg-yellow-950/50 border border-yellow-200 dark:border-yellow-800 rounded-md text-sm text-yellow-800 dark:text-yellow-200"
            role="alert"
            data-testid="reset-password-load-error"
          >
            <p className="font-medium">
              Something went wrong checking your reset link.
            </p>
            <p className="mt-1">
              This is a temporary problem — please try again.
            </p>
            <p className="mt-3">
              <button
                type="button"
                className="font-medium underline"
                onClick={() => {
                  setPhase("validating");
                  setValidationKey((k) => k + 1);
                }}
              >
                Try again
              </button>
            </p>
          </div>
        )}

        {phase === "done" && (
          <div
            className="p-4 bg-green-50 dark:bg-green-950/50 border border-green-200 dark:border-green-800 rounded-md text-sm text-green-800 dark:text-green-200"
            role="status"
            aria-live="polite"
            data-testid="reset-password-success"
          >
            <p className="font-medium">Password updated.</p>
            <p className="mt-1">
              Please sign in with your new password. You&apos;ll be redirected
              shortly.
            </p>
            <p className="mt-3">
              <Link
                to="/signin"
                className="font-medium underline"
                data-testid="reset-password-signin-now"
              >
                Sign in now
              </Link>
            </p>
          </div>
        )}

        {(phase === "form" || phase === "submitting") && (
          <form
            onSubmit={handleSubmit}
            className="space-y-4 bg-slate-800/60 p-6 rounded-md border border-slate-700"
            data-testid="reset-password-form"
          >
            <div>
              <label
                htmlFor="reset-password-new"
                className="block text-sm font-medium text-gray-200 mb-1"
              >
                New password
              </label>
              <input
                id="reset-password-new"
                type="password"
                required
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-slate-900 text-white border border-slate-700 focus:border-amber-500 focus:outline-none focus:ring-1 focus:ring-amber-500"
                data-testid="reset-password-new-input"
              />
            </div>
            <div>
              <label
                htmlFor="reset-password-confirm"
                className="block text-sm font-medium text-gray-200 mb-1"
              >
                Confirm new password
              </label>
              <input
                id="reset-password-confirm"
                type="password"
                required
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                className="w-full px-3 py-2 rounded-md bg-slate-900 text-white border border-slate-700 focus:border-amber-500 focus:outline-none focus:ring-1 focus:ring-amber-500"
                data-testid="reset-password-confirm-input"
              />
            </div>

            {requirements && (
              <ul
                className="text-xs space-y-1"
                data-testid="reset-password-requirements"
              >
                {requirements.map((req) => (
                  <li
                    key={req.rule}
                    className={req.met ? "text-green-400" : "text-red-400"}
                  >
                    {req.met ? "✓" : "✗"} {req.detail}
                  </li>
                ))}
              </ul>
            )}

            {error && (
              <p
                className="text-sm text-red-400"
                role="alert"
                data-testid="reset-password-error"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={phase === "submitting"}
              className="w-full inline-flex items-center justify-center px-3 py-2 rounded-md bg-amber-600 text-white text-sm font-medium hover:bg-amber-700 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 disabled:opacity-60 disabled:cursor-not-allowed"
              data-testid="reset-password-submit"
            >
              {phase === "submitting" ? "Updating…" : "Update password"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
