// Team layouts for a match: who plays with whom.
//
// Teams used to be one switch — "Even Teams" — that split the seats by parity,
// so players 1, 3, 5, 7 fought 2, 4, 6, 8. Nobody could see why they were on
// the team they were on, and nothing but an even two-way split was possible.
// Now every seat has a team of its own choosing: a number (1–8) shared with
// allies, or 0 to fight alone. Presets fill it in organised blocks — seats 1–4
// against 5–8, three teams of three and a pair, two-a-side four ways — and any
// seat can then be moved by hand.

/** A seat's team: 0 fights alone; seats sharing 1..8 are allies. */
export type Teams = number[];

export const MAX_TEAMS = 8;

/** Team colours for rosters and lobbies: index 0 is "on your own". */
export const TEAM_COLORS = ["#9a917b", "#5b8fe0", "#d8574a", "#4ab86a", "#e0a83a", "#9a6ae0", "#3ac8c0", "#e08a4a", "#e06a9a"];

/** Every seat on its own. */
export function freeForAll(players: number): Teams {
  return Array.from({ length: players }, () => 0);
}

/**
 * `count` teams in contiguous blocks, as even as the numbers allow, larger
 * teams first: 8 players in 3 teams is 3, 3, 2 — seats 1–3, 4–6, 7–8.
 */
export function blockTeams(players: number, count: number): Teams {
  const k = Math.max(2, Math.min(count, players));
  const out: Teams = [];
  const base = Math.floor(players / k), extra = players % k;
  for (let t = 0; t < k; t++) {
    const size = base + (t < extra ? 1 : 0);
    for (let i = 0; i < size; i++) out.push(t + 1);
  }
  return out;
}

/** You and `allies` more seats on team 1, everyone else on team 2 (co-op). */
export function coopTeams(players: number, allies: number): Teams {
  return Array.from({ length: players }, (_, i) => (i <= allies ? 1 : 2));
}

/**
 * The sim's alliance id per seat. Seats on the same numbered team share one;
 * each solo seat gets one of its own, numbered after the teams.
 */
export function alliancesFor(teams: Teams): number[] {
  let next = MAX_TEAMS;
  return teams.map((t) => (t >= 1 ? t - 1 : next++));
}

/** The sides, as seat indices, largest first. */
export function sides(teams: Teams): number[][] {
  const groups = new Map<string, number[]>();
  teams.forEach((t, seat) => {
    const key = t >= 1 ? `t${t}` : `s${seat}`;
    const g = groups.get(key) ?? [];
    g.push(seat);
    groups.set(key, g);
  });
  return [...groups.values()];
}

/** "4 v 4", "2 v 2 v 2 v 2", "3 v 1 v 1", or "Free-for-all". */
export function formatLabel(teams: Teams): string {
  const s = sides(teams);
  if (s.every((g) => g.length === 1)) return teams.length === 2 ? "1 v 1" : `${teams.length}-player free-for-all`;
  return s.map((g) => g.length).sort((a, b) => b - a).join(" v ");
}

/** A match needs at least two sides. */
export function teamsValid(teams: Teams): boolean {
  return sides(teams).length >= 2;
}

/** Resize to `players` seats, keeping what can be kept. */
export function resizeTeams(teams: Teams, players: number): Teams {
  const out = teams.slice(0, players);
  while (out.length < players) out.push(0);
  return out;
}
