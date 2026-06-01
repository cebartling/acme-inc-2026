interface ProductAvailabilityBadgeProps {
  availability: "IN_STOCK" | "OUT_OF_STOCK";
}

export function ProductAvailabilityBadge({
  availability,
}: ProductAvailabilityBadgeProps) {
  const isInStock = availability === "IN_STOCK";

  return (
    <span
      data-testid="availabilityBadge"
      aria-live={isInStock ? "polite" : "assertive"}
      className={`inline-flex items-center gap-1.5 text-sm font-medium ${isInStock ? "text-green-400" : "text-red-400"}`}
    >
      <span
        className={`h-2 w-2 rounded-full ${isInStock ? "bg-green-400" : "bg-red-400"}`}
        aria-hidden="true"
      />
      {isInStock ? "In Stock" : "Out of Stock"}
    </span>
  );
}
