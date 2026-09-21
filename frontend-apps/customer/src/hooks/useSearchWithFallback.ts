import { useQuery } from "@tanstack/react-query";
import { productApi } from "@/services/api";
import { trackSearchExecuted } from "@/services/analytics";
import type { SearchFilters, SearchResponse } from "@/services/api";

export interface UseSearchWithFallbackParams {
  q: string;
  page: number;
  sort: "relevance" | "price_asc" | "price_desc" | "newest";
  filters: SearchFilters;
  activeCategories: string[];
  priceMin?: number;
  priceMax?: number;
}

export interface UseSearchWithFallbackResult {
  data: SearchResponse | undefined;
  isLoading: boolean;
  /**
   * True when the search page should show category browsing instead of results.
   *
   * Driven by the query failing rather than by the breaker's state directly: the
   * breaker only opens on the fifth failure, but leaving the customer with a blank
   * results area for the first four would be the very thing AC-0004-09-06 forbids.
   * The fallback is shown from the first failure; the breaker's job is to stop us
   * calling a service we know is down.
   */
  isSearchUnavailable: boolean;
}

/**
 * Runs a product search, reporting when the caller should fall back to category
 * browsing (US-0004-09).
 *
 * React Query's retries are disabled here on purpose. The circuit breaker counts
 * consecutive failures, and the default `retry: 3` would spend four attempts on a
 * single user-initiated search — making the "5 consecutive failures" threshold in
 * AC-0004-09-01 mean five *searches* or twenty *requests* depending on where you
 * look, and hammering a service that is already struggling.
 */
export function useSearchWithFallback({
  q,
  page,
  sort,
  filters,
  activeCategories,
  priceMin,
  priceMax,
}: UseSearchWithFallbackParams): UseSearchWithFallbackResult {
  const { data, isLoading, isError } = useQuery<SearchResponse>({
    queryKey: ["search", q, page, sort, activeCategories, priceMin, priceMax],
    queryFn: async () => {
      const result = await productApi.search({
        query: q,
        page,
        pageSize: 24,
        sort,
        filters: {
          categories: filters.categories ?? [],
          priceMin: filters.priceMin,
          priceMax: filters.priceMax,
        },
      });
      trackSearchExecuted({
        query: q,
        totalResults: result.totalResults,
        page: result.page,
        executionTimeMs: result.executionTimeMs,
      });
      return result;
    },
    enabled: q.length > 0,
    retry: false,
  });

  return {
    data,
    isLoading,
    isSearchUnavailable: isError,
  };
}
