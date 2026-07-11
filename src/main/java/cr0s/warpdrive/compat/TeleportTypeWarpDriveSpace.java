package cr0s.warpdrive.compat;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.data.CelestialObject;
import cr0s.warpdrive.data.CelestialObjectManager;
import cr0s.warpdrive.data.EnumHullPlainType;
import cr0s.warpdrive.data.EnumTier;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import micdoodle8.mods.galacticraft.api.vector.Vector3;
import micdoodle8.mods.galacticraft.api.world.ITeleportType;

/**
 * Handles Galacticraft rocket arrivals into WarpDrive space dimensions.
 * No parachute: falling in a void dimension would drop the player past y = -10 where
 * LivingHandler teleports them to the closest child planet with atmospheric entry damage.
 * Instead, players land on a small hull platform placed high in the dimension.
 */
public class TeleportTypeWarpDriveSpace implements ITeleportType {

	private static final int ARRIVAL_Y = 240;
	private static final int BORDER_MARGIN = 200;
	private static final int PLATFORM_RADIUS = 2; // 5x5
	private static final int CLEARANCE_HEIGHT = 4;
	private static final int RELOCATION_ATTEMPTS = 8;

	@Override
	public boolean useParachute() {
		return false;
	}

	@Override
	public Vector3 getPlayerSpawnLocation(final WorldServer worldServer, final EntityPlayerMP entityPlayerMP) {
		final BlockPos blockPosArrival = getOrCreateArrivalPlatform(worldServer, entityPlayerMP.posX, entityPlayerMP.posZ, true);
		return new Vector3(blockPosArrival.getX() + 0.5D, blockPosArrival.getY() + 1.0D, blockPosArrival.getZ() + 0.5D);
	}

	@Override
	public Vector3 getEntitySpawnLocation(final WorldServer worldServer, final Entity entity) {
		final BlockPos blockPosArrival = getOrCreateArrivalPlatform(worldServer, entity.posX, entity.posZ, false);
		return new Vector3(blockPosArrival.getX() + 0.5D, blockPosArrival.getY() + 2.0D, blockPosArrival.getZ() + 0.5D);
	}

	@Override
	public Vector3 getParaChestSpawnLocation(final WorldServer worldServer, final EntityPlayerMP entityPlayerMP, final Random random) {
		final BlockPos blockPosArrival = getOrCreateArrivalPlatform(worldServer, entityPlayerMP.posX, entityPlayerMP.posZ, false);
		return new Vector3(blockPosArrival.getX() + PLATFORM_RADIUS + 0.5D, blockPosArrival.getY() + 1.0D, blockPosArrival.getZ() + 0.5D);
	}

	@Override
	public void onSpaceDimensionChanged(final World world, final EntityPlayerMP entityPlayerMP, final boolean ridingAutoRocket) {
		// no-op
	}

	@Override
	public void setupAdventureSpawn(final EntityPlayerMP entityPlayerMP) {
		// no-op
	}

	// Compute a stable arrival position from the launch coordinates (Galacticraft preserves the
	// entity position through the dimension transfer), snapped to a 16 blocks grid so repeated
	// launches from the same base reuse the same platform, clamped inside the world border.
	private static BlockPos getOrCreateArrivalPlatform(final WorldServer worldServer, final double xEntity, final double zEntity,
	                                                   final boolean canBuild) {
		double x = xEntity;
		double z = zEntity;
		final CelestialObject celestialObject = CelestialObjectManager.getByDimensionId(false, worldServer.provider.getDimension());
		if (celestialObject != null) {
			final AxisAlignedBB border = celestialObject.getWorldBorderArea();
			x = Math.min(Math.max(x, border.minX + BORDER_MARGIN), border.maxX - BORDER_MARGIN);
			z = Math.min(Math.max(z, border.minZ + BORDER_MARGIN), border.maxZ - BORDER_MARGIN);
		}
		int xSnapped = (int) Math.floor(x / 16.0D) * 16 + 8;
		int zSnapped = (int) Math.floor(z / 16.0D) * 16 + 8;

		// reuse an existing floor near the arrival point when there's one
		for (int yFloor = ARRIVAL_Y; yFloor > ARRIVAL_Y - 4; yFloor--) {
			final BlockPos blockPosFloor = new BlockPos(xSnapped, yFloor, zSnapped);
			if ( !worldServer.isAirBlock(blockPosFloor)
			  && isVolumeClear(worldServer, xSnapped, yFloor + 1, zSnapped) ) {
				return blockPosFloor;
			}
		}

		if (!canBuild) {
			return new BlockPos(xSnapped, ARRIVAL_Y, zSnapped);
		}

		// find a clear volume, probing horizontally when the preferred spot is obstructed (i.e. inside an asteroid)
		for (int attempt = 0; attempt < RELOCATION_ATTEMPTS; attempt++) {
			if (isVolumeClear(worldServer, xSnapped, ARRIVAL_Y, zSnapped)) {
				buildPlatform(worldServer, xSnapped, ARRIVAL_Y, zSnapped);
				return new BlockPos(xSnapped, ARRIVAL_Y, zSnapped);
			}
			xSnapped += 16;
			if (attempt % 2 == 1) {
				zSnapped += 16;
			}
		}

		// out of luck: overwrite whatever is at the last candidate
		WarpDrive.logger.warn(String.format("No clear volume found for rocket arrival platform in dimension %d around %d %d, overwriting",
		                                    worldServer.provider.getDimension(), xSnapped, zSnapped ));
		buildPlatform(worldServer, xSnapped, ARRIVAL_Y, zSnapped);
		return new BlockPos(xSnapped, ARRIVAL_Y, zSnapped);
	}

	private static boolean isVolumeClear(final WorldServer worldServer, final int x, final int y, final int z) {
		final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
			for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
				for (int dy = 0; dy <= CLEARANCE_HEIGHT; dy++) {
					mutableBlockPos.setPos(x + dx, y + dy, z + dz);
					if (!worldServer.isAirBlock(mutableBlockPos)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static void buildPlatform(final WorldServer worldServer, final int x, final int y, final int z) {
		final Block blockHull = WarpDrive.blockHulls_plain[EnumTier.BASIC.getIndex()][EnumHullPlainType.PLAIN.ordinal()];
		final IBlockState blockStateHull = blockHull.getDefaultState();
		for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
			for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
				worldServer.setBlockState(new BlockPos(x + dx, y, z + dz), blockStateHull, 3);
			}
		}
	}
}
