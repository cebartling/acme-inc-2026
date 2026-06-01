import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import type { ProductVariant } from "@/services/api";

interface ColorSwatchSelectorProps {
  variants: ProductVariant[];
  selectedVariantId: string | undefined;
  onSelect: (variantId: string) => void;
}

const COLOR_MAP: Record<string, string> = {
  Black: "bg-gray-900 border-gray-700",
  White: "bg-white border-gray-300",
  Silver: "bg-gray-400 border-gray-500",
  Navy: "bg-blue-900 border-blue-700",
  Red: "bg-red-600 border-red-700",
  Blue: "bg-blue-600 border-blue-700",
  Green: "bg-green-600 border-green-700",
};

export function ColorSwatchSelector({
  variants,
  selectedVariantId,
  onSelect,
}: ColorSwatchSelectorProps) {
  return (
    <div>
      <p className="mb-2 text-sm font-medium text-slate-300">Color</p>
      <ToggleGroup
        type="single"
        value={selectedVariantId ?? ""}
        onValueChange={(value) => {
          if (value) onSelect(value);
        }}
        className="flex flex-wrap gap-2"
        aria-label="Select color variant"
      >
        {variants.map((variant) => {
          const colorClass = variant.color
            ? (COLOR_MAP[variant.color] ?? "bg-slate-600 border-slate-500")
            : "bg-slate-600 border-slate-500";
          const isSelected = variant.id === selectedVariantId;

          return (
            <ToggleGroupItem
              key={variant.id}
              value={variant.id}
              aria-label={`Color: ${variant.color ?? variant.name}`}
              aria-pressed={isSelected}
              aria-disabled={!variant.inStock}
              className={[
                "relative h-8 w-8 rounded-full border-2 p-0 transition-all",
                colorClass,
                isSelected
                  ? "ring-2 ring-indigo-400 ring-offset-2 ring-offset-slate-800"
                  : "",
                !variant.inStock ? "opacity-40 cursor-not-allowed" : "",
              ]
                .filter(Boolean)
                .join(" ")}
              title={
                variant.inStock
                  ? (variant.color ?? variant.name)
                  : `${variant.color ?? variant.name} — Out of Stock`
              }
            >
              {!variant.inStock && (
                <span
                  aria-hidden="true"
                  className="absolute inset-0 flex items-center justify-center"
                >
                  <span className="block h-px w-full rotate-45 bg-slate-400" />
                </span>
              )}
            </ToggleGroupItem>
          );
        })}
      </ToggleGroup>
    </div>
  );
}
