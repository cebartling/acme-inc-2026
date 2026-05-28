import { useState } from "react";

const PRICE_PRESETS = [
  { label: "Under $25", min: undefined, max: 25 },
  { label: "$25 – $50", min: 25, max: 50 },
  { label: "$50 – $100", min: 50, max: 100 },
  { label: "$100 – $200", min: 100, max: 200 },
  { label: "Over $200", min: 200, max: undefined },
] as const;

interface PriceRangeFilterProps {
  priceMin: number | undefined;
  priceMax: number | undefined;
  onApply: (min: number | undefined, max: number | undefined) => void;
  onClear: () => void;
}

export function PriceRangeFilter({
  priceMin,
  priceMax,
  onApply,
  onClear,
}: PriceRangeFilterProps) {
  const [customMin, setCustomMin] = useState(
    priceMin !== undefined ? String(priceMin) : "",
  );
  const [customMax, setCustomMax] = useState(
    priceMax !== undefined ? String(priceMax) : "",
  );

  const isPresetActive = (min: number | undefined, max: number | undefined) =>
    priceMin === min && priceMax === max;

  const handlePreset = (min: number | undefined, max: number | undefined) => {
    if (isPresetActive(min, max)) {
      onClear();
    } else {
      setCustomMin(min !== undefined ? String(min) : "");
      setCustomMax(max !== undefined ? String(max) : "");
      onApply(min, max);
    }
  };

  const handleCustomApply = () => {
    let min = customMin !== "" ? Math.max(0, Number(customMin)) : undefined;
    let max = customMax !== "" ? Math.max(0, Number(customMax)) : undefined;
    if (min !== undefined && max !== undefined && min > max) {
      [min, max] = [max, min];
    }
    if (min !== undefined || max !== undefined) {
      onApply(min, max);
    }
  };

  return (
    <div className="space-y-3">
      <ul className="space-y-1">
        {PRICE_PRESETS.map((preset) => (
          <li key={preset.label}>
            <button
              type="button"
              onClick={() => handlePreset(preset.min, preset.max)}
              className={`w-full rounded px-2 py-1 text-left text-sm transition-colors ${
                isPresetActive(preset.min, preset.max)
                  ? "bg-indigo-600 text-white"
                  : "text-slate-300 hover:bg-slate-700"
              }`}
            >
              {preset.label}
            </button>
          </li>
        ))}
      </ul>

      <div className="border-t border-slate-700 pt-3">
        <p className="mb-2 text-xs text-slate-400">Custom range</p>
        <div className="flex items-center gap-2">
          <input
            type="number"
            min={0}
            value={customMin}
            onChange={(e) => setCustomMin(e.target.value)}
            placeholder="Min"
            aria-label="Minimum price"
            className="w-20 rounded border border-slate-600 bg-slate-800 px-2 py-1 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
          <span className="text-slate-500">–</span>
          <input
            type="number"
            min={0}
            value={customMax}
            onChange={(e) => setCustomMax(e.target.value)}
            placeholder="Max"
            aria-label="Maximum price"
            className="w-20 rounded border border-slate-600 bg-slate-800 px-2 py-1 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
          <button
            type="button"
            onClick={handleCustomApply}
            className="rounded bg-indigo-600 px-2 py-1 text-xs text-white hover:bg-indigo-700"
          >
            Go
          </button>
        </div>
      </div>
    </div>
  );
}
