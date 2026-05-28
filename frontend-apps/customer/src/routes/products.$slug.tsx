import { createFileRoute, Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { ProductDetailPage } from "@/components/product/ProductDetailPage";
import { productApi } from "@/services/api";
import { trackProductViewed } from "@/services/analytics";

export const Route = createFileRoute("/products/$slug")({
  component: ProductPage,
});

function ProductPage() {
  const { slug } = Route.useParams();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["product", slug],
    queryFn: () => productApi.getProduct(slug),
  });

  useEffect(() => {
    if (!data) return;
    trackProductViewed({ productId: data.id, slug: data.slug, source: "WEB" });
  }, [data]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-8 px-4">
        <div className="mx-auto max-w-5xl">
          <div className="animate-pulse space-y-4">
            <div className="h-4 w-1/3 rounded bg-slate-700" />
            <div className="h-8 w-2/3 rounded bg-slate-700" />
            <div className="h-6 w-1/4 rounded bg-slate-700" />
            <div className="h-32 rounded bg-slate-700" />
          </div>
        </div>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 py-8 px-4">
        <div className="mx-auto max-w-5xl text-center">
          <h1 className="mb-4 text-2xl font-bold text-white">Product not found</h1>
          <p className="mb-6 text-slate-400">
            The product you&apos;re looking for doesn&apos;t exist or has been removed.
          </p>
          <Link to="/search" search={{ q: "", page: 1, sort: "relevance", category: [] }} className="text-indigo-400 hover:text-indigo-300">
            Back to search
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900">
      <ProductDetailPage product={data} />
    </div>
  );
}
