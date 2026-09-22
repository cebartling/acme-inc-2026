import { ShoppingCart } from "lucide-react";
import { useCartItemCount } from "@/stores/cart.store";

/**
 * Header cart icon with an item-count badge (AC-0004-06-06). The count is hidden at 0.
 *
 * Not yet a link: the cart page arrives with US-0004-07.
 */
export function CartBadge() {
  const itemCount = useCartItemCount();
  const label = itemCount === 1 ? "Cart: 1 item" : `Cart: ${itemCount} items`;

  return (
    <span
      data-testid="cartBadge"
      role="status"
      aria-label={label}
      className="relative inline-flex p-2"
    >
      <ShoppingCart size={24} aria-hidden="true" />
      {itemCount > 0 && (
        <span
          data-testid="cartBadgeCount"
          aria-hidden="true"
          className="absolute -right-1 -top-1 min-w-5 rounded-full bg-indigo-500 px-1.5 text-center text-xs font-semibold leading-5 text-white"
        >
          {itemCount}
        </span>
      )}
    </span>
  );
}
