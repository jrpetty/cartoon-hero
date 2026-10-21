extends TestCase
# Ported from game/src/ui/teams.test.ts.
#
# Teams used to be one switch that split seats by parity — 1, 3, 5, 7 against
# 2, 4, 6, 8 — so nobody could tell why they were on the side they were on.
# Now every seat picks its team, and presets fill them in organised blocks.

const H := preload("res://tests/meta_helpers.gd")


func same(a, b, msg: String) -> void:
	check(H.deep_eq(a, b), "%s: expected %s, got %s" % [msg, str(b), str(a)])


func test_fills_presets_in_contiguous_blocks_larger_teams_first() -> void:
	same(Teams.blockTeams(8, 2), [1, 1, 1, 1, 2, 2, 2, 2], "8 in 2")
	same(Teams.blockTeams(8, 4), [1, 1, 2, 2, 3, 3, 4, 4], "8 in 4")
	same(Teams.blockTeams(8, 3), [1, 1, 1, 2, 2, 2, 3, 3], "8 in 3")
	same(Teams.blockTeams(6, 2), [1, 1, 1, 2, 2, 2], "6 in 2")
	same(Teams.coopTeams(6, 2), [1, 1, 1, 2, 2, 2], "co-op")


func test_turns_teams_into_alliances_solos_each_on_their_own() -> void:
	var a := Teams.alliancesFor([1, 1, 0, 2, 0, 2])
	eq(a[0], a[1], "team 1 shares")
	eq(a[3], a[5], "team 2 shares")
	var distinct := {}
	for i in [0, 2, 3, 4]:
		distinct[a[i]] = true
	eq(distinct.size(), 4, "four sides")


func test_names_the_format() -> void:
	eq(Teams.formatLabel(Teams.blockTeams(8, 2)), "4 v 4")
	eq(Teams.formatLabel(Teams.blockTeams(8, 4)), "2 v 2 v 2 v 2")
	eq(Teams.formatLabel([1, 1, 1, 0, 0]), "3 v 1 v 1")
	eq(Teams.formatLabel(Teams.freeForAll(4)), "4-player free-for-all")
	eq(Teams.formatLabel(Teams.freeForAll(2)), "1 v 1")
	# Seven teams of one and a pair — as many teams as you like.
	eq(Teams.sides([1, 2, 3, 4, 5, 6, 7, 7]).size(), 7)


func test_refuses_a_match_with_only_one_side() -> void:
	eq(Teams.teamsValid([1, 1, 1, 1]), false)
	eq(Teams.teamsValid([1, 1, 1, 0]), true)
	eq(Teams.teamsValid(Teams.freeForAll(2)), true)


func test_resizes_without_losing_seats_choices() -> void:
	same(Teams.resizeTeams([1, 2, 2], 5), [1, 2, 2, 0, 0], "grow")
	same(Teams.resizeTeams([1, 2, 2, 1], 2), [1, 2], "shrink")


func test_json_teams_read_the_same() -> void:
	# A config loaded from a save holds floats; the sides are the same.
	eq(Teams.formatLabel([1.0, 1.0, 2.0, 2.0]), "2 v 2")
	same(Teams.alliancesFor([1.0, 0.0]), [0, 8], "alliances from floats")
