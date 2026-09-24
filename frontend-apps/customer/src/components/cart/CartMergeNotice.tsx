import { X } from "lucide-react";
import { useCartNoticeStore } from "@/stores/cartNotice.store";

/**
 * One-off cart notice under the header, e.g. quantities capped when a guest cart merged
 * into the account cart on sign-in (US-0004-08 AC-04). Shown until dismissed.
 */
export function CartMergeNotice() {
  const message = useCartNoticeStore((state) => state.message);
  const dismiss = useCartNoticeStore((state) => state.dismiss);

  if (!message) return null;

  return (
    <div
      role="status"
      data-testid="cartMergeNotice"
      className="flex items-start gap-3 border-b border-amber-700 bg-amber-950/60 px-4 py-3 text-sm text-amber-100"
    >
      <p className="flex-1">
        <span className="font-semibold">Your cart has been updated.</span>{" "}
        {message}
      </p>
      <button
        type="button"
        data-testid="dismissCartMergeNotice"
        onClick={dismiss}
        aria-label="Dismiss cart notice"
        className="rounded-md p-1 text-amber-200 transition-colors hover:bg-amber-900 hover:text-white"
      >
        <X size={16} aria-hidden="true" />
      </button>
    </div>
  );
}
