import { useId } from "react";
import { Loader2, Trash2 } from "lucide-react";
import type { CartItem } from "@/services/api";
import { useRemoveCartItem, useUpdateCartItem } from "@/hooks/useCart";
import { QuantityControl } from "./QuantityControl";

interface CartLineItemProps {
  cartId: string;
  item: CartItem;
}

/**
 * One cart line: the snapshot captured at add time, quantity controls and Remove
 * (US-0004-07). Each line has its own mutations so pending and error state stay local.
 */
export function CartLineItem({ cartId, item }: CartLineItemProps) {
  const update = useUpdateCartItem();
  const remove = useRemoveCartItem();
  const messageId = useId();

  const { name, variantName, imageUrl } = item.productSnapshot;
  const itemName = `${name} (${variantName})`;
  const isBusy = update.isPending || remove.isPending;
  const message =
    update.data?.clampedMessage ??
    update.error?.message ??
    remove.error?.message ??
    null;

  return (
    <li
      data-testid="cartLineItem"
      aria-busy={isBusy}
      className="flex flex-wrap items-center gap-4 border-b border-slate-700 py-4 last:border-b-0"
    >
      {imageUrl ? (
        <img
          src={imageUrl}
          alt=""
          className="h-16 w-16 flex-shrink-0 rounded-lg object-cover"
        />
      ) : (
        <div className="h-16 w-16 flex-shrink-0 rounded-lg bg-slate-700" />
      )}

      <div className="min-w-40 flex-1">
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
        describedBy={message ? messageId : undefined}
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
        className="rounded-md p-2 text-slate-400 transition-colors hover:bg-slate-700 hover:text-red-400 disabled:cursor-not-allowed disabled:opacity-40"
      >
        {isBusy ? (
          <Loader2 size={16} className="animate-spin" aria-hidden="true" />
        ) : (
          <Trash2 size={16} aria-hidden="true" />
        )}
      </button>

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
