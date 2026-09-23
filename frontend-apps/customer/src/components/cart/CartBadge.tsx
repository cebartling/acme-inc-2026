import { Link } from "@tanstack/react-router";
import { ShoppingCart } from "lucide-react";
import { useCart } from "@/hooks/useCart";

/**
 * Header cart link with an item-count badge, hidden at 0 (AC-0004-06-06).
 *
 * The count comes from the persisted cart, loaded on every page (AC-0004-07-10), and
 * updates in place when an add, update or remove writes the new cart to the cache.
 */
export function CartBadge() {
  const { data: cart } = useCart();
  const itemCount = cart?.summary.itemCount ?? 0;
  const label = itemCount === 1 ? "Cart: 1 item" : `Cart: ${itemCount} items`;

  return (
    <Link
      to="/cart"
      data-testid="cartBadge"
      aria-label={label}
      className="relative inline-flex rounded-lg p-2 transition-colors hover:bg-gray-700"
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
    </Link>
  );
}
