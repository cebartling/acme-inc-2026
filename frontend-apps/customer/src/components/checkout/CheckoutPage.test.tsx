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
  cartApi: {
    getCurrent: vi.fn(),
    startCheckout: vi.fn(),
    leaveCheckout: vi.fn(),
  },
  orderApi: { place: vi.fn(), findByKey: vi.fn() },
}));

const mockedGetCurrent = vi.mocked(cartApi.getCurrent);
const mockedStart = vi.mocked(cartApi.startCheckout);
const mockedLeave = vi.mocked(cartApi.leaveCheckout);
const mockedPlace = vi.mocked(orderApi.place);
const mockedFind = vi.mocked(orderApi.findByKey);

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

  it("leaves checkout before going back to the cart (PIN-439)", async () => {
    let release: (value: Cart) => void = () => {};
    mockedLeave.mockReturnValueOnce(
      new Promise((resolve) => {
        release = resolve;
      }),
    );
    renderPage();

    await userEvent.click(await screen.findByTestId("backToCart"));

    await waitFor(() => expect(mockedLeave).toHaveBeenCalledWith("cart-1"));
    expect(navigate).not.toHaveBeenCalled();

    expect(
      sessionStorage.getItem("acme.checkout.session-1.key"),
    ).not.toBeNull();

    release(cart);

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({ to: "/cart", replace: true }),
    );
    expect(sessionStorage.getItem("acme.checkout.session-1.key")).toBeNull();
  });

  it("clears the leave failure when placing the order (PIN-439)", async () => {
    mockedLeave.mockRejectedValueOnce(
      new ApiError("Something went wrong", 500, { code: "INTERNAL_ERROR" }),
    );
    mockedPlace.mockReturnValueOnce(new Promise(() => {}));
    renderPage();

    await userEvent.click(await screen.findByTestId("backToCart"));
    await screen.findByTestId("backToCartMessage");
    await userEvent.click(screen.getByTestId("placeOrder"));

    await waitFor(() =>
      expect(screen.queryByTestId("backToCartMessage")).not.toBeInTheDocument(),
    );
  });

  it("stays on the review with a message when leaving fails (PIN-439)", async () => {
    mockedLeave.mockRejectedValueOnce(
      new ApiError("Something went wrong", 500, { code: "INTERNAL_ERROR" }),
    );
    renderPage();

    await userEvent.click(await screen.findByTestId("backToCart"));

    expect(await screen.findByTestId("backToCartMessage")).toHaveTextContent(
      "Something went wrong",
    );
    expect(navigate).not.toHaveBeenCalled();
    expect(screen.getByTestId("placeOrder")).toBeEnabled();
  });

  it("goes to the cart when there is no cart left to release (PIN-439)", async () => {
    mockedLeave.mockRejectedValueOnce(
      new ApiError("Cart not found", 404, { code: "CART_NOT_FOUND" }),
    );
    renderPage();

    await userEvent.click(await screen.findByTestId("backToCart"));

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({ to: "/cart", replace: true }),
    );
    expect(screen.queryByTestId("backToCartMessage")).not.toBeInTheDocument();
  });

  it("can't go back while the order is being placed (PIN-439)", async () => {
    mockedPlace.mockReturnValue(new Promise(() => {}));
    renderPage();

    await userEvent.click(await screen.findByTestId("placeOrder"));

    expect(screen.getByTestId("backToCart")).toBeDisabled();
  });

  it("can't place the order while leaving checkout (PIN-439)", async () => {
    mockedLeave.mockReturnValue(new Promise(() => {}));
    renderPage();

    await userEvent.click(await screen.findByTestId("backToCart"));

    expect(screen.getByTestId("placeOrder")).toBeDisabled();
    expect(screen.getByTestId("backToCart")).toBeDisabled();
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

  it.each(["CHECKOUT_SESSION_NOT_FOUND", "PLACEMENT_ABANDONED"])(
    "tells the customer to start again on %s",
    async (code) => {
      mockedPlace.mockRejectedValueOnce(orderError(404, code));
      renderPage();

      await userEvent.click(await screen.findByTestId("placeOrder"));

      expect(await screen.findByTestId("checkoutExpired")).toHaveTextContent(
        /checkout session expired/i,
      );
      expect(
        screen.getByRole("link", { name: /back to cart/i }),
      ).toHaveAttribute("href", "/cart");
      expect(screen.queryByTestId("placeOrder")).not.toBeInTheDocument();
      await waitFor(() => expect(sessionStorage.length).toBe(0));
    },
  );

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

  describe("after a lost Place order answer (PIN-440)", () => {
    // The order may be placed, so the reload finds no cart; returns the key the first try used
    async function loseTheAnswerAndReload() {
      mockedPlace.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      const first = renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await screen.findByTestId("placeOrderMessage");
      first.unmount();

      const [, key] = mockedPlace.mock.calls[0];
      mockedPlace.mockClear();
      mockedGetCurrent.mockResolvedValue(null);
      return key;
    }

    it("finds the order by its key and goes to its confirmation, placing nothing", async () => {
      const key = await loseTheAnswerAndReload();
      mockedFind.mockResolvedValueOnce(placed);

      renderPage();

      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith({
          to: "/orders/$orderId/confirmation",
          params: { orderId: "order-1" },
          replace: true,
        }),
      );
      expect(mockedFind).toHaveBeenCalledWith(key);
      expect(mockedPlace).not.toHaveBeenCalled();
      expect(screen.queryByTestId("checkoutEmpty")).not.toBeInTheDocument();
      expect(sessionStorage.length).toBe(0);
    });

    it("shows the empty cart when the key has no order", async () => {
      await loseTheAnswerAndReload();
      mockedFind.mockRejectedValueOnce(orderError(404, "ORDER_NOT_FOUND"));

      renderPage();

      expect(await screen.findByTestId("checkoutEmpty")).toBeInTheDocument();
      expect(mockedPlace).not.toHaveBeenCalled();
      await waitFor(() => expect(sessionStorage.length).toBe(0));
    });

    it("places nothing when the cart only looked empty and the key has no order", async () => {
      await loseTheAnswerAndReload();
      // No access cookie yet: the cart looks empty until the lookup has refreshed the sign-in
      mockedGetCurrent.mockResolvedValueOnce(null).mockResolvedValue(cart);
      mockedFind.mockRejectedValueOnce(orderError(404, "ORDER_NOT_FOUND"));

      renderPage();

      expect(await screen.findByTestId("placeOrder")).toBeInTheDocument();
      expect(mockedPlace).not.toHaveBeenCalled();
    });

    it("resumes a PENDING order when its cart is gone", async () => {
      const key = await loseTheAnswerAndReload();
      mockedFind.mockResolvedValueOnce({ ...placed, status: "PENDING" });
      mockedPlace.mockResolvedValueOnce(placed);

      renderPage();

      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith(
          expect.objectContaining({ to: "/orders/$orderId/confirmation" }),
        ),
      );
      expect(mockedPlace).toHaveBeenCalledWith("session-1", key);
      expect(sessionStorage.length).toBe(0);
    });

    it("leaves a PENDING order to the customer when its cart still has lines", async () => {
      const key = await loseTheAnswerAndReload();
      mockedGetCurrent.mockResolvedValueOnce(null).mockResolvedValue(cart);
      mockedFind.mockResolvedValueOnce({ ...placed, status: "PENDING" });

      renderPage();

      await userEvent.click(await screen.findByTestId("placeOrder"));

      await waitFor(() => expect(mockedPlace).toHaveBeenCalledTimes(1));
      expect(mockedPlace).toHaveBeenCalledWith("session-1", key);
    });

    it.each([
      "CHECKOUT_SESSION_NOT_FOUND",
      "CART_NOT_FOUND",
      "PLACEMENT_ABANDONED",
    ])("shows the empty cart when resuming answers %s", async (code) => {
      await loseTheAnswerAndReload();
      mockedFind.mockResolvedValueOnce({ ...placed, status: "PENDING" });
      mockedPlace.mockRejectedValueOnce(orderError(404, code));

      renderPage();

      expect(await screen.findByTestId("checkoutEmpty")).toBeInTheDocument();
      await waitFor(() => expect(sessionStorage.length).toBe(0));
    });

    it("stops trying when another order has the cart", async () => {
      await loseTheAnswerAndReload();
      mockedFind.mockResolvedValueOnce({ ...placed, status: "PENDING" });
      mockedPlace.mockRejectedValueOnce(
        orderError(409, "CART_ALREADY_CONVERTED"),
      );

      renderPage();

      expect(await screen.findByTestId("orderUnconfirmed")).toBeInTheDocument();
      await waitFor(() => expect(sessionStorage.length).toBe(0));
    });

    it("finds the order without a reload when a refetch finds the cart gone", async () => {
      mockedPlace.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      const { queryClient } = renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await screen.findByTestId("placeOrderMessage");
      const [, key] = mockedPlace.mock.calls[0];
      mockedFind.mockResolvedValueOnce(placed);

      mockedGetCurrent.mockResolvedValue(null);
      await queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });

      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith({
          to: "/orders/$orderId/confirmation",
          params: { orderId: "order-1" },
          replace: true,
        }),
      );
      expect(mockedFind).toHaveBeenCalledWith(key);
      expect(sessionStorage.length).toBe(0);
    });

    it("keeps the next attempt after a lookup found no order", async () => {
      await loseTheAnswerAndReload();
      mockedGetCurrent.mockResolvedValueOnce(null).mockResolvedValue(cart);
      mockedFind.mockRejectedValueOnce(orderError(404, "ORDER_NOT_FOUND"));
      mockedPlace.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      const second = renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await screen.findByTestId("placeOrderMessage");
      const [, key] = mockedPlace.mock.calls[0];
      second.unmount();

      mockedGetCurrent.mockResolvedValue(null);
      mockedFind.mockResolvedValueOnce(placed);
      renderPage();

      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith(
          expect.objectContaining({ to: "/orders/$orderId/confirmation" }),
        ),
      );
      expect(mockedFind).toHaveBeenLastCalledWith(key);
    });

    it("looks again when the retry of a PENDING order loses its answer too", async () => {
      await loseTheAnswerAndReload();
      mockedGetCurrent.mockResolvedValueOnce(null).mockResolvedValue(cart);
      mockedFind.mockResolvedValueOnce({ ...placed, status: "PENDING" });
      mockedPlace.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      const { queryClient } = renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await screen.findByTestId("placeOrderMessage");

      mockedGetCurrent.mockResolvedValue(null);
      mockedFind.mockResolvedValueOnce(placed);
      await queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });

      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith(
          expect.objectContaining({ to: "/orders/$orderId/confirmation" }),
        ),
      );
    });

    it("uses a new, recoverable key after a lookup on this page found no order", async () => {
      mockedPlace.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      const { queryClient } = renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await screen.findByTestId("placeOrderMessage");
      const [, firstKey] = mockedPlace.mock.calls[0];

      // The cart looks empty once, until the lookup has refreshed the sign-in
      mockedGetCurrent.mockResolvedValueOnce(null);
      mockedFind.mockRejectedValueOnce(orderError(404, "ORDER_NOT_FOUND"));
      await queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
      await waitFor(() => expect(mockedFind).toHaveBeenCalledTimes(1));

      mockedPlace.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await waitFor(() => expect(mockedPlace).toHaveBeenCalledTimes(2));
      const [, secondKey] = mockedPlace.mock.calls[1];
      expect(secondKey).not.toBe(firstKey);
      await screen.findByTestId("placeOrderMessage");

      mockedGetCurrent.mockResolvedValue(null);
      mockedFind.mockResolvedValueOnce(placed);
      await queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });

      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith(
          expect.objectContaining({ to: "/orders/$orderId/confirmation" }),
        ),
      );
      expect(mockedFind).toHaveBeenLastCalledWith(secondKey);
    });

    it("finds the order instead of going to /cart when Back to cart finds the cart gone", async () => {
      mockedPlace.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      mockedLeave.mockRejectedValueOnce(
        new ApiError("Cart not found", 404, { code: "CART_NOT_FOUND" }),
      );
      renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await screen.findByTestId("placeOrderMessage");
      mockedGetCurrent.mockResolvedValue(null);
      mockedFind.mockResolvedValueOnce(placed);

      await userEvent.click(screen.getByTestId("backToCart"));

      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith(
          expect.objectContaining({ to: "/orders/$orderId/confirmation" }),
        ),
      );
      expect(navigate).not.toHaveBeenCalledWith({
        to: "/cart",
        replace: true,
      });
    });

    it("clears the session's key when the cart refetches mid-request", async () => {
      let answer: (order: PlacedOrder) => void = () => {};
      mockedPlace.mockReturnValueOnce(
        new Promise((resolve) => {
          answer = resolve;
        }),
      );
      const { queryClient } = renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));

      mockedGetCurrent.mockResolvedValue(null);
      await queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
      await screen.findByTestId("checkoutEmpty");
      answer(placed);

      await waitFor(() => expect(navigate).toHaveBeenCalled());
      expect(sessionStorage.length).toBe(0);
    });

    it("keeps an unanswered attempt when a newer review is left", async () => {
      const key = await loseTheAnswerAndReload();
      mockedGetCurrent.mockResolvedValue(cart);
      mockedStart.mockResolvedValue(sessionOf("session-2"));
      mockedLeave.mockResolvedValueOnce(cart);
      const newer = renderPage();
      await userEvent.click(await screen.findByTestId("backToCart"));
      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith({ to: "/cart", replace: true }),
      );
      newer.unmount();

      mockedGetCurrent.mockResolvedValue(null);
      mockedFind.mockResolvedValueOnce(placed);
      renderPage();

      await waitFor(() => expect(mockedFind).toHaveBeenCalledWith(key));
    });

    it.each([
      ["the service fails", orderError(500, "INTERNAL_ERROR")],
      ["the network fails", new TypeError("Failed to fetch")],
    ])(
      "points to the customer's orders when %s, and can try again",
      async (_, failure) => {
        await loseTheAnswerAndReload();
        mockedFind.mockRejectedValueOnce(failure);

        const second = renderPage();

        expect(await screen.findByTestId("orderUnconfirmed")).toHaveTextContent(
          /couldn’t confirm your order/i,
        );
        expect(
          screen.getByRole("link", { name: /view your orders/i }),
        ).toHaveAttribute("href", "/orders");
        second.unmount();

        mockedFind.mockResolvedValueOnce(placed);
        renderPage();

        await waitFor(() =>
          expect(navigate).toHaveBeenCalledWith(
            expect.objectContaining({ to: "/orders/$orderId/confirmation" }),
          ),
        );
      },
    );

    it("sends nothing when Place order was never pressed", async () => {
      const first = renderPage();
      await screen.findByTestId("placeOrder");
      first.unmount();
      mockedGetCurrent.mockResolvedValue(null);

      renderPage();

      expect(await screen.findByTestId("checkoutEmpty")).toBeInTheDocument();
      expect(mockedFind).not.toHaveBeenCalled();
      expect(mockedPlace).not.toHaveBeenCalled();
    });

    it("sends nothing after going back to the cart", async () => {
      mockedPlace.mockRejectedValueOnce(new TypeError("Failed to fetch"));
      mockedLeave.mockResolvedValueOnce(cart);
      const first = renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await screen.findByTestId("placeOrderMessage");
      await userEvent.click(screen.getByTestId("backToCart"));
      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith({ to: "/cart", replace: true }),
      );
      first.unmount();
      mockedPlace.mockClear();
      mockedGetCurrent.mockResolvedValue(null);

      renderPage();

      expect(await screen.findByTestId("checkoutEmpty")).toBeInTheDocument();
      expect(mockedFind).not.toHaveBeenCalled();
      expect(mockedPlace).not.toHaveBeenCalled();
    });

    it("doesn't look for an order when this page's own order empties the cart", async () => {
      await loseTheAnswerAndReload();
      mockedGetCurrent.mockResolvedValue(cart);
      mockedPlace.mockResolvedValueOnce(placed);
      const { queryClient } = renderPage();
      await userEvent.click(await screen.findByTestId("placeOrder"));
      await waitFor(() => expect(navigate).toHaveBeenCalled());

      mockedGetCurrent.mockResolvedValue(null);
      await queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });

      await screen.findByTestId("checkoutEmpty");
      expect(mockedPlace).toHaveBeenCalledTimes(1);
      expect(mockedFind).not.toHaveBeenCalled();
    });

    it("links an empty cart to the customer's orders", async () => {
      mockedGetCurrent.mockResolvedValue(null);

      renderPage();

      await screen.findByTestId("checkoutEmpty");
      expect(
        screen.getByRole("link", { name: /view your orders/i }),
      ).toHaveAttribute("href", "/orders");
    });
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

  it("sends a customer whose sign-in lapsed to sign-in, back to the cart (PIN-438)", async () => {
    // What orderApi.place does when the access cookie expired and the refresh failed too
    mockedPlace.mockImplementationOnce(async () => {
      useAuthStore.getState().clearUser();
      throw orderError(401, "SIGN_IN_REQUIRED");
    });
    renderPage();

    await userEvent.click(await screen.findByTestId("placeOrder"));

    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({
        to: "/signin",
        search: { redirect: "/cart" },
      }),
    );
  });
});
