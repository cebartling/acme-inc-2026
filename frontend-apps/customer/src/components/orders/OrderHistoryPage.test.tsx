import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { OrderHistoryPage } from "./OrderHistoryPage";
import { ApiError, orderApi } from "@/services/api";
import type { OrderList } from "@/services/api";
import { useAuthStore } from "@/stores/auth.store";

const navigate = vi.fn();
vi.mock("@tanstack/react-router", () => ({
  Link: ({
    to,
    children,
    className,
  }: {
    to: string;
    children: React.ReactNode;
    className?: string;
  }) => (
    <a href={to} className={className}>
      {children}
    </a>
  ),
  useNavigate: () => navigate,
}));

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  orderApi: { list: vi.fn() },
}));

const mockedList = vi.mocked(orderApi.list);

const onePage: OrderList = {
  orders: [
    {
      orderId: "order-1",
      orderNumber: "ACME-20261029-000001",
      status: "SUBMITTED",
      createdAt: "2026-10-29T12:00:00Z",
      itemCount: 3,
      subtotal: { amount: 69.98, currency: "USD" },
    },
  ],
  page: 1,
  pageSize: 10,
  totalResults: 1,
  totalPages: 1,
};

function pageWith(page: number, queryClient: QueryClient) {
  return (
    <QueryClientProvider client={queryClient}>
      <OrderHistoryPage page={page} />
    </QueryClientProvider>
  );
}

function renderPage(page = 1, queryClient = new QueryClient()) {
  return render(pageWith(page, queryClient));
}

function signInAs(userId: string) {
  useAuthStore.setState({
    user: {
      userId,
      customerId: userId,
      email: `${userId}@example.com`,
      firstName: "Test",
      lastName: "Customer",
    },
  });
}

/** A background refetch of whatever the page has loaded. React Query notifies a tick later. */
function refetchAnswering(queryClient: QueryClient, error: ApiError) {
  mockedList.mockRejectedValueOnce(error);
  return act(async () => {
    await queryClient.refetchQueries({ queryKey: ["orders"] });
    await new Promise((resolve) => setTimeout(resolve));
  });
}

describe("OrderHistoryPage (PIN-428)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      user: null,
      isAuthenticated: true,
      isLoading: false,
    });
  });

  it("lists each order's number, status, unit count and subtotal", async () => {
    mockedList.mockResolvedValueOnce(onePage);

    renderPage();

    const row = await screen.findByTestId("orderRow");
    expect(row).toHaveTextContent("ACME-20261029-000001");
    expect(row).toHaveTextContent("3 items");
    expect(row).toHaveTextContent("$69.98 USD");
    expect(mockedList).toHaveBeenCalledWith(1, 10);
  });

  it("shows a status the UI has never heard of as the service returned it", async () => {
    mockedList.mockResolvedValueOnce({
      ...onePage,
      orders: [{ ...onePage.orders[0], status: "SOMETHING_NEW" }],
    });

    renderPage();

    expect(await screen.findByTestId("orderStatus")).toHaveTextContent(
      "SOMETHING_NEW",
    );
  });

  it("shows an empty state to a customer with no orders", async () => {
    mockedList.mockResolvedValueOnce({
      ...onePage,
      orders: [],
      totalResults: 0,
      totalPages: 0,
    });

    renderPage();

    expect(await screen.findByTestId("ordersEmpty")).toBeInTheDocument();
  });

  it("doesn't tell a customer with orders they have none past the last page", async () => {
    mockedList.mockResolvedValueOnce({
      ...onePage,
      orders: [],
      page: 99,
    });

    renderPage(99);

    expect(
      await screen.findByRole("link", { name: "Go to your latest orders" }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("ordersEmpty")).not.toBeInTheDocument();
  });

  it("pages through orders when there's more than one page", async () => {
    mockedList.mockResolvedValueOnce({
      ...onePage,
      totalResults: 11,
      totalPages: 2,
    });
    const user = userEvent.setup();

    renderPage();

    await user.click(await screen.findByRole("button", { name: "Next page" }));

    expect(navigate).toHaveBeenCalledWith({
      to: "/orders",
      search: { page: 2 },
    });
  });

  it("hides pagination with only one page", async () => {
    mockedList.mockResolvedValueOnce(onePage);

    renderPage();

    await screen.findByTestId("orderRow");
    expect(screen.queryByTestId("searchPagination")).not.toBeInTheDocument();
  });

  it("says so when the orders can't be read", async () => {
    mockedList.mockRejectedValueOnce(new ApiError("Down", 503));

    renderPage();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /couldn’t load your orders/i,
    );
  });

  it("sends a signed-out visitor to sign-in and back, without asking for orders", async () => {
    useAuthStore.setState({ isAuthenticated: false, isLoading: false });

    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: "/orders" },
        replace: true,
      }),
    );
    expect(mockedList).not.toHaveBeenCalled();
  });

  it("waits for the auth store before deciding a visitor is signed out", () => {
    useAuthStore.setState({ isAuthenticated: false, isLoading: true });

    const { container } = renderPage();

    expect(container.querySelector("[aria-busy]")).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });

  it("comes back to the same page of orders after sign-in", async () => {
    useAuthStore.setState({ isAuthenticated: false, isLoading: false });

    renderPage(3);

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: "/orders?page=3" },
        replace: true,
      }),
    );
  });

  it("sends a customer to sign-in when the order service answers 401", async () => {
    mockedList.mockRejectedValueOnce(
      new ApiError("Sign in", 401, { code: "SIGN_IN_REQUIRED" }),
    );

    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: "/orders" },
        replace: true,
      }),
    );
  });

  it("doesn't bounce a customer who signed in again over the 401 that sent them away", async () => {
    const queryClient = new QueryClient();
    mockedList.mockResolvedValueOnce(onePage);
    const { unmount } = renderPage(1, queryClient);
    await screen.findByTestId("orderRow");
    await refetchAnswering(
      queryClient,
      new ApiError("Sign in", 401, { code: "SIGN_IN_REQUIRED" }),
    );
    await waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
    unmount();

    mockedList.mockResolvedValueOnce(onePage);
    renderPage(1, queryClient);

    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(3));
    expect(await screen.findByTestId("orderRow")).toBeInTheDocument();
    expect(navigate).toHaveBeenCalledTimes(1);
  });

  it("doesn't show one customer's cached orders to the next on this browser", async () => {
    const queryClient = new QueryClient();
    signInAs("customer-a");
    mockedList.mockResolvedValueOnce(onePage);
    const { unmount } = renderPage(1, queryClient);
    await screen.findByTestId("orderRow");
    unmount();

    signInAs("customer-b");
    mockedList.mockReturnValueOnce(new Promise(() => {}));
    renderPage(1, queryClient);

    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
    expect(screen.queryByTestId("orderRow")).not.toBeInTheDocument();
  });

  it("keeps the list on screen when a background refetch fails", async () => {
    const queryClient = new QueryClient();
    mockedList.mockResolvedValueOnce(onePage);
    renderPage(1, queryClient);
    await screen.findByTestId("orderRow");

    await refetchAnswering(queryClient, new ApiError("Down", 503));

    expect(screen.getByTestId("orderRow")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("keeps the list and its pagination on screen while the next page loads", async () => {
    const queryClient = new QueryClient();
    mockedList.mockResolvedValueOnce({
      ...onePage,
      totalResults: 11,
      totalPages: 2,
    });
    const { rerender } = renderPage(1, queryClient);
    await screen.findByTestId("orderRow");

    mockedList.mockReturnValueOnce(new Promise(() => {}));
    rerender(pageWith(2, queryClient));

    await waitFor(() => expect(mockedList).toHaveBeenCalledWith(2, 10));
    expect(screen.getByTestId("orderRow")).toBeInTheDocument();
    expect(screen.getByTestId("searchPagination")).toBeInTheDocument();
  });
});
