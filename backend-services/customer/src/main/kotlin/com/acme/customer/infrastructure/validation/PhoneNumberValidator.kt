package com.acme.customer.infrastructure.validation

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.springframework.stereotype.Component

/**
 * Result of phone number validation.
 */
sealed class PhoneValidationResult {
    /**
     * Phone number is valid.
     *
     * @property formattedNumber The phone number formatted in E.164 format.
     * @property countryCode The detected country code (e.g., "+1").
     * @property nationalNumber The national number portion.
     */
    data class Valid(
        val formattedNumber: String,
        val countryCode: String,
        val nationalNumber: String
    ) : PhoneValidationResult()

    /**
     * Phone number is invalid.
     *
     * @property message Human-readable error message.
     */
    data class Invalid(val message: String) : PhoneValidationResult()
}

/**
 * Validates phone numbers using Google's libphonenumber library.
 *
 * This component provides robust phone number validation that works
 * across all countries and phone number formats.
 */
@Component
class PhoneNumberValidator {
    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * Validates a phone number with country code.
     *
     * @param countryCode The country code (e.g., "+1", "1", or "US").
     * @param number The phone number (can include formatting).
     * @return [PhoneValidationResult.Valid] if valid, [PhoneValidationResult.Invalid] otherwise.
     */
    fun validate(countryCode: String, number: String): PhoneValidationResult {
        return try {
            // Normalize the country code
            val normalizedCountryCode = normalizeCountryCode(countryCode)

            // Parse the phone number
            val fullNumber = "$normalizedCountryCode$number"
            val phoneNumber = phoneUtil.parse(fullNumber, null)

            if (phoneUtil.isValidNumber(phoneNumber)) {
                val formattedNumber = phoneUtil.format(
                    phoneNumber,
                    PhoneNumberUtil.PhoneNumberFormat.E164
                )
                PhoneValidationResult.Valid(
                    formattedNumber = formattedNumber,
                    countryCode = "+${phoneNumber.countryCode}",
                    nationalNumber = phoneNumber.nationalNumber.toString()
                )
            } else {
                PhoneValidationResult.Invalid("Invalid phone number format for the specified country")
            }
        } catch (e: NumberParseException) {
            when (e.errorType) {
                NumberParseException.ErrorType.INVALID_COUNTRY_CODE ->
                    PhoneValidationResult.Invalid("Invalid country code")
                NumberParseException.ErrorType.NOT_A_NUMBER ->
                    PhoneValidationResult.Invalid("The input does not appear to be a phone number")
                NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD ->
                    PhoneValidationResult.Invalid("Phone number is too short")
                NumberParseException.ErrorType.TOO_SHORT_NSN ->
                    PhoneValidationResult.Invalid("Phone number is too short")
                NumberParseException.ErrorType.TOO_LONG ->
                    PhoneValidationResult.Invalid("Phone number is too long")
                else ->
                    PhoneValidationResult.Invalid("Unable to parse phone number")
            }
        }
    }

    /**
     * Validates a phone number using the default region for parsing.
     *
     * @param number The phone number (should include country code).
     * @param defaultRegion The default region code (e.g., "US") for parsing.
     * @return [PhoneValidationResult.Valid] if valid, [PhoneValidationResult.Invalid] otherwise.
     */
    fun validateWithRegion(number: String, defaultRegion: String): PhoneValidationResult {
        return try {
            val phoneNumber = phoneUtil.parse(number, defaultRegion)

            if (phoneUtil.isValidNumber(phoneNumber)) {
                val formattedNumber = phoneUtil.format(
                    phoneNumber,
                    PhoneNumberUtil.PhoneNumberFormat.E164
                )
                PhoneValidationResult.Valid(
                    formattedNumber = formattedNumber,
                    countryCode = "+${phoneNumber.countryCode}",
                    nationalNumber = phoneNumber.nationalNumber.toString()
                )
            } else {
                PhoneValidationResult.Invalid("Invalid phone number format")
            }
        } catch (e: NumberParseException) {
            PhoneValidationResult.Invalid("Unable to parse phone number: ${e.message}")
        }
    }

    /**
     * Normalizes the country code to include the "+" prefix.
     */
    private fun normalizeCountryCode(countryCode: String): String {
        val trimmed = countryCode.trim()
        return if (trimmed.startsWith("+")) {
            trimmed
        } else {
            "+$trimmed"
        }
    }
}
