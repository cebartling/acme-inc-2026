import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { OrderPageSkeleton } from "@/components/orders/OrderPageSkeleton";
import { useRedirectWhenSignedOut } from "@/hooks/useRedirectWhenSignedOut";
import { ApiError, orderApi } from "@/services/api";
import type { OrderLine } from "@/services/api";
import { useIsAuthenticated, useUserId } from "@/stores/auth.store";

/** The order service answers 400 for an ID that is no UUID, and 404 for anyone else's order. */
function isOrderNotFound(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.status === 404 ||
      (error.status === 400 && error.data?.code === "INVALID_REQUEST"))
  );
}

/** One of the customer's orders, from the product snapshot stored with it (PIN-428). */
export function OrderDetailPage({ orderId }: { orderId: string }) {
  const isAuthenticated = useIsAuthenticated();
  const userId = useUserId();
  const order = useQuery({
    // By user: the cache outlives signing out, and this isn't the next account's order
    queryKey: ["order", userId, orderId],
    queryFn: () => orderApi.get(orderId),
    enabled: isAuthenticated,
    // A 404 won't change, and the default retries would hold back the not-found page
    retry: false,
  });
  const redirecting = useRedirectWhenSignedOut(
    // The router decoded the ID, and sign-in decodes this
    `/orders/${encodeURIComponent(orderId)}`,
    order,
  );

  if (redirecting) return null;

  if (order.isPending) {
    return <OrderPageSkeleton />;
  }

  if (isOrderNotFound(order.error)) {
    return (
      <div data-testid="orderNotFound" className="text-center text-slate-300">
        <h1 className="text-3xl font-bold text-white">Order not found</h1>
        <p className="mt-4">We couldn’t find this order on your account.</p>
        <Link
          to="/orders"
          search={{ page: 1 }}
          className="mt-6 inline-block text-sm text-indigo-400 hover:text-indigo-300"
        >
          Back to your orders
        </Link>
      </div>
    );
  }

  // Not on a failed refetch, which still has the order to show
  if (order.isLoadingError) {
    return (
      <p role="alert" className="text-center text-red-400">
        We couldn’t load this order. Please try again.
      </p>
    );
  }

  const { orderNumber, status, createdAt, lineItems, pricing } = order.data;
  return (
    <div>
      <h1 data-testid="orderNumber" className="text-3xl font-bold text-white">
        {orderNumber}
      </h1>
      <p className="mt-2 text-sm text-slate-400">
        {new Date(createdAt).toLocaleDateString()} ·{" "}
        <span data-testid="orderStatus">{status}</span>
      </p>
      <ul className="mt-6 space-y-3">
        {lineItems.map((line) => (
          <OrderLineRow key={line.lineItemId} line={line} />
        ))}
      </ul>
      <p className="mt-6 text-right text-slate-300">
        Subtotal{" "}
        <span data-testid="orderSubtotal" className="font-semibold text-white">
          ${pricing.subtotal.amount.toFixed(2)}
        </span>{" "}
        {pricing.subtotal.currency}
      </p>
      <Link
        to="/orders"
        search={{ page: 1 }}
        className="mt-6 inline-block text-sm text-indigo-400 hover:text-indigo-300"
      >
        Back to your orders
      </Link>
    </div>
  );
}

function OrderLineRow({ line }: { line: OrderLine }) {
  const { productName, variantName, sku, imageUrl, quantity, lineTotal } = line;
  return (
    <li
      data-testid="orderLine"
      className="flex items-center gap-4 rounded-xl bg-slate-800 p-4 shadow-lg"
    >
      {imageUrl && (
        <img src={imageUrl} alt="" className="h-16 w-16 rounded object-cover" />
      )}
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-white">{productName ?? "Item"}</p>
        {variantName && <p className="text-sm text-slate-300">{variantName}</p>}
        {sku && <p className="text-sm text-slate-400">SKU {sku}</p>}
      </div>
      <div className="text-right text-sm text-slate-300">
        <p>Qty {quantity}</p>
        <p>
          ${lineTotal.amount.toFixed(2)} {lineTotal.currency}
        </p>
      </div>
    </li>
  );
}
