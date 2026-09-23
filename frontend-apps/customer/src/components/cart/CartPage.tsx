import { useCart } from "@/hooks/useCart";
import { CartEmptyState } from "./CartEmptyState";
import { CartLineItem } from "./CartLineItem";
import { CartSummary } from "./CartSummary";

/** The guest's cart (US-0004-07): lines, totals, or the empty state. */
export function CartPage() {
  const { data: cart, isLoading, isError } = useCart();

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
              <CartLineItem key={item.id} cartId={cart.id} item={item} />
            ))}
          </ul>
          <div>
            <CartSummary summary={cart.summary} />
          </div>
        </div>
      )}
    </>
  );
}
