import { describe, it, expect } from "vitest";
import { signinSchema } from "./signin.schema";

describe("signinSchema", () => {
  const validFormData = {
    email: "test@example.com",
    password: "password123",
    rememberMe: false,
  };

  describe("email validation", () => {
    it("accepts valid email addresses", () => {
      const result = signinSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects invalid email format", () => {
      const result = signinSchema.safeParse({
        ...validFormData,
        email: "invalid-email",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Please enter a valid email address",
        );
      }
    });

    it("rejects empty email", () => {
      const result = signinSchema.safeParse({
        ...validFormData,
        email: "",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe("Email is required");
      }
    });

    it("accepts email with subdomain", () => {
      const result = signinSchema.safeParse({
        ...validFormData,
        email: "user@mail.example.com",
      });
      expect(result.success).toBe(true);
    });

    it("accepts email with plus sign", () => {
      const result = signinSchema.safeParse({
        ...validFormData,
        email: "user+tag@example.com",
      });
      expect(result.success).toBe(true);
    });
  });

  describe("password validation", () => {
    it("accepts valid password", () => {
      const result = signinSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects empty password", () => {
      const result = signinSchema.safeParse({
        ...validFormData,
        password: "",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe("Password is required");
      }
    });

    it("accepts password with any complexity (no strength requirements for signin)", () => {
      const result = signinSchema.safeParse({
        ...validFormData,
        password: "simple",
      });
      expect(result.success).toBe(true);
    });
  });

  describe("rememberMe validation", () => {
    it("accepts rememberMe as true", () => {
      const result = signinSchema.safeParse({
        ...validFormData,
        rememberMe: true,
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.rememberMe).toBe(true);
      }
    });

    it("accepts rememberMe as false", () => {
      const result = signinSchema.safeParse({
        ...validFormData,
        rememberMe: false,
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.rememberMe).toBe(false);
      }
    });

    it("defaults rememberMe to false when not provided", () => {
      const result = signinSchema.safeParse({
        email: "test@example.com",
        password: "password123",
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.rememberMe).toBe(false);
      }
    });
  });

  describe("complete form validation", () => {
    it("validates complete valid form data", () => {
      const result = signinSchema.safeParse({
        email: "customer@acme.com",
        password: "MySecureP@ssw0rd!",
        rememberMe: true,
      });
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data).toEqual({
          email: "customer@acme.com",
          password: "MySecureP@ssw0rd!",
          rememberMe: true,
        });
      }
    });

    it("returns multiple errors for multiple invalid fields", () => {
      const result = signinSchema.safeParse({
        email: "",
        password: "",
        rememberMe: false,
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues.length).toBeGreaterThanOrEqual(2);
      }
    });
  });
});
