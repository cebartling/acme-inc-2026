import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import {
  CART_QUERY_KEY,
  mergeCartAfterSignIn,
  useRemoveCartItem,
  useUpdateCartItem,
} from "./useCart";
import { useAuthStore } from "@/stores/auth.store";
import { useCartNoticeStore } from "@/stores/cartNotice.store";
import type { Cart } from "@/services/api";
import { ApiError, cartApi } from "@/services/api";

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  cartApi: { updateItem: vi.fn(), removeItem: vi.fn(), merge: vi.fn() },
}));

const mockedUpdate = vi.mocked(cartApi.updateItem);
const mockedRemove = vi.mocked(cartApi.removeItem);

let queryClient: QueryClient;

function makeWrapper() {
  queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: 0 } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

function cartWithQuantity(quantity: number): Cart {
  return {
    id: "cart-1",
    items: [],
    summary: { itemCount: quantity, subtotal: 0, currency: "USD" },
  };
}

const variables = { cartId: "cart-1", itemId: "line-1", quantity: 3 };

describe("useUpdateCartItem", () => {
  beforeEach(() => vi.clearAllMocks());

  it("updates the line and caches the returned cart", async () => {
    mockedUpdate.mockResolvedValue(cartWithQuantity(3));
    const { result } = renderHook(() => useUpdateCartItem(), {
      wrapper: makeWrapper(),
    });

    act(() => result.current.mutate(variables));

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.clampedMessage).toBeNull();
    expect(queryClient.getQueryData(CART_QUERY_KEY)).toEqual(
      cartWithQuantity(3),
    );
  });

  it("clamps an over-max quantity to the limit and reports why (AC-0004-07-08)", async () => {
    mockedUpdate
      .mockRejectedValueOnce(
        new ApiError("Maximum order quantity is 10 for this item", 422, {
          error: "Maximum order quantity is 10 for this item",
          maxQuantity: 10,
        }),
      )
      .mockResolvedValueOnce(cartWithQuantity(10));
    const { result } = renderHook(() => useUpdateCartItem(), {
      wrapper: makeWrapper(),
    });

    act(() => result.current.mutate({ ...variables, quantity: 11 }));

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedUpdate).toHaveBeenNthCalledWith(2, "cart-1", "line-1", 10);
    expect(result.current.data?.clampedMessage).toBe(
      "Maximum order quantity is 10 for this item",
    );
    expect(queryClient.getQueryData(CART_QUERY_KEY)).toEqual(
      cartWithQuantity(10),
    );
  });

  it("keeps the updated cart when an older in-flight read resolves afterwards", async () => {
    mockedUpdate.mockResolvedValue(cartWithQuantity(3));
    const { result } = renderHook(() => useUpdateCartItem(), {
      wrapper: makeWrapper(),
    });
    let resolveStaleRead!: (cart: Cart) => void;
    const staleRead = queryClient.prefetchQuery({
      queryKey: CART_QUERY_KEY,
      queryFn: () =>
        new Promise<Cart>((resolve) => {
          resolveStaleRead = resolve;
        }),
    });

    act(() => result.current.mutate(variables));
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    resolveStaleRead(cartWithQuantity(2));
    await staleRead;

    expect(queryClient.getQueryData(CART_QUERY_KEY)).toEqual(
      cartWithQuantity(3),
    );
  });

  it("surfaces other failures without retrying", async () => {
    mockedUpdate.mockRejectedValue(new ApiError("Pricing unavailable", 503));
    const { result } = renderHook(() => useUpdateCartItem(), {
      wrapper: makeWrapper(),
    });

    act(() => result.current.mutate(variables));

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Pricing unavailable");
    expect(mockedUpdate).toHaveBeenCalledTimes(1);
  });
});

describe("useRemoveCartItem", () => {
  beforeEach(() => vi.clearAllMocks());

  it("removes the line and caches the returned cart", async () => {
    mockedRemove.mockResolvedValue(cartWithQuantity(0));
    const { result } = renderHook(() => useRemoveCartItem(), {
      wrapper: makeWrapper(),
    });

    act(() => result.current.mutate({ cartId: "cart-1", itemId: "line-1" }));

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedRemove).toHaveBeenCalledWith("cart-1", "line-1");
    expect(queryClient.getQueryData(CART_QUERY_KEY)).toEqual(
      cartWithQuantity(0),
    );
  });
});

describe("mergeCartAfterSignIn", () => {
  const mockedMerge = vi.mocked(cartApi.merge);

  const guestLine = {
    id: "guest-line",
    variantId: "variant-1",
    quantity: 2,
    unitPrice: 119.99,
    lineTotal: 239.98,
    productSnapshot: {
      productId: "product-1",
      name: "Gadget Pro",
      sku: "ACME-GP-BLK",
      variantName: "Black",
      imageUrl: null,
      attributes: {},
    },
  };
  const guestCart: Cart = {
    id: "guest-cart",
    items: [guestLine],
    summary: { itemCount: 2, subtotal: 239.98, currency: "USD" },
  };
  const mergedCart = {
    id: "user-cart",
    items: [
      {
        ...guestLine,
        id: "user-line",
        quantity: 10,
        unitPrice: 99.99,
        lineTotal: 999.9,
      },
    ],
    summary: { itemCount: 10, subtotal: 999.9, currency: "USD" },
  };

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient();
    useCartNoticeStore.setState({ message: null });
    useAuthStore.setState({ isAuthenticated: true });
  });

  it("merges a cached guest cart and caches the merged cart (AC-01, AC-06)", async () => {
    queryClient.setQueryData(CART_QUERY_KEY, guestCart);
    mockedMerge.mockResolvedValue({
      ...mergedCart,
      mergeResult: { itemsMerged: 1, quantitiesAdjusted: [] },
    });

    await mergeCartAfterSignIn(queryClient);

    expect(mockedMerge).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData<Cart>(CART_QUERY_KEY)?.id).toBe(
      "user-cart",
    );
    expect(useCartNoticeStore.getState().message).toBeNull();
  });

  it("does not call merge when there is no guest cart (AC-09)", async () => {
    queryClient.setQueryData(CART_QUERY_KEY, null);
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    await mergeCartAfterSignIn(queryClient);

    expect(mockedMerge).not.toHaveBeenCalled();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: CART_QUERY_KEY });
  });

  it("does not call merge for an empty guest cart (AC-08)", async () => {
    queryClient.setQueryData(CART_QUERY_KEY, { ...guestCart, items: [] });

    await mergeCartAfterSignIn(queryClient);

    expect(mockedMerge).not.toHaveBeenCalled();
  });

  it("merges when the guest cart had not loaded yet, letting the service decide", async () => {
    mockedMerge.mockResolvedValue(null);
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    await mergeCartAfterSignIn(queryClient);

    expect(mockedMerge).toHaveBeenCalledTimes(1);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: CART_QUERY_KEY });
  });

  it("names each capped product in the notice (AC-04)", async () => {
    queryClient.setQueryData(CART_QUERY_KEY, guestCart);
    mockedMerge.mockResolvedValue({
      ...mergedCart,
      mergeResult: {
        itemsMerged: 1,
        quantitiesAdjusted: [
          {
            variantId: "variant-1",
            requestedTotal: 12,
            adjustedTo: 10,
            reason: "MAX_ORDER_QUANTITY",
          },
        ],
      },
    });

    await mergeCartAfterSignIn(queryClient);

    expect(useCartNoticeStore.getState().message).toBe(
      "Quantity for Gadget Pro was adjusted to the maximum of 10.",
    );
  });

  it("does not cache the account's cart when signed out before the merge returned", async () => {
    queryClient.setQueryData(CART_QUERY_KEY, guestCart);
    mockedMerge.mockImplementation(async () => {
      useAuthStore.setState({ isAuthenticated: false });
      return {
        ...mergedCart,
        mergeResult: {
          itemsMerged: 1,
          quantitiesAdjusted: [
            {
              variantId: "variant-1",
              requestedTotal: 12,
              adjustedTo: 10,
              reason: "MAX_ORDER_QUANTITY",
            },
          ],
        },
      };
    });

    await mergeCartAfterSignIn(queryClient);

    expect(queryClient.getQueryData<Cart>(CART_QUERY_KEY)?.id).toBe(
      "guest-cart",
    );
    expect(useCartNoticeStore.getState().message).toBeNull();
  });

  it("never throws: a failed merge is logged and the cart reloads", async () => {
    queryClient.setQueryData(CART_QUERY_KEY, guestCart);
    mockedMerge.mockRejectedValue(new ApiError("Pricing unavailable", 503));
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    await expect(mergeCartAfterSignIn(queryClient)).resolves.toBeUndefined();

    expect(warn).toHaveBeenCalledWith(
      "Cart merge failed",
      expect.any(ApiError),
    );
    expect(invalidate).toHaveBeenCalledWith({ queryKey: CART_QUERY_KEY });
    warn.mockRestore();
  });
});
