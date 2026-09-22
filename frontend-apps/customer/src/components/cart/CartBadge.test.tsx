import { describe, it, expect, beforeEach } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { CartBadge } from "./CartBadge";
import { useCartStore } from "@/stores/cart.store";

describe("CartBadge", () => {
  beforeEach(() => {
    useCartStore.setState({ itemCount: 0 });
  });

  it("hides the count when the cart is empty", () => {
    render(<CartBadge />);

    expect(screen.getByTestId("cartBadge")).toHaveAccessibleName(
      "Cart: 0 items",
    );
    expect(screen.queryByTestId("cartBadgeCount")).not.toBeInTheDocument();
  });

  it("shows the count and updates without a reload", () => {
    render(<CartBadge />);

    act(() => useCartStore.getState().setItemCount(3));

    expect(screen.getByTestId("cartBadgeCount")).toHaveTextContent("3");
    expect(screen.getByTestId("cartBadge")).toHaveAccessibleName(
      "Cart: 3 items",
    );
  });
});
