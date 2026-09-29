import { useQueries } from "@tanstack/react-query";
import { useCart } from "@/hooks/useCart";
import { inventoryApi } from "@/services/api";
import { CartEmptyState } from "./CartEmptyState";
import { CartLineItem } from "./CartLineItem";
import { CartSummary } from "./CartSummary";
import { ClearCartButton } from "./ClearCartButton";

/** The guest's cart (US-0004-07): lines, totals, or the empty state. */
export function CartPage() {
  const { data: cart, isLoading, isError } = useCart();
  const outOfStock = useOutOfStockVariants(
    cart?.items.map((item) => item.variantId) ?? [],
  );

  if (isLoading) {
    return (
      <div className="animate-pulse space-y-4" aria-busy="true">
        <div className="h-8 w-1/3 rounded bg-slate-700" />
        <div className="h-24 rounded bg-slate-700" />
        <div className="h-24 rounded bg-slate-700" />
      </div>
    );
  }

  if (isError) {
    return (
      <p role="alert" className="text-center text-red-400">
        We couldn’t load your cart. Please try again.
      </p>
    );
  }

  return (
    <>
      <h1 className="mb-6 text-3xl font-bold text-white">Your cart</h1>
      {!cart || cart.items.length === 0 ? (
        <CartEmptyState />
      ) : (
        <div className="grid gap-6 md:grid-cols-[1fr_18rem]">
          <ul className="rounded-xl bg-slate-800 px-6 shadow-lg">
            {cart.items.map((item) => (
              <CartLineItem
                key={item.id}
                cartId={cart.id}
                item={item}
                isOutOfStock={outOfStock.has(item.variantId)}
              />
            ))}
          </ul>
          <div>
            <CartSummary summary={cart.summary} />
            <ClearCartButton cartId={cart.id} />
          </div>
        </div>
      )}
    </>
  );
}

/**
 * The cart's variants that are out of stock now (US-0004-10 AC-05). One availability check per
 * variant, under the product page's query key so the two share a cache. A check that fails,
 * e.g. a 404 for a variant that is gone, flags nothing: the cart's own errors cover that case.
 */
function useOutOfStockVariants(variantIds: string[]): Set<string> {
  const results = useQueries({
    queries: [...new Set(variantIds)].map((variantId) => ({
      queryKey: ["availability", variantId],
      queryFn: () => inventoryApi.getAvailability(variantId),
    })),
  });
  return new Set(
    results.flatMap(({ data }) =>
      data?.availability === "OUT_OF_STOCK" ? [data.variantId] : [],
    ),
  );
}
