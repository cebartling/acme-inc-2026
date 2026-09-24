import {
  type QueryClient,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type { Cart, MergeCartResponse } from "@/services/api";
import { ApiError, cartApi } from "@/services/api";
import { useAuthStore } from "@/stores/auth.store";
import { useCartNoticeStore } from "@/stores/cartNotice.store";

/** Query key for the session's cart. Every cart read and write goes through it. */
export const CART_QUERY_KEY = ["cart"] as const;

/**
 * The session's cart, loaded on every page by the header badge (US-0004-07, AC-10).
 * `null` means no cart yet.
 */
export function useCart() {
  return useQuery({
    queryKey: CART_QUERY_KEY,
    queryFn: () => cartApi.getCurrent(),
  });
}

/**
 * Caches a cart returned by a write. An in-flight read (a mount or focus refetch) is
 * cancelled first, so it cannot land afterwards and overwrite the newer cart.
 */
export async function writeCart(queryClient: QueryClient, cart: Cart) {
  await queryClient.cancelQueries({ queryKey: CART_QUERY_KEY });
  queryClient.setQueryData(CART_QUERY_KEY, cart);
}

/**
 * A 404 means the line is already gone: removed in another tab, or the session expired
 * and its cart with it (US-0004-12). Nothing to retry; the reloaded cart is the answer.
 */
export function isLineGone(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

/** Reloads the cart when the line is gone, so the page shows what is actually there. */
function reloadIfLineGone(queryClient: QueryClient, error: Error) {
  if (isLineGone(error)) {
    void queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
  }
}

interface UpdateCartItemVariables {
  cartId: string;
  itemId: string;
  quantity: number;
}

export interface UpdateCartItemResult {
  cart: Cart;
  /** Set when the requested quantity was over the limit and the line was clamped to it. */
  clampedMessage: string | null;
}

function maxQuantityOf(error: unknown): number | null {
  if (!(error instanceof ApiError) || error.status !== 422) return null;
  const max = error.data?.maxQuantity;
  return typeof max === "number" ? max : null;
}

/**
 * Changes a line's quantity. Over the limit, the line is set to the limit instead and
 * the service's message is returned alongside the cart (AC-0004-07-08).
 */
export function useUpdateCartItem() {
  const queryClient = useQueryClient();

  return useMutation<UpdateCartItemResult, Error, UpdateCartItemVariables>({
    mutationFn: async ({ cartId, itemId, quantity }) => {
      try {
        const cart = await cartApi.updateItem(cartId, itemId, quantity);
        return { cart, clampedMessage: null };
      } catch (error) {
        const max = maxQuantityOf(error);
        if (max === null) throw error;
        const cart = await cartApi.updateItem(cartId, itemId, max);
        return { cart, clampedMessage: (error as ApiError).message };
      }
    },
    onSuccess: ({ cart }) => writeCart(queryClient, cart),
    onError: (error) => reloadIfLineGone(queryClient, error),
  });
}

/** Removes a line (AC-0004-07-05). */
export function useRemoveCartItem() {
  const queryClient = useQueryClient();

  return useMutation<Cart, Error, { cartId: string; itemId: string }>({
    mutationFn: ({ cartId, itemId }) => cartApi.removeItem(cartId, itemId),
    onSuccess: (cart) => writeCart(queryClient, cart),
    onError: (error) => reloadIfLineGone(queryClient, error),
  });
}

/** "Quantity for {name} was adjusted to the maximum of {N}", one sentence per capped line. */
function adjustmentNotice(merged: MergeCartResponse): string | null {
  const adjusted = merged.mergeResult?.quantitiesAdjusted ?? [];
  if (adjusted.length === 0) return null;
  return adjusted
    .map((a) => {
      const name =
        merged.items.find((item) => item.variantId === a.variantId)
          ?.productSnapshot.name ?? "an item";
      return `Quantity for ${name} was adjusted to the maximum of ${a.adjustedTo}.`;
    })
    .join(" ");
}

/**
 * After sign-in, folds the guest cart into the user's cart (US-0004-08).
 *
 * The header badge has already loaded the guest's cart into the cache before sign-in, and
 * the session cookie is HttpOnly, so the cache is the only view of it here:
 * - no guest cart, or an empty one: no merge request (AC-09); reload as the user
 * - items, or nothing loaded yet: merge, and let the service no-op if there was nothing
 *
 * Sign-in awaits this before navigating, so the destination page starts from the merged
 * cart. It never throws: a failure is logged and the cart reloads as the signed-in user,
 * whose own cart is still correct.
 */
export async function mergeCartAfterSignIn(
  queryClient: QueryClient,
): Promise<void> {
  const guestCart = queryClient.getQueryData<Cart | null>(CART_QUERY_KEY);
  if (guestCart === null || (guestCart && guestCart.items.length === 0)) {
    await queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    return;
  }

  try {
    const merged = await cartApi.merge();
    // Signed out while the merge was in flight: sign-out already reloaded the guest cart,
    // and the account's cart must not reappear for the next visitor.
    if (!useAuthStore.getState().isAuthenticated) return;
    if (merged) {
      await writeCart(queryClient, merged);
      const notice = adjustmentNotice(merged);
      if (notice) useCartNoticeStore.getState().show(notice);
      return;
    }
  } catch (error) {
    console.warn("Cart merge failed", error);
  }
  await queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
}
