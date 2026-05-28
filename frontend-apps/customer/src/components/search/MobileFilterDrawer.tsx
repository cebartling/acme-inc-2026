import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import type { SearchFacets } from "@/services/api";
import { CheckboxFilter } from "./CheckboxFilter";
import { FilterSection } from "./FilterSection";
import { PriceRangeFilter } from "./PriceRangeFilter";

interface MobileFilterDrawerProps {
  facets: SearchFacets | undefined;
  activeCategories: string[];
  activeFilterCount: number;
  priceMin: number | undefined;
  priceMax: number | undefined;
  onToggleCategory: (category: string) => void;
  onPriceRangeApply: (min: number | undefined, max: number | undefined) => void;
  onPriceRangeClear: () => void;
}

export function MobileFilterDrawer({
  facets,
  activeCategories,
  activeFilterCount,
  priceMin,
  priceMax,
  onToggleCategory,
  onPriceRangeApply,
  onPriceRangeClear,
}: MobileFilterDrawerProps) {
  const categoryOptions = Object.entries(facets?.categories ?? {}).map(
    ([value, count]) => ({ value, label: value, count }),
  );

  return (
    <Dialog>
      <DialogTrigger asChild>
        <button
          type="button"
          className="flex items-center gap-2 rounded-md border border-slate-600 bg-slate-800 px-4 py-2 text-sm text-slate-200 hover:bg-slate-700 md:hidden"
        >
          Filters
          {activeFilterCount > 0 && (
            <span className="rounded-full bg-indigo-600 px-1.5 py-0.5 text-xs text-white">
              {activeFilterCount}
            </span>
          )}
        </button>
      </DialogTrigger>

      <DialogContent
        aria-label="Search filters"
        className="max-h-[80vh] overflow-y-auto bg-slate-900 text-slate-200"
      >
        <DialogHeader>
          <DialogTitle className="text-slate-100">Filters</DialogTitle>
        </DialogHeader>

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
      </DialogContent>
    </Dialog>
  );
}
