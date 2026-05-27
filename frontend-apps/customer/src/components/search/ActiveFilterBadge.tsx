interface ActiveFilterBadgeProps {
  label: string;
  onRemove: () => void;
}

export function ActiveFilterBadge({ label, onRemove }: ActiveFilterBadgeProps) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-indigo-900 px-3 py-1 text-xs text-indigo-200">
      {label}
      <button
        type="button"
        onClick={onRemove}
        aria-label={`Remove filter: ${label}`}
        className="ml-1 rounded-full hover:text-white focus:outline-none focus:ring-1 focus:ring-indigo-400"
      >
        ×
      </button>
    </span>
  );
}
