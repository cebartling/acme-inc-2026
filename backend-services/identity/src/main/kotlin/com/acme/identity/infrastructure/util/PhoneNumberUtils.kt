package com.acme.identity.infrastructure.util

/**
 * Utility functions for phone number handling.
 */
object PhoneNumberUtils {

    /**
     * Masks a phone number for display, showing only the last 4 digits.
     *
     * Examples:
     * - +15551234567 -> ***-***-4567
     * - 5551234567 -> ***-***-4567
     * - 123 -> ***-***-**** (edge case: fewer than 4 digits)
     *
     * @param phone The phone number to mask.
     * @return The masked phone number in format ***-***-XXXX.
     */
    fun mask(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        // Handle edge case where phone has fewer than 4 digits
        if (digits.length < 4) return "***-***-****"
        val lastFour = digits.takeLast(4)
        return "***-***-$lastFour"
    }

    /**
     * Validates that a phone number is in E.164 format.
     *
     * E.164 format: + followed by 1-15 digits, starting with country code (1-9).
     * Examples: +15551234567, +442071234567
     *
     * @param phoneNumber The phone number to validate.
     * @return true if the phone number is valid E.164 format.
     */
    fun isValidE164(phoneNumber: String): Boolean {
        return phoneNumber.matches(Regex("^\\+[1-9]\\d{1,14}$"))
    }
}
