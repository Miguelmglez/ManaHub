package com.mmg.manahub.feature.decks.domain.engine

// ═══════════════════════════════════════════════════════════════════════════════
//  ColorRoleAffinity — Deck Wizard & Engine Rework plan, Workstream 9.1
//  (docs/plans/deck-wizard-rework-plan.md)
//
//  The color-PIE layer: which [ManaColor] can plausibly fill which [RoleKey], and how well.
//  Nothing before this file modeled this -- [ArchetypeSkeletonResolver] demanded roles purely by
//  archetype/theme, with zero awareness that (say) a Selesnya (G/W) deck has essentially no
//  access to `counterspell`. This table is pure data (`commonMain`, zero engine-behavior side
//  effects on its own) -- [ArchetypeSkeletonResolver.resolveWithColor] is the sole consumer that
//  turns it into actual skeleton changes (WS9.2).
//
//  Citations are transcribed from the plan's own WS9.1 researched matrix (plan lines ~383-395,
//  gathered 2026-07-27) for the 9 literal plan roles. A handful of well-known, low-risk EXTRAS
//  (graveyard_hate/token_generator/wheel) are added beyond the plan's literal table where the
//  color-pie judgement is common knowledge (Wizards' own published color-pie articles / standard
//  EDH deckbuilding convention) rather than a guess -- deliberately NOT extended to every ~40
//  [RoleKey] in the engine; an untabled role is a real "no color-pie judgement recorded" signal,
//  see [isTabled].
//
//  "Artifact/enchant removal" (a distinct plan row) does NOT get its own entry in the resolver's
//  live vocabulary: [ArchetypeRoleClassifier] has no dedicated RoleKey for it (it folds under the
//  same `removal_spot` bucket the legacy [DeckRole.SPOT_REMOVAL] mapping uses -- see that file's
//  ROLE_SPECS). Rather than lossily merging two DIFFERENT per-color rows into one key (the plan's
//  own numbers actively disagree for Black and Green between "kill a creature" and "destroy an
//  enchantment"), this file tables it under a SEPARATE, not-yet-wired key
//  ("removal_artifact_enchant") purely for documentation/future use -- WS9.2's redistribution loop
//  only ever iterates [ResolvedArchetypeSkeleton.roleTargets]' actual keys, and no
//  [ArchetypeData] band uses this key today, so it is completely inert in this run (an
//  extension point for Batch G/WS9.5, once/if a dedicated RoleSpec exists).
// ═══════════════════════════════════════════════════════════════════════════════

object ColorRoleAffinity {

    /** How well a color can fill a given role, from "this is what the color is FOR" down to "the
     * color pie gives this color nothing here." */
    enum class ColorAffinityLevel { PRIMARY, SECONDARY, SUBSTITUTE, ABSENT }

    /**
     * One color/role cell. [substituteShape] is only meaningful when [level] is [ColorAffinityLevel
     * .SUBSTITUTE] -- a short description of what the substitute effect actually looks like (e.g.
     * "bounce/steal" for Blue's answer to spot removal), since a SUBSTITUTE is real but shaped
     * differently than the role's usual execution. [citation] is a one-line justification,
     * transcribed from the plan's researched matrix where the role is one of the plan's literal 9,
     * or a standard color-pie citation for the documented extras.
     */
    data class Entry(
        val level: ColorAffinityLevel,
        val substituteShape: String? = null,
        val citation: String,
    )

    /**
     * WS9.2's redistribution recipient pool: the plan's own suggested "interaction" role subset
     * (docs/plans/deck-wizard-rework-plan.md WS9.2: "likely removal_spot/removal_mass/
     * counterspell/protection/recursion"). When an identity can't support one of these roles, its
     * demand is redistributed onto whichever OTHER members of this same set the identity CAN
     * support -- interaction budget moves within the interaction family, it never leaks into
     * value-engine roles (ramp/card_draw/tutor) which have a different color-pie shape.
     */
    val INTERACTION_ROLES: Set<RoleKey> = setOf(
        "removal_spot", "removal_mass", "counterspell", "protection", "recursion",
    )

    private val TABLE: Map<RoleKey, Map<ManaColor, Entry>> = mapOf(
        // ── Counterspells (plan WS9.1 row 1) ────────────────────────────────────────────────
        "counterspell" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.ABSENT, citation = "White has only minor, incidental tax effects -- countering spells outright is not part of its pie (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.PRIMARY, citation = "Countering spells is Blue's single defining color-pie mechanic (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.ABSENT, citation = "No meaningful counterspell tradition in Black (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.ABSENT, citation = "No meaningful counterspell tradition in Red (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.ABSENT, citation = "No meaningful counterspell tradition in Green (plan WS9.1)."),
        ),
        // ── Spot removal (plan WS9.1 row 2 -- creature/permanent-focused) ───────────────────────
        "removal_spot" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.PRIMARY, citation = "Exile/conditional removal (banish effects) is a White staple (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "bounce/steal", citation = "Blue answers threats by bouncing or stealing them rather than destroying them (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.PRIMARY, citation = "Unconditional 'kill' effects are Black's defining removal shape (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.SECONDARY, citation = "Damage-based removal is real but struggles against big-toughness threats (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "fight/bite", citation = "Green answers threats via fight effects, conditional on its own creature's stats (plan WS9.1)."),
        ),
        // ── Board wipes (plan WS9.1 row 3) ──────────────────────────────────────────────────────
        "removal_mass" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.PRIMARY, citation = "Symmetrical board wipes are a defining White effect (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "mass bounce", citation = "Blue's closest analogue to a wipe is returning everything to hand, not destroying it (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.SECONDARY, citation = "Black has real edict/-X/-X wipes but they are not its primary identity (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.SECONDARY, citation = "Damage-based sweepers exist but skew toward small creatures only (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.ABSENT, citation = "Green has essentially no board-wipe tradition (plan WS9.1)."),
        ),
        // ── Card advantage (plan WS9.1 row 4) ───────────────────────────────────────────────────
        "card_draw" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "small-creature/tax draw", citation = "White's draw is real but narrow (go-wide triggers, taxed instants), not its pie identity (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.PRIMARY, citation = "Unrestricted card draw is Blue's defining resource (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.PRIMARY, citation = "Life-payment card draw is a full-fledged Black staple (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.SECONDARY, citation = "Impulsive draw/rummaging is real but comes with a strings-attached cost (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.SECONDARY, citation = "Green's draw is creature-count/body-based, a real but secondary source (plan WS9.1)."),
        ),
        // ── Ramp (plan WS9.1 row 5) ─────────────────────────────────────────────────────────────
        "ramp" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "catch-up effects", citation = "White's mana help is a catch-up mechanic, not a ramp identity (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.ABSENT, citation = "Blue has no ramp tradition (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "rituals", citation = "Black's mana acceleration is one-shot ritual effects, not sustained ramp (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "rituals/treasure", citation = "Red's acceleration is similarly burst-shaped (rituals, Treasure) rather than sustained (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.PRIMARY, citation = "Lands-and-dorks ramp is Green's defining mechanic -- colorless rocks are universal and don't change this (plan WS9.1)."),
        ),
        // ── Tutors (plan WS9.1 row 6) ───────────────────────────────────────────────────────────
        "tutor" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.SECONDARY, citation = "White tutors skew toward equipment/enchantment fetch, a real but narrow slice (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.SECONDARY, citation = "Blue tutors for spells/instants, real but narrower than Black's universal fetch (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.PRIMARY, citation = "Unrestricted tutoring for any card is Black's defining search identity (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "impulse", citation = "Red's 'search' is impulsive card selection (exile-and-play), not a true tutor (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.SECONDARY, citation = "Green tutors for creatures specifically, a real but narrower slice (plan WS9.1)."),
        ),
        // ── Artifact/enchantment removal (plan WS9.1 row 7) -- NOT wired into ArchetypeRoleClassifier
        // today (see file header); tabled for documentation + Batch G/WS9.5 forward use only.
        "removal_artifact_enchant" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.PRIMARY, citation = "Naturalize-style universal artifact/enchantment removal is a White staple (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "counter/bounce", citation = "Blue answers artifacts/enchantments preemptively (counter) or temporarily (bounce), not by destroying them (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.ABSENT, citation = "Enchantment removal is a classic, explicitly documented Black weakness (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.SECONDARY, citation = "Red gets real artifact destruction (Shatter effects) but no enchantment removal (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.PRIMARY, citation = "Naturalize-style universal artifact/enchantment removal is also a Green staple (plan WS9.1)."),
        ),
        // ── Protection (plan WS9.1 row 8) ───────────────────────────────────────────────────────
        "protection" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.PRIMARY, citation = "Indestructible/hexproof-granting protection is a defining White effect (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.SECONDARY, citation = "Counterspell-as-protection and phasing are real but secondary uses of Blue's toolkit (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.SECONDARY, citation = "Recursion-as-resilience (bring it back after it dies) is Black's indirect answer to protection (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.ABSENT, citation = "Red has no protection tradition (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.SECONDARY, citation = "Hexproof-granting effects give Green a real, secondary protection angle (plan WS9.1)."),
        ),
        // ── Recursion (plan WS9.1 row 9) ────────────────────────────────────────────────────────
        "recursion" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.SECONDARY, citation = "White recursion is real (return-to-hand/battlefield effects) but secondary to Black's (plan WS9.1)."),
            ManaColor.U to Entry(ColorAffinityLevel.ABSENT, citation = "Blue has no graveyard-recursion tradition (plan WS9.1)."),
            ManaColor.B to Entry(ColorAffinityLevel.PRIMARY, citation = "Reanimation/graveyard recursion is Black's defining resource-recycling mechanic (plan WS9.1)."),
            ManaColor.R to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "impulse from exile", citation = "Red's closest analogue is playing cards back from an impulse-draw exile zone, not the graveyard (plan WS9.1)."),
            ManaColor.G to Entry(ColorAffinityLevel.SECONDARY, citation = "Green recursion (creature-focused) is real but secondary to Black's (plan WS9.1)."),
        ),
        // ── Extras beyond the plan's literal 9 (well-known, low-risk color-pie calls only) ──────
        "graveyard_hate" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.PRIMARY, citation = "Exile-based graveyard hate (Rest in Peace-style effects) is a documented White staple."),
            ManaColor.U to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "bounce/mill-adjacent", citation = "Blue's graveyard interaction is incidental (bounce, some mill) rather than dedicated hate."),
            ManaColor.B to Entry(ColorAffinityLevel.PRIMARY, citation = "Black gets dedicated graveyard-exile effects alongside White (both colors are documented as primary here)."),
            ManaColor.R to Entry(ColorAffinityLevel.ABSENT, citation = "Red has no graveyard-hate tradition."),
            ManaColor.G to Entry(ColorAffinityLevel.SECONDARY, citation = "Green gets some exile-graveyard creatures, a real but secondary slice."),
        ),
        "token_generator" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.PRIMARY, citation = "Go-wide token generation is a defining White strategy pillar."),
            ManaColor.U to Entry(ColorAffinityLevel.ABSENT, citation = "Blue has essentially no token-generation identity."),
            ManaColor.B to Entry(ColorAffinityLevel.SECONDARY, citation = "Black token generation exists (armies of the dead/thopters) but is secondary to W/G."),
            ManaColor.R to Entry(ColorAffinityLevel.SECONDARY, citation = "Red token generation (goblins/elementals) is real but secondary to W/G."),
            ManaColor.G to Entry(ColorAffinityLevel.PRIMARY, citation = "Go-wide token generation is also a defining Green strategy pillar."),
        ),
        "wheel" to mapOf(
            ManaColor.W to Entry(ColorAffinityLevel.ABSENT, citation = "White has no wheel-effect tradition."),
            ManaColor.U to Entry(ColorAffinityLevel.SECONDARY, citation = "Blue gets real 'draw a fresh hand' effects (Windfall-style) but Red owns the identity."),
            ManaColor.B to Entry(ColorAffinityLevel.SUBSTITUTE, substituteShape = "forced discard", citation = "Black's forced-discard effects are adjacent to wheels without the symmetric refill."),
            ManaColor.R to Entry(ColorAffinityLevel.PRIMARY, citation = "Wheel of Fortune-style symmetric hand refills are Red's defining identity here."),
            ManaColor.G to Entry(ColorAffinityLevel.ABSENT, citation = "Green has no wheel-effect tradition."),
        ),
    )

    /**
     * `true` when [role] has a recorded color-pie judgement at all. A `false` here is a genuine
     * "we never researched this role" signal -- distinct from a tabled role's [ABSENT][ColorAffinityLevel
     * .ABSENT] cell, which means "this role IS researched, and this specific color has nothing
     * for it." Callers that only want to act on researched roles (WS9.2's redistribution loop)
     * MUST check this before trusting [affinity]'s [ColorAffinityLevel.ABSENT] as a real signal.
     */
    fun isTabled(role: RoleKey): Boolean = TABLE.containsKey(role)

    /** The full cell (level + substitute shape + citation), or `null` when [role] isn't tabled at
     * all, or [color] is [ManaColor.C] (colorless carries no role affinity -- it is the absence
     * of color, not a color with its own pie slice). */
    fun entryFor(color: ManaColor, role: RoleKey): Entry? = TABLE[role]?.get(color)

    /**
     * The affinity level for (color, role). Returns [ColorAffinityLevel.ABSENT] both for a
     * genuinely-tabled-but-absent cell AND for an untabled role -- see [isTabled] to distinguish
     * "no data" from "real absence" when that distinction matters to the caller.
     */
    fun affinity(color: ManaColor, role: RoleKey): ColorAffinityLevel = entryFor(color, role)?.level ?: ColorAffinityLevel.ABSENT

    /** Every color at [ColorAffinityLevel.PRIMARY] for [role]. Empty only for an untabled role --
     * every role actually tabled here carries at least one PRIMARY color by construction (see
     * `ColorRoleAffinityTest`'s invariant check). */
    fun bestColorsFor(role: RoleKey): Set<ManaColor> =
        TABLE[role]?.filterValues { it.level == ColorAffinityLevel.PRIMARY }?.keys ?: emptySet()

    /**
     * `true` when at least one color in [identity] has [ColorAffinityLevel.PRIMARY] or
     * [ColorAffinityLevel.SECONDARY] access to [role] -- "fully feasible" per WS9.2's terminology.
     * An untabled [role] (no color-pie judgement recorded) is always feasible -- absence of data
     * must never block a role WS9.2 has no basis to judge. A [ColorAffinityLevel.SUBSTITUTE]-only
     * identity is "reduced but real," NOT counted as feasible here -- see [isSubstituteOnly].
     */
    fun isFeasible(identity: Set<ManaColor>, role: RoleKey): Boolean {
        if (!isTabled(role)) return true
        return identity.any { color ->
            val level = affinity(color, role)
            level == ColorAffinityLevel.PRIMARY || level == ColorAffinityLevel.SECONDARY
        }
    }

    /** `true` when [role] is NOT [isFeasible] for [identity], but at least one identity color
     * still reaches it at [ColorAffinityLevel.SUBSTITUTE] -- "reduced but real" access (WS9.2
     * shrinks, rather than zeroes, this band). */
    fun isSubstituteOnly(identity: Set<ManaColor>, role: RoleKey): Boolean {
        if (!isTabled(role) || isFeasible(identity, role)) return false
        return identity.any { affinity(it, role) == ColorAffinityLevel.SUBSTITUTE }
    }
}
