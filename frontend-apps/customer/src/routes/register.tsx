import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { RegistrationForm } from "@/components/registration";
import type { RegistrationFormData } from "@/schemas/registration.schema";

export const Route = createFileRoute("/register")({
  component: RegisterPage,
});

function RegisterPage() {
  const navigate = useNavigate();

  const handleSubmit = async (data: RegistrationFormData) => {
    // TODO: Integrate with Identity Service API
    // POST /api/v1/users/register
    console.log("Registration data:", data);

    // Simulate API call
    await new Promise((resolve) => setTimeout(resolve, 1000));

    // Navigate to success page or login
    navigate({ to: "/" });
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-md mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">
            Welcome to ACME
          </h1>
          <p className="text-gray-400">Create your account to start shopping</p>
        </div>

        <RegistrationForm onSubmit={handleSubmit} />

        <p className="text-center text-gray-400 mt-6">
          Already have an account?{" "}
          <a
            href="/signin"
            className="text-cyan-400 hover:text-cyan-300 font-medium"
          >
            Sign in
          </a>
        </p>
      </div>
    </div>
  );
}
