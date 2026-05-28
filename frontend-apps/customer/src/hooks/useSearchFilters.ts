import { useNavigate, useSearch } from "@tanstack/react-router";
import type { SearchFilters } from "@/services/api";

export interface UseSearchFiltersReturn {
  filters: SearchFilters;
  activeCategories: string[];
  priceMin: number | undefined;
  priceMax: number | undefined;
  hasActiveFilters: boolean;
  activeFilterCount: number;
  setCategories: (categories: string[]) => void;
  toggleCategory: (category: string) => void;
  removeCategory: (category: string) => void;
  setPriceRange: (min: number | undefined, max: number | undefined) => void;
  clearPriceRange: () => void;
  clearAll: () => void;
}

export function useSearchFilters(): UseSearchFiltersReturn {
  const navigate = useNavigate();
  const { category, priceMin, priceMax } = useSearch({
    from: "/search",
  });

  const filters: SearchFilters = {
    categories: category.length > 0 ? category : undefined,
    priceMin,
    priceMax,
  };

  const hasActiveFilters =
    category.length > 0 || priceMin !== undefined || priceMax !== undefined;

  const activeFilterCount =
    category.length +
    (priceMin !== undefined || priceMax !== undefined ? 1 : 0);

  const updateSearch = (
    patch: Partial<{
      category: string[];
      priceMin: number | undefined;
      priceMax: number | undefined;
    }>,
  ) => {
    navigate({
      to: "/search",
      search: (prev) => ({ ...prev, page: 1, ...patch }),
    });
  };

  const setCategories = (categories: string[]) => {
    updateSearch({ category: categories });
  };

  const toggleCategory = (cat: string) => {
    const next = category.includes(cat)
      ? category.filter((c) => c !== cat)
      : [...category, cat];
    updateSearch({ category: next });
  };

  const removeCategory = (cat: string) => {
    updateSearch({ category: category.filter((c) => c !== cat) });
  };

  const setPriceRange = (min: number | undefined, max: number | undefined) => {
    updateSearch({ priceMin: min, priceMax: max });
  };

  const clearPriceRange = () => {
    updateSearch({ priceMin: undefined, priceMax: undefined });
  };

  const clearAll = () => {
    navigate({
      to: "/search",
      search: (prev) => ({
        q: prev.q,
        sort: prev.sort,
        page: 1,
        category: [],
        priceMin: undefined,
        priceMax: undefined,
      }),
    });
  };

  return {
    filters,
    activeCategories: category,
    priceMin,
    priceMax,
    hasActiveFilters,
    activeFilterCount,
    setCategories,
    toggleCategory,
    removeCategory,
    setPriceRange,
    clearPriceRange,
    clearAll,
  };
}
