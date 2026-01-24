import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { DevicesPage } from "@/components/devices/DevicesPage";
import {
  useIsAuthenticated,
  useIsAuthLoading,
} from "@/stores/auth.store";
import { identityApi, type TrustedDevice } from "@/services/api";

export const Route = createFileRoute("/devices")({
  component: DevicesPageRoute,
});

function DevicesPageRoute() {
  const navigate = useNavigate();
  const isAuthenticated = useIsAuthenticated();
  const isAuthLoading = useIsAuthLoading();

  const [devices, setDevices] = useState<TrustedDevice[]>([]);
  const [isLoadingDevices, setIsLoadingDevices] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Redirect to signin if not authenticated
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      navigate({ to: "/signin" });
    }
  }, [isAuthLoading, isAuthenticated, navigate]);

  // Fetch devices when authenticated
  useEffect(() => {
    async function fetchDevices() {
      setIsLoadingDevices(true);
      setLoadError(null);

      try {
        const response = await identityApi.getTrustedDevices();
        setDevices(response.devices);
      } catch (error) {
        console.error("Failed to load trusted devices:", error);
        setLoadError(
          error instanceof Error
            ? error.message
            : "Failed to load trusted devices."
        );
      } finally {
        setIsLoadingDevices(false);
      }
    }

    if (isAuthenticated) {
      fetchDevices();
    }
  }, [isAuthenticated]);

  // Reload devices after revocation
  const handleDevicesChanged = async () => {
    try {
      const response = await identityApi.getTrustedDevices();
      setDevices(response.devices);
    } catch (error) {
      console.error("Failed to reload trusted devices:", error);
    }
  };

  // Show loading state while checking auth or loading devices
  if (isAuthLoading || (isAuthenticated && isLoadingDevices)) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mx-auto" />
          <p className="mt-2 text-sm text-gray-600">
            {isAuthLoading ? "Checking authentication..." : "Loading devices..."}
          </p>
        </div>
      </div>
    );
  }

  // Don't render if not authenticated (will redirect)
  if (!isAuthenticated) {
    return null;
  }

  return (
    <div>
      {loadError && (
        <div className="bg-red-50 border-l-4 border-red-400 p-4 mb-4">
          <div className="flex">
            <div className="ml-3">
              <p className="text-sm text-red-700">{loadError}</p>
            </div>
          </div>
        </div>
      )}
      <DevicesPage devices={devices} onDevicesChanged={handleDevicesChanged} />
    </div>
  );
}
