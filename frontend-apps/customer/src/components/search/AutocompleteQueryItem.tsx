import { Clock } from "lucide-react";
import { CommandItem } from "@/components/ui/command";
import type { AutocompleteSuggestion } from "@/services/api";

interface AutocompleteQueryItemProps {
  suggestion: AutocompleteSuggestion;
  onSelect: (suggestion: AutocompleteSuggestion) => void;
}

export function AutocompleteQueryItem({
  suggestion,
  onSelect,
}: AutocompleteQueryItemProps) {
  return (
    <CommandItem
      data-testid="autocomplete-query-item"
      onSelect={() => onSelect(suggestion)}
    >
      <Clock className="size-4 text-muted-foreground" />
      <span>{suggestion.text}</span>
    </CommandItem>
  );
}
