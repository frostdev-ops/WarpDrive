package cr0s.warpdrive.block.movement;

import cr0s.warpdrive.Commons;
import cr0s.warpdrive.api.WarpDriveText;
import cr0s.warpdrive.config.ShipMovementCosts;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.BlockProperties;
import cr0s.warpdrive.data.CelestialObject;
import cr0s.warpdrive.data.CelestialObjectManager;
import cr0s.warpdrive.data.EnumShipCommand;
import cr0s.warpdrive.data.EnumShipMovementType;
import cr0s.warpdrive.data.EnumShipNavigationLegType;
import cr0s.warpdrive.data.VectorI;
import cr0s.warpdrive.WarpDrive;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.chunk.Chunk;

import net.minecraftforge.common.util.Constants;

public final class ShipNavigationHelper {

	private static final int ORBIT_Y = 128;
	private static final int TAKEOFF_MARGIN_BLOCKS = 16;
	private static final int LANDING_MARGIN_BLOCKS = 16;
	private static final int WAYPOINT_MAX_CHUNKS = 64;
	private static final long WAYPOINT_MAX_BLOCK_CHECKS = 500_000L;
	private static final int WAYPOINT_LABEL_MAX_LENGTH = 64;
	private static final int LANDING_GRID_MAX_SIZE = 16;
	private static final long LANDING_PROBE_TTL_TICKS = 600L;
	private static final int PROBE_MAX_WAYPOINTS = 8;
	private static final long PROBE_MAX_BLOCK_CHECKS_TOTAL = 500_000L;
	private static final int ASSIST_MAX_RINGS = 2;
	private static final int CRUISE_CLEARANCE_BLOCKS = 4;
	private static final int ATMOSPHERIC_CEILING_Y = 250;
	private static final int PATH_SAMPLE_MAX_POINTS = 64;
	private static final int PATH_SAMPLE_MAX_CHUNK_LOADS = 8;
	private static final int WAYPOINT_RETARGET_TOLERANCE = 8;
	private static final long LANDING_SELF_HEAL_TTL_TICKS = 100L;
	private static final int ASSIST_MAX_CANDIDATES = 8;
	private static final long ASSIST_MAX_BLOCK_CHECKS_PER_CANDIDATE = 50_000L;
	private static final int MAP_VERSION = 2;

	private ShipNavigationHelper() {
		// static helper
	}

	@Nonnull
	public static NBTTagCompound buildSnapshot(@Nonnull final EntityPlayerMP entityPlayerMP,
	                                           @Nonnull final TileEntityShipCore shipCore,
	                                           @Nullable final String notice) {
		return buildSnapshot(entityPlayerMP, shipCore, shipCore.getPos(), notice);
	}

	@Nonnull
	public static NBTTagCompound buildSnapshot(@Nonnull final EntityPlayerMP entityPlayerMP,
	                                           @Nonnull final TileEntityShipCore shipCore,
	                                           @Nonnull final BlockPos blockPosAccess,
	                                           @Nullable final String notice) {
		return buildSnapshot(entityPlayerMP, shipCore, blockPosAccess, notice, null);
	}
	
	@Nonnull
	public static NBTTagCompound buildSnapshot(@Nonnull final EntityPlayerMP entityPlayerMP,
	                                           @Nonnull final TileEntityShipCore shipCore,
	                                           @Nonnull final BlockPos blockPosAccess,
	                                           @Nullable final String notice,
	                                           @Nullable final ShipMovementPreview movementPreview) {
		shipCore.requestShipScanIfStale();
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setInteger("coreX", shipCore.getPos().getX());
		tagCompound.setInteger("coreY", shipCore.getPos().getY());
		tagCompound.setInteger("coreZ", shipCore.getPos().getZ());
		tagCompound.setInteger("accessX", blockPosAccess.getX());
		tagCompound.setInteger("accessY", blockPosAccess.getY());
		tagCompound.setInteger("accessZ", blockPosAccess.getZ());
		tagCompound.setInteger("dimensionId", shipCore.getWorld().provider.getDimension());
		tagCompound.setBoolean("allowed", shipCore.isCrewMember(entityPlayerMP));
		tagCompound.setBoolean("enabled", shipCore.getIsEnabled());
		tagCompound.setBoolean("offline", shipCore.isOffline());
		tagCompound.setBoolean("maintenance", shipCore.isUnderMaintenance());
		tagCompound.setBoolean("busy", shipCore.isBusy());
		tagCompound.setString("shipName", shipCore.name(null)[0]);
		tagCompound.setString("command", shipCore.getCommand().getName());
		tagCompound.setInteger("shipMass", shipCore.shipMass);
		tagCompound.setInteger("shipVolume", shipCore.shipVolume);
		tagCompound.setLong("energyStored", shipCore.energy_getEnergyStored());
		tagCompound.setString("energyUnits", shipCore.energy_getDisplayUnits());
		tagCompound.setTag("dimensions", writeDimensions(shipCore));
		tagCompound.setTag("movement", writeMovement(shipCore));
		tagCompound.setTag("orientation", writeOrientation(shipCore));
		tagCompound.setTag("tier", writeObjectArray(shipCore.getTier(), "index", "name"));
		tagCompound.setTag("upgrades", writeObjectArray(shipCore.getUpgrades(), "upgradeable", "status"));
		tagCompound.setTag("version", writeVersion(shipCore.getVersion()));
		tagCompound.setTag("assembly", writeObjectArray(shipCore.getAssemblyStatus(), "valid", "status"));
		tagCompound.setTag("energyStatus", writeObjectArray(shipCore.getEnergyStatus(), "stored", "capacity", "units"));
		tagCompound.setTag("state", writeObjectArray(shipCore.state(), "status", "enabled", "state", "energy"));
		tagCompound.setTag("driveStatus", writeDriveStatus(shipCore));
		tagCompound.setBoolean("inSpace", boolFromObjectArray(shipCore.isInSpace()));
		tagCompound.setBoolean("inHyperspace", boolFromObjectArray(shipCore.isInHyperspace()));
		tagCompound.setString("navigationTargetId", shipCore.getNavigationTargetId());
		tagCompound.setBoolean("navigationTargetIsWaypoint", shipCore.isNavigationTargetWaypoint());
		if (shipCore.isNavigationTargetWaypoint()) {
			tagCompound.setString("navigationWaypointName", shipCore.getNavigationWaypointName());
			tagCompound.setString("navigationWaypointSource", shipCore.getNavigationWaypointSource());
			tagCompound.setInteger("navigationWaypointX", shipCore.getNavigationWaypointX());
			tagCompound.setInteger("navigationWaypointY", shipCore.getNavigationWaypointY());
			tagCompound.setInteger("navigationWaypointZ", shipCore.getNavigationWaypointZ());
			final NBTTagCompound tagWaypointLanding = writeWaypointLanding(shipCore);
			if (tagWaypointLanding != null) {
				tagCompound.setTag("waypointLanding", tagWaypointLanding);
			}
		}
		tagCompound.setInteger("mapVersion", getMapVersion());
		tagCompound.setLong("serverTimeMs", shipCore.getWorld().getTotalWorldTime() * 50L);
		if (notice != null && !notice.isEmpty()) {
			tagCompound.setString("notice", notice);
		}

		final CelestialObject celestialObjectCurrent = CelestialObjectManager.get(shipCore.getWorld(), shipCore.getPos().getX(), shipCore.getPos().getZ());
		if (celestialObjectCurrent != null) {
			tagCompound.setString("currentCelestialId", celestialObjectCurrent.id);
			tagCompound.setString("currentCelestialName", celestialObjectCurrent.getDisplayName());
		}

		final CelestialObject celestialObjectTarget = getTarget(shipCore);
		final NBTTagCompound tagHeavy = getHeavySnapshot(shipCore, celestialObjectCurrent, celestialObjectTarget);
		tagCompound.setTag("route", tagHeavy.getCompoundTag("route"));
		tagCompound.setTag("destinations", tagHeavy.getTagList("destinations", 10));
		final ShipMovementPreview previewEffective;
		if (movementPreview == null) {
			final VectorI movement = shipCore.getMovement();
			previewEffective = shipCore.previewMovement(shipCore.getCommand(), movement.x, movement.y, movement.z, shipCore.getRotationSteps());
		} else {
			previewEffective = movementPreview;
		}
		tagCompound.setTag("movementPreview", previewEffective.writeToNBT());
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound getHeavySnapshot(@Nonnull final TileEntityShipCore shipCore,
	                                               @Nullable final CelestialObject celestialObjectCurrent,
	                                               @Nullable final CelestialObject celestialObjectTarget) {
		final String cacheKey = String.format("%d|%d|%d|%d|%d|%s|%d|%d|%d|%d|%d|%d|%d",
		                                      getMapVersion(),
		                                      shipCore.getWorld().provider.getDimension(),
		                                      shipCore.getPos().getX(),
		                                      shipCore.getPos().getY(),
		                                      shipCore.getPos().getZ(),
		                                      celestialObjectCurrent == null ? "" : celestialObjectCurrent.id,
		                                      shipCore.getShipScanSignature(),
		                                      shipCore.shipMass,
		                                      shipCore.getTierIndex(),
		                                      shipCore.getFront() + shipCore.getBack(),
		                                      shipCore.getLeft() + shipCore.getRight(),
		                                      shipCore.getUp() + shipCore.getDown(),
		                                      shipCore.getAutopilotMode().ordinal());
		final NBTTagCompound tagCached = shipCore.getNavigationHeavyCache(cacheKey);
		final NBTTagCompound tagHeavy = new NBTTagCompound();
		tagHeavy.setTag("route", buildRoute(shipCore, celestialObjectCurrent, celestialObjectTarget));
		if (tagCached != null) {
			tagHeavy.setTag("destinations", tagCached.getTagList("destinations", 10));
			return tagHeavy;
		}
		final NBTTagCompound tagDestinations = new NBTTagCompound();
		tagDestinations.setTag("destinations", buildDestinations(shipCore, celestialObjectCurrent));
		shipCore.setNavigationHeavyCache(cacheKey, tagDestinations);
		tagHeavy.setTag("destinations", tagDestinations.getTagList("destinations", 10));
		return tagHeavy;
	}

	@Nonnull
	private static NBTTagCompound writeDimensions(@Nonnull final TileEntityShipCore shipCore) {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setInteger("front", shipCore.getFront());
		tagCompound.setInteger("right", shipCore.getRight());
		tagCompound.setInteger("up", shipCore.getUp());
		tagCompound.setInteger("back", shipCore.getBack());
		tagCompound.setInteger("left", shipCore.getLeft());
		tagCompound.setInteger("down", shipCore.getDown());
		tagCompound.setInteger("minX", shipCore.minX);
		tagCompound.setInteger("minY", shipCore.minY);
		tagCompound.setInteger("minZ", shipCore.minZ);
		tagCompound.setInteger("maxX", shipCore.maxX);
		tagCompound.setInteger("maxY", shipCore.maxY);
		tagCompound.setInteger("maxZ", shipCore.maxZ);
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound writeMovement(@Nonnull final TileEntityShipCore shipCore) {
		final VectorI movement = shipCore.getMovement();
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setInteger("front", movement.x);
		tagCompound.setInteger("up", movement.y);
		tagCompound.setInteger("right", movement.z);
		tagCompound.setInteger("rotationSteps", shipCore.getRotationSteps());
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound writeOrientation(@Nonnull final TileEntityShipCore shipCore) {
		final Object[] values = shipCore.getOrientation();
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setInteger("x", values.length > 0 && values[0] instanceof Number ? ((Number) values[0]).intValue() : 0);
		tagCompound.setInteger("y", values.length > 1 && values[1] instanceof Number ? ((Number) values[1]).intValue() : 0);
		tagCompound.setInteger("z", values.length > 2 && values[2] instanceof Number ? ((Number) values[2]).intValue() : 0);
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound writeVersion(@Nonnull final Integer[] version) {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setString("text", version.length >= 3 ? version[0] + "." + version[1] + "." + version[2] : WarpDrive.VERSION);
		for (int index = 0; index < version.length; index++) {
			tagCompound.setInteger("part" + index, version[index]);
		}
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound writeDriveStatus(@Nonnull final TileEntityShipCore shipCore) {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setString("stateName", shipCore.getDriveStateName());
		tagCompound.setInteger("warmupTicks", shipCore.getWarmupRemainingTicks());
		tagCompound.setInteger("warmupTotal", Math.max(1, shipCore.getWarmupTotalTicks()));
		tagCompound.setInteger("cooldownTicks", shipCore.getCooldownRemainingTicks());
		tagCompound.setInteger("cooldownTotal", Math.max(1, shipCore.getCooldownTotalTicks()));
		tagCompound.setBoolean("jumpInProgress", shipCore.isJumpInProgress());
		tagCompound.setBoolean("cooling", shipCore.getCooldownRemainingTicks() > 0);
		tagCompound.setString("movementType", shipCore.getShipMovementTypeName());
		tagCompound.setString("autopilotMode", shipCore.getAutopilotMode().getName());
		tagCompound.setString("autopilotStatus", shipCore.getAutopilotStatus().getName());
		tagCompound.setString("autopilotErrorKey", shipCore.getAutopilotLastErrorKey());
		tagCompound.setInteger("autopilotLegs", shipCore.getAutopilotLegsExecuted());
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound writeObjectArray(@Nullable final Object[] values, @Nonnull final String... keys) {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		if (values == null) {
			return tagCompound;
		}
		for (int index = 0; index < keys.length && index < values.length; index++) {
			final Object value = values[index];
			if (value instanceof Boolean) {
				tagCompound.setBoolean(keys[index], (Boolean) value);
			} else if (value instanceof Number) {
				tagCompound.setLong(keys[index], ((Number) value).longValue());
			} else if (value != null) {
				tagCompound.setString(keys[index], value.toString());
			}
		}
		return tagCompound;
	}

	private static boolean boolFromObjectArray(@Nullable final Object[] values) {
		return values != null && values.length > 0 && values[0] instanceof Boolean && (Boolean) values[0];
	}

	@Nonnull
	public static ShipMovementPreview previewMovement(@Nonnull final TileEntityShipCore shipCore,
	                                                  @Nonnull final NBTTagCompound payload) {
		final EnumShipCommand command = parseShipCommand(payload.getString("command"));
		if (command == null) {
			return shipCore.previewMovement(EnumShipCommand.IDLE, 0, 0, 0, (byte) 0);
		}
		final ShipMovementPreview preview = shipCore.previewMovement(command,
		                                                             payload.getInteger("moveFront"),
		                                                             payload.getInteger("moveUp"),
		                                                             payload.getInteger("moveRight"),
		                                                             payload.getByte("rotationSteps"));
		if ("warpdrive.navigation.blocker.stale_scan".equals(preview.blockerKey)) {
			shipCore.requestShipScan();
		}
		return preview;
	}

	public static boolean executeMovement(@Nonnull final TileEntityShipCore shipCore,
	                                      @Nonnull final NBTTagCompound payload) {
		return executeMovement(shipCore, payload, null);
	}
	
	public static boolean executeMovement(@Nonnull final TileEntityShipCore shipCore,
	                                      @Nonnull final NBTTagCompound payload,
	                                      @Nullable final ShipMovementPreview previewCached) {
		final EnumShipCommand command = parseShipCommand(payload.getString("command"));
		if (command == null) {
			return false;
		}
		// a manual command from the Move/Drive tab cancels any running autopilot route (player took over)
		shipCore.cancelAutopilot();
		if (!command.isMovement()) {
			shipCore.command(new Object[] { command.getName(), true });
			return true;
		}
		final ShipMovementPreview preview = previewCached == null ? previewMovement(shipCore, payload) : previewCached;
		if (!preview.canEngage) {
			return false;
		}
		shipCore.movement(new Object[] { preview.effectiveMovement.x, preview.effectiveMovement.y, preview.effectiveMovement.z });
		shipCore.rotationSteps(new Object[] { (int) preview.rotationSteps });
		shipCore.command(new Object[] { command.getName(), true });
		return true;
	}

	public static void applySettings(@Nonnull final TileEntityShipCore shipCore,
	                                 @Nonnull final NBTTagCompound payload) {
		if (payload.hasKey("name")) {
			shipCore.name(new Object[] { payload.getString("name") });
		}
		if (payload.hasKey("enable")) {
			shipCore.enable(new Object[] { payload.getBoolean("enable") });
		}
		if (payload.hasKey("energyDisplayUnits")) {
			shipCore.energyDisplayUnits(new Object[] { payload.getString("energyDisplayUnits") });
		}
		if (payload.hasKey("dim_positive")) {
			final NBTTagCompound dimensions = payload.getCompoundTag("dim_positive");
			shipCore.dim_positive(new Object[] {
					dimensions.getInteger("front"),
					dimensions.getInteger("right"),
					dimensions.getInteger("up")
			});
		}
		if (payload.hasKey("dim_negative")) {
			final NBTTagCompound dimensions = payload.getCompoundTag("dim_negative");
			shipCore.dim_negative(new Object[] {
					dimensions.getInteger("back"),
					dimensions.getInteger("left"),
					dimensions.getInteger("down")
			});
		}
	}

	@Nullable
	public static EnumShipCommand parseShipCommand(@Nullable final String name) {
		if (name == null) {
			return null;
		}
		for (final EnumShipCommand command : EnumShipCommand.values()) {
			if ( command.name().equalsIgnoreCase(name)
			  || command.getName().equalsIgnoreCase(name) ) {
				return command;
			}
		}
		return null;
	}

	public static int getMapVersion() {
		int hash = MAP_VERSION;
		for (final CelestialObject celestialObject : CelestialObjectManager.getRegistrySnapshot(false)) {
			hash = 31 * hash + celestialObject.id.hashCode();
			hash = 31 * hash + celestialObject.getDisplayName().hashCode();
			hash = 31 * hash + celestialObject.dimensionId;
			hash = 31 * hash + celestialObject.getParentCenterX();
			hash = 31 * hash + celestialObject.getParentCenterZ();
			hash = 31 * hash + celestialObject.borderRadiusX;
			hash = 31 * hash + celestialObject.borderRadiusZ;
			hash = 31 * hash + (celestialObject.isVirtual() ? 1 : 0);
			hash = 31 * hash + (celestialObject.isSpace() ? 1 : 0);
			hash = 31 * hash + (celestialObject.isHyperspace() ? 1 : 0);
			for (final CelestialObject.RenderData renderData : celestialObject.setRenderData) {
				hash = 31 * hash + Float.floatToIntBits(renderData.red);
				hash = 31 * hash + Float.floatToIntBits(renderData.green);
				hash = 31 * hash + Float.floatToIntBits(renderData.blue);
				hash = 31 * hash + (renderData.texture == null ? 0 : renderData.texture.hashCode());
			}
			if (celestialObject.boxTextures != null) {
				for (int index = 0; index < celestialObject.boxTextures.length; index++) {
					hash = 31 * hash + celestialObject.boxTextures[index].toString().hashCode();
				}
			}
		}
		return hash;
	}

	@Nonnull
	public static NBTTagCompound buildStaticMapSnapshot() {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setInteger("mapVersion", getMapVersion());
		final NBTTagList tagListObjects = new NBTTagList();
		for (final CelestialObject celestialObject : CelestialObjectManager.getRegistrySnapshot(false)) {
			tagListObjects.appendTag(writeCelestialObject(celestialObject));
		}
		tagCompound.setTag("celestialObjects", tagListObjects);
		return tagCompound;
	}

	@Nonnull
	@SuppressWarnings("PMD.NPathComplexity")
	private static NBTTagCompound writeCelestialObject(@Nonnull final CelestialObject celestialObject) {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setString("id", celestialObject.id);
		tagCompound.setString("parentId", celestialObject.parentId == null ? "" : celestialObject.parentId);
		tagCompound.setString("name", celestialObject.getDisplayName());
		tagCompound.setString("description", celestialObject.getDescription());
		tagCompound.setBoolean("virtual", celestialObject.isVirtual());
		tagCompound.setBoolean("space", celestialObject.isSpace());
		tagCompound.setBoolean("hyperspace", celestialObject.isHyperspace());
		tagCompound.setBoolean("atmosphere", celestialObject.hasAtmosphere());
		tagCompound.setInteger("dimensionId", celestialObject.dimensionId);
		tagCompound.setInteger("dimensionCenterX", celestialObject.dimensionCenterX);
		tagCompound.setInteger("dimensionCenterZ", celestialObject.dimensionCenterZ);
		tagCompound.setInteger("parentCenterX", celestialObject.getParentCenterX());
		tagCompound.setInteger("parentCenterZ", celestialObject.getParentCenterZ());
		tagCompound.setInteger("borderRadiusX", celestialObject.borderRadiusX);
		tagCompound.setInteger("borderRadiusZ", celestialObject.borderRadiusZ);

		float red = celestialObject.isHyperspace() ? 0.85F : celestialObject.isSpace() ? 0.25F : 0.45F;
		float green = celestialObject.isHyperspace() ? 0.20F : celestialObject.isSpace() ? 0.55F : 0.75F;
		float blue = celestialObject.isHyperspace() ? 0.20F : celestialObject.isSpace() ? 1.00F : 1.00F;
		if (!celestialObject.setRenderData.isEmpty()) {
			final CelestialObject.RenderData renderData = celestialObject.setRenderData.iterator().next();
			red = renderData.red;
			green = renderData.green;
			blue = renderData.blue;
		}
		for (final CelestialObject.RenderData renderData : celestialObject.setRenderData) {
			if (renderData.texture != null) {
				tagCompound.setString("iconTexture", renderData.texture);
				break;
			}
		}
		if (!tagCompound.hasKey("iconTexture") && celestialObject.boxTextures != null && celestialObject.boxTextures.length > 0) {
			tagCompound.setString("iconTexture", celestialObject.boxTextures[0].toString());
		}
		tagCompound.setFloat("red", red);
		tagCompound.setFloat("green", green);
		tagCompound.setFloat("blue", blue);
		return tagCompound;
	}

	@Nonnull
	@SuppressWarnings("PMD.NPathComplexity")
	private static NBTTagCompound buildRoute(@Nonnull final TileEntityShipCore shipCore,
	                                         @Nullable final CelestialObject celestialObjectCurrent,
	                                         @Nullable final CelestialObject celestialObjectTarget) {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		final NBTTagList tagListLegs = new NBTTagList();
		if (shipCore.isNavigationTargetWaypoint()) {
			if (isAtNavigationDestination(shipCore, celestialObjectCurrent, null)) {
				return route(tagCompound, tagListLegs, false, "warpdrive.navigation.route.already_at_destination", "");
			}
			final Leg nextLeg = createAtmosphericLeg(shipCore);
			if (nextLeg == null) {
				return route(tagCompound, tagListLegs, false, "warpdrive.navigation.route.no_route", "warpdrive.navigation.route.waypoint_unavailable");
			}
			readyRoute(shipCore, tagCompound, tagListLegs, nextLeg);
			writeWaypointRouteTelemetry(shipCore, tagCompound, nextLeg);
			return tagCompound;
		}
		if (celestialObjectTarget == null) {
			return route(tagCompound, tagListLegs, false, "warpdrive.navigation.route.select_destination", "");
		}
		if (celestialObjectCurrent == null) {
			return route(tagCompound, tagListLegs, false, "warpdrive.navigation.route.no_celestial_here", "warpdrive.navigation.route.move_into_region");
		}
		if (celestialObjectTarget.isVirtual()) {
			return route(tagCompound, tagListLegs, false, "warpdrive.navigation.route.visual_only", "warpdrive.navigation.route.no_dimension");
		}
		if (isAtNavigationDestination(shipCore, celestialObjectCurrent, celestialObjectTarget)) {
			return route(tagCompound, tagListLegs, false, "warpdrive.navigation.route.already_at_destination", "");
		}

		final Leg nextLeg = computeNextLeg(shipCore, celestialObjectCurrent, celestialObjectTarget);
		if (nextLeg == null) {
			return route(tagCompound, tagListLegs, false, "warpdrive.navigation.route.no_route", "warpdrive.navigation.route.no_shared_hierarchy");
		}

		readyRoute(shipCore, tagCompound, tagListLegs, nextLeg);
		appendPreviewLegs(shipCore, tagListLegs, celestialObjectCurrent, celestialObjectTarget, nextLeg);
		tagCompound.setTag("legs", tagListLegs);
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound readyRoute(@Nonnull final TileEntityShipCore shipCore,
	                                        @Nonnull final NBTTagCompound tagCompound,
	                                        @Nonnull final NBTTagList tagListLegs,
	                                        @Nonnull final Leg nextLeg) {
		final ShipMovementPreview preview = nextLeg.preview == null
		                                  ? shipCore.previewMovement(nextLeg.command, nextLeg.moveFront, nextLeg.moveUp, nextLeg.moveRight, (byte) 0)
		                                  : nextLeg.preview;
		tagCompound.setBoolean("canEngage", preview.canEngage);
		tagCompound.setString("statusKey", "warpdrive.navigation.route.ready");
		tagCompound.setString("warningKey", nextLeg.warningKey);
		tagCompound.setInteger("warningStep", nextLeg.stepDistance);
		tagCompound.setInteger("warningRemaining", nextLeg.remainingDistance);
		tagCompound.setString("blockerKey", preview.canEngage ? "" : preview.blockerKey);
		tagCompound.setString("blockerMessage", preview.canEngage ? "" : preview.blockerMessage);
		tagCompound.setTag("validation", preview.writeToNBT());
		tagListLegs.appendTag(writeLeg(shipCore, nextLeg, preview));
		tagCompound.setTag("legs", tagListLegs);
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound route(@Nonnull final NBTTagCompound tagCompound, @Nonnull final NBTTagList tagListLegs,
	                                    final boolean canEngage, @Nonnull final String statusKey, @Nonnull final String warningKey) {
		tagCompound.setBoolean("canEngage", canEngage);
		tagCompound.setString("statusKey", statusKey);
		tagCompound.setString("warningKey", warningKey);
		tagCompound.setTag("legs", tagListLegs);
		return tagCompound;
	}

	private static void appendPreviewLegs(@Nonnull final TileEntityShipCore shipCore,
	                                      @Nonnull final NBTTagList tagListLegs,
	                                      @Nonnull final CelestialObject celestialObjectCurrent,
	                                      @Nonnull final CelestialObject celestialObjectTarget,
	                                      @Nonnull final Leg nextLeg) {
		if (nextLeg.type == EnumShipNavigationLegType.TAKEOFF) {
			tagListLegs.appendTag(writePreviewLeg(EnumShipNavigationLegType.SPACE_CRUISE));
		}
		final CelestialObject targetSpace = getSpaceFor(celestialObjectTarget);
		if (targetSpace != null && !celestialObjectCurrent.isHyperspace() && !celestialObjectCurrent.isSpace()
		    && celestialObjectCurrent.parent != targetSpace) {
			tagListLegs.appendTag(writePreviewLeg(EnumShipNavigationLegType.HYPERSPACE_ENTER));
		}
		if (targetSpace != null && celestialObjectCurrent.isHyperspace()) {
			tagListLegs.appendTag(writePreviewLeg(EnumShipNavigationLegType.HYPERSPACE_EXIT));
		}
		if (!celestialObjectTarget.isSpace() && !celestialObjectTarget.isHyperspace()) {
			tagListLegs.appendTag(writePreviewLeg(EnumShipNavigationLegType.ORBITAL_APPROACH));
			if (canLandOnPlanet(shipCore)) {
				tagListLegs.appendTag(writePreviewLeg(EnumShipNavigationLegType.LANDING));
			}
		}
	}

	@Nonnull
	private static NBTTagCompound writePreviewLeg(@Nonnull final EnumShipNavigationLegType type) {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setString("type", type.getName());
		tagCompound.setString("command", "preview");
		tagCompound.setInteger("moveFront", 0);
		tagCompound.setInteger("moveUp", 0);
		tagCompound.setInteger("moveRight", 0);
		tagCompound.setBoolean("requiresConfirmation", type.requiresConfirmation());
		tagCompound.setBoolean("preview", true);
		tagCompound.setInteger("stepDistance", 0);
		tagCompound.setInteger("remainingDistance", 0);
		return tagCompound;
	}

	@Nonnull
	private static NBTTagCompound writeLeg(@Nonnull final TileEntityShipCore shipCore,
	                                       @Nonnull final Leg leg, @Nonnull final ShipMovementPreview preview) {
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setString("type", leg.type.getName());
		tagCompound.setString("command", leg.command.getName());
		tagCompound.setInteger("moveFront", leg.moveFront);
		tagCompound.setInteger("moveUp", leg.moveUp);
		tagCompound.setInteger("moveRight", leg.moveRight);
		tagCompound.setBoolean("requiresConfirmation", leg.requiresConfirmation);
		tagCompound.setBoolean("preview", false);
		tagCompound.setString("warningKey", leg.warningKey);
		tagCompound.setBoolean("detour", "warpdrive.navigation.route.waypoint_detour".equals(leg.warningKey));
		tagCompound.setInteger("stepDistance", leg.stepDistance);
		tagCompound.setInteger("remainingDistance", leg.remainingDistance);
		tagCompound.setInteger("effectiveDistance", preview.effectiveDistance);
		tagCompound.setInteger("maximumDistance", preview.maximumDistance);
		tagCompound.setInteger("energyRequired", preview.energyRequired);
		// world-space vector of this leg, for the client surface chart
		final EnumFacing facing = getFacing(shipCore);
		tagCompound.setInteger("worldMoveX", facing.getXOffset() * leg.moveFront - facing.getZOffset() * leg.moveRight);
		tagCompound.setInteger("worldMoveY", leg.moveUp);
		tagCompound.setInteger("worldMoveZ", facing.getZOffset() * leg.moveFront + facing.getXOffset() * leg.moveRight);
		return tagCompound;
	}

	private static void writeWaypointRouteTelemetry(@Nonnull final TileEntityShipCore shipCore,
	                                                @Nonnull final NBTTagCompound tagRoute,
	                                                @Nonnull final Leg nextLeg) {
		final int totalDistance = Math.max(shipCore.getNavigationWaypointInitialDistance(), nextLeg.remainingDistance);
		tagRoute.setInteger("totalDistance", totalDistance);
		tagRoute.setInteger("remainingDistance", nextLeg.remainingDistance);
		tagRoute.setInteger("progressPermille", 1000 * (totalDistance - nextLeg.remainingDistance) / Math.max(1, totalDistance));
		// aggregate estimate over the remaining route, same cost model as the destinations tab
		final DestinationEstimate estimate = new DestinationEstimate();
		addCruiseEstimate(shipCore, estimate, EnumShipNavigationLegType.ATMOSPHERIC_CRUISE,
		                  shipCore.getNavigationWaypointX() - shipCore.getPos().getX(),
		                  shipCore.getNavigationWaypointY() - shipCore.getPos().getY(),
		                  shipCore.getNavigationWaypointZ() - shipCore.getPos().getZ());
		if (estimate.legs > 0) {
			final NBTTagCompound tagEstimate = new NBTTagCompound();
			tagEstimate.setInteger("legs", estimate.legs);
			tagEstimate.setInteger("jumps", estimate.jumps);
			tagEstimate.setLong("energy", estimate.energy);
			tagEstimate.setInteger("eta", estimate.eta);
			tagEstimate.setInteger("distance", estimate.distance);
			tagEstimate.setInteger("maxRange", estimate.maxRange);
			tagRoute.setTag("estimate", tagEstimate);
		}
	}

	// cached landing probe for the ship footprint at the given position; grid always included
	@Nonnull
	static LandingProbe getCachedProbeLanding(@Nonnull final TileEntityShipCore shipCore,
	                                          final int targetCoreX, final int targetCoreZ,
	                                          @Nonnull final CelestialObject celestialObject,
	                                          final long maxAgeTicks, final long blockBudget) {
		final long key = ((long) targetCoreX << 26) ^ (targetCoreZ & 0x3FFFFFFL);
		final LandingProbe cached = shipCore.getCachedLandingProbe(key, maxAgeTicks);
		if (cached != null) {
			return cached;
		}
		final LandingProbe probe = probeLanding(shipCore, targetCoreX, targetCoreZ, celestialObject,
		                                        blockBudget, true);
		shipCore.putCachedLandingProbe(key, probe);
		return probe;
	}

	// read-only dry-run survey of candidate waypoints; commits nothing
	@Nonnull
	public static NBTTagList probeWaypoints(@Nonnull final TileEntityShipCore shipCore,
	                                        @Nonnull final NBTTagCompound payload) {
		final NBTTagList results = new NBTTagList();
		final NBTTagList tagListWaypoints = payload.getTagList("waypoints", Constants.NBT.TAG_COMPOUND);
		final boolean assist = payload.getBoolean("assist");
		final int count = Math.min(PROBE_MAX_WAYPOINTS, tagListWaypoints.tagCount());
		if (count == 0) {
			return results;
		}
		final long budgetPerWaypoint = PROBE_MAX_BLOCK_CHECKS_TOTAL / count;
		for (int index = 0; index < count; index++) {
			final NBTTagCompound tagWaypoint = tagListWaypoints.getCompoundTagAt(index);
			final int dimension = tagWaypoint.getInteger("dimension");
			final int waypointX = tagWaypoint.getInteger("x");
			final int waypointZ = tagWaypoint.getInteger("z");
			final NBTTagCompound tagResult = new NBTTagCompound();
			tagResult.setInteger("dimension", dimension);
			tagResult.setInteger("x", waypointX);
			tagResult.setInteger("z", waypointZ);
			final String reasonKeyRegion = validateWaypointRegion(shipCore, dimension, waypointX, waypointZ);
			if (!reasonKeyRegion.isEmpty()) {
				tagResult.setBoolean("valid", false);
				tagResult.setString("reasonKey", reasonKeyRegion);
				results.appendTag(tagResult);
				continue;
			}
			final CelestialObject celestialObject = CelestialObjectManager.get(shipCore.getWorld(), waypointX, waypointZ);
			assert celestialObject != null;
			final LandingProbe probe = getCachedProbeLanding(shipCore, waypointX, waypointZ, celestialObject,
			                                                 LANDING_PROBE_TTL_TICKS, budgetPerWaypoint);
			tagResult.setBoolean("valid", probe.landingCoreY != null);
			tagResult.setString("reasonKey", probe.reasonKey);
			if (probe.landingCoreY != null) {
				tagResult.setInteger("landingY", probe.landingCoreY);
			} else if (assist && !probe.budgetExhausted) {
				final int[] alternate = findAlternateLanding(shipCore, waypointX, waypointZ, celestialObject);
				if (alternate != null) {
					final NBTTagCompound tagAlternate = new NBTTagCompound();
					tagAlternate.setInteger("x", alternate[0]);
					tagAlternate.setInteger("z", alternate[1]);
					tagAlternate.setInteger("landingY", alternate[2]);
					tagResult.setTag("alternate", tagAlternate);
				}
			}
			results.appendTag(tagResult);
		}
		return results;
	}

	// expanding ring scan for the nearest valid landing site around a rejected waypoint
	@Nullable
	private static int[] findAlternateLanding(@Nonnull final TileEntityShipCore shipCore,
	                                          final int targetCoreX, final int targetCoreZ,
	                                          @Nonnull final CelestialObject celestialObject) {
		final int dimension = shipCore.getWorld().provider.getDimension();
		final int strideX = shipCore.maxX - shipCore.minX + 1;
		final int strideZ = shipCore.maxZ - shipCore.minZ + 1;
		int candidates = 0;
		for (int ring = 1; ring <= ASSIST_MAX_RINGS; ring++) {
			for (int offsetX = -ring; offsetX <= ring; offsetX++) {
				for (int offsetZ = -ring; offsetZ <= ring; offsetZ++) {
					if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != ring) {
						continue;
					}
					if (candidates >= ASSIST_MAX_CANDIDATES) {
						return null;
					}
					candidates++;
					final int candidateX = targetCoreX + offsetX * strideX;
					final int candidateZ = targetCoreZ + offsetZ * strideZ;
					if (!validateWaypointRegion(shipCore, dimension, candidateX, candidateZ).isEmpty()) {
						continue;
					}
					final LandingProbe probe = probeLanding(shipCore, candidateX, candidateZ, celestialObject,
					                                        ASSIST_MAX_BLOCK_CHECKS_PER_CANDIDATE, false);
					if (probe.landingCoreY != null) {
						return new int[] { candidateX, candidateZ, probe.landingCoreY };
					}
				}
			}
		}
		return null;
	}

	@Nullable
	private static NBTTagCompound writeWaypointLanding(@Nonnull final TileEntityShipCore shipCore) {
		final CelestialObject celestialObject = CelestialObjectManager.get(shipCore.getWorld(),
		                                                                   shipCore.getNavigationWaypointX(), shipCore.getNavigationWaypointZ());
		if ( celestialObject == null
		  || celestialObject.isSpace()
		  || celestialObject.isHyperspace()
		  || shipCore.getWorld().provider.getDimension() != shipCore.getNavigationWaypointDimension() ) {
			return null;
		}
		final LandingProbe probe = getCachedProbeLanding(shipCore,
		                                                 shipCore.getNavigationWaypointX(), shipCore.getNavigationWaypointZ(),
		                                                 celestialObject, LANDING_PROBE_TTL_TICKS, WAYPOINT_MAX_BLOCK_CHECKS);
		final NBTTagCompound tagCompound = new NBTTagCompound();
		tagCompound.setBoolean("valid", probe.landingCoreY != null);
		tagCompound.setString("reasonKey", probe.reasonKey);
		tagCompound.setInteger("landingY", probe.landingCoreY == null ? shipCore.getNavigationWaypointY() : probe.landingCoreY);
		tagCompound.setInteger("highestSupportY", probe.highestSupportY);
		if (probe.columnHeights != null) {
			tagCompound.setInteger("originX", probe.originX);
			tagCompound.setInteger("originZ", probe.originZ);
			tagCompound.setInteger("stride", probe.gridStride);
			tagCompound.setInteger("gridWidth", probe.gridWidth);
			tagCompound.setInteger("gridDepth", probe.gridDepth);
			tagCompound.setIntArray("heights", probe.columnHeights);
		}
		return tagCompound;
	}

	// ----- destinations overview (jumps / energy / eta to every reachable body) -----

	@Nonnull
	@SuppressWarnings("PMD.NPathComplexity")
	private static NBTTagList buildDestinations(@Nonnull final TileEntityShipCore shipCore,
	                                            @Nullable final CelestialObject celestialObjectCurrent) {
		final NBTTagList tagList = new NBTTagList();
		for (final CelestialObject celestialObject : CelestialObjectManager.getRegistrySnapshot(false)) {
			if (celestialObject.isVirtual()) {
				continue;
			}
			final NBTTagCompound tagCompound = new NBTTagCompound();
			tagCompound.setString("id", celestialObject.id);
			tagCompound.setString("parentId", celestialObject.parentId == null ? "" : celestialObject.parentId);
			tagCompound.setString("name", celestialObject.getDisplayName());
			tagCompound.setString("type", celestialObject.isHyperspace() ? "hyperspace" : celestialObject.isSpace() ? "space" : "body");
			tagCompound.setInteger("mapX", celestialObject.parentId == null ? celestialObject.dimensionCenterX : celestialObject.getParentCenterX());
			tagCompound.setInteger("mapZ", celestialObject.parentId == null ? celestialObject.dimensionCenterZ : celestialObject.getParentCenterZ());
			final boolean isCurrent = celestialObjectCurrent != null
			                       && isAtNavigationDestination(shipCore, celestialObjectCurrent, celestialObject);
			tagCompound.setBoolean("current", isCurrent);

			final DestinationEstimate estimate = isCurrent || celestialObjectCurrent == null
			                                     ? null
			                                     : estimateDestination(shipCore, celestialObjectCurrent, celestialObject);
			if (estimate == null) {
				tagCompound.setBoolean("reachable", isCurrent);
				tagCompound.setInteger("legs", 0);
				tagCompound.setInteger("jumps", 0);
				tagCompound.setLong("energy", 0L);
				tagCompound.setInteger("eta", 0);
				tagCompound.setInteger("maxRange", 0);
				tagCompound.setInteger("distance", 0);
			} else {
				tagCompound.setBoolean("reachable", true);
				tagCompound.setInteger("legs", estimate.legs);
				tagCompound.setInteger("jumps", estimate.jumps);
				tagCompound.setLong("energy", estimate.energy);
				tagCompound.setInteger("eta", estimate.eta);
				tagCompound.setInteger("maxRange", estimate.maxRange);
				tagCompound.setInteger("distance", estimate.distance);
			}
			tagList.appendTag(tagCompound);
		}
		return tagList;
	}

	@Nullable
	@SuppressWarnings("PMD.NPathComplexity")
	private static DestinationEstimate estimateDestination(@Nonnull final TileEntityShipCore shipCore,
	                                                       @Nonnull final CelestialObject celestialObjectCurrent,
	                                                       @Nonnull final CelestialObject celestialObjectTarget) {
		if ( celestialObjectTarget.isVirtual()
		  || isAtNavigationDestination(shipCore, celestialObjectCurrent, celestialObjectTarget) ) {
			return null;
		}
		final DestinationEstimate estimate = new DestinationEstimate();
		CelestialObject currentSpace;
		int currentX;
		int currentZ;
		if (!celestialObjectCurrent.isSpace() && !celestialObjectCurrent.isHyperspace()) {
			if (celestialObjectCurrent.parent == null) {
				return null;
			}
			addSingleLegEstimate(shipCore, estimate, EnumShipNavigationLegType.TAKEOFF,
			                     Math.max(1, 256 - shipCore.maxY + TAKEOFF_MARGIN_BLOCKS));
			currentSpace = getSpaceFor(celestialObjectCurrent);
			currentX = celestialObjectCurrent.getParentCenterX();
			currentZ = celestialObjectCurrent.getParentCenterZ();
		} else {
			currentSpace = celestialObjectCurrent.isHyperspace() ? null : celestialObjectCurrent;
			currentX = shipCore.getPos().getX();
			currentZ = shipCore.getPos().getZ();
		}

		if (celestialObjectTarget.isHyperspace()) {
			addSingleLegEstimate(shipCore, estimate, EnumShipNavigationLegType.HYPERSPACE_ENTER, 1);
			return estimate;
		}
		final CelestialObject targetSpace = getSpaceFor(celestialObjectTarget);
		if (targetSpace == null) {
			return null;
		}
		if (currentSpace == null) {
			final int targetX = targetSpace.getParentCenterX();
			final int targetZ = targetSpace.getParentCenterZ();
			addCruiseEstimate(shipCore, estimate, EnumShipNavigationLegType.HYPERSPACE_CRUISE,
			                  targetX - currentX, ORBIT_Y - shipCore.getPos().getY(), targetZ - currentZ);
			addSingleLegEstimate(shipCore, estimate, EnumShipNavigationLegType.HYPERSPACE_EXIT, 1);
			currentSpace = targetSpace;
			currentX = targetSpace.dimensionCenterX;
			currentZ = targetSpace.dimensionCenterZ;
		} else if (currentSpace != targetSpace) {
			addSingleLegEstimate(shipCore, estimate, EnumShipNavigationLegType.HYPERSPACE_ENTER, 1);
			addCruiseEstimate(shipCore, estimate, EnumShipNavigationLegType.HYPERSPACE_CRUISE,
			                  targetSpace.getParentCenterX() - currentSpace.getParentCenterX(),
			                  ORBIT_Y - shipCore.getPos().getY(),
			                  targetSpace.getParentCenterZ() - currentSpace.getParentCenterZ());
			addSingleLegEstimate(shipCore, estimate, EnumShipNavigationLegType.HYPERSPACE_EXIT, 1);
			currentSpace = targetSpace;
			currentX = targetSpace.dimensionCenterX;
			currentZ = targetSpace.dimensionCenterZ;
		}

		if (celestialObjectTarget.isSpace()) {
			return estimate.legs == 0 ? null : estimate;
		}
		if (celestialObjectTarget.parent != currentSpace) {
			return null;
		}
		if (!celestialObjectTarget.isInOrbit(currentSpace.dimensionId, currentX, currentZ)) {
			addCruiseEstimate(shipCore, estimate, EnumShipNavigationLegType.ORBITAL_APPROACH,
			                  celestialObjectTarget.getParentCenterX() - currentX,
			                  ORBIT_Y - shipCore.getPos().getY(),
			                  celestialObjectTarget.getParentCenterZ() - currentZ);
		}
		if (canLandOnPlanet(shipCore)) {
			addSingleLegEstimate(shipCore, estimate, EnumShipNavigationLegType.LANDING,
			                     Math.max(1, Math.abs(shipCore.minY + LANDING_MARGIN_BLOCKS)));
		}
		return estimate.legs == 0 ? null : estimate;
	}

	private static void addSingleLegEstimate(@Nonnull final TileEntityShipCore shipCore,
	                                         @Nonnull final DestinationEstimate estimate,
	                                         @Nonnull final EnumShipNavigationLegType legType,
	                                         final int distance) {
		final EnumShipMovementType movementType = movementTypeForEstimate(legType);
		final ShipMovementCosts costs = new ShipMovementCosts(shipCore.getWorld(), shipCore.getPos(), shipCore,
		                                                      movementType, shipCore.shipMass, Math.max(1, distance));
		estimate.addLeg(Math.max(1, distance), costs);
	}

	private static void addCruiseEstimate(@Nonnull final TileEntityShipCore shipCore,
	                                      @Nonnull final DestinationEstimate estimate,
	                                      @Nonnull final EnumShipNavigationLegType legType,
	                                      final int moveX, final int moveY, final int moveZ) {
		final VectorI remaining = toLocalMovement(shipCore, moveX, moveY, moveZ);
		if (remaining.getMagnitudeSquared() <= 0L) {
			return;
		}
		final EnumShipMovementType movementType = movementTypeForEstimate(legType);
		for (int guard = 0; guard < 100000 && remaining.getMagnitudeSquared() > 0L; guard++) {
			final ShipMovementPreview preview = shipCore.previewMovement(legType.getCommand(),
			                                                             remaining.x, remaining.y, remaining.z,
			                                                             (byte) 0);
			final VectorI step = preview.effectiveMovement;
			if (step.getMagnitudeSquared() <= 0L) {
				break;
			}
			final int stepDistance = Math.max(1, (int) Math.ceil(Math.sqrt(step.getMagnitudeSquared())));
			final ShipMovementCosts stepCosts = new ShipMovementCosts(shipCore.getWorld(), shipCore.getPos(), shipCore,
			                                                          movementType, shipCore.shipMass, stepDistance);
			estimate.addLeg(stepDistance, stepCosts);
			remaining.x -= step.x;
			remaining.y -= step.y;
			remaining.z -= step.z;
		}
	}

	private static final class DestinationEstimate {
		private int legs;
		private int jumps;
		private long energy;
		private int eta;
		private int maxRange;
		private int distance;

		private void addLeg(final int distance, @Nonnull final ShipMovementCosts costs) {
			addLegs(1, distance, costs);
		}

		private void addLegs(final int count, final long distance, @Nonnull final ShipMovementCosts costs) {
			legs += count;
			jumps += count;
			energy += (long) count * costs.energyRequired;
			eta += count * (costs.warmup_seconds + costs.cooldown_seconds);
			maxRange = Math.max(maxRange, costs.maximumDistance_blocks);
			this.distance = (int) Math.min(Integer.MAX_VALUE, this.distance + distance);
		}
	}

	@Nonnull
	private static EnumShipMovementType movementTypeForEstimate(@Nonnull final EnumShipNavigationLegType legType) {
		switch (legType) {
		case TAKEOFF:           return EnumShipMovementType.PLANET_TAKEOFF;
		case LANDING:           return EnumShipMovementType.PLANET_LANDING;
		case SPACE_CRUISE:
		case ORBITAL_APPROACH:  return EnumShipMovementType.SPACE_MOVING;
		case HYPERSPACE_ENTER:  return EnumShipMovementType.HYPERSPACE_ENTERING;
		case HYPERSPACE_EXIT:   return EnumShipMovementType.HYPERSPACE_EXITING;
		case HYPERSPACE_CRUISE: return EnumShipMovementType.HYPERSPACE_MOVING;
		default:                return EnumShipMovementType.SPACE_MOVING;
		}
	}

	// ----- destination planning / engagement -----

	public static boolean setDestination(@Nonnull final EntityPlayerMP entityPlayerMP,
	                                     @Nonnull final TileEntityShipCore shipCore,
	                                     @Nonnull final String celestialObjectId) {
		if (!shipCore.isCrewMember(entityPlayerMP)) {
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), "warpdrive.navigation.denied"));
			return false;
		}
		final CelestialObject celestialObject = CelestialObjectManager.get(false, celestialObjectId);
		if (celestialObject == null) {
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), "warpdrive.navigation.unknown_destination", celestialObjectId));
			return false;
		}
		shipCore.setNavigationTargetId(celestialObjectId);
		return true;
	}

	// returns an empty string on success, or the localization key of the specific rejection reason
	@Nonnull
	public static String setWaypoint(@Nonnull final EntityPlayerMP entityPlayerMP,
	                                 @Nonnull final TileEntityShipCore shipCore,
	                                 @Nonnull final NBTTagCompound payload) {
		if (!shipCore.isCrewMember(entityPlayerMP)) {
			return chatWaypointRejection(entityPlayerMP, "warpdrive.navigation.denied");
		}
		final int dimension = payload.getInteger("dimension");
		final int waypointX = payload.getInteger("x");
		final int waypointZ = payload.getInteger("z");
		final String reasonKeyRegion = validateWaypointRegion(shipCore, dimension, waypointX, waypointZ);
		if (!reasonKeyRegion.isEmpty()) {
			return chatWaypointRejection(entityPlayerMP, reasonKeyRegion);
		}
		final CelestialObject celestialObjectCurrent = CelestialObjectManager.get(shipCore.getWorld(), shipCore.getPos().getX(), shipCore.getPos().getZ());
		assert celestialObjectCurrent != null;
		final LandingProbe landingProbe = probeLanding(shipCore, waypointX, waypointZ, celestialObjectCurrent,
		                                               WAYPOINT_MAX_BLOCK_CHECKS, false);
		if (landingProbe.landingCoreY == null) {
			return chatWaypointRejection(entityPlayerMP, landingProbe.reasonKey);
		}
		shipCore.cancelAutopilot();
		shipCore.setNavigationWaypoint(sanitizeWaypointLabel(payload.getString("name")),
		                               sanitizeWaypointLabel(payload.getString("source")),
		                               dimension, waypointX, landingProbe.landingCoreY, waypointZ);
		return "";
	}

	@Nonnull
	private static String chatWaypointRejection(@Nonnull final EntityPlayerMP entityPlayerMP, @Nonnull final String reasonKey) {
		Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), reasonKey));
		return reasonKey;
	}

	// shared cheap validation: dimension, celestial region and chunk generation. Empty string when valid.
	@Nonnull
	static String validateWaypointRegion(@Nonnull final TileEntityShipCore shipCore,
	                                     final int dimension, final int waypointX, final int waypointZ) {
		if (dimension != shipCore.getWorld().provider.getDimension()) {
			return "warpdrive.navigation.waypoint.wrong_dimension";
		}
		final CelestialObject celestialObjectCurrent = CelestialObjectManager.get(shipCore.getWorld(), shipCore.getPos().getX(), shipCore.getPos().getZ());
		final CelestialObject celestialObjectTarget = CelestialObjectManager.get(shipCore.getWorld(), waypointX, waypointZ);
		if ( celestialObjectCurrent == null
		  || celestialObjectCurrent.isSpace()
		  || celestialObjectCurrent.isHyperspace()
		  || celestialObjectTarget == null
		  || !celestialObjectCurrent.id.equals(celestialObjectTarget.id) ) {
			return "warpdrive.navigation.waypoint.outside_region";
		}
		return checkWaypointChunks(shipCore, waypointX, waypointZ);
	}

	private static String sanitizeWaypointLabel(@Nonnull final String value) {
		final String sanitized = value.replace('\n', ' ').replace('\r', ' ');
		return sanitized.substring(0, Math.min(WAYPOINT_LABEL_MAX_LENGTH, sanitized.length()));
	}

	// empty string when the whole footprint is generated, else the specific rejection reason
	@Nonnull
	private static String checkWaypointChunks(@Nonnull final TileEntityShipCore shipCore,
	                                          final int targetCoreX, final int targetCoreZ) {
		final int minChunkX = targetCoreX + shipCore.minX - shipCore.getPos().getX() >> 4;
		final int maxChunkX = targetCoreX + shipCore.maxX - shipCore.getPos().getX() >> 4;
		final int minChunkZ = targetCoreZ + shipCore.minZ - shipCore.getPos().getZ() >> 4;
		final int maxChunkZ = targetCoreZ + shipCore.maxZ - shipCore.getPos().getZ() >> 4;
		if ((long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1) > WAYPOINT_MAX_CHUNKS) {
			return "warpdrive.navigation.waypoint.too_large";
		}
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				if (!shipCore.getWorld().isChunkGeneratedAt(chunkX, chunkZ)) {
					return "warpdrive.navigation.waypoint.unexplored";
				}
			}
		}
		return "";
	}

	public static boolean isWaypointLandingValid(@Nonnull final TileEntityShipCore shipCore,
	                                             final int targetCoreX, final int targetCoreY, final int targetCoreZ) {
		final CelestialObject celestialObject = CelestialObjectManager.get(shipCore.getWorld(), targetCoreX, targetCoreZ);
		if ( celestialObject == null
		  || celestialObject.isSpace()
		  || celestialObject.isHyperspace() ) {
			return false;
		}
		final Integer landingCoreY = findWaypointLandingY(shipCore,
		                                                    targetCoreX, targetCoreZ,
		                                                    celestialObject);
		return landingCoreY != null && landingCoreY == targetCoreY;
	}

	@Nullable
	private static Integer findWaypointLandingY(@Nonnull final TileEntityShipCore shipCore,
	                                            final int targetCoreX, final int targetCoreZ,
	                                            @Nonnull final CelestialObject celestialObject) {
		return probeLanding(shipCore, targetCoreX, targetCoreZ, celestialObject, WAYPOINT_MAX_BLOCK_CHECKS, false).landingCoreY;
	}

	// resolves the landing altitude for the ship footprint at the given position,
	// reporting the specific rejection reason and optionally a downsampled support height grid
	@Nonnull
	static LandingProbe probeLanding(@Nonnull final TileEntityShipCore shipCore,
	                                 final int targetCoreX, final int targetCoreZ,
	                                 @Nonnull final CelestialObject celestialObject,
	                                 final long blockBudget, final boolean wantHeightGrid) {
		final int offsetMinX = shipCore.minX - shipCore.getPos().getX();
		final int offsetMinY = shipCore.minY - shipCore.getPos().getY();
		final int offsetMinZ = shipCore.minZ - shipCore.getPos().getZ();
		final int offsetMaxX = shipCore.maxX - shipCore.getPos().getX();
		final int offsetMaxY = shipCore.maxY - shipCore.getPos().getY();
		final int offsetMaxZ = shipCore.maxZ - shipCore.getPos().getZ();
		final int minimumCoreY = 9 - offsetMinY;
		final int maximumCoreY = 255 - offsetMaxY;
		final int originX = targetCoreX + offsetMinX;
		final int originZ = targetCoreZ + offsetMinZ;
		final int footprintWidth = offsetMaxX - offsetMinX + 1;
		final int footprintDepth = offsetMaxZ - offsetMinZ + 1;
		final int gridStride = Math.max(1, (Math.max(footprintWidth, footprintDepth) + LANDING_GRID_MAX_SIZE - 1) / LANDING_GRID_MAX_SIZE);
		final int gridWidth = (footprintWidth + gridStride - 1) / gridStride;
		final int gridDepth = (footprintDepth + gridStride - 1) / gridStride;
		final int[] columnHeights;
		if (wantHeightGrid) {
			columnHeights = new int[gridWidth * gridDepth];
			Arrays.fill(columnHeights, -1);
		} else {
			columnHeights = null;
		}
		if (minimumCoreY > maximumCoreY) {
			return LandingProbe.invalid("warpdrive.navigation.waypoint.too_high", Integer.MIN_VALUE, false,
			                            columnHeights, gridWidth, gridDepth, gridStride, originX, originZ);
		}
		final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		int highestSupportY = Integer.MIN_VALUE;
		long blockChecks = 0L;
		for (int x = originX; x <= targetCoreX + offsetMaxX; x++) {
			for (int z = originZ; z <= targetCoreZ + offsetMaxZ; z++) {
				final Chunk chunk = shipCore.getWorld().getChunk(x >> 4, z >> 4);
				for (int y = Math.min(255, chunk.getTopFilledSegment() + 15); y >= 0; y--) {
					blockChecks++;
					if (blockChecks > blockBudget) {
						return LandingProbe.invalid("warpdrive.navigation.waypoint.budget_exhausted", highestSupportY, true,
						                            columnHeights, gridWidth, gridDepth, gridStride, originX, originZ);
					}
					if (isSourceShipPosition(shipCore, x, y, z)) {
						continue;
					}
					mutableBlockPos.setPos(x, y, z);
					if (!shipCore.getWorld().isAirBlock(mutableBlockPos)) {
						highestSupportY = Math.max(highestSupportY, y);
						if (columnHeights != null) {
							final int indexGrid = ((z - originZ) / gridStride) * gridWidth + (x - originX) / gridStride;
							columnHeights[indexGrid] = Math.max(columnHeights[indexGrid], y);
						}
						break;
					}
				}
			}
		}
		if (highestSupportY == Integer.MIN_VALUE) {
			return LandingProbe.invalid("warpdrive.navigation.waypoint.no_support", highestSupportY, false,
			                            columnHeights, gridWidth, gridDepth, gridStride, originX, originZ);
		}
		final int candidateCoreY = Math.max(minimumCoreY, highestSupportY + 1 - offsetMinY);
		final int targetMinY = candidateCoreY + offsetMinY;
		final int targetMaxY = candidateCoreY + offsetMaxY;
		if (candidateCoreY > maximumCoreY) {
			return LandingProbe.invalid("warpdrive.navigation.waypoint.too_high", highestSupportY, false,
			                            columnHeights, gridWidth, gridDepth, gridStride, originX, originZ);
		}
		if (targetMinY != highestSupportY + 1) {
			return LandingProbe.invalid("warpdrive.navigation.waypoint.too_low", highestSupportY, false,
			                            columnHeights, gridWidth, gridDepth, gridStride, originX, originZ);
		}
		final AxisAlignedBB targetBounds = new AxisAlignedBB(
				targetCoreX + offsetMinX, targetMinY, targetCoreZ + offsetMinZ,
				targetCoreX + offsetMaxX + 1, targetMaxY + 1, targetCoreZ + offsetMaxZ + 1);
		if (!celestialObject.isInsideBorder(targetBounds)) {
			return LandingProbe.invalid("warpdrive.navigation.waypoint.outside_border", highestSupportY, false,
			                            columnHeights, gridWidth, gridDepth, gridStride, originX, originZ);
		}
		return new LandingProbe(candidateCoreY, "", highestSupportY, false,
		                        columnHeights, gridWidth, gridDepth, gridStride, originX, originZ);
	}

	static final class LandingProbe {

		@Nullable final Integer landingCoreY;
		@Nonnull final String reasonKey;
		final int highestSupportY;
		final boolean budgetExhausted;
		@Nullable final int[] columnHeights;
		final int gridWidth;
		final int gridDepth;
		final int gridStride;
		final int originX;
		final int originZ;

		LandingProbe(@Nullable final Integer landingCoreY, @Nonnull final String reasonKey,
		             final int highestSupportY, final boolean budgetExhausted,
		             @Nullable final int[] columnHeights, final int gridWidth, final int gridDepth,
		             final int gridStride, final int originX, final int originZ) {
			this.landingCoreY = landingCoreY;
			this.reasonKey = reasonKey;
			this.highestSupportY = highestSupportY;
			this.budgetExhausted = budgetExhausted;
			this.columnHeights = columnHeights;
			this.gridWidth = gridWidth;
			this.gridDepth = gridDepth;
			this.gridStride = gridStride;
			this.originX = originX;
			this.originZ = originZ;
		}

		@Nonnull
		static LandingProbe invalid(@Nonnull final String reasonKey, final int highestSupportY, final boolean budgetExhausted,
		                            @Nullable final int[] columnHeights, final int gridWidth, final int gridDepth,
		                            final int gridStride, final int originX, final int originZ) {
			return new LandingProbe(null, reasonKey, highestSupportY, budgetExhausted,
			                        columnHeights, gridWidth, gridDepth, gridStride, originX, originZ);
		}
	}

	private static boolean isSourceShipPosition(@Nonnull final TileEntityShipCore shipCore,
	                                            final int x, final int y, final int z) {
		return x >= shipCore.minX && x <= shipCore.maxX
		    && y >= shipCore.minY && y <= shipCore.maxY
		    && z >= shipCore.minZ && z <= shipCore.maxZ;
	}

	public static boolean cancel(@Nonnull final EntityPlayerMP entityPlayerMP,
	                             @Nonnull final TileEntityShipCore shipCore) {
		if (!shipCore.isCrewMember(entityPlayerMP)) {
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), "warpdrive.navigation.denied"));
			return false;
		}
		shipCore.cancelAutopilot();
		shipCore.clearNavigationTarget();
		return true;
	}

	@SuppressWarnings("PMD.NPathComplexity")
	public static boolean engage(@Nonnull final EntityPlayerMP entityPlayerMP,
	                             @Nonnull final TileEntityShipCore shipCore) {
		if (!shipCore.isCrewMember(entityPlayerMP)) {
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), "warpdrive.navigation.denied"));
			return false;
		}
		if (shipCore.isOffline() || shipCore.isUnderMaintenance() || shipCore.isBusy()) {
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), "warpdrive.navigation.busy"));
			return false;
		}

		final CelestialObject celestialObjectCurrent = CelestialObjectManager.get(shipCore.getWorld(), shipCore.getPos().getX(), shipCore.getPos().getZ());
		final CelestialObject celestialObjectTarget = getTarget(shipCore);
		if (celestialObjectCurrent == null || celestialObjectTarget == null && !shipCore.isNavigationTargetWaypoint()) {
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), "warpdrive.navigation.no_route"));
			return false;
		}
		if (isAtNavigationDestination(shipCore, celestialObjectCurrent, celestialObjectTarget)) {
			shipCore.cancelAutopilot();
			shipCore.clearNavigationTarget();
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleCorrect(), "warpdrive.navigation.arrived"));
			return true;
		}

		final Leg leg = shipCore.isNavigationTargetWaypoint()
		              ? createAtmosphericLeg(shipCore)
		              : computeNextLeg(shipCore, celestialObjectCurrent, celestialObjectTarget);
		if (leg == null) {
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), "warpdrive.navigation.no_route"));
			return false;
		}
		final ShipMovementPreview preview = leg.preview == null
		                                  ? shipCore.previewMovement(leg.command, leg.moveFront, leg.moveUp, leg.moveRight, (byte) 0)
		                                  : leg.preview;
		if (!preview.canEngage) {
			if ("warpdrive.navigation.blocker.stale_scan".equals(preview.blockerKey)) {
				shipCore.requestShipScan();
			}
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(),
			                                                         preview.blockerKey.isEmpty() ? "warpdrive.navigation.no_route" : preview.blockerKey));
			return false;
		}

		// the click explicitly confirms this leg; the autopilot pump chains subsequent legs per the ship's mode
		commitLeg(shipCore, shipCore.getNavigationTargetId(), leg);
		shipCore.startAutopilotRun(false);
		Commons.messageToAllPlayersInArea(shipCore, new WarpDriveText(Commons.getStyleCorrect(), "warpdrive.navigation.engaging",
		                                                               new WarpDriveText(null, leg.type.getTitleKey()), getTargetDisplayName(shipCore, celestialObjectTarget)));
		return true;
	}

	/** Engage exactly one leg, then halt at the next decision point regardless of autopilot mode. */
	public static boolean step(@Nonnull final EntityPlayerMP entityPlayerMP,
	                           @Nonnull final TileEntityShipCore shipCore) {
		final boolean ok = engage(entityPlayerMP, shipCore);
		if (ok) {
			shipCore.startAutopilotRun(true);
		}
		return ok;
	}

	public static boolean setAutopilotMode(@Nonnull final EntityPlayerMP entityPlayerMP,
	                                       @Nonnull final TileEntityShipCore shipCore,
	                                       @Nullable final String mode) {
		if (!shipCore.isCrewMember(entityPlayerMP)) {
			Commons.addChatMessage(entityPlayerMP, new WarpDriveText(Commons.getStyleWarning(), "warpdrive.navigation.denied"));
			return false;
		}
		shipCore.setAutopilotMode(cr0s.warpdrive.data.EnumShipAutopilotMode.get(mode));
		return true;
	}

	public static boolean pause(@Nonnull final EntityPlayerMP entityPlayerMP, @Nonnull final TileEntityShipCore shipCore) {
		if (!shipCore.isCrewMember(entityPlayerMP)) {
			return false;
		}
		shipCore.pauseAutopilot();
		return true;
	}

	public static boolean resume(@Nonnull final EntityPlayerMP entityPlayerMP, @Nonnull final TileEntityShipCore shipCore) {
		if (!shipCore.isCrewMember(entityPlayerMP)) {
			return false;
		}
		shipCore.resumeAutopilot();
		return true;
	}

	/** Apply a leg's movement + command on the core, marking the final destination as the engaged target. */
	public static void commitLeg(@Nonnull final TileEntityShipCore shipCore, @Nonnull final String engagedTargetId, @Nonnull final Leg leg) {
		shipCore.setNavigationEngagedTargetId(engagedTargetId);
		shipCore.movement(new Object[] { leg.moveFront, leg.moveUp, leg.moveRight });
		shipCore.rotationSteps(new Object[] { 0 });
		shipCore.command(new Object[] { leg.command.getName(), true });
	}

	@Nullable
	public static ShipMovementPreview previewNavigation(@Nonnull final TileEntityShipCore shipCore) {
		final Leg leg = computeNextLeg(shipCore);
		if (leg == null) {
			return null;
		}
		return shipCore.previewMovement(leg.command, leg.moveFront, leg.moveUp, leg.moveRight, (byte) 0);
	}

	@Nullable
	private static CelestialObject getTarget(@Nonnull final TileEntityShipCore shipCore) {
		if (shipCore.isNavigationTargetWaypoint()) {
			return null;
		}
		final String navigationTargetId = shipCore.getNavigationTargetId();
		if (navigationTargetId == null || navigationTargetId.isEmpty()) {
			return null;
		}
		return CelestialObjectManager.get(false, navigationTargetId);
	}

	/** Resolve current/target from live ship state and compute the next leg (used by the autopilot pump). */
	@Nullable
	public static Leg computeNextLeg(@Nonnull final TileEntityShipCore shipCore) {
		if (shipCore.isNavigationTargetWaypoint()) {
			return isAtNavigationDestination(shipCore, null, null) ? null : createAtmosphericLeg(shipCore);
		}
		final CelestialObject celestialObjectCurrent = CelestialObjectManager.get(shipCore.getWorld(), shipCore.getPos().getX(), shipCore.getPos().getZ());
		final CelestialObject celestialObjectTarget = getTarget(shipCore);
		if ( celestialObjectCurrent == null
		  || celestialObjectTarget == null
		  || celestialObjectTarget.isVirtual()
		  || isAtNavigationDestination(shipCore, celestialObjectCurrent, celestialObjectTarget) ) {
			return null;
		}
		return computeNextLeg(shipCore, celestialObjectCurrent, celestialObjectTarget);
	}

	@Nullable
	@SuppressWarnings("PMD.NPathComplexity")
	private static Leg computeNextLeg(@Nonnull final TileEntityShipCore shipCore,
	                                  @Nonnull final CelestialObject celestialObjectCurrent,
	                                  @Nonnull final CelestialObject celestialObjectTarget) {
		if (celestialObjectTarget.isVirtual()) {
			return null;
		}

		if (!celestialObjectCurrent.isSpace() && !celestialObjectCurrent.isHyperspace()) {
			if (celestialObjectCurrent.parent == null) {
				return null;
			}
			return new Leg(EnumShipNavigationLegType.TAKEOFF,
			               0, Math.max(1, 256 - shipCore.maxY + TAKEOFF_MARGIN_BLOCKS), 0, "", 0, 0);
		}

		if (celestialObjectCurrent.isSpace()) {
			if (celestialObjectTarget.isHyperspace()) {
				return new Leg(EnumShipNavigationLegType.HYPERSPACE_ENTER, 0, 0, 0, "", 0, 0);
			}
			if (celestialObjectTarget.isSpace()) {
				if (celestialObjectCurrent.id.equals(celestialObjectTarget.id)) {
					return null;
				}
				return new Leg(EnumShipNavigationLegType.HYPERSPACE_ENTER, 0, 0, 0, "", 0, 0);
			}

			if (isParentOf(celestialObjectCurrent, celestialObjectTarget)) {
				if (celestialObjectTarget.isInOrbit(shipCore.getWorld().provider.getDimension(), shipCore.getPos().getX(), shipCore.getPos().getZ())) {
					if (!canLandOnPlanet(shipCore)) {
						return null;
					}
					return new Leg(EnumShipNavigationLegType.LANDING,
					               0, -Math.max(1, shipCore.minY + LANDING_MARGIN_BLOCKS), 0, "", 0, 0);
				}
				return createApproachLeg(shipCore, EnumShipNavigationLegType.ORBITAL_APPROACH,
				                         celestialObjectTarget.getParentCenterX(), celestialObjectTarget.getParentCenterZ());
			}

			final CelestialObject targetSpace = getSpaceFor(celestialObjectTarget);
			if (targetSpace != null && targetSpace != celestialObjectCurrent) {
				return new Leg(EnumShipNavigationLegType.HYPERSPACE_ENTER, 0, 0, 0, "", 0, 0);
			}
		}

		if (celestialObjectCurrent.isHyperspace()) {
			final CelestialObject targetSpace = getSpaceFor(celestialObjectTarget);
			if (targetSpace == null) {
				return null;
			}
			if (targetSpace.isInOrbit(shipCore.getWorld().provider.getDimension(), shipCore.getPos().getX(), shipCore.getPos().getZ())) {
				return new Leg(EnumShipNavigationLegType.HYPERSPACE_EXIT, 0, 0, 0, "", 0, 0);
			}
			return createApproachLeg(shipCore, EnumShipNavigationLegType.HYPERSPACE_CRUISE,
			                         targetSpace.getParentCenterX(), targetSpace.getParentCenterZ());
		}

		return null;
	}

	public static boolean isAtNavigationDestination(@Nonnull final TileEntityShipCore shipCore,
	                                                @Nullable final CelestialObject celestialObjectCurrent,
	                                                @Nullable final CelestialObject celestialObjectTarget) {
		if (shipCore.isNavigationTargetWaypoint()) {
			return shipCore.getWorld().provider.getDimension() == shipCore.getNavigationWaypointDimension()
			    && shipCore.getPos().getX() == shipCore.getNavigationWaypointX()
			    && shipCore.getPos().getY() == shipCore.getNavigationWaypointY()
			    && shipCore.getPos().getZ() == shipCore.getNavigationWaypointZ();
		}
		if ( celestialObjectCurrent == null
		  || celestialObjectTarget == null
		  || celestialObjectTarget.isVirtual() ) {
			return false;
		}
		if (celestialObjectCurrent.id.equals(celestialObjectTarget.id)) {
			return true;
		}
		if ( canLandOnPlanet(shipCore)
		  || !celestialObjectCurrent.isSpace()
		  || celestialObjectTarget.isSpace()
		  || celestialObjectTarget.isHyperspace()
		  || !isParentOf(celestialObjectCurrent, celestialObjectTarget) ) {
			return false;
		}
		return celestialObjectTarget.isInOrbit(shipCore.getWorld().provider.getDimension(),
		                                       shipCore.getPos().getX(), shipCore.getPos().getZ());
	}

	private static boolean isParentOf(@Nonnull final CelestialObject celestialObjectCurrent,
	                                  @Nonnull final CelestialObject celestialObjectTarget) {
		return celestialObjectTarget.parent != null
		    && ( celestialObjectTarget.parent == celestialObjectCurrent
		      || celestialObjectTarget.parent.id.equals(celestialObjectCurrent.id) );
	}

	private static boolean canLandOnPlanet(@Nonnull final TileEntityShipCore shipCore) {
		if (!shipCore.isShipScanReady()) {
			return true;
		}
		final int shipHeight = Math.max(1, shipCore.maxY - shipCore.minY + 1);
		return shipHeight + LANDING_MARGIN_BLOCKS <= 256
		    && shipCore.shipMass <= WarpDriveConfig.SHIP_MASS_MAX_ON_PLANET_SURFACE;
	}

	@Nullable
	private static CelestialObject getSpaceFor(@Nonnull final CelestialObject celestialObject) {
		if (celestialObject.isSpace()) {
			return celestialObject;
		}
		CelestialObject celestialObjectParent = celestialObject.parent;
		while (celestialObjectParent != null) {
			if (celestialObjectParent.isSpace()) {
				return celestialObjectParent;
			}
			celestialObjectParent = celestialObjectParent.parent;
		}
		return null;
	}

	@Nonnull
	private static Leg createApproachLeg(@Nonnull final TileEntityShipCore shipCore,
	                                     @Nonnull final EnumShipNavigationLegType type,
	                                     final int targetX, final int targetZ) {
		final int moveX = targetX - shipCore.getPos().getX();
		final int moveZ = targetZ - shipCore.getPos().getZ();
		final VectorI movement = toLocalMovement(shipCore, moveX, ORBIT_Y - shipCore.getPos().getY(), moveZ);
		final int remainingDistance = (int) Math.ceil(Math.sqrt(movement.x * (double) movement.x
		                                                      + movement.y * (double) movement.y
		                                                      + movement.z * (double) movement.z));
		final ShipMovementPreview routePreview = shipCore.previewMovement(type.getCommand(),
		                                                                   movement.x, movement.y, movement.z,
		                                                                   (byte) 0);
		final VectorI step = routePreview.effectiveMovement;
		final ShipMovementPreview stepPreview = shipCore.previewMovement(type.getCommand(),
		                                                                 step.x, step.y, step.z,
		                                                                 (byte) 0);
		final int stepDistance = (int) Math.ceil(Math.sqrt(step.x * (double) step.x + step.y * (double) step.y + step.z * (double) step.z));
		final String warningKey;
		if (movement.getMagnitudeSquared() <= 0L) {
			warningKey = "warpdrive.navigation.route.already_aligned";
		} else if (routePreview.wouldBeClamped || stepDistance < remainingDistance) {
			warningKey = "warpdrive.navigation.route.cruise_split";
		} else {
			warningKey = "";
		}
		return new Leg(type, step.x, step.y, step.z, warningKey, stepDistance, remainingDistance, stepPreview);
	}

	@Nullable
	private static Leg createAtmosphericLeg(@Nonnull final TileEntityShipCore shipCore) {
		if ( !shipCore.isNavigationTargetWaypoint()
		  || shipCore.getWorld().provider.getDimension() != shipCore.getNavigationWaypointDimension()
		  || CelestialObjectManager.isInSpace(shipCore.getWorld(), shipCore.getPos().getX(), shipCore.getPos().getZ())
		  || CelestialObjectManager.isInHyperspace(shipCore.getWorld(), shipCore.getPos().getX(), shipCore.getPos().getZ()) ) {
			return null;
		}
		final int moveX = shipCore.getNavigationWaypointX() - shipCore.getPos().getX();
		final int moveY = shipCore.getNavigationWaypointY() - shipCore.getPos().getY();
		final int moveZ = shipCore.getNavigationWaypointZ() - shipCore.getPos().getZ();
		final VectorI movement = toLocalMovement(shipCore, moveX, moveY, moveZ);
		if (movement.getMagnitudeSquared() <= 0L) {
			return null;
		}
		if (isOverlappingMovement(shipCore, moveX, moveY, moveZ)) {
			return createAtmosphericDetourLeg(shipCore, movement);
		}
		final int remainingDistance = (int) Math.ceil(Math.sqrt(movement.getMagnitudeSquared()));
		final ShipMovementPreview routePreview = shipCore.previewMovement(EnumShipCommand.MANUAL,
		                                                                    movement.x, movement.y, movement.z, (byte) 0);
		final VectorI step = routePreview.effectiveMovement;
		if (step.getMagnitudeSquared() <= 0L) {
			return new Leg(EnumShipNavigationLegType.ATMOSPHERIC_CRUISE,
			               0, 0, 0, "", 0, remainingDistance, routePreview);
		}
		final EnumFacing facing = getFacing(shipCore);
		final int stepX = facing.getXOffset() * step.x - facing.getZOffset() * step.z;
		final int stepZ = facing.getZOffset() * step.x + facing.getXOffset() * step.z;
		if (isOverlappingMovement(shipCore, stepX, step.y, stepZ)) {
			return createAtmosphericDetourLeg(shipCore, movement);
		}
		final int stepDistance = (int) Math.ceil(Math.sqrt(step.getMagnitudeSquared()));
		final String warningKey = routePreview.wouldBeClamped || stepDistance < remainingDistance
		                        ? "warpdrive.navigation.route.cruise_split" : "";
		final EnumShipNavigationLegType legType = !routePreview.wouldBeClamped
		                                          && step.x == movement.x && step.y == movement.y && step.z == movement.z
		                                        ? EnumShipNavigationLegType.ATMOSPHERIC_LANDING
		                                        : EnumShipNavigationLegType.ATMOSPHERIC_CRUISE;
		if (legType == EnumShipNavigationLegType.ATMOSPHERIC_LANDING) {
			// self-heal terrain drift on the final hop so JumpSequencer's strict landing check
			// retargets to the freshly resolved altitude instead of aborting the route
			final CelestialObject celestialObject = CelestialObjectManager.get(shipCore.getWorld(),
			                                                                   shipCore.getNavigationWaypointX(), shipCore.getNavigationWaypointZ());
			if (celestialObject != null && !celestialObject.isSpace() && !celestialObject.isHyperspace()) {
				final LandingProbe freshProbe = getCachedProbeLanding(shipCore,
				                                                      shipCore.getNavigationWaypointX(), shipCore.getNavigationWaypointZ(),
				                                                      celestialObject, LANDING_SELF_HEAL_TTL_TICKS, WAYPOINT_MAX_BLOCK_CHECKS);
				if ( freshProbe.landingCoreY != null
				  && freshProbe.landingCoreY != shipCore.getNavigationWaypointY()
				  && Math.abs(freshProbe.landingCoreY - shipCore.getNavigationWaypointY()) <= WAYPOINT_RETARGET_TOLERANCE ) {
					shipCore.retargetNavigationWaypointY(freshProbe.landingCoreY);
					return createAtmosphericLeg(shipCore);
				}
				if (freshProbe.landingCoreY == null) {
					return new Leg(EnumShipNavigationLegType.ATMOSPHERIC_CRUISE,
					               0, 0, 0, freshProbe.reasonKey, 0, remainingDistance, routePreview);
				}
			}
			final ShipMovementPreview landingPreview = shipCore.previewMovement(EnumShipCommand.MANUAL,
			                                                                     step.x, step.y, step.z, (byte) 0);
			return new Leg(legType,
			               step.x, step.y, step.z, warningKey, stepDistance, remainingDistance, landingPreview);
		}
		// intermediate cruise hop: fly over terrain instead of aborting on it
		final int obstructionY = sampleTerrainAlongHop(shipCore, stepX, step.y, stepZ);
		if (obstructionY != Integer.MIN_VALUE) {
			if (step.y < 0) {
				// hold altitude over terrain and defer the descent to the final approach
				final int flatObstructionY = sampleTerrainAlongHop(shipCore, stepX, 0, stepZ);
				if (flatObstructionY == Integer.MIN_VALUE) {
					final ShipMovementPreview flatPreview = shipCore.previewMovement(EnumShipCommand.MANUAL,
					                                                                  step.x, 0, step.z, (byte) 0);
					final VectorI flatStep = flatPreview.effectiveMovement;
					if (flatStep.getMagnitudeSquared() > 0L) {
						final int flatDistance = (int) Math.ceil(Math.sqrt(flatStep.getMagnitudeSquared()));
						return new Leg(EnumShipNavigationLegType.ATMOSPHERIC_CRUISE,
						               flatStep.x, flatStep.y, flatStep.z,
						               "warpdrive.navigation.route.terrain_hold_altitude", flatDistance, remainingDistance, flatPreview);
					}
				}
			}
			final int climb = Math.min(obstructionY + CRUISE_CLEARANCE_BLOCKS + 1 - shipCore.minY,
			                           ATMOSPHERIC_CEILING_Y - shipCore.maxY);
			if (climb > 0 && shipCore.registerClimbAttempt(shipCore.minY + climb)) {
				final ShipMovementPreview climbPreview = shipCore.previewMovement(EnumShipCommand.MANUAL,
				                                                                   0, climb, 0, (byte) 0);
				if (climbPreview.effectiveMovement.y > 0) {
					final int climbStep = climbPreview.effectiveMovement.y;
					final ShipMovementPreview climbStepPreview = shipCore.previewMovement(EnumShipCommand.MANUAL,
					                                                                       0, climbStep, 0, (byte) 0);
					return new Leg(EnumShipNavigationLegType.ATMOSPHERIC_CLIMB,
					               0, climbStep, 0,
					               "warpdrive.navigation.route.terrain_climb", climbStep, remainingDistance, climbStepPreview);
				}
			}
			final Leg detourLeg = createAtmosphericDetourLeg(shipCore, movement);
			if (detourLeg != null) {
				return detourLeg;
			}
			return new Leg(EnumShipNavigationLegType.ATMOSPHERIC_CRUISE,
			               0, 0, 0, "warpdrive.navigation.route.terrain_too_high", 0, remainingDistance, routePreview);
		}
		final ShipMovementPreview stepPreview = shipCore.previewMovement(EnumShipCommand.MANUAL,
		                                                                   step.x, step.y, step.z, (byte) 0);
		return new Leg(legType,
		               step.x, step.y, step.z, warningKey, stepDistance, remainingDistance, stepPreview);
	}

	/**
	 * Coarse heightmap sweep along a planned hop. Returns the highest obstructing terrain Y,
	 * or {@link Integer#MIN_VALUE} when the path is clear or unknown: ungenerated terrain never
	 * triggers a climb, the JumpSequencer collision checks remain the safety net there.
	 */
	private static int sampleTerrainAlongHop(@Nonnull final TileEntityShipCore shipCore,
	                                         final int worldMoveX, final int worldMoveY, final int worldMoveZ) {
		if (worldMoveX == 0 && worldMoveZ == 0) {
			return Integer.MIN_VALUE;
		}
		final double hopLength = Math.sqrt((double) worldMoveX * worldMoveX + (double) worldMoveZ * worldMoveZ);
		final int samplePoints = (int) Math.min(PATH_SAMPLE_MAX_POINTS, Math.max(2, hopLength / 4.0D));
		final int halfWidth = (shipCore.maxX - shipCore.minX) / 2;
		final int halfDepth = (shipCore.maxZ - shipCore.minZ) / 2;
		final int centerX = (shipCore.minX + shipCore.maxX) / 2;
		final int centerZ = (shipCore.minZ + shipCore.maxZ) / 2;
		int chunkLoads = 0;
		int maxObstructionY = Integer.MIN_VALUE;
		for (int point = 1; point <= samplePoints; point++) {
			final double progress = point / (double) samplePoints;
			final int sampleCenterX = centerX + (int) Math.round(worldMoveX * progress);
			final int sampleCenterZ = centerZ + (int) Math.round(worldMoveZ * progress);
			final int expectedMinY = shipCore.minY + (int) Math.floor(worldMoveY * progress);
			final int[][] columns = {
				{ sampleCenterX, sampleCenterZ },
				{ sampleCenterX - halfWidth, sampleCenterZ - halfDepth },
				{ sampleCenterX + halfWidth, sampleCenterZ - halfDepth },
				{ sampleCenterX - halfWidth, sampleCenterZ + halfDepth },
				{ sampleCenterX + halfWidth, sampleCenterZ + halfDepth }
			};
			for (final int[] column : columns) {
				final int chunkX = column[0] >> 4;
				final int chunkZ = column[1] >> 4;
				Chunk chunk = shipCore.getWorld().getChunkProvider().getLoadedChunk(chunkX, chunkZ);
				if (chunk == null) {
					if ( chunkLoads >= PATH_SAMPLE_MAX_CHUNK_LOADS
					  || !shipCore.getWorld().isChunkGeneratedAt(chunkX, chunkZ) ) {
						continue;
					}
					chunkLoads++;
					chunk = shipCore.getWorld().getChunk(chunkX, chunkZ);
				}
				final int terrainY = chunk.getHeightValue(column[0] & 15, column[1] & 15);
				if (terrainY + CRUISE_CLEARANCE_BLOCKS > expectedMinY) {
					maxObstructionY = Math.max(maxObstructionY, terrainY);
				}
			}
		}
		return maxObstructionY;
	}

	@Nullable
	private static Leg createAtmosphericDetourLeg(@Nonnull final TileEntityShipCore shipCore,
	                                              @Nonnull final VectorI remaining) {
		final int width = shipCore.maxX - shipCore.minX + 1;
		final int depth = shipCore.maxZ - shipCore.minZ + 1;
		final int height = shipCore.maxY - shipCore.minY + 1;
		final int targetDeltaX = shipCore.getNavigationWaypointX() - shipCore.getPos().getX();
		final int targetDeltaY = shipCore.getNavigationWaypointY() - shipCore.getPos().getY();
		final int targetDeltaZ = shipCore.getNavigationWaypointZ() - shipCore.getPos().getZ();
		final long distanceSquaredBefore = (long) targetDeltaX * targetDeltaX
		                                 + (long) targetDeltaY * targetDeltaY
		                                 + (long) targetDeltaZ * targetDeltaZ;
		final int[][] candidates = {
			{ targetDeltaX >= 0 ? -width : width, 0, 0 },
			{ targetDeltaX >= 0 ? width : -width, 0, 0 },
			{ 0, 0, targetDeltaZ >= 0 ? -depth : depth },
			{ 0, 0, targetDeltaZ >= 0 ? depth : -depth },
			{ 0, targetDeltaY >= 0 ? -height : height, 0 },
			{ 0, targetDeltaY >= 0 ? height : -height, 0 }
		};
		final CelestialObject celestialObject = CelestialObjectManager.get(shipCore.getWorld(), shipCore.getPos().getX(), shipCore.getPos().getZ());
		for (final int[] candidate : candidates) {
			final VectorI localCandidate = toLocalMovement(shipCore, candidate[0], candidate[1], candidate[2]);
			final ShipMovementPreview preview = shipCore.previewMovement(EnumShipCommand.MANUAL,
			                                                               localCandidate.x, localCandidate.y, localCandidate.z, (byte) 0);
			final VectorI effective = preview.effectiveMovement;
			final EnumFacing facing = getFacing(shipCore);
			final int effectiveX = facing.getXOffset() * effective.x - facing.getZOffset() * effective.z;
			final int effectiveZ = facing.getZOffset() * effective.x + facing.getXOffset() * effective.z;
			final long distanceSquaredAfter = (long) (targetDeltaX - effectiveX) * (targetDeltaX - effectiveX)
			                                + (long) (targetDeltaY - effective.y) * (targetDeltaY - effective.y)
			                                + (long) (targetDeltaZ - effectiveZ) * (targetDeltaZ - effectiveZ);
			if ( effective.getMagnitudeSquared() <= 0L
			  || isOverlappingMovement(shipCore, effectiveX, effective.y, effectiveZ)
			  || distanceSquaredAfter <= distanceSquaredBefore
			  || !isMovementInsideBorder(shipCore, celestialObject, effectiveX, effective.y, effectiveZ) ) {
				continue;
			}
			final int stepDistance = (int) Math.ceil(Math.sqrt(effective.getMagnitudeSquared()));
			final int remainingDistance = (int) Math.ceil(Math.sqrt(remaining.getMagnitudeSquared()));
			return new Leg(EnumShipNavigationLegType.ATMOSPHERIC_CRUISE,
			               effective.x, effective.y, effective.z,
			               "warpdrive.navigation.route.waypoint_detour", stepDistance, remainingDistance, preview);
		}
		return null;
	}

	private static boolean isOverlappingMovement(@Nonnull final TileEntityShipCore shipCore,
	                                             final int moveX, final int moveY, final int moveZ) {
		return Math.abs(moveX) < shipCore.maxX - shipCore.minX + 1
		    && Math.abs(moveY) < shipCore.maxY - shipCore.minY + 1
		    && Math.abs(moveZ) < shipCore.maxZ - shipCore.minZ + 1;
	}

	private static boolean isMovementInsideBorder(@Nonnull final TileEntityShipCore shipCore,
	                                              @Nullable final CelestialObject celestialObject,
	                                              final int moveX, final int moveY, final int moveZ) {
		if (celestialObject == null) {
			return false;
		}
		if (shipCore.minY + moveY < 9 || shipCore.maxY + moveY > 255) {
			return false;
		}
		return celestialObject.isInsideBorder(new AxisAlignedBB(
				shipCore.minX + moveX, shipCore.minY + moveY, shipCore.minZ + moveZ,
				shipCore.maxX + moveX + 1, shipCore.maxY + moveY + 1, shipCore.maxZ + moveZ + 1));
	}

	@Nonnull
	private static String getTargetDisplayName(@Nonnull final TileEntityShipCore shipCore,
	                                           @Nullable final CelestialObject celestialObjectTarget) {
		if (shipCore.isNavigationTargetWaypoint()) {
			return shipCore.getNavigationWaypointName().isEmpty()
			     ? String.format("%d, %d", shipCore.getNavigationWaypointX(), shipCore.getNavigationWaypointZ())
			     : shipCore.getNavigationWaypointName();
		}
		return celestialObjectTarget == null ? "?" : celestialObjectTarget.getDisplayName();
	}

	@Nonnull
	private static VectorI toLocalMovement(@Nonnull final TileEntityShipCore shipCore, final int moveX, final int moveY, final int moveZ) {
		final EnumFacing facing = getFacing(shipCore);
		final int moveFront = facing.getXOffset() * moveX + facing.getZOffset() * moveZ;
		final int moveRight = -facing.getZOffset() * moveX + facing.getXOffset() * moveZ;
		return new VectorI(moveFront, moveY, moveRight);
	}

	@Nonnull
	private static EnumFacing getFacing(@Nonnull final TileEntityShipCore shipCore) {
		if (shipCore.facing != null) {
			return shipCore.facing;
		}
		final IBlockState blockState = shipCore.getWorld().getBlockState(shipCore.getPos());
		if ( blockState.getBlock() instanceof BlockShipCore
		  && blockState.getPropertyKeys().contains(BlockProperties.FACING_HORIZONTAL) ) {
			return blockState.getValue(BlockProperties.FACING_HORIZONTAL);
		}
		return EnumFacing.NORTH;
	}

	/** A planned navigation leg. The display title/description are derived client-side from {@link #type}. */
	public static final class Leg {
		public final EnumShipNavigationLegType type;
		public final EnumShipCommand command;
		public final int moveFront;
		public final int moveUp;
		public final int moveRight;
		public final boolean requiresConfirmation;
		public final int stepDistance;
		public final int remainingDistance;
		public final String warningKey;
		@Nullable
		public final ShipMovementPreview preview;

		private Leg(@Nonnull final EnumShipNavigationLegType type,
		            final int moveFront, final int moveUp, final int moveRight,
		            @Nonnull final String warningKey,
		            final int stepDistance, final int remainingDistance) {
			this(type, moveFront, moveUp, moveRight, warningKey, stepDistance, remainingDistance, null);
		}
		
		private Leg(@Nonnull final EnumShipNavigationLegType type,
		            final int moveFront, final int moveUp, final int moveRight,
		            @Nonnull final String warningKey,
		            final int stepDistance, final int remainingDistance,
		            @Nullable final ShipMovementPreview preview) {
			this.type = type;
			this.command = type.getCommand();
			this.moveFront = moveFront;
			this.moveUp = moveUp;
			this.moveRight = moveRight;
			this.requiresConfirmation = type.requiresConfirmation();
			this.stepDistance = stepDistance;
			this.remainingDistance = remainingDistance;
			this.warningKey = warningKey;
			this.preview = preview;
		}
	}
}
