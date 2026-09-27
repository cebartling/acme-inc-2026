import { Loader2, Trash2 } from "lucide-react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { isCartGone, useClearCart } from "@/hooks/useCart";

/**
 * "Clear cart" with a confirmation step (PIN-294). On success the cached cart is the empty
 * one, so the page shows the empty state and the header badge drops its count.
 */
export function ClearCartButton({ cartId }: { cartId: string }) {
  const clear = useClearCart();
  // A gone cart is not an error to show; the cart reloads instead.
  const message =
    clear.error && !isCartGone(clear.error) ? clear.error.message : null;

  return (
    <AlertDialog>
      {/* The trigger is where focus returns when the dialog closes. */}
      <AlertDialogTrigger
        data-testid="clearCart"
        disabled={clear.isPending}
        className="mt-4 flex w-full items-center justify-center gap-2 rounded-md px-4 py-2 text-sm text-slate-400 transition-colors hover:bg-slate-700 hover:text-red-400 disabled:cursor-not-allowed disabled:opacity-40"
      >
        {clear.isPending ? (
          <Loader2 size={16} className="animate-spin" aria-hidden="true" />
        ) : (
          <Trash2 size={16} aria-hidden="true" />
        )}
        Clear cart
      </AlertDialogTrigger>

      {message && (
        <p
          role="alert"
          data-testid="clearCartMessage"
          className="mt-2 text-center text-sm text-red-400"
        >
          {message}
        </p>
      )}

      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Clear your cart?</AlertDialogTitle>
          <AlertDialogDescription>
            This removes every item from your cart.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            data-testid="confirmClearCart"
            onClick={() => clear.mutate({ cartId })}
          >
            Clear cart
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
