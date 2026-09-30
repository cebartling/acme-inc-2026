import { describe, it, expect, vi } from "vitest";
import type React from "react";
import { render, screen } from "@testing-library/react";
import { SearchResultCard } from "./SearchResultCard";
import type { ProductSummary } from "@/services/api";

vi.mock("@tanstack/react-router", () => ({
  Link: ({
    children,
    ...props
  }: { children: React.ReactNode } & Record<string, unknown>) => (
    <a {...props}>{children}</a>
  ),
}));

const product: ProductSummary = {
  id: "product-1",
  slug: "gadget-pro",
  name: "Gadget Pro",
  price: 119.99,
  category: "Electronics",
  inStock: true,
  imageUrl: null,
};

describe("SearchResultCard", () => {
  it("shows the name, price and category", () => {
    render(<SearchResultCard product={product} />);

    expect(screen.getByText("Gadget Pro")).toBeInTheDocument();
    expect(screen.getByText("$119.99")).toBeInTheDocument();
    expect(screen.getByText("Electronics")).toBeInTheDocument();
  });

  // US-0004-10 AC-06: badged, not removed, and in text rather than color alone
  it("shows an Out of Stock badge for a product that is out of stock", () => {
    render(<SearchResultCard product={{ ...product, inStock: false }} />);

    expect(screen.getByTestId("outOfStockBadge")).toHaveTextContent(
      "Out of Stock",
    );
    expect(screen.getByTestId("searchResultCard")).toBeInTheDocument();
  });

  it("shows no badge for a product in stock", () => {
    render(<SearchResultCard product={product} />);

    expect(screen.queryByTestId("outOfStockBadge")).not.toBeInTheDocument();
  });

  // US-0004-10 AC-04: suggestions show an image
  it("shows the product image when there is one", () => {
    render(
      <SearchResultCard
        product={{ ...product, imageUrl: "https://img/gadget-pro" }}
      />,
    );

    expect(screen.getByTestId("searchResultImage")).toHaveAttribute(
      "src",
      "https://img/gadget-pro",
    );
  });
});
