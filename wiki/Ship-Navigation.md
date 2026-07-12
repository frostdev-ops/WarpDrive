# Ship Navigation

The ship navigation interface is opened from a ship core or linked ship controller. It provides a starmap, destination and waypoint browsers, ship status, dimension editor, movement preview, and drive controls in one screen.

## Map

- The map shows configured celestial bodies in their parent coordinate space.
- Space is treated as the containing backdrop for planets and other local bodies, not as a body at the ship location.
- In normal space, the Local map layer shows only bodies in the current solar system.
- The Hyperspace map layer shows solar-system regions and hides individual local bodies until a local system is selected.
- The ship marker uses the ship core X/Z position while in space or hyperspace. When viewing the Hyperspace layer from normal space, the ship marker is anchored to the current solar-system region.
- Celestial body tooltips show object id, type, and X/Z coordinates.

## Destinations

The Destinations tab lists reachable objects and includes:

- current location marker
- destination X/Z coordinates
- estimated leg count
- estimated energy
- estimated travel time
- effective first-leg range

Routes can include takeoff, orbital approach, landing, hyperspace entry, hyperspace cruise, and hyperspace exit. The displayed range is calculated from the same movement preview used by the drive.

## Surface Waypoints

The Waypoints tab imports enabled waypoints from VoxelMap, Xaero's Minimap, and JourneyMap when those client mods are installed. These integrations are optional and use reflection, so WarpDrive does not require any map addon on the client or server.

- Only waypoints in the ship's current planetary dimension and celestial region can be plotted.
- The waypoint X/Z coordinates identify the center of the landing footprint. The map addon's Y coordinate is not trusted as a safe ship altitude.
- The server finds the highest non-air block under the ship's full configured footprint and places the bottom of the ship box one block above it.
- The entire landing box must be air, fit below build height, and remain inside the celestial border.
- The landing footprint must be in previously generated terrain. Visit unexplored waypoint areas before plotting a ship route.
- Large routes are split into ordinary planet-movement legs and use the selected autopilot mode.
- Landing clearance is checked again immediately before the final leg deploys blocks.

## Autopilot Modes

- Off: no route chaining.
- Assisted: requires confirmation for each leg.
- Safety stops: continues through safe legs and pauses before riskier transitions.
- Full auto: continues all route legs until arrival, energy wait, pause, or failure.

Full auto preserves the engaged target through ship movement so the moved core can continue the route after each jump completes.

## Ship Bounds

The Dimensions tab edits front, back, left, right, up, and down bounds. Valid edits are applied automatically. Invalid or incomplete values remain local to the text field until they become valid.

## Movement Range

The drive clamps movement to the calculated maximum distance for the current ship, command, mass, and movement type. This range comes from the ship movement cost calculation and configured movement formulas. Defensive input limits only reject pathological values from GUI, computer, or NBT input; they are not the gameplay range.

## Troubleshooting

- If the route stops in assisted mode, press Engage to confirm the next leg.
- If the route stops in safety-stops mode, check whether the next leg requires confirmation.
- If full auto stops unexpectedly, check the drive status and blocker text for energy, cooldown, stale scan, invalid route, or movement failure.
- If a surface waypoint cannot be plotted, confirm its full ship footprint is generated, inside the current celestial region, and has enough vertical clearance above its highest block.
- If map and destination coordinates do not match expectations, confirm the celestial object parent coordinates in the active celestial object configuration.
