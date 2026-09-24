import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CartPage } from "./CartPage";
import type { Cart, CartItem } from "@/services/api";
import { ApiError, cartApi } from "@/services/api";

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
  cartApi: { getCurrent: vi.fn(), updateItem: vi.fn(), removeItem: vi.fn() },
}));

const mockedGetCurrent = vi.mocked(cartApi.getCurrent);
const mockedUpdate = vi.mocked(cartApi.updateItem);
const mockedRemove = vi.mocked(cartApi.removeItem);

function line(quantity: number, unitPrice: number): CartItem {
  return {
    id: "line-1",
    variantId: "variant-1",
    quantity,
    unitPrice,
    lineTotal: Math.round(quantity * unitPrice * 100) / 100,
    productSnapshot: {
      productId: "product-1",
      name: "Gadget Pro",
      sku: "ACME-GP-BLK",
      variantName: "Black",
      imageUrl: null,
      attributes: {},
    },
  };
}

function cartOf(...items: CartItem[]): Cart {
  const subtotal = items.reduce((sum, i) => sum + i.lineTotal, 0);
  return {
    id: "cart-1",
    items,
    summary: {
      itemCount: items.reduce((sum, i) => sum + i.quantity, 0),
      subtotal: Math.round(subtotal * 100) / 100,
      currency: "USD",
    },
  };
}

/** The cart service's 404 for a line that no longer exists (US-0004-12). */
function lineGone() {
  return new ApiError("Cart item not found: line-1", 404, {
    error: "Cart item not found: line-1",
    code: "CART_ITEM_NOT_FOUND",
  });
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: 0 }, mutations: { retry: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CartPage />
    </QueryClientProvider>,
  );
}

describe("CartPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows each line and the totals to 2 decimals", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    renderPage();

    const row = await screen.findByTestId("cartLineItem");
    expect(within(row).getByTestId("lineName")).toHaveTextContent("Gadget Pro");
    expect(within(row).getByTestId("lineUnitPrice")).toHaveTextContent(
      "$119.99 each",
    );
    expect(within(row).getByTestId("lineTotal")).toHaveTextContent("$239.98");
    expect(screen.getByTestId("cartSubtotal")).toHaveTextContent("$239.98");
    expect(screen.getByTestId("cartEstimatedTotal")).toHaveTextContent(
      "$239.98",
    );
  });

  it("shows the empty state with a link home when there is no cart", async () => {
    mockedGetCurrent.mockResolvedValue(null);
    renderPage();

    expect(await screen.findByTestId("cartEmptyState")).toBeInTheDocument();
    expect(screen.getByTestId("continueShopping")).toHaveAttribute("href", "/");
  });

  it("increments a line and shows the repriced totals", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedUpdate.mockResolvedValue(cartOf(line(3, 109.99)));
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: "Increase quantity of Gadget Pro (Black)",
      }),
    );

    expect(mockedUpdate).toHaveBeenCalledWith("cart-1", "line-1", 3);
    expect(await screen.findByText("$109.99 each")).toBeInTheDocument();
    expect(screen.getByTestId("cartSubtotal")).toHaveTextContent("$329.97");
  });

  it("clamps a typed over-max quantity and explains why", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedUpdate
      .mockRejectedValueOnce(
        new ApiError("Maximum order quantity is 10 for this item", 422, {
          maxQuantity: 10,
        }),
      )
      .mockResolvedValueOnce(cartOf(line(10, 99.99)));
    const user = userEvent.setup();
    renderPage();

    const input = await screen.findByTestId("lineQuantityInput");
    await user.clear(input);
    await user.type(input, "11{Enter}");

    const message = await screen.findByTestId("lineMessage");
    expect(message).toHaveTextContent(
      "Maximum order quantity is 10 for this item",
    );
    expect(message).toHaveAttribute("role", "alert");
    expect(screen.getByTestId("lineQuantityInput")).toHaveValue(10);
  });

  it("disables decrement at 1, leaving removal to the Remove button", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(1, 119.99)));
    renderPage();

    expect(await screen.findByTestId("decreaseQuantity")).toBeDisabled();
  });

  it("removes the last line and shows the empty state", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedRemove.mockResolvedValue(cartOf());
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: "Remove Gadget Pro (Black) from cart",
      }),
    );

    expect(mockedRemove).toHaveBeenCalledWith("cart-1", "line-1");
    expect(await screen.findByTestId("cartEmptyState")).toBeInTheDocument();
  });

  it("reloads the cart when a line was already removed elsewhere (404)", async () => {
    mockedGetCurrent
      .mockResolvedValueOnce(cartOf(line(2, 119.99)))
      .mockResolvedValueOnce(cartOf());
    mockedRemove.mockRejectedValue(lineGone());
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: "Remove Gadget Pro (Black) from cart",
      }),
    );

    expect(await screen.findByTestId("cartEmptyState")).toBeInTheDocument();
    expect(mockedGetCurrent).toHaveBeenCalledTimes(2);
  });

  it("shows no error when a stale line is gone (404), only the reloaded cart", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedUpdate.mockRejectedValue(lineGone());
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: "Increase quantity of Gadget Pro (Black)",
      }),
    );

    await vi.waitFor(() => expect(mockedGetCurrent).toHaveBeenCalledTimes(2));
    expect(screen.queryByTestId("lineMessage")).not.toBeInTheDocument();
  });

  it("still explains a 404 for a delisted variant, whose line is still in the cart", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedUpdate.mockRejectedValue(
      new ApiError("Variant not found: variant-1", 404, {
        error: "Variant not found: variant-1",
        code: "VARIANT_NOT_FOUND",
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: "Increase quantity of Gadget Pro (Black)",
      }),
    );

    expect(await screen.findByTestId("lineMessage")).toHaveTextContent(
      "Variant not found: variant-1",
    );
  });

  it("shows the latest action's error, not an older one", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedUpdate.mockRejectedValue(new ApiError("Pricing unavailable", 503));
    mockedRemove.mockRejectedValue(
      new ApiError("Cart service unavailable", 503),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: "Increase quantity of Gadget Pro (Black)",
      }),
    );
    expect(await screen.findByTestId("lineMessage")).toHaveTextContent(
      "Pricing unavailable",
    );

    await user.click(
      screen.getByRole("button", {
        name: "Remove Gadget Pro (Black) from cart",
      }),
    );
    expect(
      await screen.findByText("Cart service unavailable"),
    ).toBeInTheDocument();
  });
});
