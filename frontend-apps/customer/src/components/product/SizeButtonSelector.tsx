import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { ProductVariant } from "@/services/api";

interface SizeButtonSelectorProps {
  variants: ProductVariant[];
  selectedVariantId: string | undefined;
  onSelect: (variantId: string) => void;
}

export function SizeButtonSelector({
  variants,
  selectedVariantId,
  onSelect,
}: SizeButtonSelectorProps) {
  return (
    <div>
      <p className="mb-2 text-sm font-medium text-slate-300">Size</p>
      <ToggleGroup
        type="single"
        value={selectedVariantId ?? ""}
        onValueChange={(value) => {
          if (value) onSelect(value);
        }}
        className="flex flex-wrap gap-2"
        aria-label="Select size variant"
      >
        {variants.map((variant) => (
          <ToggleGroupItem
            key={variant.id}
            value={variant.id}
            aria-label={`Size: ${variant.size ?? variant.name}`}
            aria-pressed={variant.id === selectedVariantId}
            aria-disabled={!variant.inStock}
            className={[
              "min-w-[3rem] rounded border border-slate-600 px-3 py-1 text-sm font-medium transition-colors",
              variant.id === selectedVariantId
                ? "border-indigo-400 bg-indigo-600 text-white"
                : "bg-slate-700 text-slate-200 hover:bg-slate-600",
              !variant.inStock
                ? "line-through opacity-50 cursor-not-allowed"
                : "",
            ]
              .filter(Boolean)
              .join(" ")}
          >
            {variant.size ?? variant.name}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  );
}
