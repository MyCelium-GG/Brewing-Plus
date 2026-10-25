# Changelog

All notable changes to Brewing+ are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Brewing+ is versioned semantically (`MAJOR.MINOR.PATCH`).
It runs on Minecraft 26.3 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

## [1.0.2] - 2026-10-25

### Changed

- Requires Mycel 1.4.0. Operators now get one chat line per session when a
  MyCelium mod has an update, and `/mycel mods` marks it.

## [1.0.1] - 2026-10-23

### Changed

- Requires Mycel 1.3.1. The guide and brewing stand hints draw with Mycel's
  shared wiki theme (`MycelTheme`) instead of a private copy; same colors.

## [1.0.0] - 2026-10-20

### Added

- Initial release.
- Potion button on the brewing stand opens a guide (the stand stays open).
- **Potions** page: every potion the world can brew, filterable by
  potion / splash / lingering, searchable by name or ingredient, and a
  **Have all** filter for what you can brew right now. Each potion shows its
  full chain from a water bottle with per-step ingredient counts, what it
  brews into next, other recipes that make it, missing ingredients and the
  stand's remaining fuel.
- **Ingredients** page: every reagent and every recipe it is used in.
- **Track at stand**: ghost of the next ingredient in the stand and a card
  with the next step, wrapping to fit small screens; flags wrong
  ingredients, dead-end bottles, mixed bottles and missing fuel.
- Recipes read from the integrated server in singleplayer (datapacks
  included); on remote servers, vanilla recipes filtered by the server's
  reagent list, with unknown server reagents marked.
- Config (`config/brewingplus.json`): `enabled`, `showButton`,
  `showStandHint`, `debug`; live reload via `/mycel reload brewingplus`;
  config screen available from the mod list on Forge and NeoForge.
- Client-side only, no keybinds. Built on Mycel 1.3.0
  (`com.github.MyCelium-GG:MyCel-Lib:1.3.0` via JitPack).
