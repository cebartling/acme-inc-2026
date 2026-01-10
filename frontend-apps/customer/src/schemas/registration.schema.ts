import { z } from "zod";

const passwordSchema = z
  .string()
  .min(8, "Password must be at least 8 characters")
  .regex(/[A-Z]/, "Password must contain at least one uppercase letter")
  .regex(/[a-z]/, "Password must contain at least one lowercase letter")
  .regex(/[0-9]/, "Password must contain at least one digit")
  .regex(
    /[!@#$%^&*()_+\-=[\]{}|;:,.<>?]/,
    "Password must contain at least one special character",
  );

export const registrationSchema = z
  .object({
    email: z.string().email("Please enter a valid email address"),
    password: passwordSchema,
    confirmPassword: z.string(),
    firstName: z
      .string()
      .min(1, "First name is required")
      .max(50, "First name cannot exceed 50 characters"),
    lastName: z
      .string()
      .min(1, "Last name is required")
      .max(50, "Last name cannot exceed 50 characters"),
    tosAccepted: z.boolean().refine((val) => val === true, {
      message: "You must accept the Terms of Service to continue",
    }),
    privacyPolicyAccepted: z.boolean().refine((val) => val === true, {
      message: "You must accept the Privacy Policy to continue",
    }),
    marketingOptIn: z.boolean().default(false),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export type RegistrationFormData = z.infer<typeof registrationSchema>;

export interface PasswordStrength {
  score: number;
  label: "Weak" | "Fair" | "Good" | "Strong";
  color: string;
  requirements: {
    minLength: boolean;
    hasUppercase: boolean;
    hasLowercase: boolean;
    hasDigit: boolean;
    hasSpecialChar: boolean;
  };
}

export function calculatePasswordStrength(password: string): PasswordStrength {
  const requirements = {
    minLength: password.length >= 8,
    hasUppercase: /[A-Z]/.test(password),
    hasLowercase: /[a-z]/.test(password),
    hasDigit: /[0-9]/.test(password),
    hasSpecialChar: /[!@#$%^&*()_+\-=[\]{}|;:,.<>?]/.test(password),
  };

  const metRequirements = Object.values(requirements).filter(Boolean).length;

  if (password.length < 8) {
    return {
      score: 0,
      label: "Weak",
      color: "bg-red-500",
      requirements,
    };
  }

  if (metRequirements <= 2) {
    return {
      score: 1,
      label: "Fair",
      color: "bg-orange-500",
      requirements,
    };
  }

  if (metRequirements <= 4) {
    return {
      score: 2,
      label: "Good",
      color: "bg-yellow-500",
      requirements,
    };
  }

  return {
    score: 3,
    label: "Strong",
    color: "bg-green-500",
    requirements,
  };
}
