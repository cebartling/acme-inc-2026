import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CheckoutButton } from "./CheckoutButton";
import { ApiError, cartApi } from "@/services/api";
import { useAuthStore } from "@/stores/auth.store";

const navigate = vi.fn();
vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => navigate,
}));

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  cartApi: { startCheckout: vi.fn() },
}));

const mockedStart = vi.mocked(cartApi.startCheckout);

const session = {
  checkoutSessionId: "session-1",
  cartId: "cart-1",
  status: "INITIATED",
  expiresAt: "2026-10-29T12:30:00Z",
  cart: { itemCount: 1, subtotal: 69.99, currency: "USD" },
};

function signIn() {
  useAuthStore.getState().setUser({
    userId: "user-1",
    customerId: "customer-1",
    email: "jane@example.com",
    firstName: "Jane",
    lastName: "Doe",
  });
}

function renderButton() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CheckoutButton cartId="cart-1" />
    </QueryClientProvider>,
  );
}

describe("CheckoutButton (PIN-426)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.getState().clearUser();
  });

  it("sends a guest to sign-in with a redirect back to the cart, without starting checkout", async () => {
    renderButton();

    await userEvent.click(screen.getByTestId("checkout"));

    expect(navigate).toHaveBeenCalledWith({
      to: "/signin",
      search: { redirect: "/cart" },
    });
    expect(mockedStart).not.toHaveBeenCalled();
  });

  it("starts checkout for a signed-in customer and goes to the review page", async () => {
    signIn();
    mockedStart.mockResolvedValueOnce(session);
    renderButton();

    await userEvent.click(screen.getByTestId("checkout"));

    expect(mockedStart).toHaveBeenCalledWith("cart-1");
    expect(navigate).toHaveBeenCalledWith({ to: "/checkout" });
  });

  it("stays on the cart and says why when the cart refuses checkout", async () => {
    signIn();
    mockedStart.mockRejectedValueOnce(
      new ApiError("Some items in your cart can't be ordered", 422, {
        error: "Some items in your cart can't be ordered",
        code: "CART_VALIDATION_FAILED",
      }),
    );
    renderButton();

    await userEvent.click(screen.getByTestId("checkout"));

    expect(await screen.findByTestId("checkoutMessage")).toHaveTextContent(
      "Some items in your cart can't be ordered",
    );
    expect(navigate).not.toHaveBeenCalled();
  });
});
