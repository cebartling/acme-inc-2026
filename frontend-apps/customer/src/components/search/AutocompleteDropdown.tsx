import { Loader2 } from "lucide-react";
import { Command, CommandGroup, CommandList } from "@/components/ui/command";
import type { AutocompleteSuggestion } from "@/services/api";
import { AutocompleteProductItem } from "./AutocompleteProductItem";
import { AutocompleteCategoryItem } from "./AutocompleteCategoryItem";
import { AutocompleteQueryItem } from "./AutocompleteQueryItem";

interface AutocompleteDropdownProps {
  suggestions: AutocompleteSuggestion[];
  isLoading: boolean;
  onSelect: (suggestion: AutocompleteSuggestion) => void;
}

export function AutocompleteDropdown({
  suggestions,
  isLoading,
  onSelect,
}: AutocompleteDropdownProps) {
  const products = suggestions.filter((s) => s.type === "product");
  const categories = suggestions.filter((s) => s.type === "category");
  const queries = suggestions.filter((s) => s.type === "query");

  const hasContent = suggestions.length > 0 || isLoading;
  if (!hasContent) return null;

  return (
    <div
      data-testid="autocomplete-dropdown"
      className="absolute top-full left-0 z-50 mt-1 w-full rounded-lg border border-gray-700 bg-gray-900 shadow-lg"
    >
      <Command className="bg-transparent" shouldFilter={false}>
        <CommandList>
          {isLoading && suggestions.length === 0 && (
            <div className="flex items-center justify-center py-4">
              <Loader2 className="size-4 animate-spin text-gray-400" />
            </div>
          )}
          {products.length > 0 && (
            <CommandGroup heading="Products">
              {products.map((s) => (
                <AutocompleteProductItem
                  key={`product-${s.productId ?? s.text}`}
                  suggestion={s}
                  onSelect={onSelect}
                />
              ))}
            </CommandGroup>
          )}
          {categories.length > 0 && (
            <CommandGroup heading="Categories">
              {categories.map((s) => (
                <AutocompleteCategoryItem
                  key={`category-${s.categorySlug ?? s.text}`}
                  suggestion={s}
                  onSelect={onSelect}
                />
              ))}
            </CommandGroup>
          )}
          {queries.length > 0 && (
            <CommandGroup heading="Recent Searches">
              {queries.map((s) => (
                <AutocompleteQueryItem
                  key={`query-${s.text}`}
                  suggestion={s}
                  onSelect={onSelect}
                />
              ))}
            </CommandGroup>
          )}
        </CommandList>
      </Command>
    </div>
  );
}
