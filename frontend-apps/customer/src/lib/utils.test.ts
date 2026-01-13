import { describe, it, expect } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  describe("basic functionality", () => {
    it("returns empty string for no arguments", () => {
      expect(cn()).toBe("");
    });

    it("returns single class name", () => {
      expect(cn("foo")).toBe("foo");
    });

    it("merges multiple class names", () => {
      expect(cn("foo", "bar")).toBe("foo bar");
    });

    it("handles multiple string arguments", () => {
      expect(cn("foo", "bar", "baz")).toBe("foo bar baz");
    });
  });

  describe("conditional classes", () => {
    it("filters out falsy values", () => {
      expect(cn("foo", false, "bar")).toBe("foo bar");
    });

    it("filters out null values", () => {
      expect(cn("foo", null, "bar")).toBe("foo bar");
    });

    it("filters out undefined values", () => {
      expect(cn("foo", undefined, "bar")).toBe("foo bar");
    });

    it("filters out empty strings", () => {
      expect(cn("foo", "", "bar")).toBe("foo bar");
    });

    it("handles conditional expressions", () => {
      const isActive = true;
      const isDisabled = false;
      expect(cn("base", isActive && "active", isDisabled && "disabled")).toBe(
        "base active"
      );
    });
  });

  describe("object syntax", () => {
    it("includes classes with truthy values", () => {
      expect(cn({ foo: true, bar: false })).toBe("foo");
    });

    it("handles multiple truthy values", () => {
      expect(cn({ foo: true, bar: true, baz: false })).toBe("foo bar");
    });

    it("combines object syntax with strings", () => {
      expect(cn("base", { active: true, disabled: false })).toBe("base active");
    });
  });

  describe("array syntax", () => {
    it("handles array of class names", () => {
      expect(cn(["foo", "bar"])).toBe("foo bar");
    });

    it("handles nested arrays", () => {
      expect(cn(["foo", ["bar", "baz"]])).toBe("foo bar baz");
    });

    it("combines arrays with strings", () => {
      expect(cn("base", ["foo", "bar"])).toBe("base foo bar");
    });
  });

  describe("tailwind merge functionality", () => {
    it("merges conflicting tailwind classes (padding)", () => {
      expect(cn("p-4", "p-2")).toBe("p-2");
    });

    it("merges conflicting tailwind classes (margin)", () => {
      expect(cn("m-2", "m-4")).toBe("m-4");
    });

    it("merges conflicting tailwind classes (text color)", () => {
      expect(cn("text-red-500", "text-blue-500")).toBe("text-blue-500");
    });

    it("merges conflicting tailwind classes (background color)", () => {
      expect(cn("bg-white", "bg-black")).toBe("bg-black");
    });

    it("preserves non-conflicting tailwind classes", () => {
      expect(cn("p-4", "m-2")).toBe("p-4 m-2");
    });

    it("merges conflicting directional padding", () => {
      expect(cn("px-4", "px-2")).toBe("px-2");
    });

    it("preserves different directional padding", () => {
      expect(cn("px-4", "py-2")).toBe("px-4 py-2");
    });

    it("merges conflicting flex properties", () => {
      expect(cn("flex-row", "flex-col")).toBe("flex-col");
    });

    it("merges conflicting width classes", () => {
      expect(cn("w-full", "w-1/2")).toBe("w-1/2");
    });

    it("merges conflicting height classes", () => {
      expect(cn("h-10", "h-20")).toBe("h-20");
    });

    it("merges conflicting border radius", () => {
      expect(cn("rounded", "rounded-lg")).toBe("rounded-lg");
    });

    it("merges conflicting font weight", () => {
      expect(cn("font-normal", "font-bold")).toBe("font-bold");
    });
  });

  describe("complex combinations", () => {
    it("handles complex real-world scenario", () => {
      const isError = true;
      const isDisabled = false;
      const result = cn(
        "base-class",
        "px-4 py-2",
        isError && "border-red-500 text-red-500",
        isDisabled && "opacity-50 cursor-not-allowed",
        { "hover:bg-gray-100": !isDisabled }
      );
      expect(result).toBe(
        "base-class px-4 py-2 border-red-500 text-red-500 hover:bg-gray-100"
      );
    });

    it("handles override scenario", () => {
      // Base styles with override
      const result = cn(
        "p-4 text-gray-500 bg-white",
        "text-red-500" // Override text color
      );
      expect(result).toBe("p-4 bg-white text-red-500");
    });

    it("handles component variant pattern", () => {
      const variant = "primary";
      const size = "lg";
      const result = cn(
        "btn",
        {
          "btn-primary": variant === "primary",
          "btn-secondary": variant === "secondary",
        },
        {
          "btn-sm": size === "sm",
          "btn-lg": size === "lg",
        }
      );
      expect(result).toBe("btn btn-primary btn-lg");
    });
  });
});
