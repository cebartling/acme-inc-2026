import { useEffect, useState } from "react";

interface SearchBarProps {
  defaultValue?: string;
  onSearch: (query: string) => void;
}

export function SearchBar({ defaultValue = "", onSearch }: SearchBarProps) {
  const [value, setValue] = useState(defaultValue);

  useEffect(() => {
    setValue(defaultValue);
  }, [defaultValue]);

  const handleSubmit = () => {
    onSearch(value);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      handleSubmit();
    }
  };

  return (
    <div className="flex gap-2">
      <div className="relative flex-1">
        <input
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Search products…"
          aria-label="Search products"
          data-testid="searchInput"
          className="w-full rounded-lg bg-gray-800 px-4 py-2 text-white placeholder-gray-400 outline-none focus:ring-2 focus:ring-cyan-500"
        />
        {value.length > 0 && (
          <button
            type="button"
            onClick={() => setValue("")}
            data-testid="searchClearButton"
            className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white"
            aria-label="Clear search"
          >
            &times;
          </button>
        )}
      </div>
      <button
        type="button"
        onClick={handleSubmit}
        data-testid="searchSubmitButton"
        className="rounded-lg bg-cyan-600 px-4 py-2 font-medium text-white hover:bg-cyan-700 transition-colors"
      >
        Search
      </button>
    </div>
  );
}
