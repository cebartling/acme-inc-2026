import type { Cart } from "@/services/api";

/**
 * Cart totals (AC-0004-07-07). There is no tax or shipping service yet, so the estimated
 * total equals the subtotal. `excludesOutOfStock` says the totals leave out-of-stock lines out
 * (PIN-317). While `checkingAvailability`, the totals are held back so they don't jump once
 * a line turns out to be out of stock (PIN-328).
 */
export function CartSummary({
  summary,
  excludesOutOfStock = false,
  checkingAvailability = false,
}: {
  summary: Cart["summary"];
  excludesOutOfStock?: boolean;
  checkingAvailability?: boolean;
}) {
  const itemLabel =
    summary.itemCount === 1 ? "1 item" : `${summary.itemCount} items`;

  if (checkingAvailability) {
    return (
      <section
        aria-label="Order summary"
        aria-busy="true"
        className="rounded-xl bg-slate-800 p-6 shadow-lg"
      >
        <p
          data-testid="cartCheckingAvailability"
          className="text-sm text-slate-300"
        >
          Checking availability…
        </p>
      </section>
    );
  }

  return (
    <section
      aria-label="Order summary"
      className="rounded-xl bg-slate-800 p-6 shadow-lg"
    >
      <dl className="space-y-2 text-sm">
        <div className="flex justify-between text-slate-300">
          <dt>Subtotal ({itemLabel})</dt>
          <dd data-testid="cartSubtotal">${summary.subtotal.toFixed(2)}</dd>
        </div>
        <div className="flex justify-between border-t border-slate-700 pt-2 text-base font-semibold text-white">
          <dt>Estimated total</dt>
          <dd data-testid="cartEstimatedTotal">
            ${summary.subtotal.toFixed(2)}
          </dd>
        </div>
      </dl>
      {excludesOutOfStock && (
        <p
          data-testid="cartExcludesOutOfStock"
          className="mt-3 text-xs text-slate-400"
        >
          Out-of-stock items are not included.
        </p>
      )}
      <p className="mt-3 text-xs text-slate-400">
        Taxes and shipping calculated at checkout. Prices in {summary.currency}.
      </p>
    </section>
  );
}
