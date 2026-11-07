# Farmbound

A RuneLite plugin for Old School RuneScape Farming runs.

- **Patch readiness**: each run type shows what is ready, growing, diseased, dead or clear, from what the game shows you.
- **Single or combined runs**: pick one run type, or several for one combined run, and choose the patches to visit.
- **Preparation**: one checklist of seeds, tools, compost and the teleport items or runes for each stop (carried, worn,
  in the tool leprechaun or last seen in your bank), and your stop order, which you can change. A stop that is a short walk
  from the one before it is walked to instead.
- **Guidance**: the next stop, which teleport to use, a walking path to the patch that is planned again if you wander off,
  and the action at the patch with the patch highlighted.
- **Settings**: travel (fastest, prefer your house, or only your current setup), tree clearing, fruit picking and crop
  protection.

Farmbound only reads the game; it never acts for you. Its data is kept locally under
`.runelite/plugin-data/spyglass-farming` (its internal ID).

## Building

`./gradlew build` compiles and runs the tests; `./gradlew run` starts a local RuneLite client with the plugin loaded.

## License

BSD 2-Clause (see `LICENSE`). Bundled navigation data and adapted logic are credited in `THIRD_PARTY_NOTICES.md`.
