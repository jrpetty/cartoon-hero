# Mob Trumps — design notes

Ideas that have been thought through but not built, plus the decisions already
taken so they don't get re-litigated. Everything here is a proposal unless it
says otherwise.

---

## 1. Card recycler — dupes into fragments

**Status:** designed, not built. Waiting on two decisions (see below).

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
  stacking up. Same reasoning as the coin-flip animation.

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

### The one open question

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

## 2. The Gauntlet — a campaign against the CPU

**Status:** designed, deferred. "Cool in the future but not now."

Ten themed CPU decks in ladder order (Farm Animals → … → Bosses), each beatable
once for a reward and a badge, the CPU sharpening as you climb.

### The decision at the heart of it

**Does the player have to field a themed deck too?** If not, you bring Warden /
Wither / Ender Dragon to every round and the ladder collapses into one strategy.
If so, the gauntlet becomes a **collection check with a scoreboard**: to beat the
Farm round you must have actually collected farm animals.

That also solves unlocking for free — a round opens when you own enough of its
set to field a deck. No arbitrary "beat 3 to unlock 4", no dead ends if you hate
a category, and it fires right where the set-completion reward already does.

### Decisions taken

- **Uneven set sizes are fine** — no padding, no special-casing. Farm is 14 mobs,
  End and Bosses are 3. A 3-card boss duel is short and brutal, which suits it.
- **Holo matching: settled and already shipped** in v1.51.0 (see below). The CPU
  levels its deck to match yours and brings different mobs.

### Still open

- Themed player decks — yes or no? Everything above assumes yes.
- Strict ladder order, or free choice among unlocked rounds? Recommendation:
  collection-gated with free choice; the ladder is just presentation.

### Notes

- Small sets mean both sides may draw the same mob, and identical cards tie on
  every stat. For large sets, deal both decks from one shared pool so no card
  appears twice. For 3-mob sets overlap is unavoidable.
- The CPU runs out of brains before the ladder runs out of rungs (only three AI
  levels). Two knobs that need no new AI: **card advantage** ("the Wither deals
  itself two extra cards"), and a fourth brain worth writing — **card counting**,
  which is honest in a themed gauntlet because the CPU genuinely knows the pool
  both decks came from.
- **Reward idea:** the uploaded spec already defines a **Trophy** edition. A
  gauntlet round awarding the Trophy-edition card of a mob from that set gives
  the ladder a purpose materials never will — visibly different, stat-identical,
  breaks nothing. Wants the edition system to exist first.
- **Second life** so it isn't dead after one clear: a **Gauntlet Run** (all ten
  in sequence, one life, times on the existing leaderboard), plus per-round
  medals for clean wins.

---

## 3. Condition, serials and sleeves

**Status:** agreed in full, **nothing built**. The largest outstanding item.

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
5. **Pin the card order before building on it.** The `#12 / 81` catalogue number
   is a *different thing* from a serial — it is the mob's fixed position in the
   set of 81, identical for every player, derived from declaration order in
   `MobCards`. Verified working (81 unique ordinals, 1–81, stable), but
   inserting a mob mid-list would silently renumber every card after it. Harmless
   today; bad once serials or a Hall of Fame depend on it.

---

## 4. Knowing what you're missing

**Status:** designed, not built.

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
- **Hall of Fame** — server-wide: rarest complete sets, first to finish each
  category. Much more interesting once serials exist and "who owns
  CREEPER-000001" is a real question.

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
