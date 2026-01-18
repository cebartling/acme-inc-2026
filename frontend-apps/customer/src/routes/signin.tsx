import { useState } from "react";
import {
  createFileRoute,
  useNavigate,
  useSearch,
} from "@tanstack/react-router";
import { z } from "zod";
import { SigninForm } from "@/components/signin";
import { useAuthStore } from "@/stores/auth.store";
import type { SigninFormData } from "@/schemas/signin.schema";

const signinSearchSchema = z.object({
  redirect: z.string().optional(),
  logout: z.string().optional(),
});

export const Route = createFileRoute("/signin")({
  component: SigninPage,
  validateSearch: signinSearchSchema,
});

function SigninPage() {
  const navigate = useNavigate();
  const search = useSearch({ from: "/signin" });
  const setUser = useAuthStore((state) => state.setUser);
  const [error, setError] = useState<string | undefined>(undefined);

  // Show logout message if redirected after logout
  const logoutMessage =
    search.logout === "true" ? "You have been signed out." : undefined;

  const handleSubmit = async (data: SigninFormData) => {
    setError(undefined);

    try {
      // TODO: Integrate with Identity Service API
      // POST /api/v1/auth/signin
      console.log("Signin data:", data);

      // Simulate API call
      await new Promise((resolve) => setTimeout(resolve, 1000));

      // TODO: Replace with actual API response
      // Simulate invalid credentials for testing error handling
      if (
        data.email === "invalid@example.com" ||
        data.password === "wrongpassword"
      ) {
        throw new Error("Invalid credentials");
      }

      // For now, simulate successful signin
      setUser({
        userId: "user_123",
        customerId: "cust_123",
        email: data.email,
        firstName: "Demo",
        lastName: "User",
      });

      // Navigate to redirect URL or dashboard
      const redirectTo = search.redirect || "/dashboard";
      navigate({ to: redirectTo });
    } catch (err) {
      // Handle API errors
      setError("Invalid email or password. Please try again.");
      console.error("Signin error:", err);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">Welcome Back</h1>
          <p className="text-gray-400">Sign in to your ACME account</p>
        </div>

        {logoutMessage && (
          <div
            className="mb-6 p-3 text-sm text-green-600 bg-green-50 dark:bg-green-950/50 border border-green-200 dark:border-green-800 rounded-md"
            role="status"
            aria-live="polite"
          >
            {logoutMessage}
          </div>
        )}

        <SigninForm onSubmit={handleSubmit} error={error} />
      </div>
    </div>
  );
}
