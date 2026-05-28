import {
  createFileRoute,
  useNavigate,
  useSearch,
} from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { searchParamsSchema } from "@/schemas/search.schema";
import { productApi } from "@/services/api";
import { trackSearchExecuted, trackFiltersApplied } from "@/services/analytics";
import { useSearchFilters } from "@/hooks/useSearchFilters";
import {
  SearchBar,
  SearchResults,
  SearchEmptyState,
  SearchPagination,
  SearchSortSelector,
  FilterPanel,
  ActiveFiltersBar,
  MobileFilterDrawer,
} from "@/components/search";
import type { SearchResponse } from "@/services/api";

export const Route = createFileRoute("/search")({
  validateSearch: searchParamsSchema,
  component: SearchPage,
});

function SearchPage() {
  const navigate = useNavigate();
  const { q, page, sort } = useSearch({ from: "/search" });

  const {
    filters,
    activeCategories,
    priceMin,
    priceMax,
    hasActiveFilters,
    activeFilterCount,
    toggleCategory,
    removeCategory,
    setPriceRange,
    clearPriceRange,
    clearAll,
  } = useSearchFilters();

  const { data, isLoading } = useQuery<SearchResponse>({
    queryKey: ["search", q, page, sort, activeCategories, priceMin, priceMax],
    queryFn: async () => {
      const result = await productApi.search({
        query: q,
        page,
        pageSize: 24,
        sort: sort as "relevance" | "price_asc" | "price_desc" | "newest",
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
  });

  useEffect(() => {
    if (data && hasActiveFilters) {
      trackFiltersApplied({
        query: q,
        categories: activeCategories,
        priceMin,
        priceMax,
        resultCount: data.totalResults,
      });
    }
  }, [data, hasActiveFilters, q, activeCategories, priceMin, priceMax]);

  const handleSearch = (newQuery: string) => {
    navigate({
      to: "/search",
      search: { q: newQuery, page: 1, sort, category: [] },
    });
  };

  const handlePageChange = (newPage: number) => {
    navigate({ to: "/search", search: (prev) => ({ ...prev, page: newPage }) });
  };

  const handleSortChange = (newSort: string) => {
    navigate({
      to: "/search",
      search: (prev) => ({
        ...prev,
        page: 1,
        sort: newSort as "relevance" | "price_asc" | "price_desc" | "newest",
      }),
    });
  };

  const handleSuggestionClick = (suggestion: string) => {
    navigate({
      to: "/search",
      search: { q: suggestion, page: 1, sort, category: [] },
    });
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center">
        <div className="text-center">
          <div
            className="animate-spin rounded-full h-8 w-8 border-b-2 border-white mx-auto"
            aria-busy="true"
          />
          <p className="mt-2 text-sm text-slate-400">Searching...</p>
        </div>
      </div>
    );
  }

  const hasResults = data && data.totalResults > 0;

  return (
    <div
      data-testid="searchPage"
      className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-8 px-4"
    >
      <div className="max-w-6xl mx-auto">
        <div className="mb-6">
          <SearchBar defaultValue={q} onSearch={handleSearch} />
        </div>

        {q.length > 0 && data && (
          <div className="mb-4 flex items-center justify-between gap-4">
            <MobileFilterDrawer
              facets={data.facets}
              activeCategories={activeCategories}
              activeFilterCount={activeFilterCount}
              priceMin={priceMin}
              priceMax={priceMax}
              onToggleCategory={toggleCategory}
              onPriceRangeApply={setPriceRange}
              onPriceRangeClear={clearPriceRange}
            />
            <div className="ml-auto">
              <SearchSortSelector value={sort} onChange={handleSortChange} />
            </div>
          </div>
        )}

        {q.length > 0 && data && hasActiveFilters && (
          <ActiveFiltersBar
            activeCategories={activeCategories}
            priceMin={priceMin}
            priceMax={priceMax}
            onRemoveCategory={removeCategory}
            onClearPriceRange={clearPriceRange}
            onClearAll={clearAll}
          />
        )}

        {q.length === 0 && (
          <p className="text-slate-400 text-center mt-12">
            Enter a search query above to find products.
          </p>
        )}

        {q.length > 0 && data && (
          <div className="flex gap-6">
            {hasResults && (
              <FilterPanel
                facets={data.facets}
                activeCategories={activeCategories}
                priceMin={priceMin}
                priceMax={priceMax}
                onToggleCategory={toggleCategory}
                onPriceRangeApply={setPriceRange}
                onPriceRangeClear={clearPriceRange}
              />
            )}

            <div className="flex-1">
              {hasResults ? (
                <SearchResults
                  query={q}
                  totalResults={data.totalResults}
                  results={data.results}
                >
                  {data.totalPages > 1 && (
                    <SearchPagination
                      currentPage={page}
                      totalPages={data.totalPages}
                      onPageChange={handlePageChange}
                    />
                  )}
                </SearchResults>
              ) : (
                <SearchEmptyState
                  query={q}
                  spellingSuggestion={data.spellingSuggestion}
                  onSuggestionClick={handleSuggestionClick}
                />
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
