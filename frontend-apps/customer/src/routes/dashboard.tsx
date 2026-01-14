import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { ProfileCompletenessWidget } from "@/components/profile/ProfileCompletenessWidget";
import {
  useCustomerId,
  useIsAuthenticated,
  useIsAuthLoading,
} from "@/stores/auth.store";

export const Route = createFileRoute("/dashboard")({
  component: DashboardPage,
});

function DashboardPage() {
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
      <div className="max-w-4xl mx-auto">
        <h1 className="text-3xl font-bold text-white mb-8">Dashboard</h1>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Profile Completeness Widget */}
          <div data-testid="profile-completeness-widget">
            <ProfileCompletenessWidget customerId={customerId} />
          </div>

          {/* Placeholder for other dashboard widgets */}
          <div className="bg-gray-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold text-white mb-4">
              Recent Activity
            </h2>
            <p className="text-gray-400">No recent activity to display.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
