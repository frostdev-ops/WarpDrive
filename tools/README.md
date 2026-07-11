# WID4 celestial map generator

`gen_celestial_map.py` + `celestial_map.json` produce the pack's celestial map:
`src/main/resources/config/celestialObjects-Galacticraft+ExtraPlanets.xml`
(schema version 3, validated against `WarpDrive.xsd`). The generated XML must
never be hand-edited — edit the manifest and regenerate.

## Usage

```
python tools/gen_celestial_map.py --check     # validate the manifest only
python tools/gen_celestial_map.py             # rewrite the fork resource XML
python tools/gen_celestial_map.py --out "<instance>/config/warpdrive/celestialObjects-Galacticraft+ExtraPlanets.xml"
```

Requires Python 3, stdlib only.

## Two targets to keep in sync

1. **Fork resource** (`src/main/resources/config/...xml`) — bundled in the jar,
   unpacked by WarpDrive into fresh instances. This is the default output.
2. **Live instance config** (`<instance>/config/warpdrive/...xml`) — the copy the
   game actually reads once it exists. After changing the map, regenerate with
   `--out` pointing at every live instance (local test profile AND the server),
   or delete the instance copy and let WarpDrive unpack the bundled one.

## Manifest rules

- **Never move a shipped body.** Coordinates are pinned in the manifest
  (explicit `center` per body). Moving a body strands ships/players parked at
  its old in-space location. Add new bodies in free space instead; use
  `--check` to catch overlaps.
- **Dimension ids come from the mods' configs** — verify against the pack's
  `config/` (or the mod jar defaults) before adding an entry:
  - Galacticraft: Moon -28, Mars -29, Asteroids -30, Venus -31
  - GalaxySpace: -1005..-1026 (see `GSConfigDimensions`), exoplanets
    Proxima b -1025, Barnarda C/C1/C2 -1030/-1031/-1032, Tau Ceti f -1338
  - More Planets: Diona -2542, Chalos -2543, Nibiru -2544, Fronos -2545,
    Koentus -2642
  - WarpDrive-owned: Hyperspace -100; space systems Sol -101, Aurora -102,
    Verdance -103, Umbra -104, Alpha Centauri -105, Barnard's Star -106,
    Tau Ceti -107
  - GC `-26/-27` and GalaxySpace `-1126..-1129` are **provider ids, not
    dimensions** — never map them.
  - GC/GS **space stations** are dynamic dimensions registered at runtime by
    `CompatGalacticraftSpaceStations` — never add them to the manifest.
- Bodies without a `dim` are render-only markers (Saturn, Uranus, Neptune,
  the star sprites).
- A child's `center` is expressed in its parent's dimension coordinate space
  (which is centered on 0,0). For render-only parents, children are offset
  from the parent's own position instead.
- `ring`/`slot` and `angle`/`radius` placement are supported as alternatives
  to `center` for new bodies; slot counts are fixed per ring so a new body
  never shifts an existing one.

## Layout notes (2026-07)

- Hyperspace grew 400000² → 800000² so the three new star systems fit at
  radius 250000: Alpha Centauri (0, 250000), Barnard's Star (-250000, 0),
  Tau Ceti (250000, 0). The four original systems keep their quadrant centers.
- The GalaxySpace exoplanets moved out of Sol's outer ring into their own
  systems (matching GalaxySpace's own star systems), so reaching them now
  requires hyperspace. **Migration caveat:** anything parked at their old
  in-Sol positions (space dim -101, outer ring around x -13827..-59018,
  z 22042..61464) is now in open space.
- Jupiter gained dimension -1026 (GalaxySpace's station-anchor world) at its
  existing map position; Deimos -1013 and Barnarda C2 -1032 were added.
- History: the original generated map was lost from the live instance when a
  clean WarpDrive-only test regenerated `config/warpdrive/` (2026-07-11); it
  was reconstructed into this manifest. The manifest in git is now the single
  source of truth.
