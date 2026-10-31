import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { OrderLoadError } from "@/components/orders/OrderLoadError";
import { OrderPageSkeleton } from "@/components/orders/OrderPageSkeleton";
import { SearchPagination } from "@/components/search";
import { useRedirectWhenSignedOut } from "@/hooks/useRedirectWhenSignedOut";
import { orderApi } from "@/services/api";
import type { OrderSummary } from "@/services/api";
import { useIsAuthenticated, useUserId } from "@/stores/auth.store";

const PAGE_SIZE = 10;

/** The signed-in customer's orders, newest first (PIN-428, epic 005 F5). */
export function OrderHistoryPage({ page }: { page: number }) {
  const navigate = useNavigate();
  const isAuthenticated = useIsAuthenticated();
  const userId = useUserId();
  const orders = useQuery({
    // By user: the cache outlives signing out, and these aren't the next account's orders
    queryKey: ["orders", userId, page],
    queryFn: () => orderApi.list(page, PAGE_SIZE),
    enabled: isAuthenticated,
    retry: false,
    // Keeps the list, and the focus on its pagination, while the next page loads
    placeholderData: keepPreviousData,
  });
  const redirecting = useRedirectWhenSignedOut(
    page > 1 ? `/orders?page=${page}` : "/orders",
    orders,
  );

  if (redirecting) return null;

  if (orders.isPending) {
    return <OrderPageSkeleton />;
  }

  // Not on a failed refetch, which still has the orders to show
  if (orders.isLoadingError) {
    return (
      <OrderLoadError message="We couldn’t load your orders." query={orders} />
    );
  }

  const { orders: rows, totalResults, totalPages } = orders.data;
  return (
    <div>
      <h1 className="mb-6 text-3xl font-bold text-white">Your orders</h1>
      {totalResults === 0 ? (
        <div data-testid="ordersEmpty" className="text-center text-slate-300">
          <p>You haven’t placed any orders yet.</p>
          <Link
            to="/"
            className="mt-4 inline-block text-sm text-indigo-400 hover:text-indigo-300"
          >
            Start shopping
          </Link>
        </div>
      ) : rows.length === 0 ? (
        // Past the last page, e.g. a stale or hand-edited ?page=
        <p className="text-center text-slate-300">
          There are no orders on this page.{" "}
          <Link
            to="/orders"
            search={{ page: 1 }}
            className="text-indigo-400 hover:text-indigo-300"
          >
            Go to your latest orders
          </Link>
        </p>
      ) : (
        <>
          <ul className="space-y-3">
            {rows.map((order) => (
              <OrderRow key={order.orderId} order={order} page={page} />
            ))}
          </ul>
          {totalPages > 1 && (
            <SearchPagination
              label="Order history pagination"
              currentPage={page}
              totalPages={totalPages}
              onPageChange={(newPage) =>
                navigate({ to: "/orders", search: { page: newPage } })
              }
            />
          )}
        </>
      )}
    </div>
  );
}

function OrderRow({ order, page }: { order: OrderSummary; page: number }) {
  const { orderId, orderNumber, status, createdAt, itemCount, subtotal } =
    order;
  return (
    <li data-testid="orderRow">
      <Link
        to="/orders/$orderId"
        params={{ orderId }}
        // So "Back to your orders" returns here; page 1 keeps the plain URL
        search={{ page: page > 1 ? page : undefined }}
        className="block rounded-xl bg-slate-800 p-4 shadow-lg hover:bg-slate-700"
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="font-semibold text-white">{orderNumber}</span>
          <span data-testid="orderStatus" className="text-sm text-slate-300">
            {status}
          </span>
        </div>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-400">
          <span>
            {new Date(createdAt).toLocaleDateString()} · {itemCount}{" "}
            {itemCount === 1 ? "item" : "items"}
          </span>
          <span>
            ${subtotal.amount.toFixed(2)} {subtotal.currency}
          </span>
        </div>
      </Link>
    </li>
  );
}
