import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { useVariantSelection } from "./useVariantSelection";
import { trackVariantSelected } from "@/services/analytics";
import type { ProductDetail } from "@/services/api";

vi.mock("@/services/api", () => ({
  inventoryApi: {
    getAvailability: vi.fn().mockResolvedValue({
      variantId: "var-1",
      availability: "IN_STOCK",
    }),
  },
  pricingApi: {
    getPrice: vi.fn().mockResolvedValue({
      variantId: "var-1",
      price: 119.99,
      originalPrice: null,
      tierPricing: [],
    }),
  },
}));

vi.mock("@/services/analytics", () => ({
  trackVariantSelected: vi.fn(),
}));

const makeProduct = (overrides?: Partial<ProductDetail>): ProductDetail => ({
  id: "prod-1",
  slug: "gadget-pro",
  name: "Gadget Pro",
  description: null,
  price: 119.99,
  category: "Electronics",
  tags: [],
  availability: "IN_STOCK",
  relatedProducts: [],
  variants: [
    {
      id: "var-1",
      sku: "ACME-GP-BLK",
      name: "Black",
      color: "Black",
      size: null,
      isDefault: true,
      inStock: true,
      priceOverride: null,
      images: [],
      tierPricing: [],
    },
    {
      id: "var-2",
      sku: "ACME-GP-WHT",
      name: "White",
      color: "White",
      size: null,
      isDefault: false,
      inStock: true,
      priceOverride: null,
      images: [],
      tierPricing: [],
    },
  ],
  ...overrides,
});

function makeWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe("useVariantSelection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("is exported as a function", () => {
    expect(typeof useVariantSelection).toBe("function");
  });

  it("initializes selectedVariantId to the isDefault variant", () => {
    const { result } = renderHook(() => useVariantSelection(makeProduct()), {
      wrapper: makeWrapper(),
    });
    expect(result.current.selectedVariantId).toBe("var-1");
  });

  it("falls back to first variant when none is marked isDefault", () => {
    const product = makeProduct({
      variants: [
        {
          id: "var-a",
          sku: "A",
          name: "A",
          color: null,
          size: null,
          isDefault: false,
          inStock: true,
          priceOverride: null,
          images: [],
          tierPricing: [],
        },
        {
          id: "var-b",
          sku: "B",
          name: "B",
          color: null,
          size: null,
          isDefault: false,
          inStock: true,
          priceOverride: null,
          images: [],
          tierPricing: [],
        },
      ],
    });
    const { result } = renderHook(() => useVariantSelection(product), {
      wrapper: makeWrapper(),
    });
    expect(result.current.selectedVariantId).toBe("var-a");
  });

  it("does not fire analytics on initial mount", () => {
    renderHook(() => useVariantSelection(makeProduct()), {
      wrapper: makeWrapper(),
    });
    expect(vi.mocked(trackVariantSelected)).not.toHaveBeenCalled();
  });

  it("fires analytics when selectedVariantId changes after mount", async () => {
    const { result } = renderHook(() => useVariantSelection(makeProduct()), {
      wrapper: makeWrapper(),
    });

    act(() => {
      result.current.setSelectedVariantId("var-2");
    });

    await waitFor(() => {
      expect(vi.mocked(trackVariantSelected)).toHaveBeenCalledWith({
        productId: "prod-1",
        variantId: "var-2",
      });
    });
  });

  it("exposes the selected variant object matching selectedVariantId", () => {
    const { result } = renderHook(() => useVariantSelection(makeProduct()), {
      wrapper: makeWrapper(),
    });
    expect(result.current.selectedVariant?.id).toBe("var-1");
    expect(result.current.selectedVariant?.sku).toBe("ACME-GP-BLK");
  });

  it("updates selectedVariant when setSelectedVariantId is called", () => {
    const { result } = renderHook(() => useVariantSelection(makeProduct()), {
      wrapper: makeWrapper(),
    });
    act(() => {
      result.current.setSelectedVariantId("var-2");
    });
    expect(result.current.selectedVariantId).toBe("var-2");
    expect(result.current.selectedVariant?.sku).toBe("ACME-GP-WHT");
  });
});

describe("variant default selection logic (unit)", () => {
  it("default variant is the one with isDefault=true", () => {
    const product = makeProduct();
    const defaultVariant = product.variants.find((v) => v.isDefault);
    expect(defaultVariant?.id).toBe("var-1");
  });

  it("falls back to first variant when no isDefault is set", () => {
    const product = makeProduct({
      variants: [
        {
          id: "var-a",
          sku: "A",
          name: "A",
          color: null,
          size: null,
          isDefault: false,
          inStock: true,
          priceOverride: null,
          images: [],
          tierPricing: [],
        },
        {
          id: "var-b",
          sku: "B",
          name: "B",
          color: null,
          size: null,
          isDefault: false,
          inStock: true,
          priceOverride: null,
          images: [],
          tierPricing: [],
        },
      ],
    });
    const defaultVariant =
      product.variants.find((v) => v.isDefault) ?? product.variants[0];
    expect(defaultVariant?.id).toBe("var-a");
  });

  it("returns undefined when product has no variants", () => {
    const product = makeProduct({ variants: [] });
    const defaultVariant =
      product.variants.find((v) => v.isDefault) ?? product.variants[0];
    expect(defaultVariant).toBeUndefined();
  });
});
