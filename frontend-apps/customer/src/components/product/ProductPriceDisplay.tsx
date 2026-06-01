import type { TierPricingEntry } from "@/services/api";

interface ProductPriceDisplayProps {
  price: number;
  originalPrice?: number | null;
  tierPricing?: TierPricingEntry[];
}

export function ProductPriceDisplay({
  price,
  originalPrice,
  tierPricing = [],
}: ProductPriceDisplayProps) {
  const hasDiscount = originalPrice != null && originalPrice > price;
  const discountPct = hasDiscount
    ? Math.round((1 - price / originalPrice!) * 100)
    : 0;

  return (
    <div aria-live="polite">
      <div className="flex items-baseline gap-3">
        <span className="text-3xl font-bold text-cyan-400">
          ${price.toFixed(2)}
        </span>
        {hasDiscount && (
          <>
            <span className="text-lg text-slate-500 line-through">
              ${originalPrice!.toFixed(2)}
            </span>
            <span className="rounded bg-red-600 px-2 py-0.5 text-sm font-semibold text-white">
              {discountPct}% Off
            </span>
          </>
        )}
      </div>
      {tierPricing.length > 0 && (
        <div className="mt-2 space-y-1" aria-label="Tier pricing">
          {tierPricing.map((tier) => (
            <p key={tier.minQuantity} className="text-sm text-slate-400">
              Buy {tier.minQuantity}+ for{" "}
              <span className="font-medium text-cyan-400">
                ${tier.price.toFixed(2)}
              </span>{" "}
              each
            </p>
          ))}
        </div>
      )}
    </div>
  );
}
