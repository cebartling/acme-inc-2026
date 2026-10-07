import { useQueries } from "@tanstack/react-query";
import type { Cart } from "@/services/api";
import { useCart } from "@/hooks/useCart";
import { availabilityQueryKey } from "@/hooks/useVariantSelection";
import { inventoryApi } from "@/services/api";
import { CartEmptyState } from "./CartEmptyState";
import { CartLineItem } from "./CartLineItem";
import { CartSummary } from "./CartSummary";
import { ClearCartButton } from "./ClearCartButton";

/** The guest's cart (US-0004-07): lines, totals, or the empty state. */
export function CartPage() {
  const { data: cart, isLoading, isError } = useCart();
  const { outOfStock, pending } = useStockChecks(
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
                isStockPending={pending.has(item.variantId)}
              />
            ))}
          </ul>
          <div>
            <CartSummary
              summary={orderableSummary(cart, outOfStock)}
              excludesOutOfStock={outOfStock.size > 0}
              checkingAvailability={pending.size > 0}
            />
            <ClearCartButton cartId={cart.id} />
          </div>
        </div>
      )}
    </>
  );
}

/**
 * The cart's variants that are out of stock now (US-0004-10 AC-05), and those whose check has
 * no answer yet (PIN-328). One availability check per variant, under the product page's query
 * key so the two share a cache. A check that fails, e.g. a 404 for a variant that is gone,
 * flags nothing: the cart's own errors cover that case. It is not retried, so it doesn't hold
 * back the totals and + through the retries' backoff.
 */
function useStockChecks(variantIds: string[]): {
  outOfStock: Set<string>;
  pending: Set<string>;
} {
  const uniqueIds = [...new Set(variantIds)];
  const results = useQueries({
    queries: uniqueIds.map((variantId) => ({
      queryKey: availabilityQueryKey(variantId),
      queryFn: () => inventoryApi.getAvailability(variantId),
      retry: false,
    })),
  });
  // Keyed on the id the line asked about, not the one the response echoes back
  return {
    outOfStock: new Set(
      uniqueIds.filter(
        (_, i) => results[i]?.data?.availability === "OUT_OF_STOCK",
      ),
    ),
    pending: new Set(uniqueIds.filter((_, i) => results[i]?.isPending)),
  };
}

/**
 * The totals of the lines that can be ordered (PIN-317). The cart service knows nothing about
 * stock, so its summary still counts an out-of-stock line; leave those lines out here.
 */
function orderableSummary(
  cart: Cart,
  outOfStock: Set<string>,
): Cart["summary"] {
  if (outOfStock.size === 0) return cart.summary;
  const orderable = cart.items.filter(
    (item) => !outOfStock.has(item.variantId),
  );
  const subtotal = orderable.reduce((sum, item) => sum + item.lineTotal, 0);
  return {
    itemCount: orderable.reduce((sum, item) => sum + item.quantity, 0),
    subtotal: Math.round(subtotal * 100) / 100,
    currency: cart.summary.currency,
  };
}
