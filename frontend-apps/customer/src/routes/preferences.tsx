import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import { PreferencesPage } from "@/components/preferences/PreferencesPage";
import {
  useCustomerId,
  useIsAuthenticated,
  useIsAuthLoading,
} from "@/stores/auth.store";

export const Route = createFileRoute("/preferences")({
  component: PreferencesPageRoute,
});

function PreferencesPageRoute() {
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
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto" />
          <p className="mt-2 text-sm text-gray-600">Loading...</p>
        </div>
      </div>
    );
  }

  // Don't render if not authenticated (will redirect)
  if (!isAuthenticated || !customerId) {
    return null;
  }

  return <PreferencesPage customerId={customerId} />;
}
