import { describe, it, expect, beforeEach } from "vitest";
import { useCartStore } from "./cart.store";

describe("cart store", () => {
  beforeEach(() => {
    useCartStore.setState({ itemCount: 0 });
  });

  it("starts empty", () => {
    expect(useCartStore.getState().itemCount).toBe(0);
  });

  it("replaces the count with the server's total", () => {
    useCartStore.getState().setItemCount(3);
    useCartStore.getState().setItemCount(5);

    expect(useCartStore.getState().itemCount).toBe(5);
  });
});
