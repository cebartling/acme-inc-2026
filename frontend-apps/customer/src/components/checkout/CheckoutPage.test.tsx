import { describe, it, expect, vi, beforeEach } from "vitest";
import type React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CheckoutPage } from "./CheckoutPage";
import { CART_QUERY_KEY } from "@/hooks/useCart";
import type { Cart, CheckoutSession, PlacedOrder } from "@/services/api";
import { ApiError, cartApi, orderApi } from "@/services/api";
import { useAuthStore } from "@/stores/auth.store";

const navigate = vi.fn();
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
  useNavigate: () => navigate,
}));

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  cartApi: { getCurrent: vi.fn(), startCheckout: vi.fn() },
  orderApi: { place: vi.fn() },
}));

const mockedGetCurrent = vi.mocked(cartApi.getCurrent);
const mockedStart = vi.mocked(cartApi.startCheckout);
const mockedPlace = vi.mocked(orderApi.place);

const cart: Cart = {
  id: "cart-1",
  items: [
    {
      id: "line-1",
      variantId: "variant-1",
      quantity: 2,
      unitPrice: 34.99,
      lineTotal: 69.98,
      productSnapshot: {
        productId: "product-1",
        name: "Gadget Pro",
        sku: "ACME-GP-BLK",
        variantName: "Black",
        imageUrl: null,
        attributes: {},
      },
    },
  ],
  summary: { itemCount: 2, subtotal: 69.98, currency: "USD" },
};

function sessionOf(checkoutSessionId: string): CheckoutSession {
  return {
    checkoutSessionId,
    cartId: "cart-1",
    status: "INITIATED",
    expiresAt: "2026-10-29T12:30:00Z",
    cart: cart.summary,
  };
}

const placed: PlacedOrder = {
  orderId: "order-1",
  orderNumber: "ACME-20261029-000001",
  status: "SUBMITTED",
  createdAt: "2026-10-29T12:00:00Z",
  summary: { subtotal: { amount: 69.98, currency: "USD" } },
};

function orderError(status: number, code: string) {
  return new ApiError(code, status, { error: code, code });
}

function signIn() {
  useAuthStore.getState().setUser({
    userId: "user-1",
    customerId: "customer-1",
    email: "jane@example.com",
    firstName: "Jane",
    lastName: "Doe",
  });
}

function renderPage(queryClient = newQueryClient()) {
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <CheckoutPage />
      </QueryClientProvider>,
    ),
  };
}

function newQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

describe("CheckoutPage (PIN-426)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    signIn();
    mockedGetCurrent.mockResolvedValue(cart);
    mockedStart.mockResolvedValue(sessionOf("session-1"));
  });

  it("shows the session's lines and subtotal", async () => {
    renderPage();

    expect(await screen.findByText("Gadget Pro")).toBeInTheDocument();
    expect(screen.getByTestId("cartSubtotal")).toHaveTextContent("$69.98");
    expect(mockedStart).toHaveBeenCalledWith("cart-1");
  });

  it("places the order and goes to its confirmation, emptying the cart", async () => {
    mockedPlace.mockResolvedValueOnce(placed);
    const { queryClient } = renderPage();
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    await userEvent.click(await screen.findByTestId("placeOrder"));

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/orders/$orderId/confirmation",
        params: { orderId: "order-1" },
        replace: true,
      }),
    );
    expect(mockedPlace).toHaveBeenCalledWith("session-1", expect.any(String));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: CART_QUERY_KEY });
  });

  it("places one order on a double-click", async () => {
    mockedPlace.mockReturnValue(new Promise(() => {}));
    renderPage();
    const button = await screen.findByTestId("placeOrder");

    await userEvent.dblClick(button);

    expect(mockedPlace).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
  });

  it("retries with the same key when the cart is unavailable, also after a reload", async () => {
    mockedPlace.mockRejectedValue(orderError(503, "CART_UNAVAILABLE"));
    const first = renderPage();

    await userEvent.click(await screen.findByTestId("placeOrder"));
    expect(await screen.findByTestId("placeOrderMessage")).toHaveTextContent(
      /try again/i,
    );
    await userEvent.click(screen.getByTestId("placeOrder"));
    first.unmount();
    renderPage();
    await userEvent.click(await screen.findByTestId("placeOrder"));

    await waitFor(() => expect(mockedPlace).toHaveBeenCalledTimes(3));
    const keys = mockedPlace.mock.calls.map(([, key]) => key);
    expect(new Set(keys).size).toBe(1);
    expect(keys[0].length).toBeLessThanOrEqual(255);
  });

  it("uses a new key for a new checkout session", async () => {
    mockedPlace.mockRejectedValue(orderError(503, "CART_UNAVAILABLE"));
    const first = renderPage();
    await userEvent.click(await screen.findByTestId("placeOrder"));
    await screen.findByTestId("placeOrderMessage");
    first.unmount();

    mockedStart.mockResolvedValue(sessionOf("session-2"));
    renderPage();
    await userEvent.click(await screen.findByTestId("placeOrder"));

    await waitFor(() => expect(mockedPlace).toHaveBeenCalledTimes(2));
    const [[, firstKey], [secondSession, secondKey]] = mockedPlace.mock.calls;
    expect(secondSession).toBe("session-2");
    expect(secondKey).not.toBe(firstKey);
  });

  it("tells the customer to start again when the session lapsed", async () => {
    mockedPlace.mockRejectedValueOnce(
      orderError(404, "CHECKOUT_SESSION_NOT_FOUND"),
    );
    renderPage();

    await userEvent.click(await screen.findByTestId("placeOrder"));

    expect(await screen.findByTestId("checkoutExpired")).toHaveTextContent(
      /checkout session expired/i,
    );
    expect(screen.getByRole("link", { name: /back to cart/i })).toHaveAttribute(
      "href",
      "/cart",
    );
    expect(screen.queryByTestId("placeOrder")).not.toBeInTheDocument();
  });

  it("says so when the order was already placed in another tab", async () => {
    mockedPlace.mockRejectedValueOnce(
      orderError(409, "CART_ALREADY_CONVERTED"),
    );
    renderPage();

    await userEvent.click(await screen.findByTestId("placeOrder"));

    expect(await screen.findByTestId("orderAlreadyPlaced")).toHaveTextContent(
      /already placed in another tab/i,
    );
    expect(screen.queryByTestId("placeOrder")).not.toBeInTheDocument();
  });

  it("shows a refusal to start checkout without retrying it", async () => {
    mockedStart.mockRejectedValue(
      new ApiError("Some items in your cart can't be ordered", 422, {
        error: "Some items in your cart can't be ordered",
        code: "CART_VALIDATION_FAILED",
      }),
    );
    // The app's defaults, which retry queries, rather than the tests' retry: false
    renderPage(new QueryClient());

    expect(await screen.findByTestId("checkoutUnavailable")).toHaveTextContent(
      "Some items in your cart can't be ordered",
    );
    expect(mockedStart).toHaveBeenCalledTimes(1);
  });

  it("says the cart couldn't load rather than that it is empty", async () => {
    mockedGetCurrent.mockRejectedValue(new ApiError("HTTP 503", 503));
    renderPage();

    expect(await screen.findByTestId("checkoutUnavailable")).toHaveTextContent(
      /couldn’t load your cart/i,
    );
    expect(screen.queryByTestId("checkoutEmpty")).not.toBeInTheDocument();
  });

  it("sends a guest to sign-in without starting checkout", async () => {
    useAuthStore.getState().clearUser();
    renderPage();

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: "/cart" },
      }),
    );
    expect(mockedStart).not.toHaveBeenCalled();
  });
});
