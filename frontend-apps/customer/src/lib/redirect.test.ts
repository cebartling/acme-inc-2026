import { describe, expect, it } from "vitest";
import { isSafeRedirect } from "./redirect";

describe("isSafeRedirect", () => {
  it.each([
    "/",
    "/orders",
    "/orders?page=3",
    "/orders/abc-123",
    // PIN-428: an ID holding "://" is still a path on this site
    "/orders/a%3A%2F%2Fb",
    "/orders/a://b",
    "/search?next=https://example.com",
  ])("accepts %s", (url) => {
    expect(isSafeRedirect(url)).toBe(true);
  });

  it.each([
    "",
    "orders",
    "https://evil.example",
    "javascript:alert(1)",
    "//evil.example",
    "/\\evil.example",
    "/\t/evil.example",
    "/%2Fevil.example",
    "/%5Cevil.example",
    // Won't decode
    "/orders/100%",
  ])("rejects %j", (url) => {
    expect(isSafeRedirect(url)).toBe(false);
  });
});
