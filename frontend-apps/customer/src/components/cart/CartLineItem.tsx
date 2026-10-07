import { useId } from "react";
import { Loader2, Trash2 } from "lucide-react";
import type { CartItem } from "@/services/api";
import {
  isLineGone,
  useRemoveCartItem,
  useUpdateCartItem,
} from "@/hooks/useCart";
import { CartItemOutOfStockWarning } from "./CartItemOutOfStockWarning";
import { QuantityControl } from "./QuantityControl";

interface CartLineItemProps {
  cartId: string;
  item: CartItem;
  /** The variant went out of stock after it was added (US-0004-10 AC-05). */
  isOutOfStock?: boolean;
  /** The line's stock check has no answer yet, so its quantity can't go up (PIN-328). */
  isStockPending?: boolean;
}

/**
 * One cart line: the snapshot captured at add time, quantity controls and Remove
 * (US-0004-07). Each line has its own mutations so pending and error state stay local.
 */
export function CartLineItem({
  cartId,
  item,
  isOutOfStock = false,
  isStockPending = false,
}: CartLineItemProps) {
  const update = useUpdateCartItem();
  const remove = useRemoveCartItem();
  const messageId = useId();
  const outOfStockId = useId();

  const { name, variantName, imageUrl } = item.productSnapshot;
  const itemName = `${name} (${variantName})`;
  const isBusy = update.isPending || remove.isPending;
  // Only the latest action's outcome: an old update error must not mask a newer remove error.
  // A gone line is not an error to show; the cart reloads instead.
  const errorText = (error: Error | null) =>
    error && !isLineGone(error) ? error.message : null;
  const message =
    remove.submittedAt > update.submittedAt
      ? errorText(remove.error)
      : (update.data?.clampedMessage ?? errorText(update.error));
  // The warning explains why the quantity can't go up (PIN-317)
  const describedBy =
    [isOutOfStock && outOfStockId, message && messageId]
      .filter(Boolean)
      .join(" ") || undefined;

  return (
    <li
      data-testid="cartLineItem"
      data-out-of-stock={isOutOfStock || undefined}
      aria-busy={isBusy}
      className="flex flex-wrap items-center gap-4 border-b border-slate-700 py-4 last:border-b-0"
    >
      {imageUrl ? (
        <img
          src={imageUrl}
          alt=""
          className={`h-16 w-16 flex-shrink-0 rounded-lg object-cover ${isOutOfStock ? "opacity-40 grayscale" : ""}`}
        />
      ) : (
        <div className="h-16 w-16 flex-shrink-0 rounded-lg bg-slate-700" />
      )}

      <div className={`min-w-40 flex-1 ${isOutOfStock ? "opacity-50" : ""}`}>
        <p data-testid="lineName" className="font-semibold text-white">
          {name}
        </p>
        <p className="text-sm text-slate-400">{variantName}</p>
        <p data-testid="lineUnitPrice" className="text-sm text-slate-300">
          ${item.unitPrice.toFixed(2)} each
        </p>
      </div>

      <QuantityControl
        // Resync the input after every submission, including a clamp that leaves the
        // quantity where it was (typed 11 at a max of 10 that was already 10).
        key={`${item.quantity}:${update.submittedAt}`}
        quantity={item.quantity}
        itemName={itemName}
        disabled={isBusy}
        canIncrease={!isOutOfStock && !isStockPending}
        describedBy={describedBy}
        onChange={(quantity) =>
          update.mutate({ cartId, itemId: item.id, quantity })
        }
      />

      <p
        data-testid="lineTotal"
        className="w-24 text-right font-semibold text-white"
      >
        ${item.lineTotal.toFixed(2)}
      </p>

      <button
        type="button"
        data-testid="removeLine"
        aria-label={`Remove ${itemName} from cart`}
        disabled={isBusy}
        onClick={() => remove.mutate({ cartId, itemId: item.id })}
        className={
          isOutOfStock
            ? "inline-flex items-center gap-1.5 rounded-md bg-red-600 px-3 py-2 text-sm font-semibold text-white transition-colors hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-40"
            : "rounded-md p-2 text-slate-400 transition-colors hover:bg-slate-700 hover:text-red-400 disabled:cursor-not-allowed disabled:opacity-40"
        }
      >
        {isBusy ? (
          <Loader2 size={16} className="animate-spin" aria-hidden="true" />
        ) : (
          <Trash2 size={16} aria-hidden="true" />
        )}
        {/* US-0004-10 AC-05: the way out for a line that can't be ordered, so it is spelled out */}
        {isOutOfStock && <span aria-hidden="true">Remove</span>}
      </button>

      {isOutOfStock && (
        <CartItemOutOfStockWarning id={outOfStockId} name={name} />
      )}

      {message && (
        <p
          id={messageId}
          role="alert"
          data-testid="lineMessage"
          className="w-full text-sm text-red-400"
        >
          {message}
        </p>
      )}
    </li>
  );
}
