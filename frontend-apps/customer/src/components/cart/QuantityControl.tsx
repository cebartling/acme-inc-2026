import { useState } from "react";
import { Minus, Plus } from "lucide-react";

interface QuantityControlProps {
  quantity: number;
  onChange: (quantity: number) => void;
  disabled?: boolean;
  /** Used for accessible names, e.g. "Gadget Pro (Black)". */
  itemName: string;
  describedBy?: string;
}

/**
 * −/+ buttons and a numeric input for a cart line (AC-0004-07-04). Typed values commit on
 * blur or Enter, not per keystroke. Quantity 0 is not offered here; Remove deletes a line.
 *
 * Remount it (via `key`) after each change is applied so the input resyncs.
 */
export function QuantityControl({
  quantity,
  onChange,
  disabled = false,
  itemName,
  describedBy,
}: QuantityControlProps) {
  const [input, setInput] = useState(String(quantity));

  const commit = () => {
    const next = Number(input);
    if (Number.isInteger(next) && next >= 1 && next !== quantity) {
      onChange(next);
    } else {
      setInput(String(quantity));
    }
  };

  const buttonClass =
    "rounded-md border border-slate-600 p-1.5 text-slate-200 transition-colors hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-40";

  return (
    <div className="flex items-center gap-1">
      <button
        type="button"
        data-testid="decreaseQuantity"
        aria-label={`Decrease quantity of ${itemName}`}
        disabled={disabled || quantity <= 1}
        onClick={() => onChange(quantity - 1)}
        className={buttonClass}
      >
        <Minus size={14} aria-hidden="true" />
      </button>
      <input
        type="number"
        data-testid="lineQuantityInput"
        aria-label={`Quantity of ${itemName}`}
        aria-describedby={describedBy}
        min={1}
        step={1}
        value={input}
        disabled={disabled}
        onChange={(e) => setInput(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === "Enter") commit();
        }}
        className="w-16 rounded-lg border border-slate-600 bg-slate-900 px-2 py-1 text-center text-sm text-white"
      />
      <button
        type="button"
        data-testid="increaseQuantity"
        aria-label={`Increase quantity of ${itemName}`}
        disabled={disabled}
        onClick={() => onChange(quantity + 1)}
        className={buttonClass}
      >
        <Plus size={14} aria-hidden="true" />
      </button>
    </div>
  );
}
