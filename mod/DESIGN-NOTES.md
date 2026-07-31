# Mob Trumps — design notes

Ideas that have been thought through but not built, plus the decisions already
taken so they don't get re-litigated. Everything here is a proposal unless it
says otherwise.

---

## 1. Card recycler — dupes into fragments

**Status:** BUILT (v1.63.0). The open questions were decided — see "Decided"
below.

Drop unwanted duplicate cards into a machine, get **card fragments** (or
"scrap"), spend fragments for a card you don't own.

### The thing that reframes it

Before v1.50.0 every kill dropped a card, so spare commons piled up in chests.
Since v1.50.0 a common drops **1 in 20**, so a second chicken card takes another
~20 chickens. **Duplicates are now scarce, not overflowing.** A recycler can't be
balanced as "drain the pile" any more — either the rates are generous enough that
a handful of spares matters, or the machine accepts something besides dupes.

### Two machines, one loop

**Card Shredder → Card Fragments → Printing Press.** You pulp old cards and
print new ones; the metaphor is physically coherent and it names the failure
case for free (a *misprint*, not "you lost your fragments").

**The Shredder**

- **Yield scales with tier, gently** — 4 / 6 / 10 / 16 / 28 for common →
  legendary. The instinct is 1:20, but the drop ladder already equalises effort
  (a common takes ~20 kills, a legendary takes 1), so in *work* terms they cost
  about the same. A mild curve matches player expectation without turning boss
  farming into a fragment mine.
- **Duplicates only.** The book records discovery permanently, so shredding a
  spare can never cost collection progress — but the machine must refuse your
  last copy, and cards currently in a deck. That removes most of the exploit
  surface.
- **A machine, not a button** — hopper, visible grind, progress bar, fragments
  stacking up. Same reasoning as the coin-flip animation. *(built v1.64.0, in
  the screen rather than as a world hopper: the card rides down a funnel into a
  toothed drum, the drum turns and throws sparks, a bar fills, and the fragments
  pile into a tray. The press runs it in reverse — a blank sheet feeds through
  the rollers and either a card rises out of the slot or the sheet comes out
  torn and scorched.)*

**The Printing Press**

You choose a **tier**, invest fragments up to that tier's maximum, and pull. The
odds are **linear in what you invested**; the max guarantees a card. The result
is a **random card of that tier — not necessarily one you are missing.**

| Tier | Max (guarantees) | 50% at | Shred yield |
|---|---|---|---|
| Common | 30 | 15 | 4 |
| Uncommon | 45 | 22 | 6 |
| Rare | 70 | 35 | 10 |
| Epic | 110 | 55 | 16 |
| Legendary | 180 | 90 | 28 |

Same-tier conversion is a steady **~7 spare cards per printed card** across the
whole ladder; a legendary printed purely from spare commons costs 45 of them.

### The property that makes it work — don't break it

Linear odds **plus losing the fragments on a failure** means the expected cost
per card is identical however you play: 15 fragments at 50% averages 30 per
common, and so does 30 at 100%. There is no optimal strategy to look up, only a
choice about variance.

That collapses the moment a consolation refund is added. Refund 25% on a failure
and the expected cost becomes `0.75·max + 0.25·invested`, so investing low is
strictly cheaper, everyone minimum-bets, and the decision evaporates.
**No refund on a failure.** A minimum stake of ~5 fragments is fine purely to
stop one-fragment spam clogging the UI.

### Decided (v1.63.0)

1. **Pure random of the tier.** The press does *not* lean toward cards you are
   missing. It is for volume and trading; hunting is for completion. This is the
   "already correct" branch of the question below, chosen deliberately: the
   press being useless for your last two commons is the point, not a flaw.
2. **Shred yield scales with condition** — a mint spare pays full, a ruined one
   pays half, never below 1. Gives condition a second reason to exist and makes
   a battered duplicate the obvious thing to pulp.
3. **No special handling for low serials.** A card is a card; `CREEPER-000001`
   can be shredded like any other. Recorded here because someone will
   eventually do it.

Also decided in the build: **"spare" means the mob is already filed in your
Collection Book.** Since v1.59.0 the book is the record of what you own, so a
loose card whose mob is filed is by definition a second copy, and pulping it
cannot cost collection progress or a card the campaign needs. A card of a mob
you have not filed is your only one and the machine refuses it.

### The one open question (answered: pure random)

A random card of the tier means **the press gets worse as your collection
fills** — own 10 of the 12 commons and a successful print is only 17% likely to
be new, so it is nearly useless for your last few cards.

If the intent is "the press is for volume and trading, hunting is for
completion", that is already correct. If it should stay useful late, the minimal
change is to **weight the roll toward cards you don't own** (≈3×) without ever
guaranteeing one — still a gamble, still yields duplicates, but it leans your
way. *Undecided.*

### Sequencing warning

This collides with the condition/serial system (§3) in ways that are much easier
to design for than to retrofit:

- Worn cards become the natural thing to shred, so yield probably wants to scale
  with **condition** too, making mint cards worth keeping.
- Shredding `CREEPER-000001` would be a genuine tragedy — the machine should
  refuse very low serials, or at minimum demand confirmation.

Building serials first makes the recycler richer for free.

---

## 2. The Campaign — twenty missions

**Status:** BUILT (v1.57.0). Supersedes the earlier "Gauntlet" sketch.

Twenty missions, each anchored to a category, mission 1 the farm and mission 20
the bosses.

### The structural move (revised v1.59.0)

**Sixteen against sixteen.** The mission fields its own deck and the player
fields sixteen out of their Collection Book. The campaign does not open until
the book holds 16 cards -- the collection is the entry fee, and what you have
hunted is what you take in. Your saved battle deck is used first, filtered to
what is actually filed, then topped up from the rest of the book.

This replaces the original reading below, which is kept because the deck
BUILDER is unchanged -- the mission's sixteen are still its whole anchor
category padded from outside it.

**Originally: every mission is ONE 16-card deck, dealt 8 and 8.** In Top Trumps a single
deck is shared between two players, so the mission's deck *is* the sixteen
cards. This resolves several problems at once:

- **No ownership gate.** The mission provides the cards, so the campaign never
  waits on the player's collection — which matters now that rarity-scaled drops
  make collections build slowly.
- **Both sides always hold comparable material**; nobody can bring the Warden to
  the chicken round.
- **The mission's identity is its deck** — fixed and seeded, identical every
  attempt, therefore learnable.
- **The mirror-match problem in the boss round disappears**, because the three
  bosses are three cards in a sixteen-card deck rather than the entire deck.

Collection still matters, as power rather than access: **any card in the mission
deck the player has hunted plays at their holo level.** The CPU's half plays at
base. Hunt your cows and the Pasture gets easier.

### Data model

```
CampaignMission(index 1..20, id, name, tagline,
                anchor: Category,        // themed set, used whole
                subsidyBand: Tier..Tier, // what the padding is drawn from
                brain: NORMAL|HARD|COUNTER,
                cpuExtra: 0..2)          // uneven deal, 9/7 rather than 8/8
```

### Deck builder

```
deck    = every member of the anchor category   (max 14, so always padded)
need    = 16 - deck.size
subsidy = `need` cards from OUTSIDE the anchor, where
            tier is within the mission's band,
            preferring AFFINE categories, falling back to any,
            seeded on the mission id so it never changes
```

Affinity keeps the padding thematic: Farm-Creature-Village,
Undead-Monster-Illager, Nether-Boss, Aquatic-Creature, End-Boss.

Verified against the real pools: **no Wild Creature is common tier** (mildest are
Bat 8, Wolf 7, Bee 7, all Uncommon), so mission 1 is 14 farm animals + Bat +
Wolf — one tier above "two commons" because nothing gentler exists in that set.
Mission 20 needs 13 from Epic+Legendary and **27** are available outside the
bosses, so "mostly high tier" holds comfortably.

### The twenty

| # | Name | Anchor | Pad | Band |
|---|---|---|---|---|
| 1 | The First Pasture | Farm | 2 | Uncommon |
| 2 | Woodland Wanderers | Creatures | 5 | Uncommon |
| 3 | Shallow Water | Aquatic | 3 | Uncommon |
| 4 | Shallow Graves | Undead | 5 | Unc-Rare |
| 5 | Stampede | Farm | 2 | Rare |
| 6 | Things That Hiss | Monsters | 9 | Unc-Rare |
| 7 | Tooth and Claw | Creatures | 5 | Rare |
| 8 | The Trading Post | Village | 12 | Rare |
| 9 | The Deep | Aquatic | 3 | Rare-Epic |
| 10 | Ashlands | Nether | 7 | Rare-Epic |
| 11 | The Long Night | Undead | 5 | Epic |
| 12 | Raid Bells | Illagers | 10 | Rare-Epic |
| 13 | Cave-In | Monsters | 9 | Epic |
| 14 | Iron and Emerald | Village | 12 | Epic |
| 15 | The Fortress | Nether | 7 | Epic |
| 16 | Void-Touched | The End | 13 | Epic |
| 17 | The Mansion | Illagers | 10 | Epic-Leg |
| 18 | The Outer Isles | The End | 13 | Epic-Leg |
| 19 | Reckoning | Bosses | 13 | Leg-Epic |
| 20 | The Last Trump | Bosses | 13 | Legendary |

Every category anchors exactly two missions; the pair feels different because
the padding changes entirely — Village I is four villagers propped up by twelve
Rares, Village II the same four backed by twelve Epics.

Opponent ramp, as shipped in v1.60.0. **Both decks are always exactly 16** —
neither side ever holds more cards than the other, so the difficulty lives in
the cards themselves and in how the opponent plays them, never in a card-count
advantage.

| missions | brain | counts the deck |
|---|---|---|
| 1-10 | Normal | - |
| 11-15 | Hard | - |
| 16-20 | Hard | yes |

Plus a per-mission **holo level** the opponent's deck is fielded at:

```
mission  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20
holo     2  1  2  2  3  1  1  2  2  1  0  0  0  0  1  0  0  0  0  0
```

That is not a ramp and is not meant to be: it is per-mission compensation for
how strong a category's own cards happen to be. Farm animals and reef fish
need premium prints to be a match for any real collection; Wild Creatures need
less; by mission 11 an Epic-tier deck stands on its own and the help comes off
entirely. Card counting is worth roughly 25-50 points of win rate on its own,
which is why it is held back until mission 16.

Card counting is not a fourth enum but a flag on the Hard brain: in a fixed
16-card mission deck it genuinely knows the pool, so tracking what has been
played is honest inference rather than cheating. Note 15-16 hand the extra card
*back* — you trade a material advantage for a smarter opponent.

### Measured, not guessed

Fitted by DP over the holo level against a target curve, with a penalty for the
difficulty going backwards, then verified at 3000 runs per mission. Two
collections: "no legends" is commons through rares, roughly a mid-game book;
"full" draws from all 81.

| # | no-legends | full | | # | no-legends | full |
|---|---|---|---|---|---|---|
| 1 | 80% | 61% | | 11 | 59% | 61% |
| 2 | 77% | 63% | | 12 | 48% | 61% |
| 3 | 70% | 43% | | 13 | 42% | 64% |
| 4 | 53% | 37% | | 14 | 39% | 62% |
| 5 | 59% | 45% | | 15 | 34% | 49% |
| 6 | 68% | 46% | | 16 | 38% | 63% |
| 7 | 64% | 54% | | 17 | 39% | 50% |
| 8 | 56% | 47% | | 18 | 35% | 40% |
| 9 | 53% | 35% | | 19 | 32% | 42% |
| 10 | 53% | 45% | | 20 | 29% | 35% |

Descends 80% to 29%: no walkover, no wall, mean error 4.9 points against the
target. Earlier attempts to let the fit choose the opponent's *brain* as well
produced a better number (3.9) and an unshippable result — it put Hard on
mission 1 and Easy on mission 20. Brain and card counting are a designed ramp;
only the holo level is fitted.

The "full" column is choppier because a complete collection trivialises the
mid-tier decks. That is the collection doing its job, and there is no knob left
to flatten it without giving one side more cards.

### Progression, rewards, screen

Sequential unlock, clear N to open N+1. A `CAMPAIGN` attachment maps mission id
to cleared/claimed plus a **flawless** flag (won without losing a round), so a
cleared mission still has something to chase.

First clear awards a **Trophy-edition card** — the mission names the mob. Stat-identical, so it cannot
unbalance anything; purely a thing only winning can produce. This is what the
edition enum added in 1.54.0 was reserved for.

The screen is a vertical route of 20 tiles in category accent colours, state
readable at a glance: locked (dim, chained), available (lit, pulsing), cleared
(stamped), flawless (gold star). Clicking opens a briefing — name, tagline, the
full 16-card deck as a hoverable fan, the opponent's skull rating and the Trophy
on offer — then Begin.

### The reward ladder

No metals before mission 6. A card game must not hand a new player the
materials they were meant to go and mine for, so 1-5 pay in the things that
*let* you go mining, themed to the mission that gave them: bread and beef for
the farm, the day's catch for the reef, torches for the graves, a saddle and
four ender pearls for the stampede. Iron arrives at 6 in a moderate 8, gold at
7, the first single diamond at 9.

**Every book the campaign pays is an enchanted one** — a stack of blank books is
a shopping list, not a reward. They start at mission 7 rolled at level 5 and
climb to level 30, table-style with no curation, so they are a lottery ticket
rather than a guarantee. The count goes 1 (7-8), 2 (9-12), 3 (13-19), and the
finale is the only mission in the campaign that pays **4, rolled at level 30**,
alongside 12 diamonds, a netherite ingot, 16 experience bottles and a notch
apple.

### Open calls

1. **8/8 from one 16-card deck** is the reading of "every deck has 16 cards"
   that everything above rests on. Sixteen *each side* would change the builder.
2. **No ownership requirement.** Missions hand you the cards; the collection
   makes them stronger, never available.

---

## 3. Condition, serials and sleeves

**Status:** BUILT, with two knowing exceptions — see the audit at the end of
this section. Was "agreed in full, nothing built"; that is long out of date.

The idea: every card becomes a unique physical object with a history rather than
an interchangeable copy. Two Creeper cards play identically, but one may be
`CREEPER-000001`, mint, unlocked by you on day one; the other `CREEPER-004317`,
worn to 40%, unlocked by a stranger. **All value is scarcity and preservation —
never stats.**

### What a card carries

Shown: mob, per-mob serial, authenticity id (hidden but verified), condition %
and label, who unlocked it, when, edition, sleeved or not, plus the existing
stats. **Never shown or stored for display:** times handled, current owner,
previous owners.

### Serials

- Per-mob sequences — `CREEPER-000001`, `ZOMBIE-000001` — so every mob has
  exactly one `000001`, the server's historical first of that card.
- Allocated **when the kill creates the card**, server-side and atomically, so
  simultaneous kills cannot collide. Persistent across restart/crash, never
  reset, **never reused** even if the card is destroyed.
- A hidden authenticity id makes renaming or anvil-copying lore produce a fake
  the server rejects.
- Six-digit padding by default (configurable).

### Unlocked by

The player whose kill created that exact card. UUID plus a name snapshot so it
still reads correctly after a rename. **Immutable — it does not change on
trade.** It is history, not ownership. A complete set of `000001`s is therefore
a set of the server's earliest discoveries, each signed by its finder.

### Condition

Starts at **100%**. Falls only when an **unsleeved** card *enters an active
hand*, and the first such entry is free — **once per card, ever**, not once per
player. Every qualifying entry after that costs **5 points** (configurable,
default 5). Floor 0%; nothing in normal play repairs it.

No wear from: creating, picking up, moving between slots, storage, switching
away, or re-inspecting a card already in hand. Detect genuine held-item
transitions by authenticity id, not item type, and never let death, respawn,
reconnect, dimension change, GUI opening or inventory sync fake one.

Labels: 100 Mint · 90–95 Near Mint · 75–85 Excellent · 55–70 Good · 30–50 Worn ·
5–25 Damaged · 0 Ruined. The card visibly degrades — corner wear, then creases
and fading, then obvious damage. The numeric percentage is authoritative.

### Card Sleeve

One item, one card, no tiers. A sleeved card never wears; sleeving preserves
what is left but repairs nothing. Still holdable, inspectable, tradeable and
playable while sleeved, with the card visible inside. All insert/remove is
server-side and must never duplicate either item.

### Inspection

Right-click for a large view: front is art, name, stats, serial and visible
wear; a Flip control shows the back — serial, exact % and label, unlocked by,
unlock date, edition, sleeved status. Inspecting never itself causes wear.

### Editions

Standard, Foil, Trophy, first-kill, flawless-kill. Edition changes art and
desirability **only, never stats**. Serials run in creation order regardless of
edition — a Trophy does not get its own separate `000001`.

### Decisions added on top of the brief

1. **Build order**, since everything hangs off identity: serials + authenticity
   + non-stackable cards + migration → condition + sleeve → inspection screen.
2. **Non-stackable is a foundational blocker, not a detail.** Cards register
   with a plain `Item.Properties()` today and stack to 64. Unstacking them
   ripples through the binder, trading and the shredder, because "one card"
   stops meaning "one item in a stack of forty".
3. **This should land before the recycler (§1).** Worn cards become the obvious
   thing to shred, yield probably wants to scale with condition, and the
   shredder must refuse `000001`. Cheap now, painful to retrofit.
4. **Trophy edition is the Gauntlet's reward** (§2) — visibly different,
   stat-identical, only obtainable by winning.
5. **Pin the card order before building on it.** *(done, v1.62.0 —
   `MobCards.ORDER_FINGERPRINT` is checked at startup and warns loudly if the
   declaration order has moved.)* The `#12 / 81` catalogue number
   is a *different thing* from a serial — it is the mob's fixed position in the
   set of 81, identical for every player, derived from declaration order in
   `MobCards`. Verified working (81 unique ordinals, 1–81, stable), but
   inserting a mob mid-list would silently renumber every card after it. Harmless
   today; bad once serials or a Hall of Fame depend on it.

### Audit (v1.62.0) — what of this is real

| Spec | State |
|---|---|
| Per-mob serials, atomic, never reused | `SerialRegistry` (SavedData) |
| Six-digit padding, configurable | `CardIdentityService.serialDigits()` |
| Unlocked by — UUID + name snapshot, immutable | `CardIdentity` |
| Non-stackable cards | shipped — `stacksTo(1)` |
| Condition, wear on hand entry only | `CardWear` + `ConditionTracker` |
| Never faked by death/respawn/dimension/sync | `ConditionTracker.seed()` |
| Card visibly degrades | 5 in-hand overlay stages + inspect overlay |
| Sleeve — one item, one card, never wears | flag on the card, not a container |
| Inspection with a Flip to the back | `CardInspectScreen` |
| Editions, art only, shared serial run | `CardEdition`, Trophy frame |
| Card order pinned | `ORDER_FINGERPRINT`, checked at startup |

**Two knowing departures, not omissions:**

1. **The sleeve is a flag, not a container.** The brief implies an item holding
   a card. A nested ItemStack opens a duplication window on every insert and
   remove; a flag cannot. The card stays one item through sleeving, so it is
   still playable, displayable and tradeable as itself.
2. **Counterfeits are detected by presence, not by ledger.** A card is authentic
   if it carries a server-issued uid. Catching a *copied* uid would need a
   registry of every uid ever issued, which grows without bound for the life of
   the world. Renaming cannot forge a card; op-level NBT editing can clone one.
   Worth revisiting only if that turns out to matter on a real server.

Wear was also retuned in v1.58.0 against the brief's "first entry free, 5 points
after": it is now **two** free handlings, and 5 points per **two** entries after
that. The brief's rate meant a card you had held twice was already 95%.

---

## 4. Knowing what you're missing

**Status:** BUILT (v1.62.0).

Today a missing mob shows a face-down card: you know *how many* you lack, not
*which*, so you can't plan a hunt and "kill everything" is the only strategy.
A full checklist would fix that but cost the discovery.

**Recommendation — neither extreme:**

- Missing cards show a **silhouette on their category backdrop**, labelled with
  the set. Enough to recognise "something big, ocean-dwelling" and go looking;
  the moment of *oh, it's an Elder Guardian* stays yours.
- **If you have ever killed that mob, the book names it.** Kills are already
  counted whether or not a card drops, so the data exists. The book then
  reflects what your character has genuinely encountered — you've met the thing,
  so hiding its name would just be the game being coy.
- The **card scanner** follows the same principle: point it at a mob and,
  because you are actually standing there looking at it, it tells you whether
  you still need it and your live drop odds. Knowledge from presence, not from
  a menu.

---

## 5. Smaller ideas, not yet explored

- **Trading Post** — dupes as credit toward sealed packs, or one specific
  missing card at a steep markup. Overlaps heavily with §1; probably one feature.
- **Trump rules** — one ability per card drawn from what the mob is (Creeper
  pots both cards on a loss, Enderman swaps top for bottom, Wolf peeks at a
  stat). Toggleable per table so the classic game survives.
- **Hall of Fame** — *(built v1.65.0.)* Three server-wide records, all
  append-only and permanent: who unlocked each mob's `000001`, who finished each
  category first, and who has completed all 81. Recorded at the moment the
  serial registry hands out number 1, so it is a fact about the world rather
  than about anyone's inventory — it stays true after the card is traded, and
  after it is shredded. A migrated card never claims a first, because its place
  in history cannot be proven. Reached from the dueling table; fetched on
  request rather than pushed, since these change rarely.

---

## Decisions already shipped (don't re-open without reason)

| Decision | Version |
|---|---|
| Rarity is a **lower-wins** stat — 1 beats 10, since it's the harder mob | 1.48.0 |
| Awards are **never** paid out automatically; you press Collect | 1.48.0 |
| One spawn egg per completed set, chosen from that set, once ever | 1.48.0 |
| PvP turns are 7 seconds | 1.47.0 / 1.49.0 |
| Drawn rounds are settled by a **coin flip**, not by the chooser picking again | 1.49.0 |
| Card drops scale on **tier**, not category: 1 in 20 / 10 / 5 / 2, legendary always | 1.50.0 |
| Pity guarantee at 3× the average wait, reset on every card | 1.50.0 |
| CPU decks match your holo level and never deal mobs you're holding | 1.51.0 |
