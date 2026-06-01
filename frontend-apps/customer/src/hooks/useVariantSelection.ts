import { useState, useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import type {
  ProductDetail,
  ProductVariant,
  VariantAvailability,
  VariantPrice,
} from "@/services/api";
import { inventoryApi, pricingApi } from "@/services/api";
import { trackVariantSelected } from "@/services/analytics";

export interface UseVariantSelectionResult {
  selectedVariant: ProductVariant | undefined;
  selectedVariantId: string | undefined;
  setSelectedVariantId: (id: string) => void;
  availability: VariantAvailability | undefined;
  price: VariantPrice | undefined;
  isLoading: boolean;
  isError: boolean;
}

export function useVariantSelection(
  product: ProductDetail,
): UseVariantSelectionResult {
  const defaultVariant =
    product.variants.find((v) => v.isDefault) ?? product.variants[0];

  const [selectedVariantId, setSelectedVariantId] = useState<
    string | undefined
  >(defaultVariant?.id);

  const isFirstRender = useRef(true);

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    if (selectedVariantId) {
      trackVariantSelected({
        productId: product.id,
        variantId: selectedVariantId,
      });
    }
  }, [selectedVariantId, product.id]);

  const availabilityQuery = useQuery({
    queryKey: ["availability", selectedVariantId],
    queryFn: () => inventoryApi.getAvailability(selectedVariantId!),
    enabled: !!selectedVariantId,
  });

  const priceQuery = useQuery({
    queryKey: ["price", selectedVariantId],
    queryFn: () => pricingApi.getPrice(selectedVariantId!),
    enabled: !!selectedVariantId,
  });

  const selectedVariant = product.variants.find(
    (v) => v.id === selectedVariantId,
  );

  return {
    selectedVariant,
    selectedVariantId,
    setSelectedVariantId,
    availability: availabilityQuery.data,
    price: priceQuery.data,
    isLoading: availabilityQuery.isLoading || priceQuery.isLoading,
    isError: availabilityQuery.isError || priceQuery.isError,
  };
}
