import type { SearchFacets } from "@/services/api";
import { CheckboxFilter } from "./CheckboxFilter";
import { FilterSection } from "./FilterSection";
import { PriceRangeFilter } from "./PriceRangeFilter";

interface FilterPanelProps {
  facets: SearchFacets | undefined;
  activeCategories: string[];
  priceMin: number | undefined;
  priceMax: number | undefined;
  onToggleCategory: (category: string) => void;
  onPriceRangeApply: (min: number | undefined, max: number | undefined) => void;
  onPriceRangeClear: () => void;
}

export function FilterPanel({
  facets,
  activeCategories,
  priceMin,
  priceMax,
  onToggleCategory,
  onPriceRangeApply,
  onPriceRangeClear,
}: FilterPanelProps) {
  const categoryOptions = Object.entries(facets?.categories ?? {}).map(
    ([value, count]) => ({ value, label: value, count }),
  );

  return (
    <aside
      data-testid="filterPanel"
      aria-label="Search filters"
      className="w-56 shrink-0"
    >
      <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-400">
        Filters
      </h2>

      {categoryOptions.length > 0 && (
        <FilterSection title="Category">
          <CheckboxFilter
            options={categoryOptions}
            selectedValues={activeCategories}
            onToggle={onToggleCategory}
          />
        </FilterSection>
      )}

      <FilterSection title="Price">
        <PriceRangeFilter
          priceMin={priceMin}
          priceMax={priceMax}
          onApply={onPriceRangeApply}
          onClear={onPriceRangeClear}
        />
      </FilterSection>
    </aside>
  );
}
