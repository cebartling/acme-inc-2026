import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AutocompleteDropdown } from "./AutocompleteDropdown";
import type { AutocompleteSuggestion } from "@/services/api";

const mockProducts: AutocompleteSuggestion[] = [
  { type: "product", text: "Wireless Router", productId: "p1" },
  { type: "product", text: "Wireless Mouse", productId: "p2" },
];

const mockCategories: AutocompleteSuggestion[] = [
  { type: "category", text: "Wires & Cables", categorySlug: "wires-cables" },
];

const mockQueries: AutocompleteSuggestion[] = [
  { type: "query", text: "wireless charging" },
];

describe("AutocompleteDropdown", () => {
  it("renders nothing when no suggestions and not loading", () => {
    const { container } = render(
      <AutocompleteDropdown
        suggestions={[]}
        isLoading={false}
        onSelect={vi.fn()}
      />,
    );
    expect(container.firstChild).toBeNull();
  });

  it("renders product suggestions in a Products group", () => {
    render(
      <AutocompleteDropdown
        suggestions={mockProducts}
        isLoading={false}
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByText("Products")).toBeInTheDocument();
    expect(screen.getByText("Wireless Router")).toBeInTheDocument();
    expect(screen.getByText("Wireless Mouse")).toBeInTheDocument();
  });

  it("renders category suggestions in a Categories group", () => {
    render(
      <AutocompleteDropdown
        suggestions={mockCategories}
        isLoading={false}
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByText("Categories")).toBeInTheDocument();
    expect(screen.getByText("Wires & Cables")).toBeInTheDocument();
  });

  it("renders recent search suggestions in a Recent Searches group", () => {
    render(
      <AutocompleteDropdown
        suggestions={mockQueries}
        isLoading={false}
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByText("Recent Searches")).toBeInTheDocument();
    expect(screen.getByText("wireless charging")).toBeInTheDocument();
  });

  it("renders all suggestion types grouped correctly", () => {
    const all = [...mockProducts, ...mockCategories, ...mockQueries];
    render(
      <AutocompleteDropdown
        suggestions={all}
        isLoading={false}
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByText("Products")).toBeInTheDocument();
    expect(screen.getByText("Categories")).toBeInTheDocument();
    expect(screen.getByText("Recent Searches")).toBeInTheDocument();
  });

  it("renders loading indicator when loading with no suggestions", () => {
    render(
      <AutocompleteDropdown
        suggestions={[]}
        isLoading={true}
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByTestId("autocomplete-dropdown")).toBeInTheDocument();
  });
});
