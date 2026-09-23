import type { Cart } from "@/services/api";

/**
 * Cart totals (AC-0004-07-07). There is no tax or shipping service yet, so the estimated
 * total equals the subtotal.
 */
export function CartSummary({ summary }: { summary: Cart["summary"] }) {
  const itemLabel =
    summary.itemCount === 1 ? "1 item" : `${summary.itemCount} items`;

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
      <p className="mt-3 text-xs text-slate-400">
        Taxes and shipping calculated at checkout. Prices in {summary.currency}.
      </p>
    </section>
  );
}
