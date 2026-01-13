import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { PreferencesPage } from "@/components/preferences/PreferencesPage";
import {
  useCustomerId,
  useUserId,
  useIsAuthenticated,
  useIsAuthLoading,
} from "@/stores/auth.store";
import { customerApi, type PreferencesResponse } from "@/services/api";
import type { FullPreferencesData } from "@/schemas/profile.schema";

export const Route = createFileRoute("/preferences")({
  component: PreferencesPageRoute,
});

/**
 * Transforms API response to form data format.
 */
function transformPreferencesResponse(
  response: PreferencesResponse
): FullPreferencesData {
  return {
    communication: {
      email: response.preferences.communication.email,
      sms: response.preferences.communication.sms,
      push: response.preferences.communication.push,
      marketing: response.preferences.communication.marketing,
      frequency: response.preferences.communication
        .frequency as FullPreferencesData["communication"]["frequency"],
    },
    privacy: {
      shareDataWithPartners: response.preferences.privacy.shareDataWithPartners,
      allowAnalytics: response.preferences.privacy.allowAnalytics,
      allowPersonalization: response.preferences.privacy.allowPersonalization,
    },
    display: {
      language: response.preferences.display.language,
      currency: response.preferences.display.currency,
      timezone: response.preferences.display.timezone,
    },
  };
}

function PreferencesPageRoute() {
  const navigate = useNavigate();
  const customerId = useCustomerId();
  const userId = useUserId();
  const isAuthenticated = useIsAuthenticated();
  const isAuthLoading = useIsAuthLoading();

  const [preferences, setPreferences] = useState<FullPreferencesData | null>(
    null
  );
  const [isLoadingPreferences, setIsLoadingPreferences] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      navigate({ to: "/login" });
    }
  }, [isAuthLoading, isAuthenticated, navigate]);

  // Fetch preferences when authenticated
  useEffect(() => {
    async function fetchPreferences() {
      if (!customerId || !userId) return;

      setIsLoadingPreferences(true);
      setLoadError(null);

      try {
        const response = await customerApi.getPreferences(customerId, userId);
        setPreferences(transformPreferencesResponse(response));
      } catch (error) {
        console.error("Failed to load preferences:", error);
        setLoadError(
          error instanceof Error
            ? error.message
            : "Failed to load preferences. Using default values."
        );
        // Don't block rendering - component will use defaults
      } finally {
        setIsLoadingPreferences(false);
      }
    }

    if (isAuthenticated && customerId && userId) {
      fetchPreferences();
    }
  }, [isAuthenticated, customerId, userId]);

  // Show loading state while checking auth or loading preferences
  if (isAuthLoading || (isAuthenticated && isLoadingPreferences)) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto" />
          <p className="mt-2 text-sm text-gray-600">
            {isAuthLoading ? "Checking authentication..." : "Loading preferences..."}
          </p>
        </div>
      </div>
    );
  }

  // Don't render if not authenticated (will redirect)
  if (!isAuthenticated || !customerId) {
    return null;
  }

  return (
    <div>
      {loadError && (
        <div className="bg-yellow-50 border-l-4 border-yellow-400 p-4 mb-4">
          <div className="flex">
            <div className="ml-3">
              <p className="text-sm text-yellow-700">{loadError}</p>
            </div>
          </div>
        </div>
      )}
      <PreferencesPage
        customerId={customerId}
        initialPreferences={preferences ?? undefined}
      />
    </div>
  );
}
