import { createFileRoute } from "@tanstack/react-router";
import { CartPage } from "@/components/cart/CartPage";

export const Route = createFileRoute("/cart")({
  component: CartRoute,
});

function CartRoute() {
  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-900 via-slate-800 to-slate-900 px-4 py-8">
      <div className="mx-auto max-w-5xl">
        <CartPage />
      </div>
    </div>
  );
}
