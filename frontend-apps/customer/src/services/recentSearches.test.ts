import { describe, it, expect, beforeEach } from "vitest";
import {
  getRecentSearches,
  addRecentSearch,
  clearRecentSearches,
} from "./recentSearches";

const CUSTOMER_ID = "cust-123";

describe("recentSearches", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("returns empty array when no searches stored", () => {
    expect(getRecentSearches(CUSTOMER_ID)).toEqual([]);
  });

  it("stores and retrieves a search", () => {
    addRecentSearch(CUSTOMER_ID, "wireless keyboard");
    expect(getRecentSearches(CUSTOMER_ID)).toEqual(["wireless keyboard"]);
  });

  it("puts most recent search first", () => {
    addRecentSearch(CUSTOMER_ID, "keyboard");
    addRecentSearch(CUSTOMER_ID, "mouse");
    expect(getRecentSearches(CUSTOMER_ID)).toEqual(["mouse", "keyboard"]);
  });

  it("deduplicates case-insensitively", () => {
    addRecentSearch(CUSTOMER_ID, "Keyboard");
    addRecentSearch(CUSTOMER_ID, "keyboard");
    expect(getRecentSearches(CUSTOMER_ID)).toEqual(["keyboard"]);
  });

  it("caps at 5 entries", () => {
    for (let i = 1; i <= 7; i++) {
      addRecentSearch(CUSTOMER_ID, `query ${i}`);
    }
    const searches = getRecentSearches(CUSTOMER_ID);
    expect(searches).toHaveLength(5);
    expect(searches[0]).toBe("query 7");
    expect(searches[4]).toBe("query 3");
  });

  it("scopes storage by customer ID", () => {
    addRecentSearch("cust-a", "alpha");
    addRecentSearch("cust-b", "beta");
    expect(getRecentSearches("cust-a")).toEqual(["alpha"]);
    expect(getRecentSearches("cust-b")).toEqual(["beta"]);
  });

  it("clears searches for a specific customer", () => {
    addRecentSearch(CUSTOMER_ID, "query");
    clearRecentSearches(CUSTOMER_ID);
    expect(getRecentSearches(CUSTOMER_ID)).toEqual([]);
  });

  it("ignores blank queries", () => {
    addRecentSearch(CUSTOMER_ID, "   ");
    expect(getRecentSearches(CUSTOMER_ID)).toEqual([]);
  });
});
