package com.acme.customer.domain

/**
 * Enum representing the type of address.
 *
 * Addresses can be categorized as either shipping addresses (for deliveries)
 * or billing addresses (for payment and invoicing purposes). A customer
 * can have multiple addresses of each type.
 */
enum class AddressType {
    /**
     * Address used for shipping/delivery of orders.
     * Note: PO Box addresses cannot be used for shipping.
     */
    SHIPPING,

    /**
     * Address used for billing/invoicing purposes.
     * PO Box addresses are allowed for billing.
     */
    BILLING
}
