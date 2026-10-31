import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { OrderConfirmation } from "./OrderConfirmation";
import { ApiError, orderApi } from "@/services/api";
import type { OrderDetails } from "@/services/api";

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
  orderApi: { get: vi.fn(), place: vi.fn() },
}));

const mockedGet = vi.mocked(orderApi.get);
const mockedPlace = vi.mocked(orderApi.place);

const order: OrderDetails = {
  orderId: "order-1",
  orderNumber: "ACME-20261029-000001",
  status: "SUBMITTED",
  createdAt: "2026-10-29T12:00:00Z",
  lineItems: [],
  pricing: { subtotal: { amount: 69.98, currency: "USD" } },
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrderConfirmation orderId="order-1" />
    </QueryClientProvider>,
  );
}

describe("OrderConfirmation (PIN-426)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("shows the order number by reading the order, never placing it again", async () => {
    mockedGet.mockResolvedValueOnce(order);

    renderPage();

    expect(await screen.findByTestId("orderNumber")).toHaveTextContent(
      "ACME-20261029-000001",
    );
    expect(screen.getByTestId("orderSubtotal")).toHaveTextContent("$69.98");
    expect(mockedGet).toHaveBeenCalledWith("order-1");
    expect(mockedPlace).not.toHaveBeenCalled();
  });

  it("links to the order's detail page (PIN-428)", async () => {
    mockedGet.mockResolvedValueOnce(order);

    renderPage();

    expect(
      await screen.findByRole("link", { name: "View order" }),
    ).toHaveAttribute("href", "/orders/$orderId");
  });

  it("says so when the order can't be read", async () => {
    mockedGet.mockRejectedValueOnce(
      new ApiError("Order not found", 404, {
        error: "Order not found",
        code: "ORDER_NOT_FOUND",
      }),
    );

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /couldn’t load this order/i,
    );
  });
});
