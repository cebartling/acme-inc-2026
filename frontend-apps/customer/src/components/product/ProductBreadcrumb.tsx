import { Link } from "@tanstack/react-router";

interface ProductBreadcrumbProps {
  category: string | null;
  productName: string;
}

export function ProductBreadcrumb({
  category,
  productName,
}: ProductBreadcrumbProps) {
  return (
    <nav aria-label="Breadcrumb" className="mb-6">
      <ol className="flex flex-wrap items-center gap-1.5 text-sm text-slate-400">
        <li>
          <Link to="/" className="hover:text-slate-200">
            Home
          </Link>
        </li>
        <li aria-hidden="true" className="text-slate-600">
          /
        </li>
        <li>
          <Link
            to="/search"
            search={{ q: category ?? "", page: 1, sort: "relevance", category: category ? [category] : [] }}
            className="hover:text-slate-200"
          >
            {category ?? "Products"}
          </Link>
        </li>
        <li aria-hidden="true" className="text-slate-600">
          /
        </li>
        <li className="text-slate-200" aria-current="page">
          {productName}
        </li>
      </ol>
    </nav>
  );
}
