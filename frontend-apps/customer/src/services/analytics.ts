/**
 * Analytics tracking seam.
 *
 * No real analytics provider is wired up yet. This module exists so
 * application code can emit named events without coupling to a specific
 * vendor. In development, events are logged to the console; in production,
 * they are a no-op until a provider is connected.
 *
 * TODO: integrate a real analytics provider (Mixpanel / Segment / etc.)
 * and replace the body of trackEvent here.
 */
export function trackEvent(
  name: string,
  properties?: Record<string, unknown>,
): void {
  if (import.meta.env.DEV) {
    // eslint-disable-next-line no-console
    console.debug("[analytics]", name, properties ?? {});
  }
}

export interface SigninFailedProperties {
  errorType: string;
  attemptNumber?: number;
}

export function trackSigninFailed(properties: SigninFailedProperties): void {
  trackEvent("SigninFailed", {
    source: "WEB",
    errorType: properties.errorType,
    attemptNumber: properties.attemptNumber,
  });
}

export interface InactiveAccountDisplayedProperties {
  accountStatus: "PENDING_VERIFICATION" | "SUSPENDED" | "DEACTIVATED";
  resolutionOffered: "RESEND_VERIFICATION" | "CONTACT_SUPPORT" | "REACTIVATE";
}

export function trackInactiveAccountDisplayed(
  properties: InactiveAccountDisplayedProperties,
): void {
  trackEvent("InactiveAccountDisplayed", {
    source: "WEB",
    accountStatus: properties.accountStatus,
    resolutionOffered: properties.resolutionOffered,
  });
}

export interface SearchExecutedProperties {
  query: string;
  totalResults: number;
  page: number;
  executionTimeMs: number;
  sessionId?: string;
}

export function trackSearchExecuted(
  properties: SearchExecutedProperties,
): void {
  trackEvent("SearchExecuted", {
    source: "WEB",
    query: properties.query,
    totalResults: properties.totalResults,
    page: properties.page,
    executionTimeMs: properties.executionTimeMs,
    sessionId: properties.sessionId,
  });
}

export interface FiltersAppliedProperties {
  query: string;
  categories: string[];
  priceMin?: number;
  priceMax?: number;
  resultCount: number;
  sessionId?: string;
}

export function trackFiltersApplied(
  properties: FiltersAppliedProperties,
): void {
  trackEvent("FiltersApplied", {
    source: "WEB",
    query: properties.query,
    categories: properties.categories,
    priceMin: properties.priceMin,
    priceMax: properties.priceMax,
    resultCount: properties.resultCount,
    sessionId: properties.sessionId,
  });
}

export interface AutocompleteSelectedProperties {
  query: string;
  selectedText: string;
  selectedType: "product" | "category" | "query";
  positionIndex: number;
}

export function trackAutocompleteSelected(
  properties: AutocompleteSelectedProperties,
): void {
  trackEvent("AutocompleteSelected", {
    source: "WEB",
    query: properties.query,
    selectedText: properties.selectedText,
    selectedType: properties.selectedType,
    positionIndex: properties.positionIndex,
  });
}
