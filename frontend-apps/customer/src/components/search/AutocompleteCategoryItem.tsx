import { FolderOpen } from "lucide-react";
import { CommandItem } from "@/components/ui/command";
import type { AutocompleteSuggestion } from "@/services/api";

interface AutocompleteCategoryItemProps {
  suggestion: AutocompleteSuggestion;
  onSelect: (suggestion: AutocompleteSuggestion) => void;
}

export function AutocompleteCategoryItem({
  suggestion,
  onSelect,
}: AutocompleteCategoryItemProps) {
  return (
    <CommandItem
      data-testid="autocomplete-category-item"
      onSelect={() => onSelect(suggestion)}
    >
      <FolderOpen className="size-4 text-muted-foreground" />
      <span>{suggestion.text}</span>
    </CommandItem>
  );
}
