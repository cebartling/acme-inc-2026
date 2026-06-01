import { describe, it, expect, vi } from "vitest";
import { trackSearchExecuted, trackVariantSelected } from "./analytics";

describe("trackSearchExecuted", () => {
  it("calls console.debug with SearchExecuted event in dev mode", () => {
    const spy = vi.spyOn(console, "debug").mockImplementation(() => {});
    trackSearchExecuted({
      query: "widget",
      totalResults: 42,
      page: 1,
      executionTimeMs: 55,
    });
    expect(spy).toHaveBeenCalledWith("[analytics]", "SearchExecuted", {
      source: "WEB",
      query: "widget",
      totalResults: 42,
      page: 1,
      executionTimeMs: 55,
      sessionId: undefined,
    });
    spy.mockRestore();
  });
});

describe("trackVariantSelected", () => {
  it("calls console.debug with VariantSelected event in dev mode", () => {
    const spy = vi.spyOn(console, "debug").mockImplementation(() => {});
    trackVariantSelected({ productId: "prod-1", variantId: "var-2" });
    expect(spy).toHaveBeenCalledWith("[analytics]", "VariantSelected", {
      source: "WEB",
      productId: "prod-1",
      variantId: "var-2",
    });
    spy.mockRestore();
  });
});
