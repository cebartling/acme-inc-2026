import { Link } from "@tanstack/react-router";
import { ShoppingCart } from "lucide-react";

/** Shown when the cart has no lines (AC-0004-07-06). */
export function CartEmptyState() {
  return (
    <div
      data-testid="cartEmptyState"
      className="rounded-xl bg-slate-800 p-10 text-center shadow-lg"
    >
      <ShoppingCart
        size={40}
        className="mx-auto mb-4 text-slate-500"
        aria-hidden="true"
      />
      <h2 className="mb-2 text-xl font-semibold text-white">
        Your cart is empty
      </h2>
      <p className="mb-6 text-slate-400">
        Items you add to your cart will appear here.
      </p>
      <Link
        to="/"
        data-testid="continueShopping"
        className="inline-block rounded-lg bg-indigo-600 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-indigo-500"
      >
        Continue shopping
      </Link>
    </div>
  );
}
