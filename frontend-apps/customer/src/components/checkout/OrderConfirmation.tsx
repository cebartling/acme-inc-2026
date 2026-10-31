import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { CheckCircle2 } from "lucide-react";
import { orderApi } from "@/services/api";

/**
 * The placed order (PIN-426, journey 0005 step 6). It reads the order back, so a reload never
 * places it again (AC-6.5).
 */
export function OrderConfirmation({ orderId }: { orderId: string }) {
  const order = useQuery({
    queryKey: ["order", orderId],
    queryFn: () => orderApi.get(orderId),
  });

  if (order.isPending) {
    return (
      <div className="animate-pulse space-y-4" aria-busy="true">
        <div className="h-8 w-1/2 rounded bg-slate-700" />
        <div className="h-16 rounded bg-slate-700" />
      </div>
    );
  }

  if (order.isError) {
    return (
      <p role="alert" className="text-center text-red-400">
        We couldn’t load this order. Please try again.
      </p>
    );
  }

  const { orderNumber, pricing } = order.data;
  return (
    <div className="rounded-xl bg-slate-800 p-8 text-center shadow-lg">
      <CheckCircle2
        size={40}
        className="mx-auto text-green-400"
        aria-hidden="true"
      />
      <h1 className="mt-4 text-3xl font-bold text-white">
        Thank you for your order
      </h1>
      <p className="mt-4 text-slate-300">
        Order number{" "}
        <span data-testid="orderNumber" className="font-semibold text-white">
          {orderNumber}
        </span>
      </p>
      <p className="mt-2 text-sm text-slate-400">
        Subtotal{" "}
        <span data-testid="orderSubtotal">
          ${pricing.subtotal.amount.toFixed(2)}
        </span>{" "}
        {pricing.subtotal.currency}
      </p>
      <div className="mt-6 flex justify-center gap-6 text-sm">
        <Link
          to="/orders/$orderId"
          params={{ orderId }}
          className="text-indigo-400 hover:text-indigo-300"
        >
          View order
        </Link>
        <Link to="/" className="text-indigo-400 hover:text-indigo-300">
          Continue shopping
        </Link>
      </div>
    </div>
  );
}
