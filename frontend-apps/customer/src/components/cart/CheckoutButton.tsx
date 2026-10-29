import { useMutation } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { cartApi } from "@/services/api";
import { useIsAuthenticated } from "@/stores/auth.store";

/**
 * "Checkout" (PIN-426, journey 0005 step 1). Only signed-in customers can place orders, so a
 * guest goes to sign-in first, without locking the cart; their cart merges on sign-in. A
 * signed-in customer's cart is locked for checkout here, so a cart that can't be ordered
 * (an out-of-stock line, stock that can't be checked) is refused while they're still on it.
 */
export function CheckoutButton({ cartId }: { cartId: string }) {
  const navigate = useNavigate();
  const isAuthenticated = useIsAuthenticated();
  const start = useMutation({
    mutationFn: () => cartApi.startCheckout(cartId),
    onSuccess: () => navigate({ to: "/checkout" }),
  });

  function handleClick() {
    if (!isAuthenticated) {
      navigate({ to: "/signin", search: { redirect: "/cart" } });
      return;
    }
    start.mutate();
  }

  return (
    <>
      <button
        type="button"
        data-testid="checkout"
        onClick={handleClick}
        disabled={start.isPending}
        className="mt-4 flex w-full items-center justify-center gap-2 rounded-md bg-indigo-600 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
      >
        {start.isPending && (
          <Loader2 size={16} className="animate-spin" aria-hidden="true" />
        )}
        Checkout
      </button>
      {start.error && (
        <p
          role="alert"
          data-testid="checkoutMessage"
          className="mt-2 text-center text-sm text-red-400"
        >
          {start.error.message}
        </p>
      )}
    </>
  );
}
