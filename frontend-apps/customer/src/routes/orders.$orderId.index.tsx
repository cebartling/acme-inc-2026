import { createFileRoute } from "@tanstack/react-router";
import { z } from "zod";
import { OrderDetailPage } from "@/components/orders/OrderDetailPage";
import { orderHistoryPage } from "@/schemas/orderHistoryPage.schema";

export const Route = createFileRoute("/orders/$orderId/")({
  validateSearch: z.object({
    // Optional, so a link from outside the history needn't name one
    page: orderHistoryPage.optional().catch(undefined),
  }),
  component: OrderDetailRoute,
});

function OrderDetailRoute() {
  const { orderId } = Route.useParams();
  const { page } = Route.useSearch();
  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 px-4 py-8">
      <div className="mx-auto max-w-3xl">
        <OrderDetailPage orderId={orderId} page={page} />
      </div>
    </div>
  );
}
