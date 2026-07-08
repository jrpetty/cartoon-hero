# Voxelia MMO — Future Ideas (parking lot)

Design notes for features we've discussed but not built yet. Nothing here is
committed to a timeline — it's a backlog to pull from later.

---

## ✅ SHIPPED: Prestige / Ascension

Built and live: per-skill prestige at level 100 (resets the skill + its talents,
grants +1 permanent talent point per prestige, capped at 3), with prestige stars
on every surface, a two-click confirm button, a full-screen celebration +
particles/sound, and a server-wide announcement. Original design notes kept
below for reference.

**Goal:** give the mod a real endgame. Right now a skill caps at level 100 and
then XP is wasted; Prestige turns "max level" into a milestone, not a dead end.

**How it works (draft):**
- When a skill reaches level 100, the player can **ascend** it (a command
  `/voxelia prestige <skill>` and/or a button on the skills screen).
- Ascending **resets that skill to level 1** (XP back to 0) but grants a
  **permanent, stacking bonus** to that skill's power — e.g. +5% to its
  signature effect per ascension.
- Track an **ascension count per skill** (new `PlayerPrestige` data attachment,
  persisted + copied on death). Cap it (e.g. max 5 ascensions/skill) so it's not
  infinite.
- Show **prestige stars** next to the skill (menus, HUD) and in the chat title
  (e.g. `[✦✦ Master Miner]`). Cosmetic prestige identity.
- The ascension bonus feeds the same `TalentLogic`/`SkillEffects` multiplier path
  the talents use, so it stacks cleanly with existing systems.
- Config: `prestigeEnabled`, `prestigeBonusPerRank` (0.05), `prestigeMaxRank` (5),
  `prestigeRequiresMaxLevel` (true).

**Why it's good:** long-term goal, reuses existing multiplier plumbing, adds a
visible flex (stars), low risk. Pairs with the 20% death penalty for stakes.

**Open questions:** does ascending also refund/clear that skill's talent points?
Should there be a global "Character Prestige" for ascending all 11? Cosmetic-only
vs. real power creep — tune the bonus so it's meaningful but not broken.

---

## Parking lot (other brainstormed ideas)

### Endgame & progression
- **Specializations** — at level 50 a skill forks into a subclass you pick
  (Mining → Prospector vs Tunneler), each with unique perks. Build identity.
- **Paragon levels** — infinite tiny levels past 100 so XP is never wasted.
- **Capstone challenges** — a one-time challenge per skill unlocks a signature perk.

### Combat & danger
- **Mob difficulty scaling** — mobs get tougher as your level rises (pairs with
  the death penalty). Config-gated.
- **Skill bosses** — rare mini-bosses that drop skill essence/gear.
- **Ability synergies** — chaining two ultimates in a window triggers a bonus.
- **Dodge-roll / active block** — small combat-depth layer.

### Gear & items
- **Skill-scaling tools** — a pickaxe/rod/bow that grows stronger with its skill.
- **Skill essence** — a resource from activities, used to craft/upgrade gear.
- **Gem socketing** — socket gems into gear for skill bonuses (loot chase).
- **Respec token** & **XP-boost consumables** as craftable items.
- **Soulbound gear** that levels alongside you.

### New systems (flagship swings)
- **Magic / Spellcasting skill** — a 12th skill: mana, spell tomes, unlockable
  spells (blink, chain-lightning, heal, mining ray).
- **Attunement / Elements** — attune to Fire/Frost/Earth/Storm; changes how hits
  and abilities behave.
- **Homestead / claim bonuses** — a hearthstone that buffs XP/regen/crops in an area.
- **Beastmastery / tameable pets** — a companion that levels and fights/gathers.

### Content & world
- ~~Vein-miner / tree-feller~~ — **rejected** (decided against it for this mod).
- **Roguelike skill dungeons** — a portal to a level-scaled dungeon for loot/essence.
- **Shrines / totems** — placeable, buff a skill in a radius (great for co-op bases).
- **World events** — Blood Moon (danger + XP), Harvest Festival (farming boost),
  meteor showers (rare ore to race for).
- **Skill-gated content** — deep ores / rare fish that need a minimum level.

### Multiplayer & social
- **Party XP sharing** + party chat.
- **Guilds** with a shared level & bank.
- **Mentor bonus** — a high-level player near a low-level boosts their XP.

### Goals & meta
- **Daily bounties** — rotating tasks with chunky rewards.
- **Quest chain / storyline** — a guided path that teaches each skill.
- **Skill synergy web** — milestones in two skills unlock a hybrid perk.
- **Currency + shop** — earn "skill marks", spend at a vendor for tokens/boosts/cosmetics.

### Feel & QoL
- ~~Character profile screen~~ — **shipped** (`/voxelia profile` / P key).
- ~~Scoreboard sidebar toggle~~ — **shipped** (`/voxelia sidebar` / J key).
- **Rested XP** — bonus XP for a while after logging back in.
- **Milestone rewards** — a small one-time bonus at skill level 25/50/75.
- **Gravestones** — recover your dropped items from a grave.
- **Titles & particle auras**, per-skill level-up sounds, nameplate skill badges.
