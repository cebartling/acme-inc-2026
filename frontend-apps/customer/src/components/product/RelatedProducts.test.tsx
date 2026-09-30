import { describe, it, expect, vi } from "vitest";
import type React from "react";
import { render, screen } from "@testing-library/react";
import { RelatedProducts } from "./RelatedProducts";
import type { ProductSummary } from "@/services/api";

vi.mock("@tanstack/react-router", () => ({
  Link: ({
    children,
    ...props
  }: { children: React.ReactNode } & Record<string, unknown>) => (
    <a {...props}>{children}</a>
  ),
}));

const products: ProductSummary[] = [
  {
    id: "product-2",
    slug: "gadget-lite",
    name: "Gadget Lite",
    price: 79.99,
    category: "Electronics",
    inStock: true,
    imageUrl: null,
  },
];

describe("RelatedProducts", () => {
  it("is headed Related Products normally", () => {
    render(<RelatedProducts products={products} />);

    expect(
      screen.getByRole("heading", { name: "Related Products" }),
    ).toBeInTheDocument();
  });

  // US-0004-10 AC-04: the service only returns in-stock products, so they double as alternatives
  it("is headed as alternatives when the viewed variant is out of stock", () => {
    render(<RelatedProducts products={products} alternatives />);

    expect(
      screen.getByRole("heading", { name: "Alternatives you might like" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Gadget Lite")).toBeInTheDocument();
  });

  it("renders nothing without products", () => {
    const { container } = render(
      <RelatedProducts products={[]} alternatives />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
