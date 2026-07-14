package com.mmg.manahub.feature.home.presentation

import com.mmg.manahub.R

/**
 * Categorization for the Home dashboard's Rules Tip catalog (Home feature overhaul Phase 2.4).
 *
 * Each category has a short, English display label resolved from [labelRes] — never hardcoded
 * (F-11). Order here is purely a presentation grouping; it has no bearing on daily selection.
 */
enum class TipCategory(val labelRes: Int) {
    FUNDAMENTALS(R.string.tip_category_fundamentals),
    STACK_TIMING(R.string.tip_category_stack_timing),
    COMBAT(R.string.tip_category_combat),
    KEYWORDS(R.string.tip_category_keywords),
    GAME_STATE(R.string.tip_category_game_state),
    COMMANDER(R.string.tip_category_commander),
    TOKENS_COUNTERS(R.string.tip_category_tokens_counters),
    DECKBUILDING(R.string.tip_category_deckbuilding),
    MODERN_MECHANICS(R.string.tip_category_modern_mechanics),
}

/**
 * A single daily rules tip. [body] may contain inline mana/tap symbol syntax (`{T}`, `{W}`,
 * `{2}`, `{C}`, …) — [RulesTipWidget] renders it through [com.mmg.manahub.core.ui.components.OracleText],
 * which turns that syntax into icons.
 */
data class RuleTip(
    val title: String,
    val body: String,
    val category: TipCategory,
)

/**
 * The full Rules Tip catalog (Home feature overhaul Phase 2.4).
 *
 * Merges the original 62 tips (each fact-checked and assigned a category; near-duplicates of the
 * richer Appendix A tips below were dropped rather than kept side-by-side with a redundant
 * restatement) with the ~110-tip Appendix A catalog from the overhaul plan doc, verified against
 * the Comprehensive Rules current as of late 2025. The MODERN_MECHANICS entries were additionally
 * spot-checked against live rulings/reminder text for the three least-familiar mechanics
 * (Mobilize, Station, Harmonize) before shipping, per the accuracy-gate requirement.
 *
 * Deliberately NOT padded to hit a round number — every entry here earns its place with a
 * non-redundant insight. Current size is documented in [MTG_TIPS_CATALOG]'s call site
 * (`RulesTipWidget`); a future catalog test should assert against the real count rather than an
 * assumed one.
 */
val MTG_TIPS_CATALOG: List<RuleTip> = buildList {
    // ═══════════════════════════════════════════════════════════════════════
    //  FUNDAMENTALS
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("One land per turn", "You may play one land per turn, only during one of your main phases, only when the stack is empty. Playing a land doesn't use the stack, so it can't be responded to.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Mana doesn't carry over", "Your mana pool empties at the end of every step and phase, not just at the end of the turn. Floating mana from your upkeep is gone by your draw step.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Two main phases", "You have a main phase before and after combat. Casting sorceries after combat often gives you more information about how the turn went.", TipCategory.FUNDAMENTALS))
    add(RuleTip("The untap step is silent", "No player receives priority during the untap step. Abilities that trigger \"at the beginning of your untap step\" actually wait and go on the stack during upkeep.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Summoning sickness, precisely", "A creature you haven't controlled since your turn began can't attack or use abilities with {T} or {Q} in the cost. It can still block and use all its other abilities.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Tapped creatures still bite", "A tapped creature can't block, but it deals and receives combat damage normally, and its static abilities keep working.", TipCategory.FUNDAMENTALS))
    add(RuleTip("First player skips a draw — sometimes", "In a two-player game the starting player skips their first draw step. In multiplayer games like Commander, everyone draws on turn one.", TipCategory.FUNDAMENTALS))
    add(RuleTip("The London mulligan", "Each mulligan, you shuffle and draw seven again, then put one card on the bottom of your library for each mulligan you've taken. In multiplayer Commander, the first mulligan is customarily free.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Maximum hand size", "You only discard down to seven during your own cleanup step. During the turn you can hold any number of cards.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Cast vs. play", "You \"cast\" spells and \"play\" lands. Effects that care about casting (like storm or magecraft) never trigger from playing a land.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Beginning of combat matters", "\"Beginning of combat\" is a step before attackers are declared. It's your last window to tap down a creature you fear will attack.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Three ways to lose", "You lose the game if your life is 0 or less, you try to draw from an empty library, or you have ten or more poison counters — in any format, including Commander.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Mana Burn is gone", "Mana burn was removed in Magic 2010. Unused mana in your mana pool at end of step or phase simply disappears — it doesn't cause damage.", TipCategory.FUNDAMENTALS))
    add(RuleTip("Colorless vs. Generic", "Generic mana ({1}, {2}) can be paid by any mana. Colorless mana ({C}) can ONLY be paid by colorless sources — they're different!", TipCategory.FUNDAMENTALS))

    // ═══════════════════════════════════════════════════════════════════════
    //  STACK_TIMING
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("Last in, first out", "The stack resolves top-down: the most recently cast spell resolves first. \"In response\" always means going on top.", TipCategory.STACK_TIMING))
    add(RuleTip("Priority passes for everything", "Every spell and every non-mana ability waits on the stack while each player gets a chance to respond before it resolves.", TipCategory.STACK_TIMING))
    add(RuleTip("Mana abilities are instant-instant", "Tapping a land or creature for mana doesn't use the stack and can't be responded to or countered.", TipCategory.STACK_TIMING))
    add(RuleTip("Triggers are mandatory", "Triggered abilities trigger whether you want them to or not (unless they say \"you may\"). Forgetting your own detrimental trigger isn't an option in tournament play.", TipCategory.STACK_TIMING))
    add(RuleTip("Targets lock at cast time", "You choose targets when you cast a spell, not when it resolves. If every target is illegal by resolution, the spell \"fizzles\" — it does nothing at all, not even its untargeted parts.", TipCategory.STACK_TIMING))
    add(RuleTip("Counter wars", "A counterspell on the stack is itself a spell: it can be countered in response.", TipCategory.STACK_TIMING))
    add(RuleTip("Kill the ability's source? Too late", "Once an ability is on the stack, it resolves even if its source leaves the battlefield. Sacrificing a creature in response to removal still gets you its activated ability.", TipCategory.STACK_TIMING))
    add(RuleTip("You can respond to yourself", "After casting a spell you may hold priority and cast another one in response to your own spell — useful to sneak a pump spell under expected removal.", TipCategory.STACK_TIMING))
    add(RuleTip("APNAP order", "When several players must act or put triggers on the stack simultaneously, the Active Player acts first, then Non-Active Players in turn order. Later triggers resolve first.", TipCategory.STACK_TIMING))
    add(RuleTip("You order your own triggers", "If several of your abilities trigger at the same time, you choose the order they go on the stack — so you choose the order they resolve, last-added first.", TipCategory.STACK_TIMING))
    add(RuleTip("\"Can't be countered\" isn't \"can't be answered\"", "You can still respond to an uncounterable spell: remove its target and it fizzles anyway.", TipCategory.STACK_TIMING))
    add(RuleTip("Flash means anytime", "Flash lets you cast a spell whenever you could cast an instant — during combat, on an opponent's turn, or in response to another spell.", TipCategory.STACK_TIMING))
    add(RuleTip("Split second freezes the game", "While a split-second spell is on the stack, players can't cast spells or activate non-mana abilities. Triggered abilities and mana abilities still work.", TipCategory.STACK_TIMING))
    add(RuleTip("Copies aren't cast", "A copy of a spell is put onto the stack, not cast, so cast-triggers like prowess or storm don't see it. Magecraft is the exception: it counts copies too.", TipCategory.STACK_TIMING))

    // ═══════════════════════════════════════════════════════════════════════
    //  COMBAT
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("Attack players, not creatures", "You declare attacks against a player, a planeswalker, or a battle — never against a creature. Blocking is how creatures meet.", TipCategory.COMBAT))
    add(RuleTip("Attackers tap on declaration", "Attacking taps the creature (unless it has vigilance). If you want to tap it for an ability instead, do it before attackers are declared.", TipCategory.COMBAT))
    add(RuleTip("Blocking is free", "Blockers don't tap to block. A tapped creature simply can't be declared as a blocker.", TipCategory.COMBAT))
    add(RuleTip("Blocked stays blocked", "If the only blocker is destroyed before damage, the attacker is still blocked and deals no combat damage — unless it has trample.", TipCategory.COMBAT))
    add(RuleTip("Trample math", "A trampler only needs to assign lethal damage to its blockers; the rest hits the player. With deathtouch, \"lethal\" is just 1 per blocker — the rest tramples through.", TipCategory.COMBAT))
    add(RuleTip("First strike is a whole extra step", "First-strike and double-strike damage happens in its own combat damage step. A creature killed by first strike never gets to deal its damage.", TipCategory.COMBAT))
    add(RuleTip("Double strike hits twice", "A double striker deals damage in the first-strike step AND the regular step. Killing it after the first hit prevents the second — but combined with a pump spell, that first hit already doubles the bonus.", TipCategory.COMBAT))
    add(RuleTip("Multiple blockers, ordered", "When several creatures block one attacker, the attacker's controller orders them and must assign lethal damage to each before moving to the next.", TipCategory.COMBAT))
    add(RuleTip("Vigilance keeps options open", "A vigilant attacker stays untapped, so it can still block on the way back or use its {T} abilities after combat.", TipCategory.COMBAT))
    add(RuleTip("Menace needs a crowd", "A creature with menace can't be blocked by exactly one creature — it's two blockers or none.", TipCategory.COMBAT))
    add(RuleTip("The best trick window", "The strongest moment for a combat trick is after blockers are declared but before damage: your opponent has committed and can no longer change blocks.", TipCategory.COMBAT))
    add(RuleTip("Attack triggers stick", "\"Whenever this creature attacks\" triggers the moment attackers are declared. Removing the attacker afterward doesn't undo the trigger.", TipCategory.COMBAT))
    add(RuleTip("Indestructible isn't invincible", "Indestructible creatures still take damage, can be blocked, and die to sacrifice effects or -X/-X. Only \"destroy\" and lethal damage bounce off.", TipCategory.COMBAT))
    add(RuleTip("Fight isn't combat", "Fight and \"bite\" effects deal damage outside combat: first strike does nothing there, but deathtouch and lifelink apply in full.", TipCategory.COMBAT))
    add(RuleTip("Removing an attacker mid-combat", "Killing an attacking creature before the damage step prevents all its combat damage — even if it has already triggered its attack abilities.", TipCategory.COMBAT))
    add(RuleTip("Blocking legality has no size limit", "Any untapped creature can legally block any attacker, regardless of power or toughness — a 0/1 can block a 10/10 all day; it just won't survive.", TipCategory.COMBAT))

    // ═══════════════════════════════════════════════════════════════════════
    //  KEYWORDS (evergreen & common abilities)
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("Hexproof vs. shroud", "Hexproof: your opponents can't target it, but you still can. Shroud: nobody can target it, including you.", TipCategory.KEYWORDS))
    add(RuleTip("Ward is a toll, not a wall", "Ward doesn't stop targeting. When an opponent targets the creature, a triggered ability counters their spell unless they pay — and that trigger can itself be responded to.", TipCategory.KEYWORDS))
    add(RuleTip("Protection = DEBT", "\"Protection from X\" means it can't be Damaged, Enchanted or equipped, Blocked, or Targeted by X. A board wipe that doesn't target still destroys it.", TipCategory.KEYWORDS))
    add(RuleTip("Lifelink has no gap", "Lifelink life gain happens at the same time as the damage — there's no window to kill a player \"before the lifelink resolves\", since it isn't a separate trigger. It does matter for effects that trigger specifically on life gain, though.", TipCategory.KEYWORDS))
    add(RuleTip("Haste, fully", "Haste removes summoning sickness entirely: the creature can attack AND use its {T} abilities the turn it arrives.", TipCategory.KEYWORDS))
    add(RuleTip("Flying meets Reach", "Flying creatures can only be blocked by creatures with flying or reach — don't forget your reach creatures can block fliers too, even though they can't fly themselves.", TipCategory.KEYWORDS))
    add(RuleTip("Deathtouch scales down", "Any nonzero damage from a deathtouch source is lethal — combat or not. A 1/1 deathtoucher trades with anything it blocks or fights.", TipCategory.KEYWORDS))
    add(RuleTip("Prowess counts casts", "Prowess triggers when you cast a noncreature spell — copies put on the stack by other effects don't trigger it.", TipCategory.KEYWORDS))
    add(RuleTip("Equip at sorcery speed", "Equip abilities can only be activated during your main phase with an empty stack. If the equipped creature dies, the Equipment survives, unattached.", TipCategory.KEYWORDS))
    add(RuleTip("Auras target on the way in", "Casting an Aura targets, so hexproof stops it. But an Aura put onto the battlefield by an effect doesn't target and can land on anything legal.", TipCategory.KEYWORDS))
    add(RuleTip("Defender still defends", "A creature with defender can't attack, but it blocks, triggers, and uses abilities like any other creature.", TipCategory.KEYWORDS))
    add(RuleTip("Kicker is part of casting", "Kicker costs are paid as you cast the spell. It's still one spell — countering it counters the kicked version too.", TipCategory.KEYWORDS))
    add(RuleTip("Cycling dodges counterspells", "Cycling is an activated ability, not a spell. A counterspell can't stop it; only effects that counter abilities can. It can also be activated at instant speed — you discard first, then draw, which can feed graveyard synergies.", TipCategory.KEYWORDS))
    add(RuleTip("Landfall counts every land", "Landfall triggers whenever a land enters the battlefield under your control — played, fetched, or put there by a spell. Fetch lands trigger it twice across two turns of value.", TipCategory.KEYWORDS))
    add(RuleTip("Regenerate is a pre-paid shield", "Regeneration must be activated before the destruction happens. When it saves the creature: tap it, remove it from combat, remove all damage.", TipCategory.KEYWORDS))
    add(RuleTip("Phasing", "Phased-out permanents are treated as if they don't exist. Auras and Equipment attached to them phase out too, and phase back in together.", TipCategory.KEYWORDS))
    add(RuleTip("Cascade", "Cascade triggers when you cast the spell. You reveal cards until you find one with lesser mana value, then cast it for free — at instant speed if it's an instant.", TipCategory.KEYWORDS))
    add(RuleTip("Morph", "Face-down morphed creatures are 2/2 colorless creatures with no name, type, or abilities. Opponents must allow you to turn them face-up for the morph cost — a related-but-different mechanic, Manifest, is put face-down by effects instead of being cast that way.", TipCategory.KEYWORDS))
    add(RuleTip("Storm", "Storm copies the spell for each spell cast before it this turn. Rituals and cantrips earlier in the turn all count!", TipCategory.KEYWORDS))
    add(RuleTip("Flashback", "Flashback lets you cast a spell from the graveyard by paying its flashback cost. The spell is then exiled, not returned to the graveyard.", TipCategory.KEYWORDS))
    add(RuleTip("Dredge", "Dredge is a replacement effect — when you would draw a card, you may instead mill yourself equal to the dredge value and return the dredge card to hand.", TipCategory.KEYWORDS))
    add(RuleTip("Delve", "You can exile any number of cards from your graveyard when paying for Delve — each exiled card pays for {1} of generic mana.", TipCategory.KEYWORDS))
    add(RuleTip("Convoke", "Tapping a creature for Convoke can pay for one generic mana or one mana of the creature's color. All tap abilities are mana abilities.", TipCategory.KEYWORDS))
    add(RuleTip("Suspend", "Suspending is casting without paying the mana cost. The spell sits in exile with time counters, one removed each upkeep, then cast for free when the last is removed.", TipCategory.KEYWORDS))
    add(RuleTip("Escape", "Escape lets you recast cards from the graveyard by paying the escape cost and exiling a set number of other graveyard cards.", TipCategory.KEYWORDS))
    add(RuleTip("Foretell", "Cards can be foretold face-down in exile for {2} on any turn. The reduced foretell cost can be paid on later turns at sorcery speed (or instant speed if it's an instant).", TipCategory.KEYWORDS))
    add(RuleTip("Boast", "Boast abilities can only be activated once per turn, and only if the creature attacked this turn. They're not tap abilities.", TipCategory.KEYWORDS))
    add(RuleTip("Sagas", "Sagas trigger on your precombat main phase. Chapter I triggers when it enters, subsequent chapters on later turns. The saga sacrifices itself after the final chapter.", TipCategory.KEYWORDS))
    add(RuleTip("MDFCs", "Modal Double-Faced Cards can be played as either face. The back face can only be played as a land (or whatever its type is), not cast normally from hand.", TipCategory.KEYWORDS))
    add(RuleTip("Split Cards", "A split card in hand or on the stack has the combined characteristics of both halves. Its mana value is the sum of both sides.", TipCategory.KEYWORDS))
    add(RuleTip("Adventures", "Adventure cards can be cast as the Adventure (exile it), then cast the creature later from exile. While in exile, it's still a card you own.", TipCategory.KEYWORDS))

    // ═══════════════════════════════════════════════════════════════════════
    //  GAME_STATE (state-based actions & zones)
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("The invisible referee", "State-based actions (creatures dying to damage or 0 toughness, players losing at 0 life, legends clashing) are checked automatically whenever a player would get priority. Nobody can respond to them.", TipCategory.GAME_STATE))
    add(RuleTip("Zero toughness can't be saved", "A creature with toughness 0 or less is put into the graveyard by the rules, not destroyed — regeneration and indestructible don't help.", TipCategory.GAME_STATE))
    add(RuleTip("-X/-X beats indestructible", "Reducing toughness to 0 isn't destruction, so it kills indestructible creatures. Damage never can.", TipCategory.GAME_STATE))
    add(RuleTip("The legend rule, per player", "If YOU control two legendary permanents with the same name, you choose one and put the other into the graveyard. Opponents' copies don't clash with yours. It's not destruction — but it does count as dying.", TipCategory.GAME_STATE))
    add(RuleTip("Counters cancel out", "+1/+1 and -1/-1 counters on the same permanent are removed in pairs automatically.", TipCategory.GAME_STATE))
    add(RuleTip("Tokens vanish, but they die first", "A token that leaves the battlefield triggers \"dies\" abilities normally, then ceases to exist. It can never come back from the graveyard — \"returning it to hand\" just makes it disappear.", TipCategory.GAME_STATE))
    add(RuleTip("Clones copy the print", "A copy effect copies the printed characteristics (plus other copy effects) — not counters, damage, Auras, or Equipment on the original.", TipCategory.GAME_STATE))
    add(RuleTip("Empty library isn't loss — drawing is", "You only lose when you attempt to draw from an empty library. Sitting at zero cards is safe until someone makes you draw.", TipCategory.GAME_STATE))
    add(RuleTip("Spells finish resolving first", "State-based actions wait until a spell has fully resolved. Your life can dip to 0 mid-resolution and come back up before the game ever checks.", TipCategory.GAME_STATE))
    add(RuleTip("Sacrifice pierces everything", "Sacrificing is neither destruction nor targeting: indestructible, hexproof, and protection are all useless against \"each player sacrifices a creature.\"", TipCategory.GAME_STATE))
    add(RuleTip("Exile isn't dying", "\"Dies\" means going to the graveyard from the battlefield. Exiling a creature triggers no death triggers — which is exactly why exile removal is prized.", TipCategory.GAME_STATE))
    add(RuleTip("Copying a spell isn't casting it", "A copy of a spell is put directly onto the stack — it was never cast. Effects that trigger \"whenever you cast a spell\" won't see it.", TipCategory.GAME_STATE))

    // ═══════════════════════════════════════════════════════════════════════
    //  COMMANDER
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("Commander tax", "Casting your commander from the command zone costs {2} more for each previous time you've cast it from there. The tax never resets during the game.", TipCategory.COMMANDER))
    add(RuleTip("21 means dead", "A player who has taken 21 or more combat damage from the same commander over the course of the game loses. It's tracked per commander, and only combat damage counts.", TipCategory.COMMANDER))
    add(RuleTip("Your commander can still \"die\"", "If your commander goes to the graveyard or exile, death and exile triggers see it there first; then, when state-based actions are checked, you may move it to the command zone.", TipCategory.COMMANDER))
    add(RuleTip("Hand and library are different", "If your commander would go to your hand or library, you choose to send it to the command zone as a replacement — it never reaches that zone at all.", TipCategory.COMMANDER))
    add(RuleTip("Color identity is strict", "A card's color identity includes every colored mana symbol anywhere on it — cost, rules text, even the back face. A hybrid {W/U} symbol makes it both white and blue. Every card in your 99 must fit inside your commander's identity.", TipCategory.COMMANDER))
    add(RuleTip("Singleton, with exceptions", "One copy of each card except basic lands — and cards whose own text says \"a deck can have any number,\" which override the rule.", TipCategory.COMMANDER))
    add(RuleTip("Partners split the seat", "Two partner commanders start in the command zone together, the deck drops to 98, color identity is their union, and each one tracks its own tax and its own 21 commander damage.", TipCategory.COMMANDER))
    add(RuleTip("Stolen commanders still count", "Your commander is yours even while an opponent controls it — its combat damage still counts toward that commander's 21 against whoever it hits.", TipCategory.COMMANDER))
    add(RuleTip("Command-zone casts are normal casts", "Casting a commander from the command zone follows normal timing and can be countered. If it is, you can send it back to the command zone and try again later — with more tax.", TipCategory.COMMANDER))
    add(RuleTip("The monarch", "The monarch draws an extra card at the beginning of their end step. Deal combat damage to the monarch and you steal the crown.", TipCategory.COMMANDER))
    add(RuleTip("Goad forces the issue", "A goaded creature must attack each combat if able, and must attack someone other than the player who goaded it if possible.", TipCategory.COMMANDER))
    add(RuleTip("When a player leaves", "When a player loses a multiplayer game, all cards they own leave the game with them, and all spells and abilities they controlled cease to exist.", TipCategory.COMMANDER))
    add(RuleTip("Poison ignores the 40", "Ten poison counters kill a Commander player just like anyone else — the 40 starting life doesn't help against infect or toxic.", TipCategory.COMMANDER))
    add(RuleTip("Politics is a resource", "In multiplayer, threat assessment beats raw power: the player who looks the scariest eats the removal. Table talk, deals, and timing are part of the game.", TipCategory.COMMANDER))

    // ═══════════════════════════════════════════════════════════════════════
    //  TOKENS_COUNTERS (tokens, counters & card economy)
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("Treasure is more than mana", "A Treasure is \"{T}, Sacrifice this artifact: Add one mana of any color.\" It's also an artifact sitting on the battlefield: it counts for affinity and metalcraft, and it can be stolen or destroyed like any other permanent.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Food, Clue, Blood, Map", "Food: {2}, {T}, sacrifice to gain 3 life. Clue: {2}, sacrifice to draw. Blood: {1}, discard a card, sacrifice to draw. Map: {1}, {T}, sacrifice to make one of your creatures explore.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Powerstones are picky", "A Powerstone taps for {C}, but that mana can't be spent to cast a nonartifact spell. Activating abilities is fine.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Token copies are the real thing", "A token copy of a legendary creature is still legendary — make a copy of your own legend and the legend rule bites immediately.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Stun counters", "A permanent with a stun counter doesn't untap as normal; instead, one stun counter is removed. Vigilance sidesteps them entirely on the attack.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Shield counters", "If a permanent with a shield counter would be destroyed or dealt damage, remove a shield counter instead. Exile, sacrifice, and -X/-X still work.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Keyword counters are permanent", "Flying, first strike, or deathtouch counters grant the ability for as long as the counter stays — they survive end of turn, unlike most pump spells.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Infect vs. toxic", "Infect replaces its damage: -1/-1 counters on creatures, poison on players. Toxic deals normal damage AND gives poison counters on top.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Loyalty is sorcery-paced", "You may activate one loyalty ability of each planeswalker you control per turn, only during your main phases with an empty stack — plus or minus, it's one activation per walker.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Attacking planeswalkers", "Combat damage dealt to a planeswalker removes that many loyalty counters. At zero loyalty it's gone — state-based, no response.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Counters survive blinking? No.", "Exiling a permanent and returning it makes it a new object: all counters, damage, and attachments are gone, and summoning sickness is back.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Proliferate picks per permanent", "Proliferate lets you add one counter of each kind already on any number of chosen permanents and players — poison, loyalty, +1/+1, charge, all of them.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Undergrowth counts cards, not tokens", "Undergrowth counts creature CARDS in your graveyard — not tokens (they cease to exist when they leave the battlefield, so they never sit in the graveyard to be counted).", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Afterlife", "Afterlife X creates X 1/1 white and black flying Spirit tokens when the creature dies. Each token has flying.", TipCategory.TOKENS_COUNTERS))
    add(RuleTip("Amass", "Amass creates a Zombie Army token if you don't have one, then puts +1/+1 counters on it. You can only have one Army at a time.", TipCategory.TOKENS_COUNTERS))

    // ═══════════════════════════════════════════════════════════════════════
    //  DECKBUILDING (deckbuilding & strategy)
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("Play the minimum", "60 cards in constructed, 100 in Commander — exactly. Every card above the minimum lowers the odds of drawing your best cards.", TipCategory.DECKBUILDING))
    add(RuleTip("Respect the curve", "Count your deck's mana costs. If you can't reliably play something meaningful on turns two and three, the deck will lose games before its top-end matters.", TipCategory.DECKBUILDING))
    add(RuleTip("Land counts, rough guide", "About 17 lands in a 40-card limited deck, 22-26 in a 60-card deck depending on curve, and 36-38 (plus ramp) in Commander.", TipCategory.DECKBUILDING))
    add(RuleTip("The Commander trinity", "A rough baseline for a functional Commander deck: ~10 ramp pieces, ~10 card-draw effects, ~10 pieces of interaction/removal. Adjust, but never to zero.", TipCategory.DECKBUILDING))
    add(RuleTip("Card advantage wins long games", "Every effect that trades one of your cards for two of your opponent's — or draws you two — compounds. The player with more cards usually wins the attrition war.", TipCategory.DECKBUILDING))
    add(RuleTip("Redundancy beats brilliance", "If your deck needs an effect to function, play functional duplicates of it. One copy of a combo piece is a prayer, not a plan.", TipCategory.DECKBUILDING))
    add(RuleTip("Mulligan for function, not land count", "Keep hands that DO something in the first three turns. Four lands and three uncastable cards is a mulligan too.", TipCategory.DECKBUILDING))
    add(RuleTip("Tempo vs. value", "Cheap interaction that stalls the opponent buys time (tempo); two-for-ones grind ahead (value). Know which one your deck wants and mulligan for it.", TipCategory.DECKBUILDING))
    add(RuleTip("Sideboards answer, mainboards ask", "In best-of-three, the 15-card sideboard is for answers too narrow for game one. Build it against the decks you actually expect to face.", TipCategory.DECKBUILDING))
    add(RuleTip("Removal isn't optional", "A deck that can't interact loses to the first strategy faster than its own. Even the most proactive deck wants a few cheap answers.", TipCategory.DECKBUILDING))
    add(RuleTip("Tutors still shuffle", "Cards that \"search your library\" are called tutors. Remember to shuffle your library after searching, even if you didn't find anything.", TipCategory.DECKBUILDING))

    // ═══════════════════════════════════════════════════════════════════════
    //  MODERN_MECHANICS (2023-2026 mechanics)
    // ═══════════════════════════════════════════════════════════════════════
    add(RuleTip("Battles", "A Siege enters with defense counters and is protected by an opponent. Attack it and deal it damage to remove counters; when the last one is removed, its controller exiles it and casts the transformed back face for free.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("The Ring tempts you", "Each time the Ring tempts you, choose one of your creatures as Ring-bearer; the Ring's abilities are cumulative (up to four) but only one creature bears it at a time.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Discover", "Discover N exiles cards from the top of your library until a nonland card with mana value N or less appears — cast it for free or put it into your hand. Unlike cascade, the hand is always an option.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Disguise & cloak", "Both put a card face down as a colorless 2/2 with ward {2}. Disguise lets you turn it face up any time for its disguise cost; cloaked cards flip for their mana cost if they're creatures.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Plot", "Pay a card's plot cost to exile it from your hand at sorcery speed; on any later turn, cast it from exile for free — also at sorcery speed. Pre-pay this turn, deploy next.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Committing a crime", "You commit a crime whenever a spell or ability you control targets an opponent or anything they control or own in the graveyard, on the stack, or exiled. Yes, targeting counts even if the spell is countered.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Saddle", "Tap any number of your other creatures with total power N or more to saddle a Mount; the Mount gets its saddled bonus for the turn. Saddling happens at sorcery speed.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Rooms", "A Room is a split enchantment: cast it by unlocking one door, and later pay the other door's cost during your main phase to unlock that half too. Each unlocked door's abilities are active.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Impending", "Cast a spell for its impending cost and it enters with time counters and isn't a creature yet; remove one each of your end steps, and when the last leaves, it wakes up. Cheaper now, scarier later.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Offspring", "Pay the extra offspring cost when casting the creature and, as it enters, you create a 1/1 token copy of it. All its triggered abilities now come in duplicate.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Omens", "An Omen is the spell half of a creature card: when the Omen resolves, the card is shuffled into its owner's library instead of going to the graveyard — the creature comes back later.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Mobilize", "When a creature with mobilize N attacks, you create N tapped and attacking 1/1 red Warrior tokens; sacrifice them at the beginning of the next end step. Free attackers, temporary bodies.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Harmonize", "Cast the card from your graveyard for its harmonize cost; you may also tap an untapped creature as an additional cost to reduce the generic mana by that creature's power. Big creatures make graveyard spells cheap.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Station", "Tap an untapped creature you control to put charge counters equal to its power on a Spacecraft; at each STATION threshold it powers up new abilities — enough counters and it becomes a creature itself.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Warp", "Cast a card from your hand for its warp cost to get it now at a discount; it's exiled at the next end step, and you may cast it again from exile on a later turn for its real cost.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Void", "Void abilities give a bonus if a nonland permanent left the battlefield this turn or a spell was warped this turn. Sacrifice fodder suddenly pays double.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Lander tokens", "A Lander is a colorless artifact token with \"{2}, {T}, Sacrifice this token: search your library for a basic land card, put it onto the battlefield tapped, then shuffle.\" Slow ramp you can bank for later.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Start your engines & max speed", "If you have no speed, \"start your engines!\" sets it to 1. Your speed increases the first time each turn an opponent loses life, up to 4 — where max speed abilities switch on.", TipCategory.MODERN_MECHANICS))
    add(RuleTip("Exhaust abilities", "An exhaust ability can be activated only once per game. Spend it at the moment of maximum impact — there is no second chance.", TipCategory.MODERN_MECHANICS))
}

/** Total tip count — used by [RulesTipWidget]'s daily-selection modulus. */
val MTG_TIPS_COUNT: Int get() = MTG_TIPS_CATALOG.size
