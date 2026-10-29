import { createFileRoute } from "@tanstack/react-router";
import { OrderConfirmation } from "@/components/checkout/OrderConfirmation";

export const Route = createFileRoute("/orders/$orderId/confirmation")({
  component: OrderConfirmationRoute,
});

function OrderConfirmationRoute() {
  const { orderId } = Route.useParams();
  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 px-4 py-8">
      <div className="mx-auto max-w-2xl">
        <OrderConfirmation orderId={orderId} />
      </div>
    </div>
  );
}
