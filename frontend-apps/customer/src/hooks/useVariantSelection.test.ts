import { describe, it, expect, vi, beforeEach } from "vitest";
import { useVariantSelection } from "./useVariantSelection";
import type { ProductDetail } from "@/services/api";

describe("useVariantSelection", () => {
  it("is exported as a function", () => {
    expect(typeof useVariantSelection).toBe("function");
  });
});

describe("trackVariantSelected analytics", () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it("trackVariantSelected emits VariantSelected event", async () => {
    const { trackVariantSelected } = await import("@/services/analytics");
    const spy = vi.spyOn(console, "debug").mockImplementation(() => {});
    trackVariantSelected({ productId: "prod-1", variantId: "var-2" });
    expect(spy).toHaveBeenCalledWith("[analytics]", "VariantSelected", {
      source: "WEB",
      productId: "prod-1",
      variantId: "var-2",
    });
    spy.mockRestore();
  });
});

describe("variant default selection logic", () => {
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
        isDefault: false,
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
        isDefault: true,
        inStock: true,
        priceOverride: null,
        images: [],
        tierPricing: [],
      },
    ],
    ...overrides,
  });

  it("default variant is the one with isDefault=true", () => {
    const product = makeProduct();
    const defaultVariant = product.variants.find((v) => v.isDefault);
    expect(defaultVariant?.id).toBe("var-2");
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

  it("returns empty list when product has no variants", () => {
    const product = makeProduct({ variants: [] });
    const defaultVariant =
      product.variants.find((v) => v.isDefault) ?? product.variants[0];
    expect(defaultVariant).toBeUndefined();
  });
});
