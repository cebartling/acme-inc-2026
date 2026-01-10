import { describe, it, expect } from "vitest";
import {
  registrationSchema,
  calculatePasswordStrength,
} from "./registration.schema";

describe("registrationSchema", () => {
  const validFormData = {
    email: "test@example.com",
    password: "Test@123!",
    confirmPassword: "Test@123!",
    firstName: "John",
    lastName: "Doe",
    tosAccepted: true as const,
    privacyPolicyAccepted: true as const,
    marketingOptIn: false,
  };

  describe("email validation", () => {
    it("accepts valid email addresses", () => {
      const result = registrationSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects invalid email format", () => {
      const result = registrationSchema.safeParse({
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
      const result = registrationSchema.safeParse({
        ...validFormData,
        email: "",
      });
      expect(result.success).toBe(false);
    });
  });

  describe("password validation", () => {
    it("accepts valid password meeting all requirements", () => {
      const result = registrationSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects password shorter than 8 characters", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        password: "Te@1",
        confirmPassword: "Te@1",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Password must be at least 8 characters",
        );
      }
    });

    it("rejects password without uppercase letter", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        password: "test@123!",
        confirmPassword: "test@123!",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Password must contain at least one uppercase letter",
        );
      }
    });

    it("rejects password without lowercase letter", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        password: "TEST@123!",
        confirmPassword: "TEST@123!",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Password must contain at least one lowercase letter",
        );
      }
    });

    it("rejects password without digit", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        password: "TestPass@!",
        confirmPassword: "TestPass@!",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Password must contain at least one digit",
        );
      }
    });

    it("rejects password without special character", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        password: "TestPass123",
        confirmPassword: "TestPass123",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Password must contain at least one special character",
        );
      }
    });
  });

  describe("confirmPassword validation", () => {
    it("accepts matching passwords", () => {
      const result = registrationSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects non-matching passwords", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        confirmPassword: "Different@123!",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        const confirmError = result.error.issues.find(
          (issue) => issue.path[0] === "confirmPassword",
        );
        expect(confirmError?.message).toBe("Passwords do not match");
      }
    });
  });

  describe("firstName validation", () => {
    it("accepts valid first name", () => {
      const result = registrationSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects empty first name", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        firstName: "",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe("First name is required");
      }
    });

    it("rejects first name exceeding 50 characters", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        firstName: "A".repeat(51),
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "First name cannot exceed 50 characters",
        );
      }
    });

    it("accepts first name with exactly 50 characters", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        firstName: "A".repeat(50),
      });
      expect(result.success).toBe(true);
    });
  });

  describe("lastName validation", () => {
    it("accepts valid last name", () => {
      const result = registrationSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects empty last name", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        lastName: "",
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe("Last name is required");
      }
    });

    it("rejects last name exceeding 50 characters", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        lastName: "B".repeat(51),
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.error.issues[0].message).toBe(
          "Last name cannot exceed 50 characters",
        );
      }
    });
  });

  describe("tosAccepted validation", () => {
    it("accepts when terms of service is accepted", () => {
      const result = registrationSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects when terms of service is not accepted", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        tosAccepted: false,
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        const tosError = result.error.issues.find(
          (issue) => issue.path[0] === "tosAccepted",
        );
        expect(tosError?.message).toBe(
          "You must accept the Terms of Service to continue",
        );
      }
    });
  });

  describe("privacyPolicyAccepted validation", () => {
    it("accepts when privacy policy is accepted", () => {
      const result = registrationSchema.safeParse(validFormData);
      expect(result.success).toBe(true);
    });

    it("rejects when privacy policy is not accepted", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        privacyPolicyAccepted: false,
      });
      expect(result.success).toBe(false);
      if (!result.success) {
        const privacyError = result.error.issues.find(
          (issue) => issue.path[0] === "privacyPolicyAccepted",
        );
        expect(privacyError?.message).toBe(
          "You must accept the Privacy Policy to continue",
        );
      }
    });
  });

  describe("marketingOptIn validation", () => {
    it("accepts when marketing opt-in is true", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        marketingOptIn: true,
      });
      expect(result.success).toBe(true);
    });

    it("accepts when marketing opt-in is false", () => {
      const result = registrationSchema.safeParse({
        ...validFormData,
        marketingOptIn: false,
      });
      expect(result.success).toBe(true);
    });
  });
});

describe("calculatePasswordStrength", () => {
  describe("Weak password", () => {
    it("returns Weak for passwords shorter than 8 characters", () => {
      const result = calculatePasswordStrength("Te@1");
      expect(result.label).toBe("Weak");
      expect(result.score).toBe(0);
      expect(result.color).toBe("bg-red-500");
    });

    it("returns Weak for empty password", () => {
      const result = calculatePasswordStrength("");
      expect(result.label).toBe("Weak");
      expect(result.score).toBe(0);
    });
  });

  describe("Fair password", () => {
    it("returns Fair for password with 8+ chars but missing 2+ requirements", () => {
      const result = calculatePasswordStrength("testtest");
      expect(result.label).toBe("Fair");
      expect(result.score).toBe(1);
      expect(result.color).toBe("bg-orange-500");
    });
  });

  describe("Good password", () => {
    it("returns Good for password missing 1 requirement", () => {
      const result = calculatePasswordStrength("Testtest1");
      expect(result.label).toBe("Good");
      expect(result.score).toBe(2);
      expect(result.color).toBe("bg-yellow-500");
    });
  });

  describe("Strong password", () => {
    it("returns Strong for password meeting all requirements", () => {
      const result = calculatePasswordStrength("Test@123!");
      expect(result.label).toBe("Strong");
      expect(result.score).toBe(3);
      expect(result.color).toBe("bg-green-500");
    });
  });

  describe("requirements tracking", () => {
    it("correctly identifies missing requirements", () => {
      const result = calculatePasswordStrength("test");
      expect(result.requirements.minLength).toBe(false);
      expect(result.requirements.hasUppercase).toBe(false);
      expect(result.requirements.hasLowercase).toBe(true);
      expect(result.requirements.hasDigit).toBe(false);
      expect(result.requirements.hasSpecialChar).toBe(false);
    });

    it("correctly identifies all requirements met", () => {
      const result = calculatePasswordStrength("Test@123!");
      expect(result.requirements.minLength).toBe(true);
      expect(result.requirements.hasUppercase).toBe(true);
      expect(result.requirements.hasLowercase).toBe(true);
      expect(result.requirements.hasDigit).toBe(true);
      expect(result.requirements.hasSpecialChar).toBe(true);
    });

    it("recognizes various special characters", () => {
      const specialChars = [
        "!",
        "@",
        "#",
        "$",
        "%",
        "^",
        "&",
        "*",
        "(",
        ")",
        "_",
        "+",
        "-",
        "=",
        "[",
        "]",
        "{",
        "}",
        "|",
        ";",
        ":",
        ",",
        ".",
        "<",
        ">",
        "?",
      ];

      specialChars.forEach((char) => {
        const result = calculatePasswordStrength(`Test123${char}`);
        expect(result.requirements.hasSpecialChar).toBe(true);
      });
    });
  });
});
