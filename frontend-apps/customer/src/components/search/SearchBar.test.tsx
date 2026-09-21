import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SearchBar } from "./SearchBar";
import { SEARCH_QUERY_MAX_LENGTH } from "@/schemas/search.schema";

// The autocomplete hook fetches and reads auth state; neither is relevant to the
// query-length contract under test here.
vi.mock("@/hooks/useAutocomplete", () => ({
  useAutocomplete: () => ({
    suggestions: [],
    isLoading: false,
    isOpen: false,
    setIsOpen: vi.fn(),
  }),
}));

vi.mock("@/stores/auth.store", () => ({
  useIsAuthenticated: () => false,
  useCustomerId: () => null,
}));

describe("SearchBar", () => {
  it("is defined and is a function", () => {
    expect(SearchBar).toBeDefined();
    expect(typeof SearchBar).toBe("function");
  });

  describe("query length", () => {
    it("caps the input at the length the search service accepts", () => {
      render(<SearchBar onSearch={vi.fn()} />);

      expect(screen.getByTestId("searchInput")).toHaveAttribute(
        "maxlength",
        String(SEARCH_QUERY_MAX_LENGTH),
      );
    });

    it("cannot submit more than the maximum, even by pasting", async () => {
      const onSearch = vi.fn();
      const user = userEvent.setup();
      render(<SearchBar onSearch={onSearch} />);

      const input = screen.getByTestId("searchInput");
      await user.click(input);
      await user.paste("x".repeat(250));
      await user.click(screen.getByTestId("searchSubmitButton"));

      // Over-length queries used to reach the router, where validateSearch threw and
      // rendered a full-page error instead of a search result (AC-0004-09-06).
      expect(onSearch).toHaveBeenCalledTimes(1);
      expect(onSearch.mock.calls[0][0]).toHaveLength(SEARCH_QUERY_MAX_LENGTH);
    });
  });
});
