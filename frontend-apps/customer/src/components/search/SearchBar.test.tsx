import { describe, it, expect } from "vitest";
import { SearchBar } from "./SearchBar";

describe("SearchBar", () => {
  it("is defined and is a function", () => {
    expect(SearchBar).toBeDefined();
    expect(typeof SearchBar).toBe("function");
  });
});
