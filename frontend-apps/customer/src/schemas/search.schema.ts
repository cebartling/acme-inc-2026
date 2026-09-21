import { z } from "zod";

/**
 * Longest query the product service accepts — its SearchRequest declares
 * `@field:Size(max = 200)`. Keep the two in step.
 */
export const SEARCH_QUERY_MAX_LENGTH = 200;

export const searchParamsSchema = z.object({
  // Truncated rather than rejected. `validateSearch` throwing takes down the whole
  // route: TanStack Router renders its "Something went wrong!" boundary, which is a
  // generic error page shown for a search — exactly what AC-0004-09-06 forbids, and
  // reachable just by pasting a long string into the search box. Trimming to the
  // service's limit degrades to a search the customer can still act on.
  q: z
    .string()
    .optional()
    .default("")
    .transform((v) => v.trim().slice(0, SEARCH_QUERY_MAX_LENGTH)),
  page: z.coerce.number().int().min(1).default(1),
  sort: z
    .enum(["relevance", "price_asc", "price_desc", "newest"])
    .default("relevance"),
  category: z.array(z.string()).default([]),
  priceMin: z.coerce.number().min(0).optional(),
  priceMax: z.coerce.number().min(0).optional(),
});

export type SearchParams = z.infer<typeof searchParamsSchema>;
