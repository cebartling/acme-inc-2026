import { AlertTriangle } from "lucide-react";

/**
 * Tells the customer why there are no search results and points them at the
 * category browsing below (AC-0004-09-03).
 */
export function SearchUnavailableBanner() {
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
        </div>
      </div>
    </div>
  );
}
