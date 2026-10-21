## sim/carousel.ts — Warband Tactics carousel: the shared draft round, and the
## lobby's great equaliser. Every few rounds a ring of units rides out, each
## already carrying a component, and everyone still standing takes one. Pick
## order runs from the weakest warband to the strongest: if you're winning you
## pick last, from whatever the field left behind.
##
## A CarouselPick is a Dictionary {type, star, item} (`item` is the component
## the unit rides out carrying).
class_name Carousel

## How many options ride out — enough that even the last picker has a choice.
const CAROUSEL_SIZE := 6


## Draft rounds: one every fifth round, offset from camps and augment picks.
static func isDraftRound(round: int) -> bool:
	return round % 5 == 3


## The tiers on offer, which climb as the run goes on.
static func bandForRound(round: int) -> Array:
	var step := floori(round / 5.0) # 0 at round 3, 1 at round 8, 2 at 13…
	if step <= 0:
		return [1, 2]
	if step == 1:
		return [2, 3]
	if step == 2:
		return [3, 4]
	return [4, 5]


## Roll the ring of units, each carrying a component.
static func rollCarousel(rng: RNG, round: int, size: int = CAROUSEL_SIZE) -> Array:
	var band := bandForRound(round)
	var out: Array = []
	for i in size:
		var tier: int = band[rng.int_(0, band.size() - 1)]
		var pool: Array = C.TIER_UNITS[tier] if C.TIER_UNITS[tier].size() > 0 else C.TIER_UNITS[1]
		if pool.is_empty():
			continue
		var type: String = pool[rng.int_(0, pool.size() - 1)]
		out.append({
			"type": type,
			"star": 1,
			"item": C.COMPONENT_IDS[rng.int_(0, C.COMPONENT_IDS.size() - 1)],
		})
	return out


## How strong a carousel option looks, for the AI's greedy pick (and for
## ordering what's left). Higher tier is better; ties break on the component.
static func pickValue(p: Dictionary) -> float:
	var def = C.UNITS.get(p.type)
	return float(UnitTiers.tierOf(p.type)) * 10.0 + (float(def.attack) if def != null else 0.0) / 10.0
