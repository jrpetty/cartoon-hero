## One match's verdict: what went well and what cost you, in plain sentences,
## from the end-of-match report. Every line is a comparison with the realm(s)
## you actually played, so "good" means better than them, not than a guess.
##
## The report is the MatchReport Dictionary Metrics.matchReport() builds (TS
## field names). Feedback: { good: String[], bad: String[] }.
class_name Feedback


static func mmss(s: float) -> String:
	return JS.num(floor(s / 60.0)) + ":" + JS.num(floor(fmod(s, 60.0))).lpad(2, "0")


static func pct(x: float) -> String:
	return JS.num(JS.round(x * 100.0)) + "%"


static func num(n: float) -> String:
	return JS.locale_int(JS.round(n))


static func _n(d: Dictionary, key: String, fallback := 0.0) -> float:
	return float(JS.nz(d.get(key), fallback))


static func matchFeedback(r: Dictionary) -> Dictionary:
	var you: Dictionary = r.you
	var foe: Dictionary = r.foe
	var good: Array = []
	var bad: Array = []
	var min_ := maxf(1.0, r.durationSec / 60.0)
	var enemies: Array = (JS.nz(r.get("players"), []) as Array).filter(func(p): return p.relation == "enemy" and not p.get("horde"))
	var nFoe := maxi(1, enemies.size() if enemies.size() > 0 else (foe.teams.size() if foe.teams.size() > 0 else 1))
	var nYou := maxi(1, you.teams.size())
	var per := func(x: float, n: int) -> float: return x / n

	# Economy.
	var eco: float = per.call(you.gathered, nYou) / maxf(1.0, per.call(foe.gathered, nFoe))
	if foe.gathered > 0 and eco >= 1.15:
		good.append({"s": "You out-gathered them %s× (%s a minute)." % [JS.to_fixed(eco, 1), num(you.gathered / min_)], "w": eco - 1})
	elif foe.gathered > 0 and eco <= 0.85:
		bad.append({"s": "They out-gathered you — your income was %s of theirs." % pct(eco), "w": 1 - eco})
	var tcIdle: float = float(you.idleTcTime) / you.tcSeconds if you.tcSeconds > 0 else 0.0
	if r.durationSec >= 300:
		if tcIdle <= 0.1:
			good.append({"s": "Your Town Center was busy all game (%s idle)." % pct(tcIdle), "w": 0.3})
		elif tcIdle >= 0.3:
			bad.append({"s": "Your Town Center sat idle %s of the match — keep villagers queued." % pct(tcIdle), "w": tcIdle})
	var vilMin: float = you.peakVillagers * min_
	var vIdle: float = you.idleVillagerTime / 60.0 / vilMin if vilMin > 0 else 0.0
	if vIdle >= 0.12:
		bad.append({"s": "Villagers stood idle for %s villager-minutes." % JS.num(JS.round(you.idleVillagerTime / 60.0)), "w": vIdle * 2})
	if you.banked >= maxf(1500.0, you.gathered * 0.25):
		bad.append({"s": "%s resources were never spent — more production buildings." % num(you.banked), "w": 0.5})

	# Fighting.
	var kd: float = float(you.unitsKilled) / you.unitsLost if you.unitsLost else float(you.unitsKilled)
	if you.unitsKilled + you.unitsLost >= 8:
		if kd >= 1.4:
			good.append({"s": "You won the fights: %s kills for %s losses." % [JS.num(you.unitsKilled), JS.num(you.unitsLost)], "w": kd - 1})
		elif kd <= 0.7:
			bad.append({"s": "You lost the fights: %s kills for %s losses." % [JS.num(you.unitsKilled), JS.num(you.unitsLost)], "w": 1 / maxf(0.1, kd) - 1})
	var vilLost := _n(you.lostByType, "villager")
	if vilLost >= 8:
		bad.append({"s": "%s of your villagers were killed — guard the woodline and gold." % JS.num(vilLost), "w": vilLost / 10})
	var vilKilled := _n(foe.lostByType, "villager")
	if vilKilled >= 8:
		good.append({"s": "You killed %s of their villagers." % JS.num(vilKilled), "w": vilKilled / 10})

	# Attacking and defending.
	if foe.firstAttackAt >= 0 and foe.firstAttackAt <= 480:
		if you.killsDefending >= foe.killsAttacking and you.buildingsLost <= 1:
			good.append({"s": "You held their early attack at %s (%s kills at home for %s losses)." % [mmss(foe.firstAttackAt), JS.num(you.killsDefending), JS.num(foe.killsAttacking)], "w": 0.8})
		else:
			var lostB := (", %s buildings lost" % JS.num(you.buildingsLost)) if you.buildingsLost else ""
			bad.append({"s": "Their attack at %s hurt: %s of yours died at home%s." % [mmss(foe.firstAttackAt), JS.num(foe.killsAttacking), lostB], "w": 0.8})
	if you.firstAttackAt >= 0 and you.firstAttackAt <= 480:
		if you.killsAttacking > foe.killsDefending:
			good.append({"s": "Your early attack at %s paid off: %s kills in their base for %s losses." % [mmss(you.firstAttackAt), JS.num(you.killsAttacking), JS.num(foe.killsDefending)], "w": 0.8})
		elif foe.killsDefending >= 3:
			bad.append({"s": "Your early attack at %s was held: %s kills for %s losses." % [mmss(you.firstAttackAt), JS.num(you.killsAttacking), JS.num(foe.killsDefending)], "w": 0.7})
	if you.firstAttackAt < 0 and r.durationSec >= 900 and you.buildingsRazed == 0:
		bad.append({"s": "You never took the fight into their base.", "w": 0.5})
	if you.buildingsRazed >= 3:
		good.append({"s": "You razed %s of their buildings." % JS.num(you.buildingsRazed), "w": you.buildingsRazed / 6.0})
	if you.buildingsLost >= 4:
		bad.append({"s": "You lost %s buildings." % JS.num(you.buildingsLost), "w": you.buildingsLost / 8.0})

	# Ages and tech.
	var me = null
	for p in JS.nz(r.get("players"), []):
		if p.relation == "you":
			me = p
			break
	var ageAt := func(a, i := 1) -> float:
		return float(a[i]) if a is Array and i < a.size() and (a[i] is float or a[i] is int) and a[i] > 0 else -1.0
	var myBanner: float = ageAt.call(me.get("ageTimes") if me != null else null)
	var foeBanner: Array = enemies.map(func(p): return ageAt.call(p.get("ageTimes"))).filter(func(t): return t > 0)
	if myBanner > 0 and foeBanner.size() > 0:
		var avg: float = foeBanner.reduce(func(a, b): return a + b, 0.0) / foeBanner.size()
		if myBanner <= avg - 60:
			good.append({"s": "You reached the Banner Age %s before them (%s)." % [mmss(avg - myBanner), mmss(myBanner)], "w": 0.4})
		elif myBanner >= avg + 90:
			bad.append({"s": "You reached the Banner Age %s after them (%s)." % [mmss(myBanner - avg), mmss(myBanner)], "w": 0.4})
	elif myBanner < 0 and foeBanner.size() > 0 and r.durationSec >= 900:
		bad.append({"s": "You never left the Hearth Age.", "w": 0.6})
	var up: float = per.call(you.upgrades, nYou) / maxf(1.0, per.call(foe.upgrades, nFoe))
	if foe.upgrades > 0 and up >= 1.4 and you.upgrades >= 4:
		good.append({"s": "You out-researched them (%s technologies)." % JS.num(you.upgrades), "w": 0.3})
	elif you.upgrades + 3 <= float(foe.upgrades) / nFoe * nYou:
		bad.append({"s": "You researched %s technologies to their %s." % [JS.num(you.upgrades), JS.num(JS.round(float(foe.upgrades) / nFoe))], "w": 0.3})

	# The unit that carried it.
	var kills: Array = []
	for u in you.killsByUnit:
		if u != "villager":
			kills.append([u, you.killsByUnit[u]])
	JS.sort_by_cmp(kills, func(a, b): return float(b[1]) - float(a[1]))
	if kills.size() > 0 and kills[0][1] >= 5:
		var best: Array = kills[0]
		var def = C.UNITS.get(best[0])
		good.append({"s": "Your %s did the most work: %s kills." % [def.name if def != null else best[0], JS.num(best[1])], "w": 0.2})

	var top := func(xs: Array) -> Array:
		JS.sort_by_cmp(xs, func(a, b): return b.w - a.w)
		return xs.slice(0, 5).map(func(x): return x.s)
	return {"good": top.call(good), "bad": top.call(bad)}
