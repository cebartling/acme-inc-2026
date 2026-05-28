interface FilterSectionProps {
  title: string;
  children: React.ReactNode;
}

export function FilterSection({ title, children }: FilterSectionProps) {
  return (
    <details open className="border-b border-slate-700 py-4">
      <summary className="cursor-pointer select-none text-sm font-semibold text-slate-200 uppercase tracking-wide">
        {title}
      </summary>
      <div className="mt-3 space-y-2">{children}</div>
    </details>
  );
}
