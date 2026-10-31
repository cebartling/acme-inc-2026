import { z } from "zod";

/** The order service's limit. */
const MAX_PAGE = 10_000;

/**
 * A page of the order history in a URL. Routes add their own `.catch`: a bad `page` must fall back,
 * because `validateSearch` throwing takes down the route.
 */
export const orderHistoryPage = z.coerce.number().int().min(1).max(MAX_PAGE);
