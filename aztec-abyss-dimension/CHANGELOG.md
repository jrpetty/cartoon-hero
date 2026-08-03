# Changelog

Every change to the Arena Engine and the modes around it, newest first.

**This file is updated with every change.** If a commit touches the engine and is
not in here, that is a bug in the process, not an omission worth living with — the
whole point is that one file answers "what is different now".

Legend: **feat** new capability · **fix** something that did not work · **change**
behaviour that was already there · **docs**

---

## Unreleased

### Induction — nobody moves until they say what they are

- **feat** `MazeInduction` — arriving in the Glade now holds you where you stand
  until you take a trade. Arrival was the weakest moment in the whole loop: you
  appeared in the middle of a field with an empty inventory and no instructions,
  and the game's position was that you might like to read a sign at some point.
  Jobs were the spine of everything and were **optional**.
- **change** This is not a tutorial gate for its own sake. It is the one moment the
  whole group is standing in the same place with the same question in front of
  them, which is exactly when a party should be deciding who the Med-jack is.
- **feat** **Starter kits**, on the generous side of sensible. Materials are
  genuinely hard to come by here — no soil, no animals, an unbreakable floor — so
  arriving empty is not a challenge, it is an hour of nothing. Every kit carries
  bread, torches and something to swing, because those are survival rather than
  trade and a Track-hoe who cannot defend themselves at night is a casualty.
  - **Runner** — iron sword, leather helmet and boots, 32 torches, 8 signs, cooked
    beef, a golden apple. Light armour on purpose: a Runner in iron has stopped
    being able to outrun the thing chasing them.
  - **Builder** — iron axe, crafting table, 32 planks, 32 cobblestone, 16 sticks,
    8 iron, 12 coal, wool and carpet. Enough to build something on day one rather
    than spend it collecting sticks.
  - **Med-jack** — four bandages ready to use plus 24 string and 12 paper for a
    dozen more, two golden apples, stone sword. The point is being useful in the
    first five minutes, not after a week of accumulating string.
  - **Track-hoe** — iron hoe, water bucket, 24 wheat seeds, 12 each of carrots,
    potatoes and beetroot, 16 bone meal, saplings. Everything the Glade eats grows
    from this.
- **change** **One kit per game.** Changing trade later is free and gives you
  nothing, because a kit per switch is a vending machine. The Box's role crates
  cover anybody who changes their mind, one dawn later — a real cost measured in a
  day rather than a wasted week.
- **change** Kit state is **saved**, not held in memory. Otherwise a restart hands
  everybody a second kit for the price of a reconnect, which a group finds inside
  a day.
- **change** Creative and spectator are exempt — an operator fixing the map is not
  a Greenie.

### The Chart Floor — the maze from above, revealed only where you have been

- **feat** `ChartFloor` — a 42×42 sunken mosaic in the Glade you walk on. It
  **starts black**. The only thing visible on day one is the Glade's own square in
  the middle, because that is the only ground anybody has stood on. Everything
  else fills in **live** as Runners move: a corridor somebody ran appears as a
  line, a dead end as a stub, and the shape of the maze assembles itself out of
  other people's legs over a week.
- **change** Charting existed and had nowhere to go — it lived in a SavedData and
  could be printed into chat. That is a debug readout, not a map: you cannot stand
  round a chat message with four people and argue about which way the south
  passage bends.
- **feat** **It cannot draw what nobody walked.** Nothing in the renderer reads the
  world. A fog-of-war map that quietly knows the answer is a minimap, and a minimap
  makes Runners pointless — the only way a lane appears is that somebody walked it
  and came back.
- **feat** **Live Runner positions** in light blue, but only on the chart standing
  tonight. A dot on last Tuesday's chart would be a lie about where somebody is.
- **feat** **One chart per layout**, and the dial on the south edge turns the page.
  A route is only true on the layout it was found on, so a single shared chart
  cannot express the thing most worth writing down.
- **feat** Charts are labelled **Chart I, II, III in the order the Glade met them**
  — never by which day they are. Working out that Chart IV is the one that comes
  back every seventh night is something the Glade has to do for itself.
- **change** Pixels are sampled as a **union** over the ~2.3 cells each covers, not
  a midpoint. Sampling the middle cell would make single-width corridors flicker in
  and out depending on which side of a boundary they fell, and a map that drops
  lanes is worse than no map.
- **change** Refreshes **diff against what is drawn** and touch only what moved —
  1,764 blocks a second would not be acceptable, a handful is.
- **change** Geometry version 6: the floor is laid and the homestead moved east.
  The homestead can be anywhere; the map wants the biggest uninterrupted square in
  the clearing and there is exactly one of those.

### Skills — what you have become, as opposed to what you are

- **feat** `MazeSkills` — twelve skills, three per trade, three ranks each.
  `/maze skills` to read your sheet, `/maze learn <skill>` to spend, `/maze forget`
  to put every point in a trade back **free**. Jobs answered "what do you do here"
  and never answered "what have you become" — two Runners a fortnight in were
  identical, which makes a fortnight worth nothing.
- **feat** **Points come from doing the job.** Track-hoes earn by harvesting,
  Med-jacks by treating, Builders by making and marking, Runners by charting **and
  by distance covered** — the last one matters most, because a Runner's job is
  ground and the game only paid them for ground that was *new*. Sprinting a known
  route to check it still goes through earned nothing, which is backwards.
- **change** Each trade converts experience at **its own rate** (Med-jack 30,
  Builder 40, Track-hoe 50, Runner 55). A Med-jack treats six people in a game and
  a Track-hoe pulls three hundred carrots; charging both the same is how one trade
  becomes the obvious one to grind.
- **change** Every buff is deliberately **sideways, not upward**. Nothing raises
  your health, nothing lets you fight a Griever, nothing shortens the maze. Speed
  is hard-capped at II however you stack it, because Speed III plus sprinting
  outruns a Griever by enough that the night stops mattering.
- **feat** Forged gear now **carries its maker's skill in the object**. A sword
  does not get sharper because the smith studied afterwards, and does not get
  blunter when handed to somebody untrained.
- **feat** **Bandages.** `/maze bandage` turns 2 string and 1 paper into two — or
  **three for a Med-jack**, half again for the same materials, and theirs mend
  longer. The trade could hold off a Changing and, at the top, cure one; both are
  rare and dramatic and useless on an ordinary Tuesday. This is the small version
  of the same idea, and it turns the role from an emergency service into a supply
  line.
- **feat** Notable hooks: **Cartographer** charts up to three cells around you
  (feeding the Glade's shared map), **Antivenom** raises your sting threshold,
  **Second Wind** gives a Runner ten seconds once a day when it is genuinely going
  wrong, **Salvage** gets a Builder some of the Glade back when they rearrange it,
  **Green Thumb** takes the best farmer in the Glade rather than the sum — one
  field does not grow four times over because four people stand in it.

### The Box does the job it is named for

- **feat** `TheBox` — every dawn the lift comes up loaded. The Box was the
  centrepiece of the Glade, the thing every Glader arrived in, and a decorative
  cage. Meanwhile the maze gave you nothing to build with — no seeds, no saplings,
  no string, no iron, no way to replace a broken tool — so everyone's inventory
  only ever went down and the Glade could never become anything.
- **feat** The **staples crate** carries what the dimension structurally cannot:
  seeds and saplings (there is no soil out there), string, leather and feathers
  (those come off animals and there are none), iron, coal and cobblestone (the
  floor does not break). Generous on what you cannot otherwise have, thin on what
  you can.
- **feat** **Two cures in the first crate.** Day one is the only day nobody has had
  a chance to earn one, and a Glade whose first Changing is unanswerable teaches
  the wrong lesson about the entire system.
- **feat** **Role crates** — one per job somebody is actually doing. No Med-jack,
  no medical crate. That sounds harsh and is the point: jobs are a decision the
  group makes together, and a decision with no consequence is not one.
- **change** Scaled per head at **half rate**, not one-for-one. Four people do not
  eat four times the wheat, they clear the field four times as fast — so straight
  scaling would make a big Glade richer per person than a small one.
- **change** Crates **top up** rather than being replaced. A delivery system that
  quietly eats what you stored in it is one nobody stores anything in.

### Nightlife — the maze has something living in it

- **feat** `MazeNight` — 20 to 35 zombies, skeletons, spiders and husks through the
  corridors at dusk, cleared at dawn. The night had exactly one inhabitant and it
  was unkillable in practice, so a night you survived was a night in which nothing
  happened.
- **feat** **One night in nine is a bad one: eighty.** The rare night matters more
  than the common one — a dusk that is usually survivable and occasionally is not
  makes every dusk a real question.
- **feat** Skeletons and spiders are deliberately over-represented, because string,
  bone, arrows and gunpowder do not otherwise exist here. A Glade that wants bows,
  beds or bonemeal has to send somebody out after dark — a far better reason to be
  in the maze at night than "you failed to get back".
- **change** They scale with the day at **half** the Grievers' rate. These are the
  loot, and loot that outgrows the players stops being loot.

### The caches are worth the walk now

- **feat** Iron armour at tier 2 and up, the occasional diamond piece and iron
  sword in the far corners, plus string, arrows, leather, coal and seeds. Walking
  four hundred cells to find two steaks is not a reason to walk four hundred cells.

### The maze stops being square, and the exit stops being a gap in a wall

- **feat** `PortalAnnex` — every exit now **protrudes out of the square**. A walled
  lane 10 blocks wide and 40 long runs out of the maze into ground that is not
  maze, ending in a chamber with the way out in it. The maze is a perfect 96×96
  and should be; the exit is the one place that breaks, so from inside a corridor
  the doorway visibly leads somewhere that is not more corridor.
- **feat** **The Last Stand.** Sixty of them, standing in the lane between you and
  the portal, raised once a day the first time anybody walks in. Finding the way
  out used to be the whole game *and* the end of it — you stepped through and won,
  so the hardest thing you ever did was the walking and arrival asked nothing of
  you. Now everything the Glade spent the week building is for this, in a corridor
  narrow enough that four people can hold a line and one probably cannot.
  Configurable (`mazeLastStandCount`) and scaled by the day like everything else.
- **change** `atExit` moved to the chamber portal. The doorway in the maze wall is
  now where the last fight *starts*.
- **fix** **The sky lid was never placed.** `SKY_LID_Y` was `WALL_TOP_Y + 4` = 82,
  and the dimension is 80 blocks tall from y=0 — so the highest writable block is
  79 and every `setBlock` for the barrier roof silently did nothing. The maze has
  had no roof at all this whole time, despite the code saying it did. It is now at
  `WALL_TOP_Y + 1`, inside the world.

### Griever holes — they come from somewhere and they go somewhere

- **feat** `GrieverHoles` — sixteen shafts sunk through the corridor floor into lit
  chambers, at fixed points a Runner can chart. Grievers **come up out of the
  nearest one** at dusk and **go back down** at dawn.
- **fix** They used to appear in whatever corridor the dice picked, which meant the
  map had no geography of danger — every corridor was equally likely to produce
  one, so no corridor was frightening for a *reason*. And at dawn they were
  teleported to a map corner and deleted, which is the least interesting way for a
  monster to stop existing.
- **feat** Every shaft has a ladder the whole way up. An unbreakable floor and
  unbreakable walls around a pit is not a Griever hole, it is a place a player is
  deleted for being curious.
- **change** Geometry version 5: the lid moved inside the world, the seven annexes
  were built, and the holes were dug. The annexes and holes are stamped a row at a
  time off the builder's existing staging — 5,600 columns and 16 shafts is not
  something to do on one tick.

### The maze gets its own clock, and a game gets a beginning and an end

- **fix** **The maze was not running on a clock at all.** Every schedule in it read
  `level.getDayTime()`, which for any dimension that is not the overworld *is the
  overworld's time*. Someone sleeping in a bed on the surface skipped the maze's
  night. `/time set day` threw the doors open. The day counter counted the
  overworld's days, so a maze first entered on a mature world opened on "day 400".
- **feat** `MazeClock` — the maze keeps its own time, persisted with the world.
  `mazeDaySeconds` and `mazeNightSeconds`, **600 each by default**, so a night is
  exactly ten minutes and every other timing in the place is derived from those two
  numbers rather than being a magic tick constant.
- **feat** The sky follows. `setDayTime` is a no-op off the overworld, so each
  player in the maze is sent a time packet every tick with the phase mapped onto a
  vanilla day and the daylight-cycle flag off. Daylight always spans sunrise to
  sunset whatever the real length is — the sky moves at a different speed rather
  than skipping any of it. Step out and the real sky is back within a second.
- **feat** **A game has an end.** It runs from the first person walking in to the
  last one leaving, however they leave; when the maze empties, that is the end of
  it. The next game re-rolls which of the seven layouts is day one. They still run
  in order after that — a game that opens on Saturday's maze goes Sunday, Monday,
  Tuesday; the next might open on Wednesday's.
- **change** The reshape now happens **at midnight**, not at dawn. It was named for
  midnight, described as midnight, and actually fired at the day rollover.
  `todaysLayout` tells the truth about this: after midnight the standing walls are
  already tomorrow's, so anyone caught out overnight is walking tomorrow's maze
  before tomorrow arrives.
- **change** Which layout is standing is now tracked **by name**, so a restart, a
  midnight reshape and a brand new game are one code path instead of three pieces
  of bookkeeping that could each go stale alone.
- **change** The runtime ticks every tick rather than every twentieth. A clock read
  once a second is wrong by up to a second, which is the difference between getting
  through the door and not. The expensive work is still gated to once a second.

### The reshape, given a voice

- **feat** Eight seconds either side of midnight: a Warden roar to open it, then
  grinding stone laid over hammer-falls, dust, and a low note when it stops. Loud
  in the corridors and distant from the Glade — the difference between those two
  places is the entire point of the Glade.
- **feat** Anyone standing **in** the maze when it moves gets six seconds of not
  being able to trust their footing, and a subtitle that says so. The single most
  characteristic thing this place does used to be one stone-break sound and an
  action-bar line you would miss if you were in your inventory.

### Day 12 no longer plays like day 3

- **fix** The only thing that moved with the calendar was the Griever cap, and it
  moved once a **week** — so a fortnight-long run got two extra spiders and was
  otherwise identical. The maze had a day counter and no difficulty curve, which
  made the number decorative.
- **feat** `Griever.dayScale()` — health, damage and reach all ride on the day, one
  config number (`mazeDayScalingPercent`, default 12). Day 10 is roughly double day
  1; day 20 hits the 3× ceiling. Speed climbs at a quarter of that rate and stops
  at +25%, because a Griever that badly outruns a sprinting runner is not harder,
  it is unplayable.
- **change** The cap climbs every **other day** instead of every week, and they
  arrive faster on later nights — the cap is where a night ends up, the arrival
  rate is what it feels like getting there.
- **feat** The status bar now shows the countdown to the next phase and the night's
  danger multiplier. Knowing the doors seal in four minutes is a decision; knowing
  it is "day" is not.

### Builder and Track-hoe get the jobs their names promise

- **feat** **Builder** — anything they craft comes out with **+40% durability** and
  hits for **+20%**. Stamped on the *object*, not the player: a sword a Builder made
  is still a better sword after they hand it over, and after they are dead.
  Durability is written into the item so the number is visible in the tooltip.
- **feat** **Track-hoe** — **double drops from every farmable block**. Rolled from
  the block's own loot table rather than a hand-written list, so bonus wheat comes
  with a bonus seed at the game's own odds and a crop this code has never heard of
  still doubles correctly. Matched on block *class*, so wheat, carrots, potatoes,
  beetroot, nether wart, berries, cocoa, cane and both gourds are all covered
  without naming any of them.

### Jobs — the Glade stops being a waiting room

The Glade is half the clock. Sixty minutes of daylight and thirty of night, and
until now every minute of it that was not *inside the maze* was dead time — you
stood in a clearing full of buildings you could not use until the doors opened.
Four jobs, each one wired into something the map already had and was refusing to
use.

- **feat** `MazeJobs` — `/maze job` to take one, `/maze jobs` for the roster. Four
  levels each, earned by doing the work. Experience is kept **per job**, so
  changing your mind is free; a Glade where switching burns a week is a Glade
  where nobody ever tries the other three.
- **feat** The **Job Board** by the Box — four posts, one per trade, where
  everyone lands. A job you have to go looking for is a job nobody takes.
- **feat** **Runner** — Speed I outside the Glade (II at level 3), and the only
  job paid for charting. At level 4 the day's exit *section* is named to you as
  you leave the Glade: a quarter of the guesswork, not the route.
- **feat** **Builder** — may leave carpet in the corridors (wool at 2, lanterns
  and banners at 3), and a marked cell shows as `✚` on the Glade's map for
  everyone. At level 4 a mark also **charts** the cell for the whole Glade — the
  one perk that pays other people rather than the person who earned it.
- **feat** **Med-jack** — `/maze treat` on a Changing runner within five blocks
  buys them 30 seconds (45 at level 2); at level 3 it cures outright, once a day;
  at level 4 the cure stops costing the day. The Changing was a private
  catastrophe nobody else could touch.
- **feat** **Track-hoe** — harvesting a mature crop in the Glade fills the
  **larder**, the one number in the maze one player raises and another spends.
  `/maze rations` draws bread from it. The field was farmland, a water channel, a
  fence and three crops, drawn in and left as a backdrop.
- **fix** **You could break inside the Glade but never place.** A rule that only
  ever destroys is not a rule; you could pull your own hut apart and never put it
  back. Placement is now allowed inside the Glade — up to a **12-block ceiling**,
  because the Glade is open to the sky and an uncapped height is a pillar to the
  wall tops and a walk over the entire maze. The old blanket ban closed that
  exploit by accident.
- **fix** **Marks were permanent.** Signs and torches could be placed in the
  corridors and never removed, so one torch on the wrong wall stayed there for the
  life of the world and a chart could only ever be added to. Marks now break back
  down outside the Glade — and only marks; the walls still do not come apart.
- **feat** **The field grows at dawn**, explicitly, rather than relying on vanilla
  random ticks. This is a bespoke dimension with a barrier lid over most of it, so
  vanilla growth would have made the Track-hoe's job quietly work on some worlds
  and not others — the worst kind of bug, because it looks like bad luck. Harvested
  ground reseeds itself, and the field is sown at mixed ages so it can be worked on
  day one.
- **change** A day in the Glade is now a day the field moved, whether or not
  anybody ran the maze. The day counter had nothing to accumulate in before this.
- **change** Geometry version 4, so existing worlds restamp and get the Job Board.

### The Map Room — charting finally means something

- **feat** `MazeCharts` — every corridor cell you stand in is recorded, per player
  and for the Glade as a whole, persisted across deaths and days. The best idea in
  the source material is that Runners chart the maze and the Gladers assemble it;
  the ingredients were all there (section colours, placeable signs and torches) and
  nothing ever *collected* any of it.
- **feat** The **Map Room** in the Glade, and `/maze map` — a drawn window centred
  on the Glade: your cells solid, cells other Runners brought back dimmer, unknown
  ground dark. Plus percentage charted and a per-section breakdown.
- **change** This works because the base graph is stamped once and never moves —
  only 200 toggles shift nightly. Charting is durable knowledge, which the game
  previously refused to acknowledge.
- **fix** The Glade had **zero interactions anywhere in the maze package**. A
  homestead, huts, a field, deadheads, woods and a firepit, every one of them
  scenery. The Map Room is the first building in the clearing with a job.
- **feat** The nightly reshape is audible — grinding stone and a message. The
  entire maze rearranged itself at midnight in total silence, which made the map
  feel arbitrary rather than alive.
- **change** Geometry version 3, so existing worlds restamp and get the Map Room.

### Hunger Games — scattered spawns and a closing border

- **feat** `"spawns": "scattered"` — one `[Spawn]` each, remembered per player so a
  respawn returns you to your own pedestal. Every mode until now wanted players
  together; a battle royale wants the exact opposite, and that could not be asked.
- **feat** `"border": { from, to, seconds, wait_seconds }` — closes vanilla's own
  world border, so the red wall, the sound and the damage outside come free and the
  shrink is smooth. Set once, not re-issued, or the interpolation would restart
  every tick and the border would never arrive.
- **feat** Scattered spawns **plus** respawn off ends the run when one player is
  left and fires `run_won`. Deliberately only that combination — it is the shape of
  a battle royale and nothing else.
- **feat** `aztecabyss:hunger` ships as a worked example: a reaping countdown, a
  border on a 90-second fuse, and a payout for the winner.

### `delay` and `every` — the script layer learns about later

- **feat** `{ "delay": { "seconds": 30, "do": [...] } }` and
  `{ "every": { "seconds": 10, "times": 5, "do": [...] } }`. The script layer could
  say "when this happens, do that" and had no way to say "in thirty seconds, do
  that" — so every countdown, delayed gate, staged reveal and timed penalty was
  faked by polling `tick` against a hand-incremented variable. That is the shape of
  a missing feature, not a technique.
- **change** Both work in both modes. A countdown is not a free-mode idea.
- **change** `Script.runActions` is now the single path for running an action list,
  so delayed and immediate work cannot drift apart.
- **change** The queue is capped at 256 and cleared when a run ends — a delayed
  action firing into a finished run would act on an arena nobody is in.
- **feat** `/arena status` reports how many actions are queued.

### Fix the build (second attempt)

- **fix** The per-gate edit used unanchored whole-file string replacement, so the
  gate tag and the burst loop were injected into **every** matching site — landing
  in `spawnAt()`, the boss spawner, which has no `gate`, `pick` or `burst` in
  scope. Seven errors, all in a method the change was never meant to touch.
- **process** Two build failures in a row from scripted edits I did not read back.
  Verifying the edit landed where intended costs one command; not verifying cost
  three builds and an hour.

### Fix the build

- **fix** `rulesetId()` was declared twice in `EngineArena` — the block-events
  iteration added an accessor that already existed 300 lines above it. One line,
  and it broke two builds: the per-gate commit stacked on top of an already-broken
  block-events commit before either had verified.

### Per-gate tuning, and a full marker reference

- **feat** `[Horde]` gains `weight=`, `burst=`, `mobs=`, `health=`, `damage=`,
  `from_round=`, `until_round=`. Every gate was equally likely and identically
  scaled, which made a map's four ways in interchangeable however different the
  rooms behind them were.
- **change** `health=` / `damage=` are **percentages on top of** the round curve,
  not absolutes. `200` means "twice as tough as whatever this round is", which
  still means something at round 40; an absolute stops meaning anything by 10.
- **feat** `burst=` sends several out of one gate at once — a pack arriving
  together is a different problem from the same number trickling in, and trickle
  was the only thing the engine could do.
- **feat** `mobs=` restricts a gate to named entities, drawn from the ruleset table
  so scaling, roles and equipment still apply. A gate naming something absent
  falls back to the table rather than sending nothing.
- **docs** `MARKERS.md` — every marker, every option, and what people actually
  build with each, plus a table of which markers each kind of game needs.

### Blocks you can react to, and round-mode parity

- **feat** `use_block` and `break_block` events with a `block` condition. Rounds
  answered *when*, regions answered *where somebody stands*, and nothing answered
  *what did they just pull* — so a map could ask you to reach a place but never to
  operate anything. Levers, buttons, plates and mining are the foundation of every
  puzzle and switch.
- **change** The `region` on a block event resolves from the **block's** position,
  not the player's — "the lever in the vault", not "a lever pulled by someone
  standing in the vault".
- **change** Interactions are not cancelled; the lever still flips. A trigger that
  ate the interaction would mean every switch needing a rule to behave like one.
- **fix** `tick` and `set_bar` were free-mode only, making round mode a
  second-class citizen of its own engine for no reason but build order. Both work
  in both modes now; round mode appends the round number to a custom bar.
- **feat** `aztecabyss:vault` ships as a worked example — three levers, then the
  door.

### Respawning and kits — and a defect in shipped content

- **fix** `aztecabyss:capture` shipped two iterations ago with **permadeath**,
  because death in the engine was unconditionally final. First death and that
  player spectated the rest of the match. Capture the flag where the first death
  removes a player is not capture the flag.
- **feat** `"respawn": { "enabled": true, "seconds": 5 }` — off by default, since
  a survival arena needs death to be final and anything competitive needs the
  opposite. No death screen: healed, sent to your side's spawn, brief resistance
  and slowness so spawn camping is not a strategy.
- **change** Respawning takes precedence over downing — two different answers to
  the same moment, and the map already chose one.
- **feat** `"kit": "<pool>"` — handed out on every spawn, first and after. Gives
  every entry rather than drawing one, because a loadout is a list of what you get
  and rolling it would start teammates with different equipment.
- **fix** First join used the map's single spawn even on a team map, so players
  only reached their own side after dying once.

### The script layer becomes observable

- **fix** A mistyped action hit `default -> {}` and did nothing, silently; a
  mistyped condition was ignored. Both are the right *runtime* behaviour — a map
  written for a later engine must still run on an earlier one — and both meant a
  rule that loaded perfectly and never did anything, with no symptom to chase.
  Unknown events, conditions and actions are now collected at load and reported by
  `/arena rules`.
- **feat** `/arena trace` — live per-rule tracing: what fired, what was skipped.
- **feat** `/arena vars` — every variable the run currently holds. Variables are
  the substrate of every non-arena game and were completely invisible.
- **feat** `/arena teams` — who is on which side.
- **change** This is the same failure mode this project keeps producing — accepts
  input, does nothing, says nothing — applied to the primary authoring surface.

### Teams — the engine learns that players can be on different sides

- **feat** `Teams` — named sides with colours, membership and even balancing.
  Previously every player was on the same side permanently, which is not a
  limitation of the arena mode but the absence of a concept: capture the flag,
  team deathmatch, hunters and runners, infection and attack/defend were all one
  idea away and none were reachable.
- **feat** `[Spawn] team=` marks a side's spawn; a map with teams but one spawn
  still plays, symmetrically.
- **feat** Actions `join_team`, `balance_teams`, `team_message`, `add_team_var`,
  `set_team_var`, `teleport_to_spawn`. Conditions `team` and `team_var`.
- **change** Membership rides vanilla scoreboard teams, buying name colouring,
  glow and friendly-fire-off for free rather than reimplementing three systems.
- **change** `balance_teams` fills smallest-first, not round-robin — round-robin is
  only even if everybody arrives at once, which on a server never happens.
- **change** Team scores are normal variables under a prefixed name
  (`team:red:score`), so every existing condition and readout keeps working.
- **feat** `aztecabyss:capture` ships as a worked example — capture the flag,
  three to win.

### Free mode — the engine can host a game with no horde in it

- **fix/feat** `startIn` **refused** any map without `[Horde]` markers. That single
  guard made round-survival not one mode but the only thing the engine could
  express — a race, an escape room, a heist and a puzzle have no horde by
  definition, so no amount of scripting could reach them.
- **feat** `"rounds": { "mode": "free" }` — no rounds, nothing spawns, no wave.
  The map is regions, variables and script; the script ends it.
- **feat** `tick` event, once a second — the only recurring event a map without
  rounds has, and what a deadline hangs off.
- **feat** `set_bar` action and an automatic run clock, so a race has a time
  without the author building one.
- **feat** `seconds` condition with `at_least` / `at_most`.
- **feat** `aztecabyss:heist` ships as a worked example — three idols, one way out,
  five minutes, ~30 lines, a complete game with no Java.
- **change** Validation now calls a missing horde a warning that names free mode,
  not an error.

### Variables and regions — the engine stops being only an arena

- **feat** `Vars` — run-scoped and per-player integers. The script layer could do
  a lot and could not count, so the only state a map had was the round number and
  which doors were open. Almost everything that is not round-survival is counting.
- **feat** `[Region] id= radius= height=` firing `region_enter` / `region_leave`,
  edge-triggered. Rounds let a map react to *when*; regions let it react to
  *where*, which is the substrate of checkpoints, capture points and finish lines.
- **feat** Conditions `var`, `my_var` (with `equals`/`at_least`/`at_most`, and
  `total` to sum a per-player name across the squad) and `region`.
- **feat** Actions `set_var`, `add_var`, `set_my_var`, `add_my_var`, `win`, `lose`.
- **change** `win`/`lose` end a run *with an outcome*. `end_run` said nothing about
  whether that was a good thing, and a game has to be winnable on its own terms.
- **change** Still integers only, no expressions — that covers counting, flags,
  timers and scores and cannot hang a server. A downloaded map stays safe to run.

### Marker Blocks replace signs entirely — dealers included

- **change** `/arena marker dealer` now gives a Marker Block. Every marker kind is
  a block; nothing is a sign any more, so a finished map has nothing on its walls
  explaining itself to the engine.
- **feat** Interactive kinds (`dealer`, `box`, `perk`, `upgrade`, `loot`, `door`,
  `trap`, `objective`) keep a clickable outline in survival while staying
  invisible. The rest stay completely intangible — a `[Horde]` marker should be as
  absent to a player as the air it looks like.
- **feat** Look-at prompts. An invisible shop has to announce itself or it is a
  secret, not a shop: face one within five blocks and the action bar shows what it
  sells and what it costs, greyed out if you cannot afford it.
- **change** `DealerSign` parses a plain list of lines, so a sign front, a sign
  back and a Marker Block all feed one implementation.
- **compat** Existing maps built on signs keep working — both surfaces still feed
  the same parser.

### The portal screen was unreadable

- **fix** Every string on the picker was drawn with `dropShadow = false`.
  Minecraft's font is designed around that shadow; without it, small text on a
  dark ground goes thin and shimmers. That was the "fizzy", and the blur was only
  half the story.
- **fix** Card bodies were `0x99`–`0xCC` alpha, so each one was a slightly
  different muddy grey depending on what happened to be behind it. Opaque now — a
  card is a surface, not a tint.
- **change** Contrast raised throughout: blurbs `0x9A9A9A` → `0xC8C4BA`, titles to
  near-white, "Never attempted" off near-black. The difficulty pill is solid
  rather than `0x66` alpha.
- **fix** Same shadow and contrast problems fixed on the Records screen, which had
  inherited them.

### Griever gets a face

- **feat** `GrieverLayer` — dark segmented plate over the whole model and eight
  burning eyes drawn with `RenderType.eyes`, so it is full-bright in a dark
  corridor. A Griever no longer reads as a large spider.
- **change** Done as a render layer on the shared spider renderer rather than a
  new entity: a spider already moves the way this should move, and the animation
  is the half that sells it. Ordinary spiders are untouched — the layer checks the
  tag and returns.

### `5a3b0ba` — Reaching the maze exit crashed the game

- **fix** `tickRunners` walked the live player list and called `changeDimension`
  from inside it, which removes the player from the list being iterated.
  `ConcurrentModificationException` out of the server tick, taking the world down.
  The hardest thing in the mod ended in a crash **every time**. Escapees are now
  collected and sent home after the loop, and the loop walks a copy.
- **change** Griever darkness removed. Being blinded near one took the corridor
  away exactly when you most needed to read it.

### `632bfdb` — Maze caches, a real payout, records for every map

- **feat** 28 chests per night, re-scattered with each reshape, weighted toward
  dead ends and richer further from the Glade. Tier 3 can hold an enchanted golden
  apple or the serum.
- **change** Escape payout raised hard — diamonds, netherite, enchanted apples,
  1500 XP × tier. A netherite block and an enchanted book are reserved for a clean
  sub-three-minute route.
- **feat** `Leaderboards` — records keyed by **string**, so the Maze (scored in
  seconds) and published maps (files, no enum index) can have them. Previously
  keyed by an `int` index into `ArenaMap`, so **a map you made recorded nothing**.
- **feat** Solo and group kept as separate boards. Four people reaching round
  thirty is a different result from one person doing it, not a better one.
- **feat** **Records** button on the portal picker. Requested on press rather than
  pushed with the picker, so opening a portal costs no packet.
- **change** Dying still counts — the board is how far you got.

### `a19c732` — Pools, dealer back face, blur fix

- **feat** `ItemPool` — weighted lists with count ranges and enchant levels, one
  mechanism for the Box, supply caches and (next) loadouts. The Box was thirteen
  items hard-coded in a Java array.
- **feat** Three levels of override, most specific wins: `items=` on the marker →
  `pool=` naming a ruleset pool → the built-in list.
- **feat** Dealers read **both faces** of the sign — eight lines. The four-line
  cap was never the format, it was the front of the sign.
- **feat** Dealer options `enchant=`, `round=` (sealed until then), `limit=` (per
  run, per sign — the squad shares one stock).
- **fix** Picker was blurry: it painted at `0xE0` over vanilla's blur pass, so 12%
  of a blurred moving world came through behind the cards. Opaque now, and the
  blur hook is overridden away rather than painted over.
- **change** An empty pool is reported by `/arena rules` — it is almost always a
  misspelled item id.

### `58e258d` — Publishing: a map becomes a place on the portal

- **feat** `/arena publish <name>` stamps a map into a permanent slot in the Abyss
  and puts it on the picker. `/arena unpublish`, `/arena published`.
- **feat** `<world>/aztecabyss/` — a folder that exists to be opened. A readable
  JSON per map beside its `.nbt`, an index of what is on the portal, and a README.
  Hand-editable, then `/arena reloadmaps`, no restart.
- **change** Published maps run on the **engine**, not the round manager.
  `ArenaMap` is an enum wired into bosses and scoring; a published map cannot be an
  enum constant, and the engine already does all of this.
- **change** Picking a published map **joins** a run in progress rather than
  starting a second one.

### `cbfc7ae` — Marker Blocks

- **feat** A marker block holding **8 lines × 256 characters**, same grammar as
  the signs. `/arena line <n> <text>` — a command argument has no character cap.
- **feat** Invisible in survival, no collision, and its outline exists only for a
  creative player — otherwise a map is full of invisible things swallowing clicks
  meant for the wall behind them. Shows an ember to anyone in creative.
- **change** Settles the sign-era tension: markers no longer have to be either
  left visible on every wall or deleted so the author cannot see their own map.
- **change** Dealers stay signs — a shop front is meant to be walked up to.

### `6c1fe33` — Map Creator opens with a password

- **change** Was operator-only, which handed out far more than Creator or kept out
  exactly the people it exists for. `/creator <password>`, remembered per player;
  operators skip it. `/creator lock <player>` revokes.
- **feat** `creatorPassword` in the mod config.

### `263e08a` — The maze: real boundaries and a real deadline

- **fix** A ring of corridor ran the whole way round outside the Glade wall, so
  **all four doors opened onto the same lane**. Every ring-to-ring edge sealed.
- **change** Nothing outside the Glade breaks. The floor breaks nowhere — one hole
  is a way under a wall.
- **feat** Barrier lid four blocks above the wall tops. Grievers still walk the
  wall tops beneath it; the Glade keeps its open sky.
- **change** Signs and torches are the only placeable blocks. Charting the maze is
  the job, and the section colours exist to be written down.
- **change** The Changing is two phases: 90 seconds of symptoms with the clock on
  screen, **then** lethal. It used to kill you across the same 90 seconds it spent
  telling you to run somewhere.
- **change** Grievers thrown out of the Glade rather than deleted; recalled to the
  corners at dawn.
- **change** Grievers 150 HP, 12 damage, 2.4× scale, soot and soul particles.
- **fix** Day rollover reset nothing — run clocks counted through it and sting
  tallies survived a night's sleep.
- **feat** Exit portal hums and throws light for 60 blocks.
- **feat** Glade wall dressed: plinth, banded course, buttresses, toothed skyline,
  lanterns on the inward face only.
- **feat** Geometry version marker — existing worlds restamp themselves.
- **change** The Workshop is a genuine flat world from `y=0`, not a void with a
  platform in it.

### `4684acc` — Nothing enters the Abyss except through a gate

- **fix** Mobs appearing inside the temple were **zombie reinforcements** — on
  Hard a damaged zombie summons another beside itself, so fighting in the pyramid
  bred zombies in the pyramid. Untagged, so they counted toward no round and
  survived the end-of-round sweep. `SPAWN_REINFORCEMENTS_CHANCE` zeroed.
- **feat** Join guard: during a live run, any monster without a mod tag is refused
  entry to the Abyss.
- **feat** Map Creator on the picker as its own mode.

### `cedd749` — Special rounds and two mute markers

- **fix** Specials took the first match in file order, so with `every: 5` above
  `every: 10` the ten-round special **could never fire** — every multiple of ten
  is also a multiple of five. Shipped `brutal.json` had exactly that. Rarest wins.
- **feat** `/arena validate` now catches an objective with a bad kind or a
  nonexistent `item=`, and a teleport pad with no partner.

### `c3592df` — Bleed-out and defend objectives

- **fix** Bleed-out never killed. The killing blow was struck while the victim was
  still in the `downed` map, and the damage interception that keeps a downed player
  alive cancelled it — marked dead, stuck at 1 HP, unrevivable and unkillable.
- **fix** Defend objectives took damage **per tick**, not per second. Ten mobs =
  50 HP/s, so `hp=600` lasted twelve seconds and every defend objective in every
  map was unwinnable.

---

## Earlier engine work

Condensed; see `git log` for the full sequence.

| Commit | What |
|---|---|
| `d20a00c` | Datapack map manifests — shared maps arrive with name and rules |
| `49cf703` | Dealer repair-and-reload; maps get a verb |
| `68ab57d` | Downed & revive; special rounds |
| `34dd381` | Every entity usable by a spawner, and findable via `/arena mobs` |
| `57c826c` | Traps, teleporters, mob roles that do something |
| `0c8773e` | A map is a fixture, not a consumable — the engine restores what it overwrites |
| `e9db2b5` | Power-ups; the round bar survives a relog |
| `12e326b` | More than one person can play |
| `b0deaa9` | Extraction — a map can be won, not only lost |
| `11dea89` | Three worked rulesets |
| `8e05b32` | `/arena set` — retune a marker without retyping it |
| `fab23c8` | Prefabs |
| `3a0dece` | Copy, paste, rotate, mirror, undo |
| `2440905` | Map identity — title, author, blurb, difficulty |
| `23de3d9` | The Director — pressure-based pacing |
| `9e8f496` | The script layer — triggers and actions |
| `85f6c6f` | The Workshop, the selection wand, marker signs |
| `f714943` | **The engine first plays a map nobody wrote code for** |
| `e2557c2` | Rulesets from datapacks, reloadable live |
| `e879cc0` | Save a build to a file and place it back |
| `50a4b1b` | Markers, world scanning, validation |
| `57063fc` | **Engine begins** — currencies and dealer signs |

---

## Recurring failure modes

Kept here because the same shapes keep coming back.

**Modify-while-iterating.** Three separate bugs: the bleed-out kill, the maze exit
crash, and the downed-map removal. Any "act on a player from inside a loop over
players" is suspect — collect, then act after the loop.

**Features that accept bad input, do nothing, and say nothing.** Ruleset typos,
dead markers, nonexistent entity and item ids, empty pools, lone teleport pads.
Every one of these now reports. The rule: *every documented marker does something,
and anything that cannot do its job says so.*

**Compiling green proves nothing.** Every correctness bug in this project was
found by tracing what a person does. Roughly thirty green builds have caught zero
design faults.
