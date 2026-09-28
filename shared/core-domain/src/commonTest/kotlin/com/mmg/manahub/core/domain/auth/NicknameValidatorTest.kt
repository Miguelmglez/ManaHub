package com.mmg.manahub.core.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class NicknameValidatorTest {

    @Test
    fun blankIsRequired() {
        assertEquals(NicknameValidationResult.REQUIRED, NicknameValidator.validate("   "))
        assertEquals(NicknameValidationResult.REQUIRED, NicknameValidator.validate(""))
    }

    @Test
    fun surroundingSpacesAreTrimmedBeforeValidation() {
        assertEquals(NicknameValidationResult.VALID, NicknameValidator.validate("  Jace  "))
        assertEquals("Jace", NicknameValidator.normalize("  Jace  "))
    }

    @Test
    fun thirtyCharactersIsValidAndThirtyOneIsTooLong() {
        assertEquals(NicknameValidationResult.VALID, NicknameValidator.validate("a".repeat(30)))
        assertEquals(NicknameValidationResult.TOO_LONG, NicknameValidator.validate("a".repeat(31)))
    }

    @Test
    fun accentsAndDotsAreRejectedLikeTheServerBoundCheck() {
        assertEquals(NicknameValidationResult.INVALID_CHARACTERS, NicknameValidator.validate("José"))
        assertEquals(NicknameValidationResult.INVALID_CHARACTERS, NicknameValidator.validate("J.R."))
    }

    @Test
    fun apostrophesHyphensUnderscoresAndDigitsAreValid() {
        assertEquals(NicknameValidationResult.VALID, NicknameValidator.validate("O'Neil-the_2nd"))
    }
}
