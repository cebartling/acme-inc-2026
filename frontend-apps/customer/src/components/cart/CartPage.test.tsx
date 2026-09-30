import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CartPage } from "./CartPage";
import type { Cart, CartItem } from "@/services/api";
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
  cartApi: {
    getCurrent: vi.fn(),
    updateItem: vi.fn(),
    removeItem: vi.fn(),
    clearCart: vi.fn(),
  },
  inventoryApi: { getAvailability: vi.fn() },
}));

const mockedGetCurrent = vi.mocked(cartApi.getCurrent);
const mockedUpdate = vi.mocked(cartApi.updateItem);
const mockedRemove = vi.mocked(cartApi.removeItem);
const mockedClear = vi.mocked(cartApi.clearCart);
const mockedAvailability = vi.mocked(inventoryApi.getAvailability);

/** Every line in stock unless a test says otherwise (US-0004-10 checks each line). */
function resetMocks() {
  vi.clearAllMocks();
  mockedAvailability.mockImplementation(async (variantId) => ({
    variantId,
    availability: "IN_STOCK",
  }));
}

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
  beforeEach(resetMocks);

  // US-0004-10 AC-05: a line that went out of stock since it was added
  it("warns about a line that is now out of stock and keeps Remove prominent", async () => {
    const inStock = { ...line(1, 19.99), id: "line-2", variantId: "variant-2" };
    inStock.productSnapshot = { ...inStock.productSnapshot, name: "Widget" };
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99), inStock));
    mockedAvailability.mockImplementation(async (variantId) => ({
      variantId,
      availability: variantId === "variant-1" ? "OUT_OF_STOCK" : "IN_STOCK",
    }));
    renderPage();

    const warning = await screen.findByTestId("lineOutOfStock");
    expect(warning).toHaveAttribute("role", "alert");
    expect(warning).toHaveTextContent(
      "Gadget Pro is now out of stock and cannot be included in your order",
    );
    const [outRow, inRow] = screen.getAllByTestId("cartLineItem");
    expect(outRow).toHaveAttribute("data-out-of-stock", "true");
    expect(within(outRow).getByTestId("removeLine")).toHaveTextContent(
      "Remove",
    );
    expect(inRow).not.toHaveAttribute("data-out-of-stock");
    expect(within(inRow).queryByTestId("lineOutOfStock")).toBeNull();
  });

  it("does not flag a line whose stock could not be checked", async () => {
    const outOfStock = {
      ...line(1, 19.99),
      id: "line-2",
      variantId: "variant-2",
    };
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99), outOfStock));
    mockedAvailability.mockImplementation(async (variantId) => {
      if (variantId === "variant-1") {
        throw new ApiError("Variant not found", 404);
      }
      return { variantId, availability: "OUT_OF_STOCK" };
    });
    renderPage();

    // The other line's warning shows once the checks have settled, so the failed one has too
    await screen.findByTestId("lineOutOfStock");
    const [failedRow, outRow] = screen.getAllByTestId("cartLineItem");
    expect(outRow).toHaveAttribute("data-out-of-stock", "true");
    expect(failedRow).not.toHaveAttribute("data-out-of-stock");
    expect(within(failedRow).queryByTestId("lineOutOfStock")).toBeNull();
  });

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

  it("reloads the cart and explains when a change conflicts with another (409)", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedUpdate.mockRejectedValue(
      new ApiError(
        "Your cart was changed at the same time. Please try again.",
        409,
        {
          error: "Your cart was changed at the same time. Please try again.",
          code: "CART_CONFLICT",
        },
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: "Increase quantity of Gadget Pro (Black)",
      }),
    );

    expect(await screen.findByTestId("lineMessage")).toHaveTextContent(
      "Your cart was changed at the same time. Please try again.",
    );
    await vi.waitFor(() => expect(mockedGetCurrent).toHaveBeenCalledTimes(2));
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

describe("CartPage: clear cart (PIN-294)", () => {
  beforeEach(resetMocks);

  async function confirmClear(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole("button", { name: "Clear cart" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(
      within(dialog).getByRole("button", { name: "Clear cart" }),
    );
  }

  it("clears the cart after confirmation and shows the empty state", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedClear.mockResolvedValue(cartOf());
    const user = userEvent.setup();
    renderPage();

    await confirmClear(user);

    expect(mockedClear).toHaveBeenCalledWith("cart-1");
    expect(await screen.findByTestId("cartEmptyState")).toBeInTheDocument();
  });

  it("keeps the cart when the confirmation is cancelled", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Clear cart" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", {
        name: "Cancel",
      }),
    );

    expect(mockedClear).not.toHaveBeenCalled();
    expect(screen.getByTestId("cartLineItem")).toBeInTheDocument();
    // Keyboard users land back where they were, not at the top of the page.
    await vi.waitFor(() =>
      expect(screen.getByTestId("clearCart")).toHaveFocus(),
    );
  });

  it("reloads the cart when the service no longer has it (404)", async () => {
    mockedGetCurrent
      .mockResolvedValueOnce(cartOf(line(2, 119.99)))
      .mockResolvedValueOnce(null);
    mockedClear.mockRejectedValue(
      new ApiError("Cart not found: cart-1", 404, {
        error: "Cart not found: cart-1",
        code: "CART_NOT_FOUND",
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await confirmClear(user);

    expect(await screen.findByTestId("cartEmptyState")).toBeInTheDocument();
    expect(mockedGetCurrent).toHaveBeenCalledTimes(2);
  });

  it("shows no error when the reloaded cart is a different one (404)", async () => {
    // e.g. signed in on another tab: the page's guest cart is gone, the account's cart has lines.
    mockedGetCurrent
      .mockResolvedValueOnce(cartOf(line(2, 119.99)))
      .mockResolvedValueOnce({ ...cartOf(line(1, 119.99)), id: "cart-2" });
    mockedClear.mockRejectedValue(
      new ApiError("Cart not found: cart-1", 404, {
        error: "Cart not found: cart-1",
        code: "CART_NOT_FOUND",
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await confirmClear(user);

    await vi.waitFor(() =>
      expect(screen.getByTestId("cartSubtotal")).toHaveTextContent("$119.99"),
    );
    await vi.waitFor(() =>
      expect(screen.getByTestId("clearCart")).toBeEnabled(),
    );
    expect(screen.queryByTestId("clearCartMessage")).not.toBeInTheDocument();
  });

  it("reloads the cart and explains when clearing conflicts with another change (409)", async () => {
    mockedGetCurrent.mockResolvedValue(cartOf(line(2, 119.99)));
    mockedClear.mockRejectedValue(
      new ApiError(
        "Your cart was changed at the same time. Please try again.",
        409,
        {
          error: "Your cart was changed at the same time. Please try again.",
          code: "CART_CONFLICT",
        },
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await confirmClear(user);

    expect(await screen.findByTestId("clearCartMessage")).toHaveTextContent(
      "Your cart was changed at the same time. Please try again.",
    );
    await vi.waitFor(() => expect(mockedGetCurrent).toHaveBeenCalledTimes(2));
  });
});
