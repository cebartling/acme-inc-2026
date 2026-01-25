import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { ProfileCompletenessWidget } from "@/components/profile/ProfileCompletenessWidget";
import {
  useCustomerId,
  useIsAuthenticated,
  useIsAuthLoading,
} from "@/stores/auth.store";
import {
  useCustomerProfile,
  useCustomerLoading,
  useCustomerError,
  useCustomerStore,
} from "@/stores/customer.store";

export const Route = createFileRoute("/dashboard")({
  component: DashboardPage,
});

function DashboardPage() {
  const navigate = useNavigate();
  const customerId = useCustomerId();
  const isAuthenticated = useIsAuthenticated();
  const isAuthLoading = useIsAuthLoading();

  // Customer profile state
  const profile = useCustomerProfile();
  const isProfileLoading = useCustomerLoading();
  const profileError = useCustomerError();
  const { fetchProfile } = useCustomerStore();

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      navigate({ to: "/signin" });
    }
  }, [isAuthLoading, isAuthenticated, navigate]);

  // Fetch profile if authenticated but profile not loaded
  useEffect(() => {
    if (isAuthenticated && !profile && !isProfileLoading && !profileError) {
      fetchProfile();
    }
  }, [isAuthenticated, profile, isProfileLoading, profileError, fetchProfile]);

  // Show loading state while checking auth or loading profile
  if (isAuthLoading || (isProfileLoading && !profile)) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-white mx-auto" />
          <p className="mt-2 text-sm text-slate-400">Loading...</p>
        </div>
      </div>
    );
  }

  // Show error state with retry option
  if (profileError) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 flex items-center justify-center">
        <div className="max-w-md mx-auto text-center">
          <div className="bg-red-900/20 border border-red-500 rounded-lg p-6">
            <h2 className="text-xl font-semibold text-red-400 mb-2">Error Loading Profile</h2>
            <p className="text-gray-300 mb-4">{profileError}</p>
            <button
              onClick={() => fetchProfile()}
              className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Don't render if not authenticated or no profile (will redirect or show error)
  if (!isAuthenticated || !customerId || !profile) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-12 px-4">
      <div className="max-w-4xl mx-auto">
        {/* Welcome header with customer info */}
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-white mb-2">
            Welcome, {profile.name.displayName}!
          </h1>
          <p className="text-gray-400">Customer #{profile.customerNumber}</p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Profile Completeness Widget */}
          <div data-testid="profile-completeness-widget">
            <ProfileCompletenessWidget customerId={profile.customerId} />
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
