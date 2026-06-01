import type { ProductVariant } from "@/services/api";
import { ColorSwatchSelector } from "./ColorSwatchSelector";
import { SizeButtonSelector } from "./SizeButtonSelector";

interface VariantSelectorProps {
  variants: ProductVariant[];
  selectedVariantId: string | undefined;
  onSelect: (variantId: string) => void;
}

function hasColors(variants: ProductVariant[]): boolean {
  return variants.some((v) => v.color !== null);
}

function hasSizes(variants: ProductVariant[]): boolean {
  return variants.some((v) => v.size !== null);
}

export function VariantSelector({
  variants,
  selectedVariantId,
  onSelect,
}: VariantSelectorProps) {
  if (variants.length === 0) return null;

  return (
    <div className="space-y-4" data-testid="variantSelector">
      {hasColors(variants) && (
        <ColorSwatchSelector
          variants={variants}
          selectedVariantId={selectedVariantId}
          onSelect={onSelect}
        />
      )}
      {hasSizes(variants) && (
        <SizeButtonSelector
          variants={variants}
          selectedVariantId={selectedVariantId}
          onSelect={onSelect}
        />
      )}
    </div>
  );
}
