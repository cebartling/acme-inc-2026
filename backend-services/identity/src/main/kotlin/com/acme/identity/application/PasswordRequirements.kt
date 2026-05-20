package com.acme.identity.application

/**
 * Single requirement check result. Surfaced in API responses so the
 * client can render a checklist of unmet rules per US-0003-13 AC-05.
 */
data class PasswordRequirement(
    val rule: String,
    val met: Boolean,
    val detail: String
)

/**
 * Stateless validator for new-password requirements. Used by the
 * password-reset confirm flow; intentionally not wired into existing
 * change-password yet (a separate refactor).
 */
object PasswordRequirements {
    const val MIN_LENGTH = 8

    fun check(password: String): List<PasswordRequirement> = listOf(
        PasswordRequirement(
            rule = "MIN_LENGTH",
            met = password.length >= MIN_LENGTH,
            detail = "At least $MIN_LENGTH characters"
        ),
        PasswordRequirement(
            rule = "UPPERCASE",
            met = password.any { it.isUpperCase() },
            detail = "At least one uppercase letter"
        ),
        PasswordRequirement(
            rule = "LOWERCASE",
            met = password.any { it.isLowerCase() },
            detail = "At least one lowercase letter"
        ),
        PasswordRequirement(
            rule = "DIGIT",
            met = password.any { it.isDigit() },
            detail = "At least one digit"
        ),
        PasswordRequirement(
            rule = "SPECIAL",
            met = password.any { !it.isLetterOrDigit() },
            detail = "At least one special character"
        )
    )

    fun isValid(password: String): Boolean = check(password).all { it.met }
}
