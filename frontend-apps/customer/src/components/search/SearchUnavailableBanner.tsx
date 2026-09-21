import { AlertTriangle } from "lucide-react";

export interface SearchUnavailableBannerProps {
  /** Re-runs the search. */
  onRetry: () => void;
  /** Disables the retry control while an attempt is in flight. */
  isRetrying?: boolean;
}

/**
 * Tells the customer why there are no search results and points them at the
 * category browsing below (AC-0004-09-03).
 *
 * The retry control exists because re-typing the same search term does not re-run the
 * query — the queryKey is unchanged, so React Query serves the cached failure and the
 * circuit breaker's recovery probe never fires.
 */
export function SearchUnavailableBanner({
  onRetry,
  isRetrying = false,
}: SearchUnavailableBannerProps) {
  return (
    <div
      className="rounded-md border border-amber-700 bg-amber-950/50 p-4"
      role="alert"
      aria-live="polite"
      data-testid="searchUnavailableBanner"
    >
      <div className="flex items-start">
        <AlertTriangle
          className="mt-0.5 mr-3 h-5 w-5 flex-shrink-0 text-amber-400"
          aria-hidden="true"
        />
        <div className="flex-1 text-sm text-amber-200">
          <p className="font-semibold text-amber-100">
            Search is temporarily unavailable. Browse by category instead.
          </p>
          <p className="mt-1">
            Pick a category below to keep shopping. Search will come back
            automatically.
          </p>
          <button
            type="button"
            onClick={onRetry}
            disabled={isRetrying}
            className="mt-3 rounded-md border border-amber-600 px-3 py-1.5 text-sm font-medium text-amber-100 transition-colors hover:bg-amber-900/60 disabled:cursor-not-allowed disabled:opacity-60"
            data-testid="searchRetryButton"
          >
            {isRetrying ? "Trying again..." : "Try search again"}
          </button>
        </div>
      </div>
    </div>
  );
}
