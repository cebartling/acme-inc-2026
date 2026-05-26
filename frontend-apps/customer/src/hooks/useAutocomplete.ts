import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { productApi, type AutocompleteSuggestion } from "@/services/api";
import { useDebouncedValue } from "./useDebouncedValue";
import { useIsAuthenticated, useCustomerId } from "@/stores/auth.store";
import { getRecentSearches } from "@/services/recentSearches";

interface UseAutocompleteReturn {
  suggestions: AutocompleteSuggestion[];
  isLoading: boolean;
  isOpen: boolean;
  setIsOpen: (open: boolean) => void;
}

export function useAutocomplete(inputValue: string) {
  const [isOpen, setIsOpen] = useState(false);
  const debouncedQuery = useDebouncedValue(inputValue, 150);
  const isAuthenticated = useIsAuthenticated();
  const customerId = useCustomerId();

  const { data, isLoading } = useQuery({
    queryKey: ["autocomplete", debouncedQuery],
    queryFn: () => productApi.autocomplete(debouncedQuery),
    enabled: debouncedQuery.length >= 2,
    staleTime: 30_000,
  });

  const recentSearches = useMemo(
    () => (isAuthenticated && customerId ? getRecentSearches(customerId) : []),
    [isAuthenticated, customerId],
  );

  const suggestions = useMemo(() => {
    const backendSuggestions = data?.suggestions ?? [];

    if (!isAuthenticated || !customerId) return backendSuggestions;

    const matchingRecent = recentSearches
      .filter(
        (s) =>
          s.toLowerCase().startsWith(inputValue.toLowerCase()) &&
          !backendSuggestions.some(
            (bs) => bs.text.toLowerCase() === s.toLowerCase(),
          ),
      )
      .map(
        (s): AutocompleteSuggestion => ({
          type: "query",
          text: s,
        }),
      );

    return [...backendSuggestions, ...matchingRecent].slice(0, 8);
  }, [data, isAuthenticated, customerId, inputValue, recentSearches]);

  const result: UseAutocompleteReturn = {
    suggestions,
    isLoading: debouncedQuery.length >= 2 && isLoading,
    isOpen:
      isOpen &&
      (suggestions.length > 0 || (debouncedQuery.length >= 2 && isLoading)),
    setIsOpen,
  };

  return result;
}
