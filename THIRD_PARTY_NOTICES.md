# Third-party notices

Farmbound's own code is original (it began as the Farming part of the author's Spyglass plugin, hence the
`com.spyglassfarming` package). The data tables and the pieces of logic below come from, or are adapted from,
BSD 2-Clause licensed projects and are credited here per the terms of those licenses.

## Farming patch tables and capture guards (farming/*)

Farmbound's Farming foundations (`FarmingPatchCatalog` with its areas in `farming/patches.tsv`, `FarmingProduce`,
`FarmingDecoderTable` with its rows in `farming/patch-states.tsv`, `FarmingPatchType`, `FarmingObserver`, `CalibrationSample`,
`FarmingCalibration`) derive data
and adapt algorithms from RuneLite's core Time Tracking plugin, read at
RuneLite `master` commit `e98399f931c6` (2026-10-14):

- `net.runelite.client.plugins.timetracking.farming.FarmingWorld`: farming
  area region ids, alias ("extra") regions, transmit-varbit assignments and
  the Falador / Port Sarim, Catherby and Fossil Island bounds rules.
- `net.runelite.client.plugins.timetracking.farming.PatchImplementation`:
  the raw varbit value ranges for every supported patch type (herb, allotment,
  flower, tree, fruit tree, bush, hops and the special types;
  `FarmingDecoderTable` is generated from it) and each type's health-check flag.
- The same `PatchImplementation` (COMPOST and BIG_COMPOST) and `FarmingWorld`
  bin entries: the compost bins, their transmit varbits and value ranges
  (`CompostBin`, canonical §28.15; added 2026-10-22). The bins' object ids,
  tiles, value ranges and Close op counts were then corrected from the game
  cache as published by two data repositories that carry no licence file and
  from which only facts are used (no file is copied): osrs-dumps
  (github.com/Joshua-F/osrs-dumps, commit `6e5033af`: loc multilocs, varbits,
  client scripts) and mejrs data_osrs (github.com/mejrs/data_osrs, commit
  `6a3ca6f1`: object placements) (canonical §28.16; added 2026-10-22).
- `net.runelite.client.plugins.timetracking.farming.CompostTracker`
  (Copyright (c) 2022 LlemonDuck, BSD-2): the compost message patterns (applied,
  Fertile Soil, already treated, Inspect) and the rule that the message is
  credited to the patch chosen with a compost item, Fertile Soil or Inspect
  within 30 seconds (`PatchCompost`, `FarmingCaptureAdapter`; added 2026-10-22).
- `net.runelite.client.plugins.timetracking.farming.Produce`: growth tick
  minutes, growth stages, regrow minutes and harvest stages.
- `net.runelite.client.plugins.timetracking.farming.FarmingTracker` and
  `net.runelite.client.plugins.timetracking.TimeTrackingPlugin`: the capture
  guards (no reads while a modal is open, the post-login Welcome screen,
  same-region settling), the rules for recognising an observed growth tick,
  and the growth-boundary / farm-tick offset model `FarmingCalibration`
  adapts (Farmbound learns its own offset from its own samples).

No RuneLite file is copied verbatim and nothing from the Time Tracking
plugin is used at runtime: Farmbound reads no Time Tracking configuration and
imports nothing from its package.

> Copyright (c) 2018 Abex
> Copyright (c) 2018, NotFoxtrot <https://github.com/NotFoxtrot>
> Copyright (c) 2018, Daniel Teo <https://github.com/takuyakanbr>
> Copyright (c) 2019 Abex
> All rights reserved.
>
> Redistribution and use in source and binary forms, with or without
> modification, are permitted provided that the following conditions are met:
>
> 1. Redistributions of source code must retain the above copyright notice, this
> list of conditions and the following disclaimer.
> 2. Redistributions in binary form must reproduce the above copyright notice,
> this list of conditions and the following disclaimer in the documentation
> and/or other materials provided with the distribution.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
> ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
> WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
> DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
> ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
> (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
> LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
> ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
> (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
> SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Transport arrival tiles and account readers (travel/*, account/*)

Farmbound's Phase F transport catalogue
(`src/main/resources/com/spyglassfarming/travel/transports/*.json`) and
account readers (`account/AccountReaders`) use facts read from RuneLite
`master` commit `e98399f931c6` (2026-10-14):

- `net.runelite.client.plugins.worldmap.TeleportLocationData` (Copyright (c)
  2018, Morgan Lewis): arrival tiles of spells, tablets and teleport items,
  including the house portal and portal nexus destinations (`poh.json`).
- `net.runelite.client.plugins.worldmap.FairyRingLocation` (Copyright (c)
  2018, Morgan Lewis): fairy-ring arrival tiles.
- `net.runelite.client.plugins.worldmap.TransportationPointLocation`
  (Copyright (c) 2019, Kyle Sergio; Copyright (c) 2019, Bryce Altomare;
  Copyright (c) 2019, Kyle Stead): fixed spirit-tree tiles, and the quetzal
  landing tiles used as quetzal whistle arrivals (`teleport-items.json`, added
  2026-10-22; cross-checked against Shortest Path's `quetzal_whistle` and
  `teleportation_items` tables @142e73e2, BSD-2).
- `net.runelite.client.plugins.worldmap.FarmingPatchLocation` (Copyright (c)
  2018, Torkel Velure): player-grown spirit-tree tiles.
- `net.runelite.client.plugins.fairyring.FairyRing` (Copyright (c) 2018, Yoav
  Ram; Copyright (c) 2018, Infinitay): fairy-ring destination names.
- `net.runelite.client.plugins.dailytaskindicators.DailyTasksPlugin`
  (Copyright (c) 2018, Infinitay; Copyright (c) 2018-2019, Shaun Dreclin): the
  rule that a `*_DIARY_*_COMPLETE` varbit of 1 means the tier is complete.
- `net.runelite.client.plugins.runepouch.RunepouchOverlay` (Copyright (c)
  2017, Tyler; Copyright (c) 2022, Adam): reading the pouch through
  `RUNE_POUCH_TYPE_n` / `RUNE_POUCH_QUANTITY_n` and the `RUNEPOUCH_RUNE` enum.

Only coordinates, names and reading rules are used; no RuneLite file is
copied and none of these plugins is used at runtime. These files are
distributed under the same BSD 2-Clause terms reproduced above. Spell levels,
rune costs, quest requirements, daily limits and charge behaviour were
checked against the Old School RuneScape Wiki (facts only, no text copied).
The Shortest Path inputs used by Farmbound are listed under World navigation data below.

## Quest Helper facts and RuneLite client scripts (account/*, farming/*, prep/*)

Phase F's gap closure and Phase H use facts (not code) read from Quest Helper
(`Zoinkwiz/quest-helper` master @ `5f79193`, BSD 2-Clause, Copyright (c)
2020, Zoinkwiz): the spellbook varbit values (`requirements.util.Spellbook`),
the diary reward varbits used for Karamja easy / medium / hard
(`questinfo.QuestVarbits`), the fairy-ring staff variants
(`collections.ItemCollections.FAIRY_STAFF`), and the herb run's patch access
requirements and seed-dibber / rake unlock conditions
(`helpers.mischelpers.farmruns.HerbRun`). Quest Helper is not used at runtime
and nothing from it is imported, read or depended on.

From RuneLite `master` @ `e98399f931c6` (BSD 2-Clause, terms above): the
spellbook layout in `MagicSpellbookRedraw.rs2asm` and the bank tab layout in
`BankMainBuild.rs2asm` (game client scripts as shipped in RuneLite), and the
search-assist approach of `plugins.bank.BankSearch` (used through RuneLite's
public client classes).

> BSD 2-Clause License
>
> Copyright (c) 2020, Zoinkwiz
> All rights reserved.
>
> Redistribution and use in source and binary forms, with or without
> modification, are permitted provided that the following conditions are met:
>
> 1. Redistributions of source code must retain the above copyright notice, this
> list of conditions and the following disclaimer.
> 2. Redistributions in binary form must reproduce the above copyright notice,
> this list of conditions and the following disclaimer in the documentation
> and/or other materials provided with the distribution.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
> ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
> WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
> DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
> ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
> (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
> LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
> ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
> (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
> SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## World navigation data (navigation/*)

From
the Shortest Path RuneLite plugin, https://github.com/Skretzo/shortest-path,
commit `142e73e2a5f840bb16e227e564a036156f4cfc77` (BSD 2-Clause, as published
in that repository; its LICENSE file names no individual copyright holder, so
the project and its contributors are credited): the world collision map
`collision-map.zip`; the transport tables `transports.tsv`,
`agility_shortcuts.tsv`, `boats.tsv`, `ships.tsv`, `quetzals.tsv`,
`magic_mushtrees.tsv`, `hot_air_balloons.tsv`, `fairy_rings.tsv`, `spirit_trees.tsv` and
`item-variations.tsv` (unmodified copies under `src/test/resources/com/spyglassfarming/navigation/`; the plugin ships
`collision-map.zip` and `transitions.json`, generated from them, under `src/main/resources/com/spyglassfarming/navigation/`); the format of the collision map and
the requirement grammar of the tables, read by Farmbound's own
`WorldCollision`, `TransitionCatalog` and `NavRequirement`; and the Wilderness
bounds in `Wilderness.java`. `routes.json` is generated by Farmbound from these
files; `spyglass_transitions.tsv` is Farmbound's own data. The world network
entrance tiles in `travel/network-nodes.json` are the origins of
`fairy_rings.tsv` and `spirit_trees.tsv`. Per the project's `CONTRIBUTING.md`,
`collision-map.zip` is generated from the game cache by the project's tooling.
The plugin's listed authors are Skretzo, FIrgolitsch and mpickering. No Shortest
Path code is included. In `spyglass_transitions.tsv`, the Hespori cave
entrance tile comes from RuneLite's `DungeonLocation.HESPORI` (RuneLite, BSD
2-Clause, terms above); object ids, options and requirements from the OSRS Wiki.

> BSD 2-Clause License
>
> Copyright (c) Shortest Path (Skretzo) and contributors
> All rights reserved.
>
> Redistribution and use in source and binary forms, with or without
> modification, are permitted provided that the following conditions are met:
>
> 1. Redistributions of source code must retain the above copyright notice, this
> list of conditions and the following disclaimer.
> 2. Redistributions in binary form must reproduce the above copyright notice,
> this list of conditions and the following disclaimer in the documentation
> and/or other materials provided with the distribution.
>
> THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
> ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
> WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
> DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
> ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
> (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
> LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
> ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
> (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
> SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
