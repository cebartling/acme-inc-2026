import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CartBadge } from "./CartBadge";
import { cartApi } from "@/services/api";

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
  cartApi: { getCurrent: vi.fn() },
}));

const mockedGetCurrent = vi.mocked(cartApi.getCurrent);

function renderBadge() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CartBadge />
    </QueryClientProvider>,
  );
}

describe("CartBadge", () => {
  beforeEach(() => vi.clearAllMocks());

  it("links to the cart page and hides the count when there is no cart", async () => {
    mockedGetCurrent.mockResolvedValue(null);
    renderBadge();

    const badge = screen.getByTestId("cartBadge");
    expect(badge).toHaveAttribute("href", "/cart");
    expect(badge).toHaveAccessibleName("Cart: 0 items");
    await waitFor(() => expect(mockedGetCurrent).toHaveBeenCalled());
    expect(screen.queryByTestId("cartBadgeCount")).not.toBeInTheDocument();
  });

  it("shows the persisted cart's item count on load", async () => {
    mockedGetCurrent.mockResolvedValue({
      id: "cart-1",
      items: [],
      summary: { itemCount: 3, subtotal: 329.97, currency: "USD" },
    });
    renderBadge();

    expect(await screen.findByTestId("cartBadgeCount")).toHaveTextContent("3");
    expect(screen.getByTestId("cartBadge")).toHaveAccessibleName(
      "Cart: 3 items",
    );
  });

  it("announces the count in a live region, since the link cannot be one", async () => {
    mockedGetCurrent.mockResolvedValue({
      id: "cart-1",
      items: [],
      summary: { itemCount: 1, subtotal: 119.99, currency: "USD" },
    });
    renderBadge();

    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("Cart: 1 item"),
    );
  });
});
