import { create } from "zustand";

interface CartNoticeState {
  /** A one-off message about the cart, e.g. quantities capped during a sign-in merge. */
  message: string | null;
  show: (message: string) => void;
  dismiss: () => void;
}

/**
 * The cart notice banner shown under the header (US-0004-08 AC-04). Not persisted: it
 * describes something that just happened, not state worth restoring on reload.
 */
export const useCartNoticeStore = create<CartNoticeState>()((set) => ({
  message: null,
  show: (message) => set({ message }),
  dismiss: () => set({ message: null }),
}));
