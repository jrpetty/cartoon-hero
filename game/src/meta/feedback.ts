// One match's verdict: what went well and what cost you, in plain sentences,
// from the end-of-match report. Every line is a comparison with the realm(s)
// you actually played, so "good" means better than them, not than a guess.

import type { MatchReport } from "../sim/metrics";
import { UNITS } from "../content/units";

const mmss = (s: number) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;
const pct = (x: number) => `${Math.round(x * 100)}%`;
const num = (n: number) => Math.round(n).toLocaleString("en-GB");

export interface Feedback { good: string[]; bad: string[] }

export function matchFeedback(r: MatchReport): Feedback {
  const { you, foe } = r;
  const good: { s: string; w: number }[] = [], bad: { s: string; w: number }[] = [];
  const min = Math.max(1, r.durationSec / 60);
  const enemies = (r.players ?? []).filter((p) => p.relation === "enemy" && !p.horde);
  const nFoe = Math.max(1, enemies.length || foe.teams.length || 1);
  const nYou = Math.max(1, you.teams.length);
  const per = (x: number, n: number) => x / n;

  // Economy.
  const eco = per(you.gathered, nYou) / Math.max(1, per(foe.gathered, nFoe));
  if (foe.gathered > 0 && eco >= 1.15) good.push({ s: `You out-gathered them ${eco.toFixed(1)}× (${num(you.gathered / min)} a minute).`, w: eco - 1 });
  else if (foe.gathered > 0 && eco <= 0.85) bad.push({ s: `They out-gathered you — your income was ${pct(eco)} of theirs.`, w: 1 - eco });
  const tcIdle = you.tcSeconds > 0 ? you.idleTcTime / you.tcSeconds : 0;
  if (r.durationSec >= 300) {
    if (tcIdle <= 0.1) good.push({ s: `Your Town Center was busy all game (${pct(tcIdle)} idle).`, w: 0.3 });
    else if (tcIdle >= 0.3) bad.push({ s: `Your Town Center sat idle ${pct(tcIdle)} of the match — keep villagers queued.`, w: tcIdle });
  }
  const vilMin = you.peakVillagers * min;
  const vIdle = vilMin > 0 ? you.idleVillagerTime / 60 / vilMin : 0;
  if (vIdle >= 0.12) bad.push({ s: `Villagers stood idle for ${Math.round(you.idleVillagerTime / 60)} villager-minutes.`, w: vIdle * 2 });
  if (you.banked >= Math.max(1500, you.gathered * 0.25)) bad.push({ s: `${num(you.banked)} resources were never spent — more production buildings.`, w: 0.5 });

  // Fighting.
  const kd = you.unitsLost ? you.unitsKilled / you.unitsLost : you.unitsKilled;
  if (you.unitsKilled + you.unitsLost >= 8) {
    if (kd >= 1.4) good.push({ s: `You won the fights: ${you.unitsKilled} kills for ${you.unitsLost} losses.`, w: kd - 1 });
    else if (kd <= 0.7) bad.push({ s: `You lost the fights: ${you.unitsKilled} kills for ${you.unitsLost} losses.`, w: 1 / Math.max(0.1, kd) - 1 });
  }
  const vilLost = you.lostByType.villager ?? 0;
  if (vilLost >= 8) bad.push({ s: `${vilLost} of your villagers were killed — guard the woodline and gold.`, w: vilLost / 10 });
  const vilKilled = foe.lostByType.villager ?? 0;
  if (vilKilled >= 8) good.push({ s: `You killed ${vilKilled} of their villagers.`, w: vilKilled / 10 });

  // Attacking and defending.
  if (foe.firstAttackAt >= 0 && foe.firstAttackAt <= 480) {
    if (you.killsDefending >= foe.killsAttacking && you.buildingsLost <= 1) good.push({ s: `You held their early attack at ${mmss(foe.firstAttackAt)} (${you.killsDefending} kills at home for ${foe.killsAttacking} losses).`, w: 0.8 });
    else bad.push({ s: `Their attack at ${mmss(foe.firstAttackAt)} hurt: ${foe.killsAttacking} of yours died at home${you.buildingsLost ? `, ${you.buildingsLost} buildings lost` : ""}.`, w: 0.8 });
  }
  if (you.firstAttackAt >= 0 && you.firstAttackAt <= 480) {
    if (you.killsAttacking > foe.killsDefending) good.push({ s: `Your early attack at ${mmss(you.firstAttackAt)} paid off: ${you.killsAttacking} kills in their base for ${foe.killsDefending} losses.`, w: 0.8 });
    else if (foe.killsDefending >= 3) bad.push({ s: `Your early attack at ${mmss(you.firstAttackAt)} was held: ${you.killsAttacking} kills for ${foe.killsDefending} losses.`, w: 0.7 });
  }
  if (you.firstAttackAt < 0 && r.durationSec >= 900 && you.buildingsRazed === 0) bad.push({ s: "You never took the fight into their base.", w: 0.5 });
  if (you.buildingsRazed >= 3) good.push({ s: `You razed ${you.buildingsRazed} of their buildings.`, w: you.buildingsRazed / 6 });
  if (you.buildingsLost >= 4) bad.push({ s: `You lost ${you.buildingsLost} buildings.`, w: you.buildingsLost / 8 });

  // Ages and tech.
  const me = r.players?.find((p) => p.relation === "you");
  const ageAt = (a?: number[], i = 1) => (a && typeof a[i] === "number" && a[i] > 0 ? a[i] : -1);
  const myBanner = ageAt(me?.ageTimes);
  const foeBanner = enemies.map((p) => ageAt(p.ageTimes)).filter((t) => t > 0);
  if (myBanner > 0 && foeBanner.length) {
    const avg = foeBanner.reduce((a, b) => a + b, 0) / foeBanner.length;
    if (myBanner <= avg - 60) good.push({ s: `You reached the Banner Age ${mmss(avg - myBanner)} before them (${mmss(myBanner)}).`, w: 0.4 });
    else if (myBanner >= avg + 90) bad.push({ s: `You reached the Banner Age ${mmss(myBanner - avg)} after them (${mmss(myBanner)}).`, w: 0.4 });
  } else if (myBanner < 0 && foeBanner.length && r.durationSec >= 900) bad.push({ s: "You never left the Hearth Age.", w: 0.6 });
  const up = per(you.upgrades, nYou) / Math.max(1, per(foe.upgrades, nFoe));
  if (foe.upgrades > 0 && up >= 1.4 && you.upgrades >= 4) good.push({ s: `You out-researched them (${you.upgrades} technologies).`, w: 0.3 });
  else if (you.upgrades + 3 <= foe.upgrades / nFoe * nYou) bad.push({ s: `You researched ${you.upgrades} technologies to their ${Math.round(foe.upgrades / nFoe)}.`, w: 0.3 });

  // The unit that carried it.
  const best = Object.entries(you.killsByUnit).filter(([u]) => u !== "villager").sort((a, b) => b[1] - a[1])[0];
  if (best && best[1] >= 5) good.push({ s: `Your ${UNITS[best[0]]?.name ?? best[0]} did the most work: ${best[1]} kills.`, w: 0.2 });

  const top = (xs: { s: string; w: number }[]) => xs.sort((a, b) => b.w - a.w).slice(0, 5).map((x) => x.s);
  return { good: top(good), bad: top(bad) };
}
