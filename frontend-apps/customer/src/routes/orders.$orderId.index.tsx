import { createFileRoute } from "@tanstack/react-router";
import { OrderDetailPage } from "@/components/orders/OrderDetailPage";

export const Route = createFileRoute("/orders/$orderId/")({
  component: OrderDetailRoute,
});

function OrderDetailRoute() {
  const { orderId } = Route.useParams();
  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 px-4 py-8">
      <div className="mx-auto max-w-3xl">
        <OrderDetailPage orderId={orderId} />
      </div>
    </div>
  );
}
