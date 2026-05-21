import { z } from "zod";

export const signinSchema = z.object({
  email: z
    .string()
    .min(1, "Email is required")
    .email("Please enter a valid email address"),
  password: z.string().min(1, "Password is required"),
  rememberMe: z.boolean().default(false),
});

export type SigninFormInput = z.input<typeof signinSchema>;
export type SigninFormData = z.output<typeof signinSchema>;
