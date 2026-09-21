import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { useSearchWithFallback } from "./useSearchWithFallback";
import { productApi } from "@/services/api";

vi.mock("@/services/api", () => ({
  productApi: { search: vi.fn() },
}));

vi.mock("@/services/analytics", () => ({
  trackSearchExecuted: vi.fn(),
}));

const mockedSearch = vi.mocked(productApi.search);

function makeWrapper() {
  const queryClient = new QueryClient({
    // No retries here either: the hook sets retry:false per-query, and a client-level
    // default would mask a regression in that.
    defaultOptions: { queries: { retry: 0 } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

const params = {
  q: "widget",
  page: 1,
  sort: "relevance" as const,
  filters: {},
  activeCategories: [],
  priceMin: undefined,
  priceMax: undefined,
};

const searchResponse = {
  query: "widget",
  totalResults: 1,
  page: 1,
  pageSize: 24,
  totalPages: 1,
  results: [
    {
      id: "p1",
      slug: "widget-one",
      name: "Widget One",
      price: 9.99,
      category: "Electronics",
    },
  ],
  facets: { categories: {} },
  spellingSuggestion: null,
  executionTimeMs: 4,
};

describe("useSearchWithFallback", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns results and reports search as available on success", async () => {
    mockedSearch.mockResolvedValue(searchResponse);

    const { result } = renderHook(() => useSearchWithFallback(params), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.isSearchUnavailable).toBe(false);
    expect(result.current.data?.totalResults).toBe(1);
  });

  it("reports search as unavailable on the first failure", async () => {
    mockedSearch.mockRejectedValue(new Error("service down"));

    const { result } = renderHook(() => useSearchWithFallback(params), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => expect(result.current.isSearchUnavailable).toBe(true));
  });

  it("does not retry a failed search", async () => {
    mockedSearch.mockRejectedValue(new Error("service down"));

    const { result } = renderHook(() => useSearchWithFallback(params), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => expect(result.current.isSearchUnavailable).toBe(true));
    // React Query's default retry:3 would make this 4 and break the breaker's
    // "5 consecutive failures" threshold.
    expect(mockedSearch).toHaveBeenCalledTimes(1);
  });

  it("re-runs the search when retrySearch is called, despite an unchanged query", async () => {
    mockedSearch.mockRejectedValue(new Error("service down"));

    const { result } = renderHook(() => useSearchWithFallback(params), {
      wrapper: makeWrapper(),
    });
    await waitFor(() => expect(result.current.isSearchUnavailable).toBe(true));
    expect(mockedSearch).toHaveBeenCalledTimes(1);

    // The regression this guards: the queryKey has not changed, so without an explicit
    // refetch React Query serves the cached failure and no recovery probe is ever sent.
    await act(async () => {
      result.current.retrySearch();
    });

    await waitFor(() => expect(mockedSearch).toHaveBeenCalledTimes(2));
  });

  it("clears the unavailable state when a retry succeeds", async () => {
    mockedSearch.mockRejectedValueOnce(new Error("service down"));

    const { result } = renderHook(() => useSearchWithFallback(params), {
      wrapper: makeWrapper(),
    });
    await waitFor(() => expect(result.current.isSearchUnavailable).toBe(true));

    mockedSearch.mockResolvedValue(searchResponse);
    await act(async () => {
      result.current.retrySearch();
    });

    await waitFor(() => expect(result.current.isSearchUnavailable).toBe(false));
    expect(result.current.data?.totalResults).toBe(1);
  });

  it("does not search at all for an empty query", async () => {
    mockedSearch.mockResolvedValue(searchResponse);

    renderHook(() => useSearchWithFallback({ ...params, q: "" }), {
      wrapper: makeWrapper(),
    });

    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(mockedSearch).not.toHaveBeenCalled();
  });
});
