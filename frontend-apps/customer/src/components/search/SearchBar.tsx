import { useEffect, useRef, useState } from "react";
import { useAutocomplete } from "@/hooks/useAutocomplete";
import { AutocompleteDropdown } from "./AutocompleteDropdown";
import type { AutocompleteSuggestion } from "@/services/api";
import { addRecentSearch } from "@/services/recentSearches";
import { trackAutocompleteSelected } from "@/services/analytics";
import { useIsAuthenticated, useCustomerId } from "@/stores/auth.store";

interface SearchBarProps {
  defaultValue?: string;
  onSearch: (query: string) => void;
}

export function SearchBar({ defaultValue = "", onSearch }: SearchBarProps) {
  const [value, setValue] = useState(defaultValue);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const isAuthenticated = useIsAuthenticated();
  const customerId = useCustomerId();

  const [activeDescendant, setActiveDescendant] = useState<
    string | undefined
  >();
  const { suggestions, isLoading, isOpen, setIsOpen } = useAutocomplete(value);

  useEffect(() => {
    setValue(defaultValue);
  }, [defaultValue]);

  const executeSearch = (query: string) => {
    if (isAuthenticated && customerId && query.trim()) {
      addRecentSearch(customerId, query.trim());
    }
    onSearch(query);
    setIsOpen(false);
  };

  const handleSelect = (suggestion: AutocompleteSuggestion) => {
    trackAutocompleteSelected({
      query: value,
      selectedText: suggestion.text,
      selectedType: suggestion.type,
      positionIndex: suggestions.indexOf(suggestion),
    });
    setValue(suggestion.text);
    executeSearch(suggestion.text);
  };

  const handleSubmit = () => {
    setIsOpen(false);
    executeSearch(value);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      handleSubmit();
    } else if (e.key === "Escape") {
      setIsOpen(false);
      inputRef.current?.focus();
    }
  };

  const handleFocus = () => {
    if (value.length >= 2) {
      setIsOpen(true);
    }
  };

  const handleBlur = (e: React.FocusEvent) => {
    if (
      containerRef.current &&
      !containerRef.current.contains(e.relatedTarget as Node)
    ) {
      setIsOpen(false);
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setValue(newValue);
    if (newValue.length >= 2) {
      setIsOpen(true);
    } else {
      setIsOpen(false);
    }
  };

  const dropdownId = "autocomplete-listbox";

  return (
    <div ref={containerRef} className="relative flex gap-2" onBlur={handleBlur}>
      <div className="relative flex-1">
        <input
          ref={inputRef}
          type="text"
          value={value}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          onFocus={handleFocus}
          placeholder="Search products…"
          aria-label="Search products"
          aria-autocomplete="list"
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          aria-controls={isOpen ? dropdownId : undefined}
          aria-activedescendant={isOpen ? activeDescendant : undefined}
          data-testid="searchInput"
          className="w-full rounded-lg bg-gray-800 px-4 py-2 text-white placeholder-gray-400 outline-none focus:ring-2 focus:ring-cyan-500"
        />
        {value.length > 0 && (
          <button
            type="button"
            onClick={() => {
              setValue("");
              setIsOpen(false);
            }}
            data-testid="searchClearButton"
            className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white"
            aria-label="Clear search"
          >
            &times;
          </button>
        )}
      </div>
      <button
        type="button"
        onClick={handleSubmit}
        data-testid="searchSubmitButton"
        className="rounded-lg bg-cyan-600 px-4 py-2 font-medium text-white hover:bg-cyan-700 transition-colors"
      >
        Search
      </button>
      {isOpen && (
        <div id={dropdownId}>
          <AutocompleteDropdown
            suggestions={suggestions}
            isLoading={isLoading}
            onSelect={handleSelect}
            onHighlightChange={setActiveDescendant}
          />
        </div>
      )}
    </div>
  );
}
