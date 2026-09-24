import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { useLogout } from "./useLogout";
import { CART_QUERY_KEY } from "./useCart";
import { useCartNoticeStore } from "@/stores/cartNotice.store";

/**
 * useLogout renders under renderHook here: the "Cannot read properties of null (reading
 * 'useState')" failure noted in PasswordInput.test.tsx does not affect this hook.
 *
 * TODO: the remaining intended coverage —
 *   - logout(false) calls identityApi.logout, clears stores, emits analytics,
 *     and navigates to /signin?logout=true
 *   - logout(true) calls identityApi.logoutAll with the same client cleanup
 *   - failure paths still clear state and redirect
 *   - isLoading is true while the API call is in flight
 * End-to-end coverage of the logout flow lives in
 * acceptance-tests/features/api/customer-logout-api.feature and
 * acceptance-tests/features/customer/customer-logout-ui.feature.
 */
describe("useLogout", () => {
  it("is exported as a function", () => {
    expect(typeof useLogout).toBe("function");
  });
});

const navigate = vi.fn().mockResolvedValue(undefined);

vi.mock("@tanstack/react-router", () => ({ useNavigate: () => navigate }));

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  identityApi: {
    logout: vi.fn().mockResolvedValue(undefined),
    logoutAll: vi.fn(),
  },
}));

vi.mock("@/services/analytics", () => ({ trackEvent: vi.fn() }));

describe("useLogout and the cart", () => {
  // The cart side of sign-out (US-0004-08): the signed-in user's cart must not linger in
  // the header badge, and a pending merge notice must not carry over to the next visitor.
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient();
    useCartNoticeStore.setState({
      message: "Quantity for Gadget Pro was adjusted to the maximum of 10.",
    });
  });

  it("resets the cart query and dismisses the cart notice", async () => {
    const reset = vi.spyOn(queryClient, "resetQueries");
    const { result } = renderHook(() => useLogout(), {
      wrapper: ({ children }: { children: React.ReactNode }) =>
        React.createElement(
          QueryClientProvider,
          { client: queryClient },
          children,
        ),
    });

    await act(() => result.current.logout());

    expect(reset).toHaveBeenCalledWith({ queryKey: CART_QUERY_KEY });
    expect(useCartNoticeStore.getState().message).toBeNull();
    expect(navigate).toHaveBeenCalledWith({
      to: "/signin",
      search: { logout: true },
    });
  });
});
