import {
  type QueryClient,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type { Cart } from "@/services/api";
import { ApiError, cartApi } from "@/services/api";

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

/** A 404 means the line is already gone (e.g. removed in another tab): reload the cart. */
function reloadIfLineGone(queryClient: QueryClient, error: Error) {
  if (error instanceof ApiError && error.status === 404) {
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
