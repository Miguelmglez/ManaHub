package com.mmg.manahub.core.tagging

import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class TagLocalizationTest {

    @Test
    fun `given basic_land tag when localize in EN then returns Basic Land`() {
        // Arrange
        val tag = CardTag("basic_land", TagCategory.TYPE)

        // Act
        val localized = TagDictionary.localize(tag, "en")

        // Assert
        assertEquals("Basic Land", localized)
    }

    @Test
    fun `given basic_land tag when localize in ES then returns Básica Tierra`() {
        // Arrange
        val tag = CardTag("basic_land", TagCategory.TYPE)

        // Act
        val localized = TagDictionary.localize(tag, "es")

        // Assert
        // "Basic" -> "Básica", "Land" -> "Tierra"
        assertEquals("Básica Tierra", localized)
    }

    @Test
    fun `given basic_land tag when localize in DE then returns Basis Land`() {
        // Arrange
        val tag = CardTag("basic_land", TagCategory.TYPE)

        // Act
        val localized = TagDictionary.localize(tag, "de")

        // Assert
        // "Basic" -> "Basis", "Land" -> "Land"
        assertEquals("Basis Land", localized)
    }

    @Test
    fun `given creature tag when localize in ES then returns Criatura`() {
        // Arrange
        val tag = CardTag("creature", TagCategory.TYPE)

        // Act
        val localized = TagDictionary.localize(tag, "es")

        // Assert
        assertEquals("Criatura", localized)
    }

    @Test
    fun `given legendary_creature tag when localize in ES then returns Legendaria Criatura`() {
        // Arrange
        // This simulates a potential multi-word tag that isn't explicitly in the dictionary
        // but its components are.
        val tag = CardTag("legendary_creature", TagCategory.TYPE)

        // Act
        val localized = TagDictionary.localize(tag, "es")

        // Assert
        assertEquals("Legendaria Criatura", localized)
    }
}
