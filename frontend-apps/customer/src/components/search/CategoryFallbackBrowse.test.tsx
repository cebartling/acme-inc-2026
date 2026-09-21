import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";
import { CategoryFallbackBrowse } from "./CategoryFallbackBrowse";
import { categoryApi } from "@/services/api";

vi.mock("@/services/api", () => ({
  categoryApi: {
    listCategories: vi.fn(),
    productsInCategory: vi.fn(),
  },
}));

// SearchResultCard renders a TanStack Router <Link>, which needs router context
// this component test has no reason to stand up.
vi.mock("./SearchResultCard", () => ({
  SearchResultCard: ({ product }: { product: { name: string } }) => (
    <div data-testid="searchResultCard">{product.name}</div>
  ),
}));

const mockedApi = vi.mocked(categoryApi);

function renderFallback() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    React.createElement(
      QueryClientProvider,
      { client: queryClient },
      React.createElement(CategoryFallbackBrowse),
    ),
  );
}

const categories = {
  categories: [
    { name: "Electronics", productCount: 3 },
    { name: "Apparel", productCount: 1 },
  ],
};

const electronicsProducts = {
  category: "Electronics",
  totalResults: 2,
  page: 1,
  pageSize: 24,
  totalPages: 1,
  results: [
    {
      id: "p1",
      slug: "widget-one",
      name: "Widget One",
      price: 9.99,
      category: "Electronics",
    },
    {
      id: "p2",
      slug: "widget-two",
      name: "Widget Two",
      price: 19.99,
      category: "Electronics",
    },
  ],
};

describe("CategoryFallbackBrowse", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists the available categories with their product counts", async () => {
    mockedApi.listCategories.mockResolvedValue(categories);

    renderFallback();

    expect(await screen.findByTestId("categoryList")).toBeInTheDocument();
    expect(screen.getByText("Electronics")).toBeInTheDocument();
    expect(screen.getByText("Apparel")).toBeInTheDocument();
    expect(screen.getAllByTestId("categoryListItem")).toHaveLength(2);
  });

  it("does not load products until a category is chosen", async () => {
    mockedApi.listCategories.mockResolvedValue(categories);

    renderFallback();
    await screen.findByTestId("categoryList");

    expect(mockedApi.productsInCategory).not.toHaveBeenCalled();
    expect(screen.queryByTestId("categoryProducts")).not.toBeInTheDocument();
  });

  it("loads and shows products when a category is clicked (AC-0004-09-07)", async () => {
    mockedApi.listCategories.mockResolvedValue(categories);
    mockedApi.productsInCategory.mockResolvedValue(electronicsProducts);
    const user = userEvent.setup();

    renderFallback();
    await screen.findByTestId("categoryList");

    await user.click(screen.getByText("Electronics"));

    await waitFor(() => {
      expect(screen.getByTestId("categoryProductsGrid")).toBeInTheDocument();
    });
    expect(mockedApi.productsInCategory).toHaveBeenCalledWith("Electronics");
    expect(screen.getAllByTestId("searchResultCard")).toHaveLength(2);
    expect(screen.getByText("Widget One")).toBeInTheDocument();
  });

  it("marks the chosen category as pressed", async () => {
    mockedApi.listCategories.mockResolvedValue(categories);
    mockedApi.productsInCategory.mockResolvedValue(electronicsProducts);
    const user = userEvent.setup();

    renderFallback();
    await screen.findByTestId("categoryList");

    const electronics = screen.getByText("Electronics").closest("button");
    expect(electronics).toHaveAttribute("aria-pressed", "false");

    await user.click(screen.getByText("Electronics"));

    expect(electronics).toHaveAttribute("aria-pressed", "true");
  });

  it("reports an empty category rather than rendering nothing", async () => {
    mockedApi.listCategories.mockResolvedValue(categories);
    mockedApi.productsInCategory.mockResolvedValue({
      ...electronicsProducts,
      totalResults: 0,
      results: [],
    });
    const user = userEvent.setup();

    renderFallback();
    await screen.findByTestId("categoryList");
    await user.click(screen.getByText("Electronics"));

    expect(
      await screen.findByTestId("categoryProductsEmpty"),
    ).toBeInTheDocument();
  });

  it("explains itself when the category list cannot be loaded", async () => {
    mockedApi.listCategories.mockRejectedValue(new Error("catalog down"));

    renderFallback();

    // The fallback failing must still not leave a blank region (AC-0004-09-06).
    expect(
      await screen.findByTestId("categoryFallbackError"),
    ).toBeInTheDocument();
  });

  it("explains itself when a category's products cannot be loaded", async () => {
    mockedApi.listCategories.mockResolvedValue(categories);
    mockedApi.productsInCategory.mockRejectedValue(new Error("catalog down"));
    const user = userEvent.setup();

    renderFallback();
    await screen.findByTestId("categoryList");
    await user.click(screen.getByText("Electronics"));

    expect(
      await screen.findByTestId("categoryProductsError"),
    ).toBeInTheDocument();
  });

  it("handles a catalog with no categories", async () => {
    mockedApi.listCategories.mockResolvedValue({ categories: [] });

    renderFallback();

    expect(
      await screen.findByTestId("categoryFallbackEmpty"),
    ).toBeInTheDocument();
  });
});
