import { useQueries } from "@tanstack/react-query";
import type { Cart } from "@/services/api";
import { useCart } from "@/hooks/useCart";
import { availabilityQueryOptions } from "@/hooks/useVariantSelection";
import { CartEmptyState } from "./CartEmptyState";
import { CartLineItem } from "./CartLineItem";
import { CartSummary } from "./CartSummary";
import { ClearCartButton } from "./ClearCartButton";

/** The guest's cart (US-0004-07): lines, totals, or the empty state. */
export function CartPage() {
  const { data: cart, isPending, isPaused, isError } = useCart();
  const { outOfStock, pending } = useStockChecks(
    cart?.items.map((item) => item.variantId) ?? [],
  );

  // No cart loaded yet. Paused means offline: say so rather than show a skeleton for as
  // long as the browser stays offline (PIN-353). It loads once back online.
  if (isPending) {
    return isPaused ? (
      <p
        role="status"
        data-testid="cartOffline"
        className="text-center text-slate-300"
      >
        You’re offline. Your cart will load when you’re back online.
      </p>
    ) : (
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
      {cart === null || cart.items.length === 0 ? (
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
 * no answer yet (PIN-328). One availability check per variant, with the product page's query
 * options so the two share a cache; an answer cached there still waits for the cart's own
 * check (PIN-350). A check that fails, e.g. a 404 for a variant that is gone,
 * flags nothing: the cart's own errors cover that case. The first failure ends the wait, so it
 * doesn't hold back the totals and + through the retries' backoff. A check that stalls times out
 * (PIN-349), and one paused while the browser is offline doesn't hold anything back either.
 */
function useStockChecks(variantIds: string[]): {
  outOfStock: Set<string>;
  pending: Set<string>;
} {
  const uniqueIds = [...new Set(variantIds)];
  const results = useQueries({
    queries: uniqueIds.map((variantId) => ({
      ...availabilityQueryOptions(variantId),
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
    // Fetching and no answer since the page mounted: a cached answer from the product page waits
    // for the cart's own check (PIN-350), a later refetch doesn't, and a check paused offline isn't
    // fetching (PIN-349). A failure ends the wait even when the product page's check, joined
    // mid-run, retries (PIN-352)
    pending: new Set(
      uniqueIds.filter(
        (_, i) =>
          results[i]?.isFetching &&
          !results[i].isFetchedAfterMount &&
          results[i].failureCount === 0,
      ),
    ),
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
