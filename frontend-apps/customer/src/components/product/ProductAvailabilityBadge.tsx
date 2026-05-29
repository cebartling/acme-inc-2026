interface ProductAvailabilityBadgeProps {
  availability: "IN_STOCK" | "OUT_OF_STOCK";
}

export function ProductAvailabilityBadge({
  availability,
}: ProductAvailabilityBadgeProps) {
  if (availability === "IN_STOCK") {
    return (
      <span
        data-testid="availabilityBadge"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-green-400"
      >
        <span
          className="h-2 w-2 rounded-full bg-green-400"
          aria-hidden="true"
        />
        In Stock
      </span>
    );
  }

  return (
    <span
      data-testid="availabilityBadge"
      className="inline-flex items-center gap-1.5 text-sm font-medium text-red-400"
    >
      <span className="h-2 w-2 rounded-full bg-red-400" aria-hidden="true" />
      Out of Stock
    </span>
  );
}
