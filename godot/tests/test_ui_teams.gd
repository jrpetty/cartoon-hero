extends TestCase
# ui/teams.test.ts — team layouts: organised blocks, alliances, format labels.


func test_fills_presets_in_contiguous_blocks() -> void:
	eq(Teams.blockTeams(8, 2), [1, 1, 1, 1, 2, 2, 2, 2], "8 in 2")
	eq(Teams.blockTeams(8, 4), [1, 1, 2, 2, 3, 3, 4, 4], "8 in 4")
	eq(Teams.blockTeams(8, 3), [1, 1, 1, 2, 2, 2, 3, 3], "8 in 3")
	eq(Teams.blockTeams(6, 2), [1, 1, 1, 2, 2, 2], "6 in 2")
	eq(Teams.coopTeams(6, 2), [1, 1, 1, 2, 2, 2], "co-op")


func test_turns_teams_into_alliances_solos_on_their_own() -> void:
	var a := Teams.alliancesFor([1, 1, 0, 2, 0, 2])
	eq(a[0], a[1], "team 1 shares")
	eq(a[3], a[5], "team 2 shares")
	var d := {a[0]: true, a[2]: true, a[3]: true, a[4]: true}
	eq(d.size(), 4, "four distinct sides")


func test_names_the_format() -> void:
	eq(Teams.formatLabel(Teams.blockTeams(8, 2)), "4 v 4")
	eq(Teams.formatLabel(Teams.blockTeams(8, 4)), "2 v 2 v 2 v 2")
	eq(Teams.formatLabel([1, 1, 1, 0, 0]), "3 v 1 v 1")
	eq(Teams.formatLabel(Teams.freeForAll(4)), "4-player free-for-all")
	eq(Teams.formatLabel(Teams.freeForAll(2)), "1 v 1")
	eq(Teams.sides([1, 2, 3, 4, 5, 6, 7, 7]).size(), 7, "seven sides")


func test_refuses_a_match_with_only_one_side() -> void:
	eq(Teams.teamsValid([1, 1, 1, 1]), false)
	eq(Teams.teamsValid([1, 1, 1, 0]), true)
	eq(Teams.teamsValid(Teams.freeForAll(2)), true)


func test_resizes_without_losing_choices() -> void:
	eq(Teams.resizeTeams([1, 2, 2], 5), [1, 2, 2, 0, 0])
	eq(Teams.resizeTeams([1, 2, 2, 1], 2), [1, 2])
