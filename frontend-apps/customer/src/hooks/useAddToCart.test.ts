import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { OUT_OF_STOCK_MESSAGE, useAddToCart } from "./useAddToCart";
import { ApiError, cartApi, inventoryApi } from "@/services/api";
import { CART_QUERY_KEY } from "./useCart";

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  inventoryApi: { getAvailability: vi.fn() },
  cartApi: { addItem: vi.fn() },
}));

const mockedAvailability = vi.mocked(inventoryApi.getAvailability);
const mockedAddItem = vi.mocked(cartApi.addItem);

let queryClient: QueryClient;

function makeWrapper() {
  queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: 0 } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

const request = {
  variantId: "variant-1",
  quantity: 2,
  productSnapshot: {
    productId: "product-1",
    name: "ACME Gaming Mouse Pro",
    sku: "ACME-GM-PRO-BLK",
    variantName: "Black",
    imageUrl: null,
    attributes: {},
  },
};

const cart = {
  id: "cart-1",
  items: [],
  summary: { itemCount: 3, subtotal: 194.97, currency: "USD" },
};

describe("useAddToCart", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("checks availability, adds the item, then caches the returned cart", async () => {
    mockedAvailability.mockResolvedValue({
      variantId: "variant-1",
      availability: "IN_STOCK",
    });
    mockedAddItem.mockResolvedValue(cart);
    const { result } = renderHook(() => useAddToCart(), {
      wrapper: makeWrapper(),
    });

    act(() => result.current.mutate(request));

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedAvailability).toHaveBeenCalledWith("variant-1");
    expect(mockedAddItem).toHaveBeenCalledWith(request);
    expect(queryClient.getQueryData(CART_QUERY_KEY)).toEqual(cart);
  });

  it("does not call the cart service when the variant is out of stock", async () => {
    mockedAvailability.mockResolvedValue({
      variantId: "variant-1",
      availability: "OUT_OF_STOCK",
    });
    const { result } = renderHook(() => useAddToCart(), {
      wrapper: makeWrapper(),
    });

    act(() => result.current.mutate(request));

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe(OUT_OF_STOCK_MESSAGE);
    expect(mockedAddItem).not.toHaveBeenCalled();
    expect(queryClient.getQueryData(CART_QUERY_KEY)).toBeUndefined();
  });

  it("exposes the cart service's error message and leaves the cached cart alone", async () => {
    mockedAvailability.mockResolvedValue({
      variantId: "variant-1",
      availability: "IN_STOCK",
    });
    mockedAddItem.mockRejectedValue(
      new ApiError("Maximum order quantity is 10 for this item", 422),
    );
    const { result } = renderHook(() => useAddToCart(), {
      wrapper: makeWrapper(),
    });

    act(() => result.current.mutate(request));

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe(
      "Maximum order quantity is 10 for this item",
    );
    expect(queryClient.getQueryData(CART_QUERY_KEY)).toBeUndefined();
  });

  it("marks the cached cart stale when a concurrent change won (409)", async () => {
    mockedAvailability.mockResolvedValue({
      variantId: "variant-1",
      availability: "IN_STOCK",
    });
    mockedAddItem.mockRejectedValue(
      new ApiError(
        "Your cart was changed at the same time. Please try again.",
        409,
        {
          code: "CART_CONFLICT",
        },
      ),
    );
    const { result } = renderHook(() => useAddToCart(), {
      wrapper: makeWrapper(),
    });
    queryClient.setQueryData(CART_QUERY_KEY, cart);

    act(() => result.current.mutate(request));

    await waitFor(() => expect(result.current.isError).toBe(true));
    await waitFor(() =>
      expect(queryClient.getQueryState(CART_QUERY_KEY)?.isInvalidated).toBe(
        true,
      ),
    );
  });
});
