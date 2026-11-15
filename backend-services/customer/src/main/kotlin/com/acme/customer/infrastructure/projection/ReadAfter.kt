package com.acme.customer.infrastructure.projection

/**
 * The header that makes a read wait for the read model to catch up with a write.
 *
 * A write's response carries it, holding the id of the event that write produced. A client sends it
 * back on a read to see that write; see [CustomerReadModelQueries].
 */
object ReadAfter {
    const val HEADER = "X-Read-After"
}
