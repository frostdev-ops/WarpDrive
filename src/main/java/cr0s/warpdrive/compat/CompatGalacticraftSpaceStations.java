package cr0s.warpdrive.compat;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.data.CelestialObject;
import cr0s.warpdrive.data.CelestialObjectManager;
import cr0s.warpdrive.network.PacketHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import micdoodle8.mods.galacticraft.api.world.IOrbitDimension;
import micdoodle8.mods.galacticraft.core.dimension.SpaceStationWorldData;
import micdoodle8.mods.galacticraft.core.util.WorldUtil;

/**
 * Registers Galacticraft space station dimensions as WarpDrive celestial objects at runtime,
 * so ships can jump to/from them, they show on the star map, and breathing rules apply.
 * Stations are never persisted to the celestial XML: they are rebuilt from Galacticraft's
 * own data on every server start, and newly created stations register through WorldEvent.Load.
 */
public class CompatGalacticraftSpaceStations {

	private static final String ID_PREFIX = "galacticraft_station_";

	private static final int STATION_BORDER_RADIUS = 250;
	private static final int STATION_MARGIN = 500;
	// 8 compass slots around the home planet, then a second ring further out
	private static final int[] SLOT_X = {  1,  0, -1,  0,  1, -1, -1,  1 };
	private static final int[] SLOT_Z = {  0, -1,  0,  1, -1, -1,  1,  1 };

	private static CompatGalacticraftSpaceStations INSTANCE;

	public static void register() {
		if (INSTANCE == null) {
			INSTANCE = new CompatGalacticraftSpaceStations();
			MinecraftForge.EVENT_BUS.register(INSTANCE);
		}
	}

	// Called from FMLServerStartedEvent, after Galacticraft populated WorldUtil.registeredSpaceStations.
	// Sweeps stations from a previously loaded save, then registers the current ones.
	public static void onServerStarted() {
		final HashMap<Integer, Integer> registeredSpaceStations = WorldUtil.registeredSpaceStations;
		if (registeredSpaceStations == null) {
			return;
		}

		// unregister stations left over from another save (i.e. single player world switching)
		final List<String> idsToRemove = new ArrayList<>();
		for (final CelestialObject celestialObject : CelestialObjectManager.getRegistry(false)) {
			if ( celestialObject.id.startsWith(ID_PREFIX)
			  && !registeredSpaceStations.containsKey(celestialObject.dimensionId) ) {
				idsToRemove.add(celestialObject.id);
			}
		}
		for (final String id : idsToRemove) {
			WarpDrive.logger.info(String.format("Removing stale space station celestial object %s", id));
			CelestialObjectManager.unregisterRuntimeCelestialObject(id);
		}

		// register all known stations
		int countRegistered = 0;
		for (final Map.Entry<Integer, Integer> entry : registeredSpaceStations.entrySet()) {
			final int dimensionId = entry.getKey();
			if (!CelestialObjectManager.isRegistered(dimensionId)) {
				if (registerStation(DimensionManager.getWorld(0), dimensionId)) {
					countRegistered++;
				}
			}
		}
		if (countRegistered > 0) {
			WarpDrive.logger.info(String.format("Registered %d Galacticraft space station(s) on the star map", countRegistered));
			PacketHandler.sendClientSyncToAll();
		}
	}

	// Stations created while the server is running load their dimension right away:
	// register them as they appear.
	@SubscribeEvent
	public void onWorldLoad(@Nonnull final WorldEvent.Load event) {
		final World world = event.getWorld();
		if ( world == null
		  || world.isRemote
		  || !(world.provider instanceof IOrbitDimension) ) {
			return;
		}
		final int dimensionId = world.provider.getDimension();
		if (CelestialObjectManager.isRegistered(dimensionId)) {
			return;
		}
		if (registerStation(world, dimensionId)) {
			PacketHandler.sendClientSyncToAll();
		}
	}

	private static boolean registerStation(final World world, final int dimensionId) {
		final SpaceStationWorldData stationData;
		try {
			stationData = SpaceStationWorldData.getMPSpaceStationData(world, dimensionId, null);
		} catch (final Exception exception) {
			WarpDrive.logger.error(String.format("Failed to read Galacticraft space station data for dimension %d: %s",
			                                     dimensionId, exception ));
			return false;
		}
		if (stationData == null) {
			WarpDrive.logger.warn(String.format("No Galacticraft space station data for dimension %d, skipping", dimensionId));
			return false;
		}

		// anchor next to the home planet inside its space parent
		final int homePlanetDimensionId = stationData.getHomePlanet();
		final CelestialObject celestialHomePlanet = CelestialObjectManager.getByDimensionId(false, homePlanetDimensionId);
		CelestialObject celestialParent = null;
		double anchorX = 0.0D;
		double anchorZ = 0.0D;
		double anchorRadius = 0.0D;
		if ( celestialHomePlanet != null
		  && celestialHomePlanet.parent != null
		  && celestialHomePlanet.parent.isSpace() ) {
			celestialParent = celestialHomePlanet.parent;
			final AxisAlignedBB areaInParent = celestialHomePlanet.getAreaInParent();
			anchorX = (areaInParent.minX + areaInParent.maxX) / 2.0D;
			anchorZ = (areaInParent.minZ + areaInParent.maxZ) / 2.0D;
			anchorRadius = Math.max(areaInParent.maxX - areaInParent.minX, areaInParent.maxZ - areaInParent.minZ) / 2.0D;
		} else {
			// unmapped home planet: fall back to the first space dimension
			for (final CelestialObject celestialObject : CelestialObjectManager.getRegistry(false)) {
				if (celestialObject.isSpace()) {
					celestialParent = celestialObject;
					anchorX = celestialObject.dimensionCenterX;
					anchorZ = celestialObject.dimensionCenterZ;
					anchorRadius = 0.0D;
					break;
				}
			}
			WarpDrive.logger.warn(String.format("Galacticraft space station dimension %d has unmapped home planet %d, anchoring near the center of %s",
			                                    dimensionId,
			                                    homePlanetDimensionId,
			                                    celestialParent == null ? "-none-" : celestialParent.id ));
		}
		if (celestialParent == null) {
			WarpDrive.logger.error(String.format("No space celestial object found to anchor Galacticraft space station dimension %d, skipping", dimensionId));
			return false;
		}

		// find a free slot around the anchor, deterministic per dimension id across restarts
		final int slotFirst = Math.abs(dimensionId) % SLOT_X.length;
		Double slotX = null;
		Double slotZ = null;
		for (int ring = 1; ring <= 2 && slotX == null; ring++) {
			final double distance = anchorRadius + ring * STATION_MARGIN + STATION_BORDER_RADIUS;
			for (int indexSlot = 0; indexSlot < SLOT_X.length; indexSlot++) {
				final int slot = (slotFirst + indexSlot) % SLOT_X.length;
				final double factor = (SLOT_X[slot] != 0 && SLOT_Z[slot] != 0) ? 0.7071D : 1.0D; // diagonal slots at same distance
				final double candidateX = anchorX + SLOT_X[slot] * factor * distance;
				final double candidateZ = anchorZ + SLOT_Z[slot] * factor * distance;
				if (isSlotFree(celestialParent, candidateX, candidateZ)) {
					slotX = candidateX;
					slotZ = candidateZ;
					break;
				}
			}
		}
		if (slotX == null) {
			WarpDrive.logger.error(String.format("No free slot around %s to register Galacticraft space station dimension %d, skipping",
			                                     celestialParent.id, dimensionId ));
			return false;
		}

		final String name = stationData.getSpaceStationName() == null || stationData.getSpaceStationName().isEmpty()
		                  ? String.format("Space Station %d", dimensionId) : stationData.getSpaceStationName();
		final String owner = stationData.getOwner() == null || stationData.getOwner().isEmpty()
		                   ? "" : String.format(" (%s)", stationData.getOwner());
		final CelestialObject celestialStation = new CelestialObject(
				ID_PREFIX + dimensionId, name + owner,
				dimensionId, 0, 0,
				STATION_BORDER_RADIUS, STATION_BORDER_RADIUS,
				celestialParent.id, (int) Math.round(slotX), (int) Math.round(slotZ),
				CelestialObject.GRAVITY_LEGACY_SPACE, false, CelestialObject.PROVIDER_OTHER,
				0.75F, 0.78F, 0.82F, 1.00F );

		final boolean isRegistered = CelestialObjectManager.registerRuntimeCelestialObject(celestialStation);
		if (isRegistered) {
			WarpDrive.logger.info(String.format("Registered Galacticraft space station %s (dimension %d) on the star map at (%d %d) in %s",
			                                    name, dimensionId,
			                                    (int) Math.round(slotX), (int) Math.round(slotZ),
			                                    celestialParent.id ));
		}
		return isRegistered;
	}

	private static boolean isSlotFree(@Nonnull final CelestialObject celestialParent, final double candidateX, final double candidateZ) {
		final AxisAlignedBB candidateArea = new AxisAlignedBB(
				candidateX - STATION_BORDER_RADIUS, 0.0D, candidateZ - STATION_BORDER_RADIUS,
				candidateX + STATION_BORDER_RADIUS, 256.0D, candidateZ + STATION_BORDER_RADIUS );

		// stay inside the parent's border
		final AxisAlignedBB parentBorder = celestialParent.getWorldBorderArea();
		if ( candidateArea.minX < parentBorder.minX || candidateArea.maxX > parentBorder.maxX
		  || candidateArea.minZ < parentBorder.minZ || candidateArea.maxZ > parentBorder.maxZ ) {
			return false;
		}

		// avoid every sibling's area
		for (final CelestialObject celestialSibling : CelestialObjectManager.getRegistry(false)) {
			if ( celestialSibling.isHyperspace()
			  || celestialSibling.parent == null
			  || celestialSibling.parent.dimensionId != celestialParent.dimensionId ) {
				continue;
			}
			if (candidateArea.intersects(celestialSibling.getAreaInParent())) {
				return false;
			}
		}
		return true;
	}
}
