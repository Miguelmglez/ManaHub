package com.mmg.magicfolder.core.domain.model

data class MagicSet(
    val code: String,
    val name: String,
    val setType: SetType,
    val releasedAt: String?,
    val cardCount: Int,
    val iconSvgUri: String,
)

enum class SetType(val scryfallValue: String) {
    EXPANSION("expansion"),
    CORE("core"),
    MASTERS("masters"),
    DRAFT_INNOVATION("draft_innovation"),
    COMMANDER("commander"),
    FUNNY("funny"),
    STARTER("starter"),
    BOX("box"),
    PROMO("promo"),
    TOKEN("token"),
    MEMORABILIA("memorabilia"),
    MINIGAME("minigame"),
    OTHER("other");

    companion object {
        fun from(value: String) =
            entries.find { it.scryfallValue == value } ?: OTHER
    }
}

val PLAYABLE_SET_TYPES = setOf(
    SetType.EXPANSION,
    SetType.CORE,
    SetType.MASTERS,
    SetType.DRAFT_INNOVATION,
    SetType.COMMANDER,
    SetType.STARTER,
    SetType.FUNNY,
)
