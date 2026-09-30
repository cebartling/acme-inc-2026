import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ProductDetailPage } from "./ProductDetailPage";
import type { ProductDetail, ProductVariant } from "@/services/api";
import { cartApi, inventoryApi, pricingApi } from "@/services/api";

vi.mock("@tanstack/react-router", () => ({
  Link: ({
    to,
    children,
    ...props
  }: { to: string; children: React.ReactNode } & Record<string, unknown>) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  inventoryApi: { getAvailability: vi.fn() },
  pricingApi: { getPrice: vi.fn() },
  cartApi: { addItem: vi.fn() },
}));

vi.mock("@/services/analytics", () => ({
  trackVariantSelected: vi.fn(),
}));

const mockedAvailability = vi.mocked(inventoryApi.getAvailability);
const mockedPrice = vi.mocked(pricingApi.getPrice);
const mockedAddItem = vi.mocked(cartApi.addItem);

function variant(
  id: string,
  color: string,
  isDefault: boolean,
  inStock: boolean,
): ProductVariant {
  return {
    id,
    sku: `ACME-GP-${color.toUpperCase()}`,
    name: color,
    color,
    size: null,
    isDefault,
    inStock,
    priceOverride: null,
    images: [],
    tierPricing: [],
  };
}

// US-0004-10 AC-07's example: White out of stock, Black in stock
const product: ProductDetail = {
  id: "prod-1",
  slug: "gadget-pro",
  name: "Gadget Pro",
  description: null,
  price: 119.99,
  category: "Electronics",
  tags: [],
  availability: "IN_STOCK",
  relatedProducts: [
    {
      id: "prod-2",
      slug: "gadget-lite",
      name: "Gadget Lite",
      price: 79.99,
      category: "Electronics",
      inStock: true,
      imageUrl: null,
    },
  ],
  variants: [
    variant("var-black", "Black", true, true),
    variant("var-white", "White", false, false),
  ],
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: 0 }, mutations: { retry: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ProductDetailPage product={product} />
    </QueryClientProvider>,
  );
}

describe("ProductDetailPage stock (US-0004-10)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedAvailability.mockImplementation(async (variantId) => ({
      variantId,
      availability: variantId === "var-white" ? "OUT_OF_STOCK" : "IN_STOCK",
    }));
    mockedPrice.mockImplementation(async (variantId) => ({
      variantId,
      price: 119.99,
      originalPrice: null,
      tierPricing: [],
    }));
  });

  // AC-07: each variant's stock is its own
  it("disables Add to Cart for the out-of-stock variant only", async () => {
    const user = userEvent.setup();
    renderPage();

    // Disabled while stock and price load, so wait for the loaded state
    await vi.waitFor(() =>
      expect(screen.getByTestId("addToCartButton")).toBeEnabled(),
    );
    expect(screen.getByTestId("availabilityBadge")).toHaveTextContent(
      "In Stock",
    );

    await user.click(screen.getByRole("radio", { name: /White/ }));

    expect(
      await screen.findByText("Out of Stock", {
        selector: "[data-testid=availabilityBadge]",
      }),
    ).toBeInTheDocument();
    const button = screen.getByTestId("addToCartButton");
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-disabled", "true");

    // AC-08: no cart request for an out-of-stock variant
    await user.click(button);
    expect(mockedAddItem).not.toHaveBeenCalled();

    await user.click(screen.getByRole("radio", { name: /Black/ }));
    await vi.waitFor(() =>
      expect(screen.getByTestId("addToCartButton")).toBeEnabled(),
    );
    expect(screen.getByTestId("availabilityBadge")).toHaveTextContent(
      "In Stock",
    );
  });

  // AC-04: the in-stock related products become alternatives
  it("offers the related products as alternatives when the variant is out of stock", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "Related Products" }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("radio", { name: /White/ }));

    expect(
      await screen.findByRole("heading", {
        name: "Alternatives you might like",
      }),
    ).toBeInTheDocument();
  });
});
