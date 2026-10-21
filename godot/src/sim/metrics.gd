## sim/metrics.ts — per-team performance metrics for the in-match scoreboard
## and post-match graphs. Pure read-only derivation from world state — never
## mutates the sim, so it's safe to call every frame and from either client in
## a net game.
##
## Contract: every structure here is a Dictionary with exactly the TS field
## names, in the TS order —
##   TeamMetrics  {team, score, military, economy, stockpile, villagers, army,
##                 killed, lost, razed, lostBuildings, age, defeated}
##   SideReport   (see emptySide)
##   PlayerReport SideReport + {team, group, relation, horde, won, defeated,
##                 defeatedAt, oaths, faction, ageTimes}
##   MatchReport  {you, foe, durationSec, mapName, players}
class_name Metrics


## Current combat strength of a team's standing army (attack + survivability):
## {military, villagers, army}.
static func militaryStrength(world: World, team: int) -> Dictionary:
	var military := 0.0
	var villagers := 0
	var army := 0
	for e: Entity in world.entities:
		if not e.alive or e.team != team or e.kind != T.Kind.Unit:
			continue
		if e.type == "villager":
			villagers += 1
			continue
		army += 1
		var u = world.UNITS.get(e.type)
		military += (u.attack if u != null else 4.0) + e.hp * 0.1 + (u.pop if u != null else 1.0) * 4.0
	return {"military": JS.round(military), "villagers": villagers, "army": army}


static func teamMetrics(world: World, team: int) -> Dictionary:
	var p = world.player(team)
	var ms := militaryStrength(world, team)
	var military: float = ms.military
	var villagers: int = ms.villagers
	var army: int = ms.army
	var s: Dictionary = p.stats
	var stockpile := JS.round(p.resources.food + p.resources.wood + p.resources.gold)
	# Composite score: economy worked, fights won, territory taken, tech & age
	# climbed, plus the army currently fielded. Tuned so good play trends up.
	var score := JS.round(
		s.gathered * 0.1 +
		s.unitsKilled * 8.0 +
		s.buildingsRazed * 20.0 +
		p.age * 120.0 +
		p.upgrades.size() * 25.0 +
		military +
		villagers * 5.0,
	)
	return {
		"team": team,
		"score": score,
		"military": military,
		"economy": s.gathered,
		"stockpile": stockpile,
		"villagers": villagers,
		"army": army,
		"killed": s.unitsKilled,
		"lost": s.unitsLost,
		"razed": s.buildingsRazed,
		"lostBuildings": s.buildingsLost,
		"age": p.age,
		"defeated": p.defeated,
	}


## Snapshot all player teams at one moment (for the time-series history).
static func snapshotMetrics(world: World) -> Array:
	var out: Array = []
	for t in world.numTeams:
		out.append(teamMetrics(world, t))
	return out


# ---------------------------------------------------------------------------
# The end-of-match report: the whole ledger for both sides, so the screen can
# show the comparison that actually explains the result.

static func emptyMatchReport() -> Dictionary:
	return {"you": emptySide(), "foe": emptySide(), "durationSec": 0.0, "mapName": ""}


static func emptySide() -> Dictionary:
	return {
		"teams": [], "score": 0.0, "gathered": 0.0, "gatheredBy": {"food": 0.0, "wood": 0.0, "gold": 0.0},
		"spent": 0.0, "spentOn": {"units": 0.0, "buildings": 0.0, "tech": 0.0}, "banked": 0.0,
		"unitsKilled": 0.0, "unitsLost": 0.0, "buildingsRazed": 0.0, "buildingsLost": 0.0,
		"damageDealt": 0.0, "damageTaken": 0.0, "peakArmy": 0.0, "peakVillagers": 0.0, "idleVillagerTime": 0.0,
		"idleTcTime": 0.0, "idleProductionTime": 0.0, "tcSeconds": 0.0, "productionSeconds": 0.0,
		"age": 0, "upgrades": 0, "trainedByType": {}, "lostByType": {}, "killedByType": {}, "builtByType": {},
		"killsByUnit": {}, "damageByUnit": {}, "razedByUnit": {},
		"firstHitAt": -1.0, "firstKillAt": -1.0, "firstRazeAt": -1.0,
		"firstAttackAt": -1.0, "firstDefendAt": -1.0, "firstFieldAt": -1.0, "damageAttacking": 0.0, "damageDefending": 0.0, "damageField": 0.0, "killsAttacking": 0.0, "killsDefending": 0.0, "openerByUnit": {},
	}


static func addInto(dst: Dictionary, src: Dictionary) -> void:
	for k in src:
		dst[k] = float(dst.get(k, 0.0)) + float(src[k])


static func _earliest(a: float, b) -> float:
	if b == null or float(b) < 0:
		return a
	return float(b) if a < 0 else minf(a, float(b))


## Fold one team's ledger into a side.
static func accumulate(side: Dictionary, world: World, team: int) -> void:
	var p = world.player(team)
	var s: Dictionary = p.stats
	side.teams.append(team)
	side.score += teamMetrics(world, team).score
	side.gathered += s.gathered
	side.gatheredBy.food += s.gatheredBy.food
	side.gatheredBy.wood += s.gatheredBy.wood
	side.gatheredBy.gold += s.gatheredBy.gold
	side.spent += s.resourcesSpent
	side.spentOn.units += s.spentOn.units
	side.spentOn.buildings += s.spentOn.buildings
	side.spentOn.tech += s.spentOn.tech
	side.banked += JS.round(p.resources.food + p.resources.wood + p.resources.gold)
	side.unitsKilled += s.unitsKilled
	side.unitsLost += s.unitsLost
	side.buildingsRazed += s.buildingsRazed
	side.buildingsLost += s.buildingsLost
	side.damageDealt += JS.round(s.damageDealt)
	side.damageTaken += JS.round(s.damageTaken)
	side.peakArmy += s.peakArmy
	side.peakVillagers += s.peakVillagers
	side.idleVillagerTime += s.idleVillagerTime
	side.idleTcTime += s.idleTcTime
	side.idleProductionTime += s.idleProductionTime
	side.tcSeconds += s.tcSeconds
	side.productionSeconds += s.productionSeconds
	side.age = maxi(side.age, p.age)
	side.upgrades += p.upgrades.size()
	addInto(side.trainedByType, s.trainedByType)
	addInto(side.lostByType, s.lostByType)
	addInto(side.killedByType, s.killedByType)
	addInto(side.builtByType, s.builtByType)
	addInto(side.killsByUnit, s.get("killsByUnit", {}))
	addInto(side.damageByUnit, s.get("damageByUnit", {}))
	addInto(side.razedByUnit, s.get("razedByUnit", {}))
	side.firstHitAt = _earliest(side.firstHitAt, s.get("firstHitAt"))
	side.firstKillAt = _earliest(side.firstKillAt, s.get("firstKillAt"))
	side.firstRazeAt = _earliest(side.firstRazeAt, s.get("firstRazeAt"))
	side.firstAttackAt = _earliest(side.firstAttackAt, s.get("firstAttackAt"))
	side.firstDefendAt = _earliest(side.firstDefendAt, s.get("firstDefendAt"))
	side.firstFieldAt = _earliest(side.firstFieldAt, s.get("firstFieldAt"))
	side.damageAttacking += float(s.get("damageAttacking", 0.0))
	side.damageDefending += float(s.get("damageDefending", 0.0))
	side.damageField += float(s.get("damageField", 0.0))
	side.killsAttacking += float(s.get("killsAttacking", 0.0))
	side.killsDefending += float(s.get("killsDefending", 0.0))
	addInto(side.openerByUnit, s.get("openerByUnit", {}))


## Snapshot the whole match as "your alliance versus theirs". Allies are folded
## together because that is how the game was actually played and won.
static func matchReport(world: World, me: int, mapName: String) -> Dictionary:
	var you := emptySide()
	var foe := emptySide()
	var players: Array = []
	var winner = world.winner
	for t in world.numTeams:
		var team: int = t
		if world.areAllied(me, team):
			accumulate(you, world, team)
		elif world.areHostile(me, team):
			accumulate(foe, world, team)
		var p = world.player(team)
		var own := emptySide()
		accumulate(own, world, team)
		var pr := own.duplicate()
		pr["team"] = team
		pr["group"] = world.alliances[t] if t < world.alliances.size() else t
		pr["relation"] = "you" if team == me else ("ally" if world.areAllied(me, team) else "enemy")
		pr["horde"] = t == world.hordeTeam
		pr["won"] = winner != null and winner != T.Team.Neutral and world.areAllied(winner, team)
		pr["defeated"] = p.defeated
		pr["defeatedAt"] = p.defeatedAt
		pr["oaths"] = p.oaths.duplicate()
		pr["faction"] = p.faction
		pr["ageTimes"] = p.ageTimes.duplicate() if p.ageTimes.size() > 0 else [0.0]
		players.append(pr)
	return {"you": you, "foe": foe, "durationSec": world.time, "mapName": mapName, "players": players}
