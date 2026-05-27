import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";

export interface CheckboxFilterOption {
  value: string;
  label: string;
  count: number;
}

interface CheckboxFilterProps {
  options: CheckboxFilterOption[];
  selectedValues: string[];
  onToggle: (value: string) => void;
}

export function CheckboxFilter({
  options,
  selectedValues,
  onToggle,
}: CheckboxFilterProps) {
  return (
    <ul className="space-y-2">
      {options.map((option) => {
        const isDisabled = option.count === 0;
        const isChecked = selectedValues.includes(option.value);
        const id = `filter-${option.value}`;

        return (
          <li key={option.value} className="flex items-center gap-2">
            <Checkbox
              id={id}
              checked={isChecked}
              disabled={isDisabled}
              aria-disabled={isDisabled}
              onCheckedChange={() => !isDisabled && onToggle(option.value)}
            />
            <Label
              htmlFor={id}
              className={`flex flex-1 cursor-pointer items-center justify-between text-sm ${
                isDisabled
                  ? "cursor-not-allowed text-slate-500"
                  : "text-slate-300"
              }`}
            >
              <span>{option.label}</span>
              <span className="text-slate-500">({option.count})</span>
            </Label>
          </li>
        );
      })}
    </ul>
  );
}
