import { z } from "zod";

export const searchParamsSchema = z.object({
  q: z
    .string()
    .max(200)
    .optional()
    .default("")
    .transform((v) => v.trim()),
  page: z.coerce.number().int().min(1).default(1),
  sort: z
    .enum(["relevance", "price_asc", "price_desc", "newest"])
    .default("relevance"),
  category: z.array(z.string()).default([]),
  priceMin: z.coerce.number().optional(),
  priceMax: z.coerce.number().optional(),
});

export type SearchParams = z.infer<typeof searchParamsSchema>;
