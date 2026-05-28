import { describe, it, expect } from "vitest";
import { searchParamsSchema } from "./search.schema";

describe("searchParamsSchema", () => {
  describe("q (query string)", () => {
    it("accepts a valid query string", () => {
      const result = searchParamsSchema.safeParse({ q: "widget" });
      expect(result.success).toBe(true);
    });

    it("defaults q to empty string when omitted", () => {
      const result = searchParamsSchema.safeParse({});
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.q).toBe("");
      }
    });

    it("rejects q longer than 200 characters", () => {
      const result = searchParamsSchema.safeParse({ q: "x".repeat(201) });
      expect(result.success).toBe(false);
    });

    it("accepts q exactly 200 characters", () => {
      const result = searchParamsSchema.safeParse({ q: "x".repeat(200) });
      expect(result.success).toBe(true);
    });
  });

  describe("page", () => {
    it("defaults page to 1 when omitted", () => {
      const result = searchParamsSchema.safeParse({ q: "widget" });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.page).toBe(1);
      }
    });

    it("accepts page as an integer", () => {
      const result = searchParamsSchema.safeParse({ q: "widget", page: 3 });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.page).toBe(3);
      }
    });

    it("coerces page from string to number", () => {
      const result = searchParamsSchema.safeParse({ q: "widget", page: "2" });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.page).toBe(2);
      }
    });

    it("rejects page less than 1", () => {
      const result = searchParamsSchema.safeParse({ q: "widget", page: 0 });
      expect(result.success).toBe(false);
    });
  });

  describe("sort", () => {
    it("defaults sort to relevance when omitted", () => {
      const result = searchParamsSchema.safeParse({ q: "widget" });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.sort).toBe("relevance");
      }
    });

    it("accepts valid sort values", () => {
      for (const sort of ["relevance", "price_asc", "price_desc", "newest"]) {
        const result = searchParamsSchema.safeParse({ q: "widget", sort });
        expect(result.success).toBe(true);
      }
    });

    it("rejects invalid sort value", () => {
      const result = searchParamsSchema.safeParse({
        q: "widget",
        sort: "invalid_sort",
      });
      expect(result.success).toBe(false);
    });
  });

  describe("complete valid input", () => {
    it("parses a full valid object", () => {
      const result = searchParamsSchema.safeParse({
        q: "widget",
        page: 1,
        sort: "relevance",
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toEqual({
          q: "widget",
          page: 1,
          sort: "relevance",
          category: [],
        });
      }
    });

    it("parses category filter as an array", () => {
      const result = searchParamsSchema.safeParse({
        q: "widget",
        page: 1,
        sort: "relevance",
        category: ["Gaming", "Electronics"],
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.category).toEqual(["Gaming", "Electronics"]);
      }
    });

    it("defaults category to empty array when omitted", () => {
      const result = searchParamsSchema.safeParse({ q: "widget" });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.category).toEqual([]);
      }
    });

    it("parses priceMin and priceMax as numbers", () => {
      const result = searchParamsSchema.safeParse({
        q: "widget",
        priceMin: "25",
        priceMax: "75",
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.priceMin).toBe(25);
        expect(result.data.priceMax).toBe(75);
      }
    });

    it("leaves priceMin and priceMax undefined when omitted", () => {
      const result = searchParamsSchema.safeParse({ q: "widget" });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.priceMin).toBeUndefined();
        expect(result.data.priceMax).toBeUndefined();
      }
    });
  });
});
