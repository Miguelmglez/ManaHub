package com.mmg.manahub.core.domain.auth

/** Outcome of [NicknameValidator.validate]; the UI maps each case to its own message. */
enum class NicknameValidationResult {
    VALID,
    REQUIRED,
    TOO_LONG,
    INVALID_CHARACTERS,
}

/**
 * The single client-side nickname rule, shared by sign-up, Account and the Profile edit sheet.
 *
 * The input is trimmed first; the trimmed value is what gets saved and sent to the server.
 */
object NicknameValidator {

    /** Maximum nickname length after trimming. */
    const val MAX_LENGTH: Int = 30

    // ASCII word characters, spaces, apostrophes and hyphens; mirrors what the server accepts today.
    private val PATTERN = Regex("^[\\w\\s'\\-]{1,$MAX_LENGTH}$")

    /** Returns the canonical form of [raw] that is validated, stored and pushed. */
    fun normalize(raw: String): String = raw.trim()

    /** Validates [raw] after [normalize]. */
    fun validate(raw: String): NicknameValidationResult {
        val trimmed = normalize(raw)
        return when {
            trimmed.isEmpty() -> NicknameValidationResult.REQUIRED
            trimmed.length > MAX_LENGTH -> NicknameValidationResult.TOO_LONG
            !PATTERN.matches(trimmed) -> NicknameValidationResult.INVALID_CHARACTERS
            else -> NicknameValidationResult.VALID
        }
    }
}
