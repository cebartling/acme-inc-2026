/** What a page about orders shows while it loads. */
export function OrderPageSkeleton() {
  return (
    <div className="animate-pulse space-y-4" aria-busy="true">
      <div className="h-8 w-1/2 rounded bg-slate-700" />
      <div className="h-16 rounded bg-slate-700" />
    </div>
  );
}
