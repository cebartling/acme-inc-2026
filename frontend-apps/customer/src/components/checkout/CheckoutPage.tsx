import { useEffect, useRef, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { CartSummary } from "@/components/cart/CartSummary";
import { CART_QUERY_KEY, useCart } from "@/hooks/useCart";
import { ApiError, cartApi, orderApi } from "@/services/api";
import type { Cart } from "@/services/api";
import { useIsAuthLoading, useIsAuthenticated } from "@/stores/auth.store";

/**
 * Review and place the order (PIN-426, journey 0005 steps 4 and 5), without payment, shipping
 * or tax. Opening the page starts checkout again, which resumes the live session (PIN-330), so a
 * reload shows the same session. Its lines come from the current cart: a cart in checkout is
 * locked, so they are the session's lines.
 */
export function CheckoutPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isAuthenticated = useIsAuthenticated();
  const isAuthLoading = useIsAuthLoading();
  const {
    data: cart,
    isPending: isCartPending,
    isError: isCartError,
  } = useCart();
  const hasLines = !!cart && cart.items.length > 0;

  // Only signed-in customers can place orders; a guest signs in and starts again from the cart
  useEffect(() => {
    if (!isAuthLoading && !isAuthenticated) {
      navigate({ to: "/signin", search: { redirect: "/cart" } });
    }
  }, [isAuthLoading, isAuthenticated, navigate]);

  const session = useQuery({
    queryKey: ["checkoutSession", cart?.id],
    queryFn: () => cartApi.startCheckout(cart!.id),
    enabled: isAuthenticated && hasLines,
    // Starting checkout is a POST that moves the expiry out, so not on every focus; and not
    // from the cache on the next visit either, which could hold a lapsed session.
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    // A refusal (422, 404) won't change on a retry; showing it beats a skeleton through backoff
    retry: false,
  });

  const sessionId = session.data?.checkoutSessionId;
  const idempotencyKey = useIdempotencyKey(sessionId);

  const place = useMutation({
    mutationFn: () => orderApi.place(sessionId!, idempotencyKey!),
    onSuccess: (order) => {
      forgetIdempotencyKey(sessionId!);
      // Replace, so Back doesn't return to a review of a cart that is now an order
      navigate({
        to: "/orders/$orderId/confirmation",
        params: { orderId: order.orderId },
        replace: true,
      });
      // The cart is converted, so there is no current cart: the badge and /cart go empty
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
    onError: (error) => {
      // Either can mean another tab's order converted the cart, so the badge reloads
      const code = codeOf(error);
      if (
        code === "CART_ALREADY_CONVERTED" ||
        code === "CHECKOUT_SESSION_NOT_FOUND"
      ) {
        queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
      }
    },
  });

  if (!isAuthenticated || isCartPending || (hasLines && session.isPending)) {
    return (
      <div className="animate-pulse space-y-4" aria-busy="true">
        <div className="h-8 w-1/3 rounded bg-slate-700" />
        <div className="h-24 rounded bg-slate-700" />
      </div>
    );
  }

  const placeErrorCode = codeOf(place.error);
  if (placeErrorCode === "CHECKOUT_SESSION_NOT_FOUND") {
    return (
      <Notice testId="checkoutExpired">
        Your checkout session expired. Go back to your cart and start checkout
        again.
      </Notice>
    );
  }
  if (placeErrorCode === "CART_ALREADY_CONVERTED") {
    return (
      <Notice testId="orderAlreadyPlaced">
        This order was already placed in another tab.
      </Notice>
    );
  }
  if (!cart && isCartError) {
    return (
      <Notice testId="checkoutUnavailable">
        We couldn’t load your cart. Please try again.
      </Notice>
    );
  }
  if (!hasLines) {
    return <Notice testId="checkoutEmpty">Your cart is empty.</Notice>;
  }
  if (!session.isSuccess) {
    return (
      <Notice testId="checkoutUnavailable">
        {session.error?.message ?? "We couldn’t start checkout."}
      </Notice>
    );
  }

  return (
    <>
      <h1 className="mb-6 text-3xl font-bold text-white">Review your order</h1>
      <div className="grid gap-6 md:grid-cols-[1fr_18rem]">
        <ReviewLines items={cart.items} />
        <div>
          <CartSummary summary={session.data.cart} />
          <button
            type="button"
            data-testid="placeOrder"
            onClick={() => place.mutate()}
            disabled={place.isPending}
            className="mt-4 flex w-full items-center justify-center gap-2 rounded-md bg-indigo-600 px-6 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
          >
            {place.isPending && (
              <Loader2 size={16} className="animate-spin" aria-hidden="true" />
            )}
            {placeErrorCode === "CART_UNAVAILABLE"
              ? "Try again"
              : "Place order"}
          </button>
          {place.error && (
            <p
              role="alert"
              data-testid="placeOrderMessage"
              className="mt-2 text-center text-sm text-red-400"
            >
              {placeErrorCode === "CART_UNAVAILABLE"
                ? "We couldn’t place your order just now. Please try again."
                : place.error.message}
            </p>
          )}
          <Link
            to="/cart"
            className="mt-4 block text-center text-sm text-slate-400 hover:text-white"
          >
            Back to cart
          </Link>
        </div>
      </div>
    </>
  );
}

function ReviewLines({ items }: { items: Cart["items"] }) {
  return (
    <ul className="divide-y divide-slate-700 rounded-xl bg-slate-800 px-6 shadow-lg">
      {items.map((item) => (
        <li key={item.id} className="flex justify-between gap-4 py-4 text-sm">
          <div>
            <p className="font-medium text-white">
              {item.productSnapshot.name}
            </p>
            {item.productSnapshot.variantName && (
              <p className="text-slate-400">
                {item.productSnapshot.variantName}
              </p>
            )}
            <p className="text-slate-400">
              {item.quantity} × ${item.unitPrice.toFixed(2)}
            </p>
          </div>
          <p className="text-white">${item.lineTotal.toFixed(2)}</p>
        </li>
      ))}
    </ul>
  );
}

function Notice({ testId, children }: { testId: string; children: ReactNode }) {
  return (
    <div className="rounded-xl bg-slate-800 p-6 text-center shadow-lg">
      <p role="alert" data-testid={testId} className="text-slate-200">
        {children}
      </p>
      <Link
        to="/cart"
        className="mt-4 inline-block text-sm text-indigo-400 hover:text-indigo-300"
      >
        Back to cart
      </Link>
    </div>
  );
}

function codeOf(error: unknown): string | undefined {
  return error instanceof ApiError
    ? (error.data?.code as string | undefined)
    : undefined;
}

const storageKeyFor = (sessionId: string) => `acme.checkout.${sessionId}.key`;

/**
 * One `Idempotency-Key` per checkout session (AC-5.8), kept for every retry of it, including
 * after a reload, so a request whose answer was lost can't place a second order. Kept in
 * sessionStorage, which is per tab: another tab has its own key. Once this tab's order has
 * converted the cart, the converted cart has no live session, so the order service answers that
 * tab 404 `CHECKOUT_SESSION_NOT_FOUND`; 409 `CART_ALREADY_CONVERTED` only when the two race.
 */
function useIdempotencyKey(sessionId: string | undefined): string | undefined {
  // Also held here, so the key stays the same for this page when storage is unavailable
  const held = useRef<{ sessionId: string; key: string } | null>(null);
  if (!sessionId) return undefined;
  if (held.current?.sessionId !== sessionId) {
    held.current = { sessionId, key: storedKey(sessionId) };
  }
  return held.current.key;
}

function storedKey(sessionId: string): string {
  try {
    const existing = sessionStorage.getItem(storageKeyFor(sessionId));
    if (existing) return existing;
    const key = crypto.randomUUID();
    sessionStorage.setItem(storageKeyFor(sessionId), key);
    return key;
  } catch (error) {
    // Storage can be blocked (private mode, site settings): the key then lasts as long as the
    // page, which still covers a retry and a double-click, but not a reload
    console.warn(
      "checkout: sessionStorage unavailable for the order key",
      error,
    );
    return crypto.randomUUID();
  }
}

function forgetIdempotencyKey(sessionId: string): void {
  try {
    sessionStorage.removeItem(storageKeyFor(sessionId));
  } catch (error) {
    console.warn("checkout: couldn't clear the order key", error);
  }
}
