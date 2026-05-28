import { ActiveFilterBadge } from "./ActiveFilterBadge";

interface ActiveFiltersBarProps {
  activeCategories: string[];
  priceMin: number | undefined;
  priceMax: number | undefined;
  onRemoveCategory: (category: string) => void;
  onClearPriceRange: () => void;
  onClearAll: () => void;
}

export function ActiveFiltersBar({
  activeCategories,
  priceMin,
  priceMax,
  onRemoveCategory,
  onClearPriceRange,
  onClearAll,
}: ActiveFiltersBarProps) {
  const hasPriceFilter = priceMin !== undefined || priceMax !== undefined;
  const hasFilters = activeCategories.length > 0 || hasPriceFilter;

  if (!hasFilters) return null;

  const priceLabel =
    priceMin !== undefined && priceMax !== undefined
      ? `Price: $${priceMin} – $${priceMax}`
      : priceMin !== undefined
        ? `Price: $${priceMin}+`
        : `Price: up to $${priceMax}`;

  return (
    <div
      data-testid="activeFiltersBar"
      className="flex flex-wrap items-center gap-2 py-2"
    >
      {activeCategories.map((cat) => (
        <ActiveFilterBadge
          key={cat}
          label={`Category: ${cat}`}
          onRemove={() => onRemoveCategory(cat)}
        />
      ))}

      {hasPriceFilter && (
        <ActiveFilterBadge label={priceLabel} onRemove={onClearPriceRange} />
      )}

      <button
        type="button"
        onClick={onClearAll}
        className="text-xs text-slate-400 underline hover:text-slate-200"
      >
        Clear All Filters
      </button>
    </div>
  );
}
