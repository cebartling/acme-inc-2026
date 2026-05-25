interface SearchEmptyStateProps {
  query: string;
  spellingSuggestion?: string | null;
  onSuggestionClick?: (suggestion: string) => void;
}

export function SearchEmptyState({
  query,
  spellingSuggestion,
  onSuggestionClick,
}: SearchEmptyStateProps) {
  return (
    <div data-testid="searchEmptyState" className="mt-8 text-center">
      <p className="text-lg text-gray-300">
        No results found for &lsquo;{query}&rsquo;
      </p>
      {spellingSuggestion && (
        <p className="mt-2 text-gray-400">
          Did you mean:{" "}
          <button
            type="button"
            data-testid="searchSpellingSuggestion"
            onClick={() => onSuggestionClick?.(spellingSuggestion)}
            className="text-cyan-400 underline hover:text-cyan-300"
          >
            {spellingSuggestion}
          </button>
        </p>
      )}
    </div>
  );
}
