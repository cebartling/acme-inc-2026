interface CartItemOutOfStockWarningProps {
  name: string;
  /** Lets the line's quantity control point at this warning (PIN-317). */
  id?: string;
}

/**
 * A cart line whose variant went out of stock after it was added (US-0004-10 AC-05).
 * `role="alert"` so it is announced when the cart page's stock check comes back.
 */
export function CartItemOutOfStockWarning({
  name,
  id,
}: CartItemOutOfStockWarningProps) {
  return (
    <p
      id={id}
      role="alert"
      data-testid="lineOutOfStock"
      className="w-full rounded-md bg-red-950/60 px-3 py-2 text-sm text-red-300"
    >
      {name} is now out of stock and cannot be included in your order.
    </p>
  );
}
