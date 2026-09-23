import { useId, useState } from "react";
import { Link } from "@tanstack/react-router";
import { Loader2 } from "lucide-react";
import type { ProductDetail, ProductVariant } from "@/services/api";
import { useAddToCart } from "@/hooks/useAddToCart";

interface AddToCartFormProps {
  product: ProductDetail;
  variant: ProductVariant | undefined;
  isOutOfStock: boolean;
  /** True while variant price/availability is loading or failed to load. */
  isUnavailable: boolean;
}

function variantAttributes(variant: ProductVariant): Record<string, string> {
  const attributes: Record<string, string> = {};
  if (variant.color) attributes.color = variant.color;
  if (variant.size) attributes.size = variant.size;
  return attributes;
}

/**
 * Quantity input and Add to Cart button for the selected variant (US-0004-06).
 *
 * Remount it (via `key`) when the variant changes so the quantity and any
 * confirmation or error from the previous variant are cleared.
 */
export function AddToCartForm({
  product,
  variant,
  isOutOfStock,
  isUnavailable,
}: AddToCartFormProps) {
  const [quantityInput, setQuantityInput] = useState("1");
  const addToCart = useAddToCart();
  const quantityId = useId();
  const errorId = useId();

  const quantity = Number(quantityInput);
  const isValidQuantity = Number.isInteger(quantity) && quantity >= 1;
  const isDisabled =
    !variant ||
    isOutOfStock ||
    isUnavailable ||
    !isValidQuantity ||
    addToCart.isPending;

  const handleAdd = () => {
    if (!variant || isDisabled) return;
    addToCart.mutate({
      variantId: variant.id,
      quantity,
      productSnapshot: {
        productId: product.id,
        name: product.name,
        sku: variant.sku,
        variantName: variant.name,
        imageUrl: variant.images[0] ?? null,
        attributes: variantAttributes(variant),
      },
    });
  };

  const addedLine = addToCart.data?.items.find(
    (item) => item.variantId === addToCart.variables?.variantId,
  );

  return (
    <div className="mb-6">
      <div className="flex items-end gap-3">
        <div>
          <label
            htmlFor={quantityId}
            className="mb-1 block text-xs text-slate-400"
          >
            Quantity
          </label>
          <input
            id={quantityId}
            data-testid="quantityInput"
            type="number"
            min={1}
            step={1}
            value={quantityInput}
            onChange={(e) => setQuantityInput(e.target.value)}
            aria-invalid={addToCart.isError || !isValidQuantity}
            aria-describedby={addToCart.isError ? errorId : undefined}
            className="w-20 rounded-lg border border-slate-600 bg-slate-900 px-3 py-2 text-sm text-white"
          />
        </div>

        <button
          type="button"
          data-testid="addToCartButton"
          onClick={handleAdd}
          disabled={isDisabled}
          aria-disabled={isDisabled}
          aria-busy={addToCart.isPending}
          aria-label={
            variant
              ? `Add ${product.name} (${variant.name}) to cart`
              : undefined
          }
          className={[
            "inline-flex items-center gap-2 rounded-lg px-6 py-2.5 text-sm font-semibold transition-colors",
            isDisabled
              ? "cursor-not-allowed bg-slate-700 text-slate-500"
              : "bg-indigo-600 text-white hover:bg-indigo-500",
          ].join(" ")}
        >
          {addToCart.isPending && (
            <Loader2 size={16} className="animate-spin" aria-hidden="true" />
          )}
          {isOutOfStock ? "Out of Stock" : "Add to Cart"}
        </button>
      </div>

      {addToCart.isError && (
        <p
          id={errorId}
          role="alert"
          data-testid="addToCartError"
          className="mt-2 text-sm text-red-400"
        >
          {addToCart.error.message}
        </p>
      )}

      <div aria-live="assertive">
        {addToCart.isSuccess && addedLine && (
          <div
            data-testid="addToCartConfirmation"
            className="mt-3 rounded-lg border border-emerald-700 bg-emerald-950/40 p-3 text-sm text-emerald-200"
          >
            <p className="font-semibold">Added to cart</p>
            <p>
              {addToCart.variables.quantity} × {product.name} (
              {addedLine.productSnapshot.variantName}) · $
              {addedLine.unitPrice.toFixed(2)} each
            </p>
            <p>
              Line total: ${addedLine.lineTotal.toFixed(2)} (
              {addedLine.quantity} in cart)
            </p>
            <Link
              to="/cart"
              data-testid="viewCartLink"
              className="mt-2 inline-block font-semibold text-emerald-100 underline hover:text-white"
            >
              View Cart
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}
