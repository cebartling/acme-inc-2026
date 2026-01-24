import { useState } from "react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { identityApi, type TrustedDevice } from "@/services/api";

interface DevicesPageProps {
  devices: TrustedDevice[];
  onDevicesChanged: () => void;
}

export function DevicesPage({ devices, onDevicesChanged }: DevicesPageProps) {
  const [revokingDevice, setRevokingDevice] = useState<string | null>(null);
  const [revokingAll, setRevokingAll] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showRevokeAllConfirm, setShowRevokeAllConfirm] = useState(false);

  const handleRevokeDevice = async (deviceId: string) => {
    setRevokingDevice(deviceId);
    setError(null);

    try {
      await identityApi.revokeDevice(deviceId);
      await onDevicesChanged();
    } catch (err) {
      console.error("Failed to revoke device:", err);
      setError(
        err instanceof Error ? err.message : "Failed to revoke device"
      );
    } finally {
      setRevokingDevice(null);
    }
  };

  const handleRevokeAll = async () => {
    setRevokingAll(true);
    setError(null);

    try {
      await identityApi.revokeAllDevices();
      await onDevicesChanged();
      setShowRevokeAllConfirm(false);
    } catch (err) {
      console.error("Failed to revoke all devices:", err);
      setError(
        err instanceof Error ? err.message : "Failed to revoke all devices"
      );
    } finally {
      setRevokingAll(false);
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(date);
  };

  const formatExpiresIn = (expiresAt: string) => {
    const now = new Date();
    const expires = new Date(expiresAt);
    const diffMs = expires.getTime() - now.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays < 0) return "Expired";
    if (diffDays === 0) return "Expires today";
    if (diffDays === 1) return "Expires tomorrow";
    return `Expires in ${diffDays} days`;
  };

  return (
    <div className="max-w-4xl mx-auto p-6">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900">Trusted Devices</h1>
        <p className="mt-2 text-gray-600">
          Manage devices that can bypass multi-factor authentication for 30 days.
        </p>
      </div>

      {error && (
        <div className="mb-6 bg-red-50 border-l-4 border-red-400 p-4">
          <p className="text-sm text-red-700">{error}</p>
        </div>
      )}

      {devices.length === 0 ? (
        <Card className="p-8 text-center">
          <div className="text-gray-500">
            <svg
              className="mx-auto h-12 w-12 text-gray-400"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
              />
            </svg>
            <h3 className="mt-2 text-sm font-medium text-gray-900">
              No trusted devices
            </h3>
            <p className="mt-1 text-sm text-gray-500">
              When you complete MFA and choose "Remember this device," it will
              appear here.
            </p>
          </div>
        </Card>
      ) : (
        <>
          <div className="space-y-4">
            {devices.map((device) => (
              <Card key={device.id} className="p-6">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-3">
                      <div className="flex-shrink-0">
                        <svg
                          className="h-8 w-8 text-gray-400"
                          fill="none"
                          viewBox="0 0 24 24"
                          stroke="currentColor"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
                          />
                        </svg>
                      </div>
                      <div>
                        <h3 className="text-lg font-medium text-gray-900 flex items-center gap-2">
                          {device.deviceName}
                          {device.isCurrent && (
                            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                              Current device
                            </span>
                          )}
                        </h3>
                        <div className="mt-1 text-sm text-gray-500 space-y-1">
                          <p>
                            <span className="font-medium">Trusted:</span>{" "}
                            {formatDate(device.createdAt)}
                          </p>
                          <p>
                            <span className="font-medium">Last used:</span>{" "}
                            {formatDate(device.lastUsedAt)}
                          </p>
                          <p>
                            <span className="font-medium">IP Address:</span>{" "}
                            {device.ipAddress}
                          </p>
                          <p className="text-orange-600 font-medium">
                            {formatExpiresIn(device.expiresAt)}
                          </p>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div>
                    <Button
                      variant="destructive"
                      onClick={() => handleRevokeDevice(device.id)}
                      disabled={revokingDevice === device.id}
                    >
                      {revokingDevice === device.id ? "Revoking..." : "Revoke"}
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>

          <div className="mt-8 border-t pt-6">
            <div className="flex items-start justify-between">
              <div>
                <h3 className="text-lg font-medium text-gray-900">
                  Revoke All Devices
                </h3>
                <p className="mt-1 text-sm text-gray-500">
                  Remove trust from all devices. You'll need to complete MFA on
                  your next signin.
                </p>
              </div>
              {!showRevokeAllConfirm ? (
                <Button
                  variant="outline"
                  onClick={() => setShowRevokeAllConfirm(true)}
                >
                  Revoke All
                </Button>
              ) : (
                <div className="flex gap-2">
                  <Button
                    variant="destructive"
                    onClick={handleRevokeAll}
                    disabled={revokingAll}
                  >
                    {revokingAll ? "Revoking..." : "Confirm Revoke All"}
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => setShowRevokeAllConfirm(false)}
                    disabled={revokingAll}
                  >
                    Cancel
                  </Button>
                </div>
              )}
            </div>
            {showRevokeAllConfirm && (
              <div className="mt-3 bg-yellow-50 border-l-4 border-yellow-400 p-4">
                <p className="text-sm text-yellow-700">
                  <strong>Warning:</strong> This will revoke trust from all{" "}
                  {devices.length} device{devices.length !== 1 ? "s" : ""}. You
                  will need to complete MFA verification on each device on your
                  next signin.
                </p>
              </div>
            )}
          </div>
        </>
      )}

      <div className="mt-8 bg-blue-50 border-l-4 border-blue-400 p-4">
        <div className="flex">
          <div className="flex-shrink-0">
            <svg
              className="h-5 w-5 text-blue-400"
              viewBox="0 0 20 20"
              fill="currentColor"
            >
              <path
                fillRule="evenodd"
                d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                clipRule="evenodd"
              />
            </svg>
          </div>
          <div className="ml-3">
            <h3 className="text-sm font-medium text-blue-800">
              About Trusted Devices
            </h3>
            <div className="mt-2 text-sm text-blue-700">
              <ul className="list-disc list-inside space-y-1">
                <li>
                  Trusted devices bypass MFA for 30 days after verification
                </li>
                <li>
                  Device trust is tied to your browser and device fingerprint
                </li>
                <li>
                  Clearing browser data or updating your browser may invalidate
                  trust
                </li>
                <li>
                  For security, we recommend revoking devices you no longer use
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
