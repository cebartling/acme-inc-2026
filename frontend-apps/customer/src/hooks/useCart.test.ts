import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import {
  CART_QUERY_KEY,
  useRemoveCartItem,
  useUpdateCartItem,
} from "./useCart";
import type { Cart } from "@/services/api";
import { ApiError, cartApi } from "@/services/api";

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  cartApi: { updateItem: vi.fn(), removeItem: vi.fn() },
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
