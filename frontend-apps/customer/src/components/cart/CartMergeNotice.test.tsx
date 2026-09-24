import { describe, it, expect, beforeEach } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CartMergeNotice } from "./CartMergeNotice";
import { useCartNoticeStore } from "@/stores/cartNotice.store";

describe("CartMergeNotice", () => {
  beforeEach(() => {
    useCartNoticeStore.setState({ message: null });
  });

  it("renders nothing without a message", () => {
    render(<CartMergeNotice />);

    expect(screen.queryByTestId("cartMergeNotice")).not.toBeInTheDocument();
  });

  it("announces the message and hides on dismiss", async () => {
    render(<CartMergeNotice />);
    act(() =>
      useCartNoticeStore
        .getState()
        .show("Quantity for Gadget Pro was adjusted to the maximum of 10."),
    );

    expect(screen.getByRole("status")).toHaveTextContent(
      "Quantity for Gadget Pro was adjusted to the maximum of 10.",
    );

    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Dismiss cart notice" }));

    expect(screen.queryByTestId("cartMergeNotice")).not.toBeInTheDocument();
    expect(useCartNoticeStore.getState().message).toBeNull();
  });
});
