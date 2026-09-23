import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AddToCartForm } from "./AddToCartForm";
import type { Cart, ProductDetail, ProductVariant } from "@/services/api";
import { ApiError, cartApi, inventoryApi } from "@/services/api";

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
  cartApi: { addItem: vi.fn() },
}));

const mockedAvailability = vi.mocked(inventoryApi.getAvailability);
const mockedAddItem = vi.mocked(cartApi.addItem);

const variant: ProductVariant = {
  id: "variant-1",
  sku: "ACME-GM-PRO-BLK",
  name: "Black",
  color: "Black",
  size: null,
  isDefault: true,
  inStock: true,
  priceOverride: null,
  images: ["/img/mouse-black.png"],
  tierPricing: [],
};

const product: ProductDetail = {
  id: "product-1",
  slug: "acme-gaming-mouse-pro",
  name: "ACME Gaming Mouse Pro",
  description: null,
  price: 69.99,
  category: "Electronics",
  tags: [],
  availability: "IN_STOCK",
  relatedProducts: [],
  variants: [variant],
};

function cartWith(quantity: number, unitPrice: number): Cart {
  return {
    id: "cart-1",
    items: [
      {
        id: "line-1",
        variantId: "variant-1",
        quantity,
        unitPrice,
        lineTotal: quantity * unitPrice,
        productSnapshot: {
          productId: "product-1",
          name: product.name,
          sku: variant.sku,
          variantName: "Black",
          imageUrl: null,
          attributes: {},
        },
      },
    ],
    summary: {
      itemCount: quantity,
      subtotal: quantity * unitPrice,
      currency: "USD",
    },
  };
}

function renderForm(
  overrides: Partial<Parameters<typeof AddToCartForm>[0]> = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AddToCartForm
        product={product}
        variant={variant}
        isOutOfStock={false}
        isUnavailable={false}
        {...overrides}
      />
    </QueryClientProvider>,
  );
}

describe("AddToCartForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedAvailability.mockResolvedValue({
      variantId: "variant-1",
      availability: "IN_STOCK",
    });
  });

  it("names the product and variant for assistive tech", () => {
    renderForm();

    expect(
      screen.getByRole("button", {
        name: "Add ACME Gaming Mouse Pro (Black) to cart",
      }),
    ).toBeEnabled();
  });

  it("sends the quantity and a product snapshot, then confirms with the line total", async () => {
    mockedAddItem.mockResolvedValue(cartWith(3, 64.99));
    const user = userEvent.setup();
    renderForm();

    await user.clear(screen.getByTestId("quantityInput"));
    await user.type(screen.getByTestId("quantityInput"), "3");
    await user.click(screen.getByTestId("addToCartButton"));

    const confirmation = await screen.findByTestId("addToCartConfirmation");
    expect(confirmation).toHaveTextContent(
      "3 × ACME Gaming Mouse Pro (Black) · $64.99 each",
    );
    expect(confirmation).toHaveTextContent("Line total: $194.97 (3 in cart)");
    expect(mockedAddItem).toHaveBeenCalledWith({
      variantId: "variant-1",
      quantity: 3,
      productSnapshot: {
        productId: "product-1",
        name: "ACME Gaming Mouse Pro",
        sku: "ACME-GM-PRO-BLK",
        variantName: "Black",
        imageUrl: "/img/mouse-black.png",
        attributes: { color: "Black" },
      },
    });
    expect(screen.getByTestId("viewCartLink")).toHaveAttribute("href", "/cart");
  });

  it("disables the button and marks it busy while the add is in flight", async () => {
    let resolveAdd: (cart: Cart) => void = () => {};
    mockedAddItem.mockReturnValue(
      new Promise((resolve) => {
        resolveAdd = resolve;
      }),
    );
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByTestId("addToCartButton"));

    await waitFor(() =>
      expect(screen.getByTestId("addToCartButton")).toHaveAttribute(
        "aria-busy",
        "true",
      ),
    );
    expect(screen.getByTestId("addToCartButton")).toBeDisabled();

    resolveAdd(cartWith(1, 69.99));
    await screen.findByTestId("addToCartConfirmation");
    expect(screen.getByTestId("addToCartButton")).toBeEnabled();
  });

  it("shows the service's error linked to the quantity input", async () => {
    mockedAddItem.mockRejectedValue(
      new ApiError("Maximum order quantity is 10 for this item", 422),
    );
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByTestId("addToCartButton"));

    const error = await screen.findByTestId("addToCartError");
    expect(error).toHaveTextContent(
      "Maximum order quantity is 10 for this item",
    );
    expect(screen.getByTestId("quantityInput")).toHaveAttribute(
      "aria-describedby",
      error.id,
    );
  });

  it("is disabled for an out-of-stock variant", () => {
    renderForm({ isOutOfStock: true });

    expect(screen.getByTestId("addToCartButton")).toBeDisabled();
    expect(screen.getByTestId("addToCartButton")).toHaveTextContent(
      "Out of Stock",
    );
  });

  it("is disabled for a quantity below 1", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.clear(screen.getByTestId("quantityInput"));
    await user.type(screen.getByTestId("quantityInput"), "0");

    expect(screen.getByTestId("addToCartButton")).toBeDisabled();
  });

  it("is disabled when the product has no variant to add", () => {
    renderForm({ variant: undefined });

    expect(screen.getByTestId("addToCartButton")).toBeDisabled();
  });
});
