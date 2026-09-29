import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProductAvailabilityBadge } from "./ProductAvailabilityBadge";

describe("ProductAvailabilityBadge", () => {
  // US-0004-10 AC-01: a text label, not color alone
  it("says Out of Stock for an out-of-stock variant", () => {
    render(<ProductAvailabilityBadge availability="OUT_OF_STOCK" />);

    expect(screen.getByTestId("availabilityBadge")).toHaveTextContent(
      "Out of Stock",
    );
  });

  it("says In Stock for an in-stock variant", () => {
    render(<ProductAvailabilityBadge availability="IN_STOCK" />);

    expect(screen.getByTestId("availabilityBadge")).toHaveTextContent(
      "In Stock",
    );
  });
});
