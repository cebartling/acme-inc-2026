import { useMutation } from "@tanstack/react-query";
import type { AddToCartRequest, Cart } from "@/services/api";
import { cartApi, inventoryApi } from "@/services/api";
import { useCartStore } from "@/stores/cart.store";

export const OUT_OF_STOCK_MESSAGE = "This item is out of stock";

/**
 * Adds an item to the guest cart (US-0004-06).
 *
 * Availability is re-checked right before the add (AC-0004-06-02) rather than trusted
 * from the page's cached query, which may be minutes old. On success the header badge
 * takes the server's total (AC-0004-06-06).
 */
export function useAddToCart() {
  const setItemCount = useCartStore((state) => state.setItemCount);

  return useMutation<Cart, Error, AddToCartRequest>({
    mutationFn: async (request) => {
      const { availability } = await inventoryApi.getAvailability(
        request.variantId,
      );
      if (availability === "OUT_OF_STOCK") {
        throw new Error(OUT_OF_STOCK_MESSAGE);
      }
      return cartApi.addItem(request);
    },
    onSuccess: (cart) => setItemCount(cart.summary.itemCount),
  });
}
