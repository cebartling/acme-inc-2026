import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { OrderDetailPage } from "./OrderDetailPage";
import { ApiError, orderApi } from "@/services/api";
import type { OrderDetails } from "@/services/api";
import { useAuthStore } from "@/stores/auth.store";

const navigate = vi.fn();
vi.mock("@tanstack/react-router", () => ({
  Link: ({
    to,
    search,
    children,
    className,
  }: {
    to: string;
    search?: { page?: number };
    children: React.ReactNode;
    className?: string;
  }) => (
    <a
      href={search?.page ? `${to}?page=${search.page}` : to}
      className={className}
    >
      {children}
    </a>
  ),
  useNavigate: () => navigate,
}));

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  orderApi: { get: vi.fn() },
}));

const mockedGet = vi.mocked(orderApi.get);

const ORDER_ID = "11111111-2222-4333-8444-555555555555";

const line = {
  lineItemId: "line-1",
  variantId: "variant-1",
  productId: "product-1",
  productName: "Gadget Pro",
  variantName: "Black",
  sku: "GP-BLK",
  imageUrl: "https://example.com/gadget.png",
  quantity: 2,
  unitPrice: { amount: 34.99, currency: "USD" },
  lineTotal: { amount: 69.98, currency: "USD" },
};

const order: OrderDetails = {
  orderId: ORDER_ID,
  orderNumber: "ACME-20261029-000001",
  status: "SUBMITTED",
  createdAt: "2026-10-29T12:00:00Z",
  lineItems: [line],
  pricing: { subtotal: { amount: 69.98, currency: "USD" } },
};

function renderPage(
  orderId = ORDER_ID,
  queryClient = new QueryClient(),
  page?: number,
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <OrderDetailPage orderId={orderId} page={page} />
    </QueryClientProvider>,
  );
}

/** A background refetch of whatever the page has loaded. React Query notifies a tick later. */
function refetchAnswering(queryClient: QueryClient, error: ApiError) {
  mockedGet.mockRejectedValueOnce(error);
  return act(async () => {
    await queryClient.refetchQueries({ queryKey: ["order"] });
    await new Promise((resolve) => setTimeout(resolve));
  });
}

describe("OrderDetailPage (PIN-428)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({ isAuthenticated: true, isLoading: false });
  });

  it("shows the order's lines from the stored snapshot, and its subtotal", async () => {
    mockedGet.mockResolvedValueOnce(order);

    const { container } = renderPage();

    expect(await screen.findByTestId("orderNumber")).toHaveTextContent(
      "ACME-20261029-000001",
    );
    expect(screen.getByTestId("orderStatus")).toHaveTextContent("SUBMITTED");
    const row = screen.getByTestId("orderLine");
    expect(row).toHaveTextContent("Gadget Pro");
    expect(row).toHaveTextContent("Black");
    expect(row).toHaveTextContent("SKU GP-BLK");
    expect(row).toHaveTextContent("Qty 2");
    expect(container.querySelector("img")).toHaveAttribute(
      "src",
      line.imageUrl,
    );
    expect(screen.getByTestId("orderSubtotal")).toHaveTextContent("$69.98");
  });

  it("still shows a line whose snapshot fields are all missing", async () => {
    mockedGet.mockResolvedValueOnce({
      ...order,
      lineItems: [
        {
          ...line,
          productName: null,
          variantName: null,
          sku: null,
          imageUrl: null,
        },
      ],
    });

    const { container } = renderPage();

    expect(await screen.findByTestId("orderLine")).toHaveTextContent("Item");
    expect(container.querySelector("img")).toBeNull();
  });

  it("shows the not-found page for a 404", async () => {
    mockedGet.mockRejectedValueOnce(
      new ApiError("Nope", 404, { code: "ORDER_NOT_FOUND" }),
    );

    renderPage();

    expect(await screen.findByTestId("orderNotFound")).toBeInTheDocument();
    expect(mockedGet).toHaveBeenCalledTimes(1);
  });

  it.each(["not-a-uuid", "a://b", "50%"])(
    "shows the not-found page for %s without asking the service",
    async (orderId) => {
      renderPage(orderId);

      expect(await screen.findByTestId("orderNotFound")).toBeInTheDocument();
      expect(mockedGet).not.toHaveBeenCalled();
    },
  );

  it("says so when the order can't be read for another reason", async () => {
    mockedGet.mockRejectedValueOnce(new ApiError("Down", 503));

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /couldn’t load this order/i,
    );
    expect(screen.queryByTestId("orderNotFound")).not.toBeInTheDocument();
  });

  it("reads the order again when the customer asks to try again", async () => {
    mockedGet.mockRejectedValueOnce(new ApiError("Down", 503));
    mockedGet.mockResolvedValueOnce(order);
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByRole("button", { name: "Try again" }));

    expect(await screen.findByTestId("orderLine")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("goes back to the page of orders it was opened from", async () => {
    mockedGet.mockResolvedValueOnce(order);

    renderPage(ORDER_ID, new QueryClient(), 3);

    expect(
      await screen.findByRole("link", { name: "Back to your orders" }),
    ).toHaveAttribute("href", "/orders?page=3");
  });

  it("goes back to that page from the not-found page too", async () => {
    mockedGet.mockRejectedValueOnce(
      new ApiError("Nope", 404, { code: "ORDER_NOT_FOUND" }),
    );

    renderPage(ORDER_ID, new QueryClient(), 3);

    expect(
      await screen.findByRole("link", { name: "Back to your orders" }),
    ).toHaveAttribute("href", "/orders?page=3");
  });

  it("goes back to the first page when opened from elsewhere", async () => {
    mockedGet.mockResolvedValueOnce(order);

    renderPage();

    expect(
      await screen.findByRole("link", { name: "Back to your orders" }),
    ).toHaveAttribute("href", "/orders?page=1");
  });

  it("keeps the page of orders in the way back from sign-in", async () => {
    useAuthStore.setState({ isAuthenticated: false, isLoading: false });

    renderPage(ORDER_ID, new QueryClient(), 3);

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: `/orders/${ORDER_ID}?page=3` },
        replace: true,
      }),
    );
  });

  it("sends a signed-out visitor to sign-in and back to this order", async () => {
    useAuthStore.setState({ isAuthenticated: false, isLoading: false });

    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: `/orders/${ORDER_ID}` },
        replace: true,
      }),
    );
    expect(mockedGet).not.toHaveBeenCalled();
  });

  it("sends a customer to sign-in when the order service answers 401", async () => {
    mockedGet.mockRejectedValueOnce(
      new ApiError("Sign in", 401, { code: "SIGN_IN_REQUIRED" }),
    );

    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: `/orders/${ORDER_ID}` },
        replace: true,
      }),
    );
  });

  it("encodes the ID in the way back, which sign-in has to decode", async () => {
    useAuthStore.setState({ isAuthenticated: false, isLoading: false });

    renderPage("50%");

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: "/orders/50%25" },
        replace: true,
      }),
    );
  });

  it("keeps the order on screen when a background refetch fails", async () => {
    const queryClient = new QueryClient();
    mockedGet.mockResolvedValueOnce(order);
    renderPage(ORDER_ID, queryClient);
    await screen.findByTestId("orderLine");

    await refetchAnswering(queryClient, new ApiError("Down", 503));

    expect(screen.getByTestId("orderLine")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows the not-found page when a background refetch answers 404", async () => {
    const queryClient = new QueryClient();
    mockedGet.mockResolvedValueOnce(order);
    renderPage(ORDER_ID, queryClient);
    await screen.findByTestId("orderLine");

    await refetchAnswering(
      queryClient,
      new ApiError("Order not found", 404, { code: "ORDER_NOT_FOUND" }),
    );

    expect(screen.getByTestId("orderNotFound")).toBeInTheDocument();
  });
});
