import { Package } from "lucide-react";
import { CommandItem } from "@/components/ui/command";
import type { AutocompleteSuggestion } from "@/services/api";

interface AutocompleteProductItemProps {
  id?: string;
  suggestion: AutocompleteSuggestion;
  onSelect: (suggestion: AutocompleteSuggestion) => void;
}

export function AutocompleteProductItem({
  id,
  suggestion,
  onSelect,
}: AutocompleteProductItemProps) {
  return (
    <CommandItem
      id={id}
      value={suggestion.text}
      data-testid="autocomplete-product-item"
      onSelect={() => onSelect(suggestion)}
    >
      <Package className="size-4 text-muted-foreground" />
      <span>{suggestion.text}</span>
    </CommandItem>
  );
}
