import { createFileRoute } from "@tanstack/react-router";
import { z } from "zod";
import { OrderHistoryPage } from "@/components/orders/OrderHistoryPage";

/** The order service's limit. A bad `page` falls back to 1: `validateSearch` throwing takes down the route. */
const MAX_PAGE = 10_000;

export const Route = createFileRoute("/orders/")({
  validateSearch: z.object({
    page: z.coerce.number().int().min(1).max(MAX_PAGE).default(1).catch(1),
  }),
  component: OrdersRoute,
});

function OrdersRoute() {
  const { page } = Route.useSearch();
  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 px-4 py-8">
      <div className="mx-auto max-w-3xl">
        <OrderHistoryPage page={page} />
      </div>
    </div>
  );
}
