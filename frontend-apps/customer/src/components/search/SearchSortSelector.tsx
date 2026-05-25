interface SearchSortSelectorProps {
  value: string;
  onChange: (sort: string) => void;
}

export function SearchSortSelector({
  value,
  onChange,
}: SearchSortSelectorProps) {
  return (
    <select
      data-testid="searchSortSelector"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      aria-label="Sort results"
      className="rounded-lg bg-gray-800 px-3 py-2 text-white outline-none focus:ring-2 focus:ring-cyan-500"
    >
      <option value="relevance">Relevance</option>
      <option value="price_asc">Price: Low to High</option>
      <option value="price_desc">Price: High to Low</option>
      <option value="newest">Newest</option>
    </select>
  );
}
