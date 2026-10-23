# Brewing+

[![License](https://img.shields.io/github/license/MyCelium-GG/MyCel-Lib)](LICENSE)

Never look up a potion recipe again. Click the small potion button on the
brewing stand to open a guide: every potion the world can brew, its full
recipe from a water bottle, which ingredients you carry, and what each
ingredient does. Track a potion and the stand itself shows the next
ingredient to add. Client side, no keybinds.

Built on [Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`), the shared
core for the MyCelium ecosystem: configuration, platform abstraction and
diagnostics are reused.

| | |
|---|---|
| Mod id | `brewingplus` |
| Minecraft | **26.3** · Java **25** |
| Loaders | Fabric, Forge, NeoForge |
| Docs | [wiki.mycelium-gg.org/brewingplus](https://wiki.mycelium-gg.org/brewingplus/) |

## Behavior

- A potion button sits in the brewing stand's top-right corner. It opens
  the guide without closing the stand; "Back to stand" (or Esc) returns to
  it with the bottles still in place. If the stand holds a potion, the
  guide opens on it.
- **Potions**: a grid of every potion the world's recipes make (drinkable,
  splash, lingering). Filter by bottle type, search by name *or ingredient*
  (`carrot` finds Night Vision), or tick **Have all** to see only what you
  can brew with your inventory. A green dot marks those potions.
- Pick one to see its recipe chain from a water bottle, e.g. Splash Potion
  of Night Vision:

  ```
  Start: Water Bottle                         have 3
  1. Nether Wart    -> Awkward Potion           x5
  2. Golden Carrot  -> Potion of Night Vision   x2
  3. Gunpowder      -> Splash Potion of NV      x4
  ```

  plus what it brews into next, other recipes that make it, whether you
  carry everything (or what is missing), and the stand's remaining fuel.
  Ties go to the route that converts to splash/lingering last.
- **Ingredients**: every reagent and every recipe it is used in.
- **Track at stand**: back at the stand, a ghost of the next ingredient
  sits in the empty ingredient slot (or ghosts of the starting bottle in
  empty bottle slots), and a card beside the stand shows the next step,
  how many you carry and how many steps are left. It flags wrong
  ingredients, bottles that can no longer become the tracked potion, mixed
  bottles and missing fuel.

### Where recipes come from

Brewing is data-driven since 26.x (`minecraft:brewing` recipes), and
servers never send those recipes to clients, only the item sets of valid
reagents and bottle inputs.

- **Singleplayer / LAN host**: read from the integrated server's recipe
  manager. Exact, datapacks and modded recipes included.
- **Remote server**: the vanilla recipes, read from the game's own data
  pack, minus any whose reagent the server rejects. Reagents the server
  accepts that no known recipe uses are listed with a `?`.

The guide's title bar says which one is in use.

## Configuration (`config/brewingplus.json`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch (also toggleable live via `Mycel.setEnabled`). |
| `showButton` | `true` | Show the guide button on the brewing stand. |
| `showStandHint` | `true` | Ghost ingredient and next-step card while a potion is tracked. |
| `debug` | `false` | Log recipe loading, guide clicks and filters. |

Reload live with `/mycel reload brewingplus`. A config screen is
available from the mod list on Forge and NeoForge.

## Building

JDK 25, then:

```sh
./gradlew build            # all loaders + tests
./gradlew :common:test     # unit tests only
```

`VanillaRecipesTest` runs the route finder over the real vanilla 26.3
brewing recipes from the game jar, so a Minecraft update that changes them
shows up as a failing test.

Mycel itself comes from [JitPack](https://jitpack.io/#MyCelium-GG/MyCel-Lib)
as one universal jar for all loaders
(`com.github.MyCelium-GG:MyCel-Lib:1.3.0`). No local setup needed.
