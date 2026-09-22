import { create } from "zustand";

interface CartState {
  /** Total units in the cart, shown on the header badge. */
  itemCount: number;
  setItemCount: (itemCount: number) => void;
}

/**
 * Cart badge state (US-0004-06).
 *
 * Updated from each add-to-cart response. Deliberately not persisted: the cart lives in
 * the shopping cart service, and loading it on page load is US-0004-07.
 */
export const useCartStore = create<CartState>()((set) => ({
  itemCount: 0,
  setItemCount: (itemCount) => set({ itemCount }),
}));

export const useCartItemCount = () => useCartStore((state) => state.itemCount);
