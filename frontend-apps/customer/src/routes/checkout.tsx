import { createFileRoute } from "@tanstack/react-router";
import { CheckoutPage } from "@/components/checkout/CheckoutPage";

export const Route = createFileRoute("/checkout")({
  component: CheckoutRoute,
});

function CheckoutRoute() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 px-4 py-8">
      <div className="mx-auto max-w-5xl">
        <CheckoutPage />
      </div>
    </div>
  );
}
