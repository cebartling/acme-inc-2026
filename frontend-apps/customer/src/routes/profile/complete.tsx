import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { ProfileWizard } from "@/components/profile";
import {
  useCustomerId,
  useIsAuthenticated,
  useIsAuthLoading,
} from "@/stores/auth.store";

export const Route = createFileRoute("/profile/complete")({
  component: ProfileCompletePage,
});

function ProfileCompletePage() {
  const navigate = useNavigate();
  const customerId = useCustomerId();
  const isAuthenticated = useIsAuthenticated();
  const isLoading = useIsAuthLoading();

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      navigate({ to: "/login" });
    }
  }, [isLoading, isAuthenticated, navigate]);

  const handleComplete = () => {
    // Navigate to dashboard or home page
    navigate({ to: "/" });
  };

  const handleSkip = () => {
    // Navigate to dashboard or home page
    navigate({ to: "/" });
  };

  // Show loading state while checking auth
  if (isLoading) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-white mx-auto" />
          <p className="mt-2 text-sm text-slate-400">Loading...</p>
        </div>
      </div>
    );
  }

  // Don't render if not authenticated (will redirect)
  if (!isAuthenticated || !customerId) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-2xl mx-auto">
        <ProfileWizard
          customerId={customerId}
          onComplete={handleComplete}
          onSkip={handleSkip}
        />
      </div>
    </div>
  );
}
