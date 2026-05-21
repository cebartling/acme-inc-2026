import { AlertTriangle } from "lucide-react";

export interface SigninErrorBannerProps {
  message: string;
  remainingAttempts?: number;
}

export function SigninErrorBanner({
  message,
  remainingAttempts,
}: SigninErrorBannerProps) {
  const isUrgent = remainingAttempts !== undefined && remainingAttempts <= 2;

  const containerClasses = isUrgent
    ? "p-4 bg-red-100 dark:bg-red-950/60 border-2 border-red-500 dark:border-red-700 rounded-md"
    : "p-3 bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-800 rounded-md";

  const headingClasses = isUrgent
    ? "text-sm font-semibold text-red-800 dark:text-red-200"
    : "text-sm font-medium text-red-700 dark:text-red-300";

  return (
    <div
      className={containerClasses}
      role="alert"
      aria-live="assertive"
      data-testid="signin-error-banner"
    >
      <div className="flex items-start">
        <AlertTriangle
          className={
            isUrgent
              ? "h-5 w-5 text-red-600 dark:text-red-400 mt-0.5 mr-3 flex-shrink-0"
              : "h-4 w-4 text-red-600 dark:text-red-400 mt-0.5 mr-2 flex-shrink-0"
          }
          aria-hidden="true"
        />
        <div className="flex-1 text-sm text-red-700 dark:text-red-300">
          <p className={headingClasses}>{message}</p>

          {remainingAttempts !== undefined && (
            <p
              className={isUrgent ? "mt-1 font-semibold" : "mt-1"}
              data-testid="signin-remaining-attempts"
            >
              {remainingAttempts} attempt
              {remainingAttempts === 1 ? "" : "s"} remaining
              {isUrgent ? " before account lockout." : "."}
            </p>
          )}

          <p className={isUrgent ? "mt-3" : "mt-2"}>
            Forgot your password?{" "}
            <a
              href="/forgot-password"
              className="font-medium underline hover:no-underline"
              data-testid="signin-error-reset-link"
            >
              {isUrgent ? "Reset Password" : "Reset it here"}
            </a>
            {isUrgent && " to avoid lockout."}
          </p>
        </div>
      </div>
    </div>
  );
}
