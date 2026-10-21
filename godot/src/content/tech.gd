## content/tech.ts — the functions half of the tech module. The data (AGES,
## AGE_SHORT, MAX_AGE, UPGRADES) lives in `C`.
class_name Tech


## "Hearth", "Banner", "Crown", "Empire" — for tables and tight labels.
static func ageShort(i: int) -> String:
	var ages: Array = C.AGES
	if i >= 0 and i < ages.size():
		return ages[i].short
	return "—"
