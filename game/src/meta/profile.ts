// Persistent player profile, stored in localStorage. Tracks XP, Renown, the
// owned variant collection, the equipped loadout and lifetime stats. All
// client-side — no backend, no real currency.

import { COLLECTIBLE_UNIT_IDS, variantKey } from "./catalog";
import { levelFromXp, levelUpRenown } from "./progression";

const STORAGE_KEY = "banner_and_blade_profile_v1";

export interface ProfileData {
  name: string;
  totalXp: number;
  renown: number;
  owned: string[]; // variant keys
  loadout: Record<string, number>; // unitId -> equipped rarity
  stats: { wins: number; losses: number; played: number; bestStreak: number; streak: number };
  openedChests: number;
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
  };
}

export class Profile {
  data: ProfileData;
  private ownedSet: Set<string>;

  constructor(data?: ProfileData) {
    this.data = data ?? defaultProfile();
    this.ownedSet = new Set(this.data.owned);
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
    if (changed) this.save();
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
