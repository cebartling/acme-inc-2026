interface ProductPriceDisplayProps {
  price: number;
  originalPrice?: number;
}

export function ProductPriceDisplay({
  price,
  originalPrice,
}: ProductPriceDisplayProps) {
  const hasDiscount = originalPrice !== undefined && originalPrice > price;
  const discountPct = hasDiscount
    ? Math.round((1 - price / originalPrice!) * 100)
    : 0;

  return (
    <div className="flex items-baseline gap-3" aria-live="polite">
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
  );
}
