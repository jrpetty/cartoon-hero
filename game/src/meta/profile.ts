// Persistent player profile, stored in localStorage. Tracks XP, Renown, the
// owned variant collection, the equipped loadout and lifetime stats. All
// client-side — no backend, no real currency.

import { COLLECTIBLE_UNIT_IDS, variantKey } from "./catalog";
import { levelFromXp, levelUpRenown } from "./progression";
import { COMMANDER_IDS } from "../content/commanders";
import { BOONS_BY_ID, BoonCategory, BOON_CATEGORIES } from "../content/boons";
import { boonKey } from "./boon_cache";

/** Boon slots unlock with account level: 1 → 2 (L5) → 3 (L12). */
const SLOT_UNLOCK_LEVELS = [1, 5, 12];
// Which category each slot belongs to, in unlock order.
const SLOT_CATEGORY: BoonCategory[] = ["offensive", "defensive", "supportive"];

const STORAGE_KEY = "banner_and_blade_profile_v1";

export interface ProfileData {
  name: string;
  totalXp: number;
  renown: number;
  owned: string[]; // variant keys
  loadout: Record<string, number>; // unitId -> equipped rarity
  stats: { wins: number; losses: number; played: number; bestStreak: number; streak: number };
  openedChests: number;
  commanders: string[]; // owned commander ids
  commander: string; // selected commander id
  /** First-launch reveal: the just-granted commander to show, or "". */
  commanderReveal: string;
  valor: number; // combat-earned currency for boon caches
  boons: string[]; // owned boon keys `boonId:rarity`
  equippedBoons: Record<BoonCategory, string>; // category -> equipped boonId ("" none)
}

function defaultProfile(): ProfileData {
  const owned: string[] = [];
  const loadout: Record<string, number> = {};
  // Everyone starts owning every Common variant, equipped by default.
  for (const id of COLLECTIBLE_UNIT_IDS) {
    owned.push(variantKey(id, 0));
    loadout[id] = 0;
  }
  return {
    name: "Commander",
    totalXp: 0,
    renown: 300,
    owned,
    loadout,
    stats: { wins: 0, losses: 0, played: 0, bestStreak: 0, streak: 0 },
    openedChests: 0,
    commanders: [],
    commander: "",
    commanderReveal: "",
    valor: 150,
    boons: [],
    equippedBoons: { offensive: "", defensive: "", supportive: "" },
  };
}

export class Profile {
  data: ProfileData;
  private ownedSet: Set<string>;
  private boonSet: Set<string>;

  constructor(data?: ProfileData) {
    this.data = data ?? defaultProfile();
    this.ownedSet = new Set(this.data.owned);
    this.boonSet = new Set(this.data.boons ?? []);
    this.migrate();
  }

  /** Ensure new content (units added later) still grants its Common variant. */
  private migrate() {
    let changed = false;
    for (const id of COLLECTIBLE_UNIT_IDS) {
      const k = variantKey(id, 0);
      if (!this.ownedSet.has(k)) {
        this.ownedSet.add(k);
        this.data.owned.push(k);
        changed = true;
      }
      if (this.data.loadout[id] === undefined) {
        this.data.loadout[id] = 0;
        changed = true;
      }
    }
    // Older saves predate commanders; back-fill the fields.
    if (!Array.isArray(this.data.commanders)) { this.data.commanders = []; changed = true; }
    if (this.data.commander === undefined) { this.data.commander = ""; changed = true; }
    if (this.data.commanderReveal === undefined) { this.data.commanderReveal = ""; changed = true; }
    // First launch: grant a random starter commander ("your first chest").
    if (this.data.commanders.length === 0) {
      const starter = COMMANDER_IDS[Math.floor(Math.random() * COMMANDER_IDS.length)];
      this.data.commanders.push(starter);
      this.data.commander = starter;
      this.data.commanderReveal = starter;
      changed = true;
    }
    if (!this.data.commander || !this.data.commanders.includes(this.data.commander)) {
      this.data.commander = this.data.commanders[0] ?? "";
      changed = true;
    }
    // Boons / Valor — back-fill for older saves.
    if (typeof this.data.valor !== "number") { this.data.valor = 150; changed = true; }
    if (!Array.isArray(this.data.boons)) { this.data.boons = []; this.boonSet = new Set(); changed = true; }
    if (!this.data.equippedBoons) {
      this.data.equippedBoons = { offensive: "", defensive: "", supportive: "" };
      changed = true;
    }
    if (changed) this.save();
  }

  // --- Boons & Valor --------------------------------------------------------
  addValor(n: number) {
    this.data.valor = Math.max(0, Math.round(this.data.valor + n));
  }

  spendValor(n: number): boolean {
    if (this.data.valor < n) return false;
    this.data.valor -= n;
    this.save();
    return true;
  }

  ownsBoon(key: string): boolean {
    return this.boonSet.has(key);
  }

  boonSetSnapshot(): Set<string> {
    return new Set(this.boonSet);
  }

  /** Add a boon to the collection. Returns true if newly owned. */
  grantBoon(key: string): boolean {
    if (this.boonSet.has(key)) return false;
    this.boonSet.add(key);
    this.data.boons.push(key);
    return true;
  }

  /** Highest owned rarity of a boon, or -1 if unowned. */
  bestBoonRarity(boonId: string): number {
    for (let r = 5; r >= 0; r--) if (this.boonSet.has(boonKey(boonId, r))) return r;
    return -1;
  }

  /** Number of boon slots unlocked at the current level (1..3). */
  boonSlots(): number {
    const lvl = this.level;
    let n = 0;
    for (const need of SLOT_UNLOCK_LEVELS) if (lvl >= need) n++;
    return n;
  }

  /** Categories currently unlocked, in order. */
  unlockedBoonCategories(): BoonCategory[] {
    return SLOT_CATEGORY.slice(0, this.boonSlots());
  }

  /** Equip an owned boon into its category slot (only if that slot is unlocked). */
  equipBoon(boonId: string, on: boolean) {
    const def = BOONS_BY_ID[boonId];
    if (!def) return;
    if (this.bestBoonRarity(boonId) < 0) return; // must own at least one tier
    if (!this.unlockedBoonCategories().includes(def.category)) return; // slot locked
    this.data.equippedBoons[def.category] = on ? boonId : "";
    this.save();
  }

  /** Boons to apply in a match: one per unlocked category, at best owned rarity. */
  equippedBoonLoadout(): { id: string; rarity: number }[] {
    const out: { id: string; rarity: number }[] = [];
    for (const cat of this.unlockedBoonCategories()) {
      const id = this.data.equippedBoons[cat];
      if (!id) continue;
      const r = this.bestBoonRarity(id);
      if (r >= 0) out.push({ id, rarity: r });
    }
    return out;
  }

  // --- Commanders -----------------------------------------------------------
  ownsCommander(id: string): boolean {
    return this.data.commanders.includes(id);
  }

  selectCommander(id: string) {
    if (this.ownsCommander(id)) {
      this.data.commander = id;
      this.save();
    }
  }

  /** Recruit a random un-owned commander for Renown. Returns its id, or "". */
  recruitCommander(cost: number): string {
    const locked = COMMANDER_IDS.filter((id) => !this.ownsCommander(id));
    if (locked.length === 0 || !this.spendRenown(cost)) return "";
    const id = locked[Math.floor(Math.random() * locked.length)];
    this.data.commanders.push(id);
    this.data.commanderReveal = id;
    this.save();
    return id;
  }

  clearCommanderReveal() {
    if (this.data.commanderReveal) {
      this.data.commanderReveal = "";
      this.save();
    }
  }

  static load(): Profile {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) return new Profile(JSON.parse(raw) as ProfileData);
    } catch {
      /* ignore corrupt storage */
    }
    return new Profile();
  }

  save() {
    this.data.owned = [...this.ownedSet];
    this.data.boons = [...this.boonSet];
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.data));
    } catch {
      /* storage may be unavailable; keep running in-memory */
    }
  }

  owns(key: string): boolean {
    return this.ownedSet.has(key);
  }

  ownedSetSnapshot(): Set<string> {
    return new Set(this.ownedSet);
  }

  /** Add a variant to the collection. Returns true if newly owned. */
  grant(key: string): boolean {
    if (this.ownedSet.has(key)) return false;
    this.ownedSet.add(key);
    this.data.owned.push(key);
    return true;
  }

  /** Highest owned rarity for a unit (for "auto-equip best"). */
  bestOwnedRarity(unitId: string): number {
    let best = 0;
    for (let r = 5; r >= 0; r--) {
      if (this.ownedSet.has(variantKey(unitId, r))) {
        best = r;
        break;
      }
    }
    return best;
  }

  equip(unitId: string, rarity: number) {
    if (this.ownedSet.has(variantKey(unitId, rarity))) {
      this.data.loadout[unitId] = rarity;
      this.save();
    }
  }

  equipBestAll() {
    for (const id of COLLECTIBLE_UNIT_IDS) {
      this.data.loadout[id] = this.bestOwnedRarity(id);
    }
    this.save();
  }

  /** Loadout used in a match: unitId -> equipped rarity (0 if fair mode). */
  matchLoadout(fairMode: boolean): Record<string, number> {
    if (fairMode) {
      const flat: Record<string, number> = {};
      for (const id of COLLECTIBLE_UNIT_IDS) flat[id] = 0;
      return flat;
    }
    return { ...this.data.loadout };
  }

  addRenown(n: number) {
    this.data.renown = Math.max(0, Math.round(this.data.renown + n));
  }

  spendRenown(n: number): boolean {
    if (this.data.renown < n) return false;
    this.data.renown -= n;
    this.save();
    return true;
  }

  get level(): number {
    return levelFromXp(this.data.totalXp).level;
  }

  levelInfo() {
    return levelFromXp(this.data.totalXp);
  }

  /** Apply XP, return number of levels gained and total level-up Renown granted. */
  addXp(xp: number): { levelsGained: number; renownFromLevels: number; newLevel: number } {
    const before = this.level;
    this.data.totalXp += Math.max(0, Math.round(xp));
    const after = this.level;
    let renownFromLevels = 0;
    for (let l = before; l < after; l++) renownFromLevels += levelUpRenown(l);
    this.addRenown(renownFromLevels);
    return { levelsGained: after - before, renownFromLevels, newLevel: after };
  }

  recordResult(win: boolean) {
    this.data.stats.played++;
    if (win) {
      this.data.stats.wins++;
      this.data.stats.streak++;
      this.data.stats.bestStreak = Math.max(this.data.stats.bestStreak, this.data.stats.streak);
    } else {
      this.data.stats.losses++;
      this.data.stats.streak = 0;
    }
  }
}
