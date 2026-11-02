import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import { CartSummary } from "@/components/cart/CartSummary";
import {
  CART_QUERY_KEY,
  isCartGone,
  useCart,
  useLeaveCheckout,
  writeCart,
} from "@/hooks/useCart";
import { ApiError, cartApi, orderApi } from "@/services/api";
import type { Cart, PlacedOrder } from "@/services/api";
import { useIsAuthLoading, useIsAuthenticated } from "@/stores/auth.store";

/**
 * Review and place the order (PIN-426, journey 0005 steps 4 and 5), without payment, shipping
 * or tax. Opening the page starts checkout again, which resumes the live session (PIN-330), so a
 * reload shows the same session. Its lines come from the current cart: a cart in checkout is
 * locked, so they are the session's lines.
 *
 * Finding no cart after Place order was pressed may mean the order went through and its answer
 * was lost, so the page asks the order service for the order by its key (PIN-440). It only looks:
 * the empty cart can be a sign-in that hasn't refreshed yet, so placing here could order a live
 * cart the customer was told had failed.
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

  // The attempt to recover: one from before the reload, or this page's own if it went unanswered.
  // Never one that was answered, or this page's own order would be looked up when it empties the cart.
  const [placing, setPlacing] = useState(placingOrder);

  // The session is a variable because a cart refetch can clear `sessionId` while this is in flight
  const place = useMutation({
    mutationFn: (placedSessionId: string) => {
      rememberPlacing(placedSessionId);
      // The last lookup answered for the last attempt, and this one may need its own
      void queryClient.resetQueries({ queryKey: ORDER_RECOVERY_KEY });
      return orderApi.place(placedSessionId, idempotencyKey!);
    },
    onSuccess: (order, placedSessionId) => {
      forgetIdempotencyKey(placedSessionId);
      setPlacing(null);
      navigate(confirmationOf(order));
      // The cart is converted, so there is no current cart: the badge and /cart go empty
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
    onError: (error, placedSessionId) => {
      // Any of these can mean another tab's order converted the cart, so the badge reloads
      if (isFinalRefusal(error)) {
        forgetIdempotencyKey(placedSessionId);
        setPlacing(null);
        queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
      } else {
        setPlacing(placingOrder());
      }
    },
  });

  const recovery = useQuery({
    queryKey: [...ORDER_RECOVERY_KEY, placing?.sessionId],
    queryFn: () => recoverOrder(placing!, queryClient),
    enabled:
      isAuthenticated &&
      !isCartPending &&
      !isCartError &&
      !hasLines &&
      !place.isPending &&
      !!placing,
    // Like the session query: it can POST, and the answer decides this page once
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    retry: false,
  });

  const recovered = recovery.data;
  const recoveryError = recovery.error;
  useEffect(() => {
    if (!placing) return;
    if (recovered) {
      forgetIdempotencyKey(placing.sessionId);
      navigate(confirmationOf(recovered));
    } else if (isFinalRefusal(recoveryError)) {
      forgetIdempotencyKey(placing.sessionId);
      // The cart may only have looked empty before the lookup refreshed the sign-in
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    }
  }, [recovered, recoveryError, placing, navigate, queryClient]);

  const leave = useLeaveCheckout();
  const isBusy = place.isPending || leave.isPending;

  // Unlock first, or /cart refuses every change with CART_LOCKED until the session lapses
  function backToCart() {
    // Replace, so Back doesn't reopen the review, which would lock the cart again
    const toCart = () => navigate({ to: "/cart", replace: true });
    leave.mutate(
      { cartId: cart!.id },
      {
        onSuccess: () => {
          forgetIdempotencyKey(sessionId!);
          toCart();
        },
        onError: (error) => {
          // With an unanswered attempt the cart may have become an order: the cart reload that
          // `useLeaveCheckout` starts empties this page, which finds it
          if (isCartGone(error) && !placing) toCart();
        },
      },
    );
  }

  if (
    !isAuthenticated ||
    isCartPending ||
    (hasLines && session.isPending) ||
    recovery.isLoading ||
    recovered
  ) {
    return (
      <div className="animate-pulse space-y-4" aria-busy="true">
        <div className="h-8 w-1/3 rounded bg-slate-700" />
        <div className="h-24 rounded bg-slate-700" />
      </div>
    );
  }

  const placeErrorCode = codeOf(place.error);
  if (
    placeErrorCode === "CHECKOUT_SESSION_NOT_FOUND" ||
    placeErrorCode === "PLACEMENT_ABANDONED"
  ) {
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
    return recoveryError && !meansNoOrder(recoveryError) ? (
      <Notice testId="orderUnconfirmed" linkToOrders>
        We couldn’t confirm your order. Check your orders before trying again.
      </Notice>
    ) : (
      <Notice testId="checkoutEmpty" linkToOrders>
        Your cart is empty.
      </Notice>
    );
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
            onClick={() => {
              leave.reset();
              place.mutate(sessionId!);
            }}
            disabled={isBusy}
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
          <button
            type="button"
            data-testid="backToCart"
            onClick={backToCart}
            disabled={isBusy}
            className="mt-4 block w-full text-center text-sm text-slate-400 hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
          >
            Back to cart
          </button>
          {leave.error && !isCartGone(leave.error) && (
            <p
              role="alert"
              data-testid="backToCartMessage"
              className="mt-2 text-center text-sm text-red-400"
            >
              {leave.error.message}
            </p>
          )}
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

function Notice({
  testId,
  linkToOrders = false,
  children,
}: {
  testId: string;
  linkToOrders?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="rounded-xl bg-slate-800 p-6 text-center shadow-lg">
      <p role="alert" data-testid={testId} className="text-slate-200">
        {children}
      </p>
      <div className="mt-4 flex justify-center gap-6 text-sm">
        <Link to="/cart" className="text-indigo-400 hover:text-indigo-300">
          Back to cart
        </Link>
        {linkToOrders && (
          <Link to="/orders" className="text-indigo-400 hover:text-indigo-300">
            View your orders
          </Link>
        )}
      </div>
    </div>
  );
}

function codeOf(error: unknown): string | undefined {
  return error instanceof ApiError
    ? (error.data?.code as string | undefined)
    : undefined;
}

/** The order service's final 404s, including from an order that was cancelled for good. */
function meansNoOrder(error: unknown): boolean {
  const code = codeOf(error);
  return (
    code === "ORDER_NOT_FOUND" ||
    code === "CHECKOUT_SESSION_NOT_FOUND" ||
    code === "PLACEMENT_ABANDONED" ||
    isCartGone(error)
  );
}

/** This key can't succeed: the session or order is gone, or the cart is another order's. */
function isFinalRefusal(error: unknown): boolean {
  return meansNoOrder(error) || codeOf(error) === "CART_ALREADY_CONVERTED";
}

/**
 * The order behind an unanswered Place order, or `null` when the customer should decide again on
 * the review. A PENDING order only resumes once its cart is known to be gone. The lookup has
 * refreshed any lapsed sign-in by then, so this cart read can be trusted, and lines mean the cart
 * was never converted.
 */
async function recoverOrder(
  attempt: { sessionId: string; key: string },
  queryClient: QueryClient,
): Promise<PlacedOrder | null> {
  const order = await orderApi.findByKey(attempt.key);
  if (order.status !== "PENDING") return order;

  const cart = await cartApi.getCurrent();
  if (cart?.items.length) {
    await writeCart(queryClient, cart);
    return null;
  }
  return orderApi.place(attempt.sessionId, attempt.key);
}

/** Replaces the page, so Back doesn't return to a review of a cart that is now an order. */
function confirmationOf(order: PlacedOrder) {
  return {
    to: "/orders/$orderId/confirmation",
    params: { orderId: order.orderId },
    replace: true,
  } as const;
}

const ORDER_RECOVERY_KEY = ["orderRecovery"] as const;

const storageKeyFor = (sessionId: string) => `acme.checkout.${sessionId}.key`;

/** The session this tab last pressed Place order for. The review alone writes only its key. */
const PLACING_STORAGE_KEY = "acme.checkout.placing";

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
  if (!sessionId) {
    // A session that comes back rereads storage: the lookup may have forgotten its key meanwhile
    held.current = null;
    return undefined;
  }
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
    // A newer session's review mustn't drop an older attempt that is still unanswered
    if (sessionStorage.getItem(PLACING_STORAGE_KEY) === sessionId) {
      sessionStorage.removeItem(PLACING_STORAGE_KEY);
    }
  } catch (error) {
    console.warn("checkout: couldn't clear the order key", error);
  }
}

function rememberPlacing(sessionId: string): void {
  try {
    sessionStorage.setItem(PLACING_STORAGE_KEY, sessionId);
  } catch (error) {
    // The order still goes through; only the recovery after a lost answer is out of reach
    console.warn("checkout: couldn't record the order attempt", error);
  }
}

/** The attempt to recover after a reload; with storage blocked, there is none to find. */
function placingOrder(): { sessionId: string; key: string } | null {
  // The server render has no tab; hydration reads this again in the browser
  if (typeof window === "undefined") return null;
  try {
    const sessionId = sessionStorage.getItem(PLACING_STORAGE_KEY);
    const key = sessionId && sessionStorage.getItem(storageKeyFor(sessionId));
    return sessionId && key ? { sessionId, key } : null;
  } catch (error) {
    console.warn("checkout: couldn't read the order attempt", error);
    return null;
  }
}
