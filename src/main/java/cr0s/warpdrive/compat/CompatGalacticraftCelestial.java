package cr0s.warpdrive.compat;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.EventWarpDrive;
import cr0s.warpdrive.api.WarpDriveText;
import cr0s.warpdrive.block.movement.TileEntityShipCore;
import cr0s.warpdrive.data.CelestialObject;
import cr0s.warpdrive.data.CelestialObjectManager;
import cr0s.warpdrive.world.SpaceWorldProvider;

import java.util.List;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import micdoodle8.mods.galacticraft.api.GalacticraftRegistry;
import micdoodle8.mods.galacticraft.api.galaxies.CelestialBody;
import micdoodle8.mods.galacticraft.api.galaxies.GalaxyRegistry;
import micdoodle8.mods.galacticraft.api.galaxies.Planet;
import micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase;
import micdoodle8.mods.galacticraft.core.GalacticraftCore;

/**
 * Registers WarpDrive space dimensions as Galacticraft celestial bodies, so rockets can
 * select and fly to them. Hyperspace is deliberately never registered: it stays out of
 * reach of rockets, requiring a ship with a hyperdrive.
 * Also guards ship jumps against Galacticraft rockets that are mid-launch aboard.
 */
public class CompatGalacticraftCelestial {

	// Low Earth Orbit is cheaper to reach than the Moon
	private static final int ROCKET_TIER_SPACE = 1;

	public static void register() {
		MinecraftForge.EVENT_BUS.register(new CompatGalacticraftCelestial());

		int indexSpace = 0;
		for (final CelestialObject celestialObject : CelestialObjectManager.getRegistry(false)) {
			if (!celestialObject.isSpace()) {
				continue;
			}
			final String bodyName = "warpdrive_space_" + Math.abs(celestialObject.dimensionId);
			final Planet planetSpace = new Planet(bodyName);
			planetSpace.setParentSolarSystem(GalacticraftCore.solarSystemSol);
			// draw as an outer ring body on the celestial selection screen, clear of vanilla planets
			planetSpace.setRelativeDistanceFromCenter(new CelestialBody.ScalableDistance(1.75F + 0.25F * indexSpace, 1.75F + 0.25F * indexSpace));
			planetSpace.setRelativeOrbitTime(6.0F + indexSpace);
			planetSpace.setPhaseShift(0.7F + 1.1F * indexSpace);
			planetSpace.setBodyIcon(new ResourceLocation("galacticraftcore", "textures/gui/celestialbodies/asteroid.png"));
			planetSpace.setTierRequired(ROCKET_TIER_SPACE);
			// dimension already exists (WarpDrive registers it), so never auto-register
			planetSpace.setDimensionInfo(celestialObject.dimensionId, SpaceWorldProvider.class, false);
			planetSpace.setForceStaticLoad(false);
			GalaxyRegistry.register(planetSpace);

			WarpDrive.logger.info(String.format("Registered WarpDrive space dimension %d as Galacticraft rocket destination %s (tier %d)",
			                                    celestialObject.dimensionId, bodyName, ROCKET_TIER_SPACE ));
			indexSpace++;
		}
		if (indexSpace == 0) {
			return;
		}

		GalacticraftRegistry.registerTeleportType(SpaceWorldProvider.class, new TeleportTypeWarpDriveSpace());
		GalacticraftRegistry.registerRocketGui(SpaceWorldProvider.class, new ResourceLocation("galacticraftcore", "textures/gui/overworld_rocket_gui.png"));
	}

	// Deny ship jumps while a Galacticraft rocket aboard is mid-launch: the live entity
	// would be teleported out from under its own launch logic.
	@SubscribeEvent
	public void onPreJump(final EventWarpDrive.Ship.PreJump event) {
		if (!(event.shipController instanceof TileEntityShipCore)) {
			return;
		}
		final TileEntityShipCore shipCore = (TileEntityShipCore) event.shipController;
		final AxisAlignedBB aabbShip = new AxisAlignedBB(
				shipCore.minX, shipCore.minY, shipCore.minZ,
				shipCore.maxX + 0.99D, shipCore.maxY + 0.99D, shipCore.maxZ + 0.99D );
		final List<EntitySpaceshipBase> listRockets = event.worldCurrent.getEntitiesWithinAABB(EntitySpaceshipBase.class, aabbShip);
		for (final EntitySpaceshipBase entityRocket : listRockets) {
			if (entityRocket.launchPhase != EntitySpaceshipBase.EnumLaunchPhase.UNIGNITED.ordinal()) {
				event.setCanceled(true);
				event.appendReason(new WarpDriveText(Commons.getStyleWarning(), "warpdrive.compat.guide.rocket_launch_in_progress",
				                                     Math.round(entityRocket.posX), Math.round(entityRocket.posY), Math.round(entityRocket.posZ) ));
				return;
			}
		}
	}
}
