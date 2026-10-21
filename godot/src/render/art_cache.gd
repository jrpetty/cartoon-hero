## Sprite cache for the procedural art (placeholder; filled in below).
class_name ArtCache

static var enabled := false


static func drawUnitBase(_ctx: Ctx, _e: Entity, _tc: Dictionary) -> bool:
	return false


static func drawUnitBody(_ctx: Ctx, _e: Entity, _tc: Dictionary, _time: float, _ox: float, _oy: float, _moving: bool, _atkFrac: float, _lunge: float) -> bool:
	return false


static func drawBuildingArt(_ctx: Ctx, _e: Entity, _half: float, _tc: Dictionary, _time: float) -> bool:
	return false


static func drawConstruction(_ctx: Ctx, _e: Entity, _half: float, _tc: Dictionary, _time: float) -> bool:
	return false
