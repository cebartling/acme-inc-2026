import { createFileRoute } from "@tanstack/react-router";
import { PreferencesPage } from "@/components/preferences/PreferencesPage";

export const Route = createFileRoute("/preferences")({
  component: PreferencesPageRoute,
});

function PreferencesPageRoute() {
  // TODO: Get customerId from auth context
  const customerId = "demo-customer-id";

  // TODO: Fetch initial preferences from API
  // GET /api/v1/customers/{customerId}/preferences

  return <PreferencesPage customerId={customerId} />;
}
