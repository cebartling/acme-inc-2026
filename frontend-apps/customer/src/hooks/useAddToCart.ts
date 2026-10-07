import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { AddToCartRequest, Cart } from "@/services/api";
import { TimeoutError, cartApi, inventoryApi } from "@/services/api";
import { reloadIfStale, writeCart } from "@/hooks/useCart";

export const OUT_OF_STOCK_MESSAGE = "This item is out of stock";
export const AVAILABILITY_TIMEOUT_MESSAGE =
  "We couldn't check stock right now. Please try again.";

/**
 * Adds an item to the guest cart (US-0004-06).
 *
 * Availability is re-checked right before the add (AC-0004-06-02) rather than trusted
 * from the page's cached query, which may be minutes old. On success the returned cart
 * replaces the cached one, which updates the header badge (AC-0004-06-06).
 */
export function useAddToCart() {
  const queryClient = useQueryClient();

  return useMutation<Cart, Error, AddToCartRequest>({
    mutationFn: async (request) => {
      const { availability } = await checkAvailability(request.variantId);
      if (availability === "OUT_OF_STOCK") {
        throw new Error(OUT_OF_STOCK_MESSAGE);
      }
      return cartApi.addItem(request);
    },
    onSuccess: (cart) => writeCart(queryClient, cart),
    // A 409 CART_CONFLICT (PIN-278): a concurrent change won, so the cached cart is stale.
    onError: (error) => reloadIfStale(queryClient, error),
  });
}

/** The stock check, with a timed-out check (PIN-349) reworded for the customer. */
async function checkAvailability(variantId: string) {
  try {
    return await inventoryApi.getAvailability(variantId);
  } catch (error) {
    if (error instanceof TimeoutError) {
      throw new Error(AVAILABILITY_TIMEOUT_MESSAGE);
    }
    throw error;
  }
}
