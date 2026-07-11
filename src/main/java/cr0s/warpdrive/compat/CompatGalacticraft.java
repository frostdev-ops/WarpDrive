package cr0s.warpdrive.compat;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBlockTransformer;
import cr0s.warpdrive.api.ITransformation;
import cr0s.warpdrive.api.WarpDriveText;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.CelestialObject;
import cr0s.warpdrive.data.CelestialObjectManager;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import micdoodle8.mods.galacticraft.api.event.oxygen.GCCoreOxygenSuffocationEvent;
import micdoodle8.mods.galacticraft.core.entities.player.GCPlayerStats;
import micdoodle8.mods.galacticraft.core.entities.player.GCPlayerStatsClient;

public class CompatGalacticraft implements IBlockTransformer {
	
	public static CompatGalacticraft INSTANCE;
	
	private static Class<?> classBlockAdvanced;
	private static Class<?> classBlockConcealedDetector;
	private static Class<?> classBlockParaChest;
	private static Class<?> classBlockTier1TreasureChest;
	private static Class<?> classBlockTorchBase;
	private static Class<?> classBlockSpout;

	public static void register() {
		try {
			classBlockAdvanced = Class.forName("micdoodle8.mods.galacticraft.core.blocks.BlockAdvanced");
			classBlockConcealedDetector = Class.forName("micdoodle8.mods.galacticraft.core.blocks.BlockConcealedDetector");
			classBlockParaChest = Class.forName("micdoodle8.mods.galacticraft.core.blocks.BlockParaChest");
			classBlockTier1TreasureChest = Class.forName("micdoodle8.mods.galacticraft.core.blocks.BlockTier1TreasureChest");
			classBlockTorchBase = Class.forName("micdoodle8.mods.galacticraft.core.blocks.BlockTorchBase");

			INSTANCE = new CompatGalacticraft();
			WarpDriveConfig.registerBlockTransformer("Galacticraft", INSTANCE);

			MinecraftForge.EVENT_BUS.register(INSTANCE);
		} catch(final ClassNotFoundException exception) {
			exception.printStackTrace();
		}

		// Galacticraft Planets is optional: its absence shall not disable the core compatibility
		try {
			classBlockSpout = Class.forName("micdoodle8.mods.galacticraft.planets.venus.blocks.BlockSpout");
		} catch(final ClassNotFoundException exception) {
			WarpDrive.logger.info("Galacticraft Planets not detected, skipping related block transformers");
		}
	}
	
	// disable breathing suffocation event server side
	@SubscribeEvent
	public void onGCCoreOxygenSuffocationEventPre(final GCCoreOxygenSuffocationEvent.Pre event) {
		assert event.getEntity() != null;
		
		final Entity entity = event.getEntity();
		final int x = MathHelper.floor(entity.posX);
		final int z = MathHelper.floor(entity.posZ);
		final CelestialObject celestialObject = CelestialObjectManager.get(entity.world, x, z);
		if (celestialObject == null) {
			// unregistered dimension => exit
			return;
		}
		
		final GCPlayerStats gcPlayerStats = GCPlayerStats.get(event.getEntity());
		if (gcPlayerStats != null) {
			gcPlayerStats.setLastOxygenSetupValid(true);
		}
		
		event.setCanceled(true);
	}
	
	// disable breathing alarm overlay client side
	@SideOnly(Side.CLIENT)
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onLivingUpdate(@Nonnull final LivingUpdateEvent event) {
		if (Minecraft.getMinecraft().player == null) {
			return;
		}
		
		final EntityLivingBase entityLivingBase = event.getEntityLiving();
		if (entityLivingBase != Minecraft.getMinecraft().player) {
			return;
		}
		
		final int x = MathHelper.floor(entityLivingBase.posX);
		final int z = MathHelper.floor(entityLivingBase.posZ);
		final CelestialObject celestialObject = CelestialObjectManager.get(entityLivingBase.world, x, z);
		if (celestialObject == null) {
			// unregistered dimension => exit
			return;
		}
		
		final GCPlayerStatsClient stats = GCPlayerStatsClient.get(Minecraft.getMinecraft().player);
		if (stats != null) {
			stats.setOxygenSetupValid(true);
		}
	}
	
	@Override
	public boolean isApplicable(final Block block, final int metadata, final TileEntity tileEntity) {
		return classBlockAdvanced.isInstance(block)
		    || classBlockConcealedDetector.isInstance(block)
		    || classBlockParaChest.isInstance(block)
		    || classBlockTier1TreasureChest.isInstance(block)
		    || classBlockTorchBase.isInstance(block)
		    || (classBlockSpout != null && classBlockSpout.isInstance(block));
	}
	
	@Override
	public boolean isJumpReady(final Block block, final int metadata, final TileEntity tileEntity, final WarpDriveText reason) {
		return true;
	}
	
	@Override
	public NBTBase saveExternals(final World world, final int x, final int y, final int z,
	                             final Block block, final int blockMeta, final TileEntity tileEntity) {
		// nothing to do
		return null;
	}
	
	@Override
	public void removeExternals(final World world, final int x, final int y, final int z,
	                            final Block block, final int blockMeta, final TileEntity tileEntity) {
		// nothing to do
	}
	
	/*
	As of Galacticraft-Legacy 1.12.2-4.0.6 (originally audited against 4.0.1.184)

	- = detected by instanceof micdoodle8.mods.galacticraft.core.blocks.BlockAdvanced (derived in BlockAdvancedTile, BlockTransmitter, BlockTileGC)
	+ = not detected but no impact or already handled by vanilla compatibility
	# = needs explicit detection
		micdoodle8.mods.galacticraft.core.blocks.BlockParaChest
		micdoodle8.mods.galacticraft.core.blocks.BlockTier1TreasureChest
		micdoodle8.mods.galacticraft.core.blocks.BlockTorchBase
		micdoodle8.mods.galacticraft.planets.venus.blocks.BlockSpout (extends Block + ITileEntityProvider)
	D = handled through dictionary

	Additions in Galacticraft-Legacy 4.0.x, all verified covered by instanceof BlockAdvanced (via BlockTileGC/BlockMachineBase):
	-	micdoodle8.mods.galacticraft.core.blocks.BlockMachineBase / BlockMachine4
	-	micdoodle8.mods.galacticraft.core.blocks.BlockCompactNasaWorkbench
	-	micdoodle8.mods.galacticraft.core.blocks.BlockEmergencyBox
	-	micdoodle8.mods.galacticraft.planets.venus.blocks.BlockSolarArrayController / BlockSolarArrayModule / BlockLaserTurret
	Moved from planets.mars to core (unchanged coverage): BlockTelemetry, BlockScreen, BlockFluidTank, BlockConcealedRedstone, BlockConcealedRepeater
	BlockTier2/Tier3TreasureChest extend BlockTier1TreasureChest (covered).
	Tile entity ids are underscored ResourceLocations as of GC-Legacy: 'gc_beam_receiver', 'gc_panel_lighting'.

-	micdoodle8.mods.galacticraft.core.blocks.BlockAirLockFrame gc air lock frame (meta 0) / gc air lock controller (meta 1)
		frame = no impact
		controller = no impact
+	micdoodle8.mods.galacticraft.core.blocks.BlockAirLockWall                       air lock seal extends BlockBreakable
-	micdoodle8.mods.galacticraft.core.blocks.BlockAluminumWire                      gc aluminum wire
+	micdoodle8.mods.galacticraft.core.blocks.BlockBasic                             tin decoration, ores, etc.
+	micdoodle8.mods.galacticraft.core.blocks.BlockBasicMoon
+	micdoodle8.mods.galacticraft.core.blocks.BlockCheese
-	micdoodle8.mods.galacticraft.core.blocks.BlockEmergencyBox
+	micdoodle8.mods.galacticraft.core.blocks.BlockEnclosed                          gc aluminum wire extends Block
+	micdoodle8.mods.galacticraft.core.blocks.BlockFallenMeteor                      ? extends Block
-	micdoodle8.mods.galacticraft.core.blocks.BlockFluidPipe                         gc oxygen pipe
+	micdoodle8.mods.galacticraft.core.blocks.BlockGrating                           n/a extends Block
-	micdoodle8.mods.galacticraft.core.blocks.BlockLandingPad
-	micdoodle8.mods.galacticraft.core.blocks.BlockLandingPadFull
+	micdoodle8.mods.galacticraft.core.blocks.BlockNasaWorkbench                     gc nasa workbench extends BlockContainer
+	micdoodle8.mods.galacticraft.core.blocks.BlockOxygenDetector                    ? extends BlockContainer
+	micdoodle8.mods.galacticraft.core.blocks.BlockSpaceGlass                        n/a (glass windows) extends Block
-	micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockShortRangeTelepad    gc short range telepad
-	micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockWalkway
+	micdoodle8.mods.galacticraft.planets.mars.blocks.BlockBasicMars                 n/a extends Block
+	micdoodle8.mods.galacticraft.planets.mars.blocks.BlockConcealedRedstone         n/a extends Block
+	micdoodle8.mods.galacticraft.planets.mars.blocks.BlockConcealedRepeater         n/a extends BlockRedstoneRepeater
+	micdoodle8.mods.galacticraft.planets.mars.blocks.BlockFluidTank                 gc fluid tank extends Block
-	micdoodle8.mods.galacticraft.planets.venus.blocks.BlockCrashedProbe             gc crashed probe
		no impact
	
	
-	micdoodle8.mods.galacticraft.core.blocks.BlockCargoLoader                       gc cargo loader / gc cargo unloader
-	micdoodle8.mods.galacticraft.core.blocks.BlockDish                              gc radio telescope
-	micdoodle8.mods.galacticraft.core.blocks.BlockFuelLoader                        gc fuel loader
-	micdoodle8.mods.galacticraft.core.blocks.BlockMachine                           coal generation / ingot compressor
-	micdoodle8.mods.galacticraft.core.blocks.BlockMachine2                          gc electric ingot compressor / gc circuit fabricator / gc oxygen storage module / gc deconstructor
-	micdoodle8.mods.galacticraft.core.blocks.BlockMachine3                          gc painter
-	micdoodle8.mods.galacticraft.core.blocks.BlockMachineTiered                     gc energy storage module / gc electric furnace / gc energy storage module / gc electric furnace
-	micdoodle8.mods.galacticraft.core.blocks.BlockOxygenCollector                   gc air collector
-	micdoodle8.mods.galacticraft.core.blocks.BlockOxygenCompressor                  gc air compressor / gc oxygen decompressor
-	micdoodle8.mods.galacticraft.core.blocks.BlockOxygenDistributor                 gc air distributor
-	micdoodle8.mods.galacticraft.core.blocks.BlockOxygenSealer                      gc air sealer
-	micdoodle8.mods.galacticraft.core.blocks.BlockRefinery                          gc refinery
-	micdoodle8.mods.galacticraft.core.blocks.BlockSolar                             gc solar panel
-	micdoodle8.mods.galacticraft.core.blocks.BlockSpinThruster                      gc space station thruster
-	micdoodle8.mods.galacticraft.planets.mars.blocks.BlockMachineMarsT2             gc gas liquefier / gc methane synthesizer / gc water electrolyzer
-	micdoodle8.mods.galacticraft.planets.venus.blocks.BlockGeothermalGenerator      gc geothermal generator
		metadata    0 1 2 3 / 4 5 6 7 / 8 9 10 11 / 12 13 14 15
-	micdoodle8.mods.galacticraft.core.blocks.BlockCrafting                          gc magnetic crafting table
#	micdoodle8.mods.galacticraft.core.blocks.BlockParaChest                         gc parachest tile extends BlockContainer
#	micdoodle8.mods.galacticraft.core.blocks.BlockTier1TreasureChest                ? (including Moon/Mars/Venus) extends BlockContainer
		metadata    0 / 1 / 2 5 3 4
-	micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockBeamReceiver         gc beam receiver
		FacingSide int 0 / 1 / 2 5 3 4
		metadata    0 / 1 / 2 5 3 4
-	micdoodle8.mods.galacticraft.planets.mars.BlockScreen                           gc view screen
		metadata    0 / 1 / 2 5 3 4
		is it fully implemented yet?
-	micdoodle8.mods.galacticraft.core.BlockPanelLighting                            gc panel lighting
		metadata    0 / 1 / 2 / 3 / 4
		meta int    0 / 1 / 2 5 3 4 if metadata = 0 or 1
		meta int    0 8 / 1 9 / 2 5 3 4 / 10 13 11 12 if metadata = 2 or 3
		meta int    0 8 16 24 / 1 25 17 9 / 2 21 19 4 / 3 20 18 5 / 10 29 27 12 / 11 28 26 13 if metadata = 4
-	micdoodle8.mods.galacticraft.core.blocks.BlockPlatform (lift)                   gc platform
		metadata    0 / 1 3 4 2
		oc int      0 / 1 3 4 2 (same as metadata?)
#	micdoodle8.mods.galacticraft.core.blocks.BlockTorchBase                         n/a (including unlit and glowstone torches) extends Block
		metadata    1 3 2 4 / 5
	
#	micdoodle8.mods.galacticraft.core.blocks.BlockConcealedDetector                 gc player detector (player detector, creative only) extends Block
		metadata    0 1 2 3 / 4 5 6 7 / 8 9 10 11 / 12 13 14 15 (not EnumFacing)
-	micdoodle8.mods.galacticraft.planets.mars.blocks.BlockMachineMars               gc cryogenic chamber / gc planet terraformer / gc launch controller
		mainBlockPosition.x/y/z int absolute coordinates (optional, see cryogenic chamber)
		metadata    0 1 2 3 / 4 5 6 7 / 8 9 10 11 / 12 13 14 15
-	micdoodle8.mods.galacticraft.core.blocks.BlockMulti                             gc dummy block
-	micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockTelepadFake          gc fake short range telepad
		mainBlockPosition.x/y/z int absolute coordinates
-	micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockBeamReflector        gc beam reflector
		HasTarget   boolean 1 when targeting?
		TargetX/Y/Z int absolute coordinates
	
D-	micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockMinerBase            gc astro miner base builder
D-	micdoodle8.mods.galacticraft.planets.asteroids.blocks.BlockMinerBaseFull        gc astro miner base
		facing int  0 1 2 3 ? (only relevant on master tile?)
		masterpos.x/y/z absolute coordinates
		TargetPoints list TabCompound
			x/y/z absolute coordinates
		=> anchor
D	micdoodle8.mods.galacticraft.planets.mars.BlockBossSpawner                      minecraft:gc dungeon boss spawner (including Moon/Mars/Venus) extends Block
		chestX/Y/Z int absolute coordinates
		roomCoordsX/Y/Z int absolute coordinates
		roomSizeX/Y/Z int
		=> anchor
D-	micdoodle8.mods.galacticraft.planets.mars.BlockBrightLamp                       gc arc lamp
		metadata    0 / 1 / 2 5 3 4
		Facing int  if 0 or 1, 0 3 1 2; otherwise, no change
		AirBlocks list TagCompound
			x/y/z absolute coordinates
		=> anchor
D	micdoodle8.mods.galacticraft.planets.mars.BlockBreathableAir                    n/a extends BlockAir
		=> ignored
D	micdoodle8.mods.galacticraft.planets.mars.BlockSpaceStationBase                 gc space station extends BlockContainer
		mainBlockPosition.x/y/z
		=> anchor
D-	micdoodle8.mods.galacticraft.planets.mars.BlockTelemetry                        gc telemetry unit
		is it fully implemented yet?
		=> anchor
	*/
	
	// -----------------------------------------    {  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15 };
	private static final int[]   rotFacing        = {  0,  1,  5,  4,  2,  3,  6,  7,  8,  9, 10, 11, 12, 13, 14, 15 };
	private static final int[]   rotDetector      = {  1,  2,  3,  0,  5,  6,  7,  4,  9, 10, 11,  8, 13, 14, 15, 12 };
	private static final int[]   rotLighting23    = {  0,  9,  5,  4,  2,  3,  6,  7,  0,  1, 13, 12, 10, 11, 14, 15 };
	private static final int[]   rotLighting4     = {  8, 25, 21, 20,  2,  3,  6,  7, 16,  1, 29, 28, 10, 11, 14, 15,
	                                                  24,  9,  5,  4, 18, 19, 22, 23,  0, 17, 13, 12, 26, 27, 30, 31 };
	
	// Rewrites the NBT structures shared across the Galacticraft family of mods
	// (GalaxySpace and More Planets reuse the same conventions):
	// - 'mainBlockPosition' multiblock dummy link (TileEntityMulti and derived)
	// - 'HasTarget'/'TargetX/Y/Z' absolute target coordinates (beam reflector)
	static void rotateGalacticraftFamilyNBT(final NBTTagCompound nbtTileEntity, final ITransformation transformation) {
		if (nbtTileEntity == null) {
			return;
		}

		// multiblock: relink dummy blocks to the transformed main block position
		if (nbtTileEntity.hasKey("mainBlockPosition")) {
			final NBTTagCompound tagCompoundMainBlockPosition = nbtTileEntity.getCompoundTag("mainBlockPosition");
			if ( tagCompoundMainBlockPosition.hasKey("x")
			  && tagCompoundMainBlockPosition.hasKey("y")
			  && tagCompoundMainBlockPosition.hasKey("z") ) {
				final int x = tagCompoundMainBlockPosition.getInteger("x");
				final int y = tagCompoundMainBlockPosition.getInteger("y");
				final int z = tagCompoundMainBlockPosition.getInteger("z");
				// main block outside the jump: leave untouched, the multiblock will re-link or invalidate on tick
				if (transformation.isInside(x, y, z)) {
					final BlockPos blockPosMain = transformation.apply(x, y, z);
					tagCompoundMainBlockPosition.setInteger("x", blockPosMain.getX());
					tagCompoundMainBlockPosition.setInteger("y", blockPosMain.getY());
					tagCompoundMainBlockPosition.setInteger("z", blockPosMain.getZ());
				}
			}
		}

		// target for Beam reflector
		if (nbtTileEntity.getBoolean("HasTarget")) {
			if ( nbtTileEntity.hasKey("TargetX")
			  && nbtTileEntity.hasKey("TargetY")
			  && nbtTileEntity.hasKey("TargetZ") ) {
				final int x = nbtTileEntity.getInteger("TargetX");
				final int y = nbtTileEntity.getInteger("TargetY");
				final int z = nbtTileEntity.getInteger("TargetZ");
				if (transformation.isInside(x, y, z)) {
					final BlockPos blockPosTarget = transformation.apply(x, y, z);
					nbtTileEntity.setInteger("TargetX", blockPosTarget.getX());
					nbtTileEntity.setInteger("TargetY", blockPosTarget.getY());
					nbtTileEntity.setInteger("TargetZ", blockPosTarget.getZ());
				} else {
					nbtTileEntity.setBoolean("HasTarget", false);
				}
			}
		}
	}

	@Override
	public int rotate(final Block block, final int metadata, final NBTTagCompound nbtTileEntity, final ITransformation transformation) {
		final byte rotationSteps = transformation.getRotationSteps();

		rotateGalacticraftFamilyNBT(nbtTileEntity, transformation);

		final String idTileEntity = nbtTileEntity == null ? "" : nbtTileEntity.getString("id");

		// beam receiver ('gc_beam_receiver' as of Galacticraft-Legacy 4.0.x)
		if ( ( idTileEntity.contains("beam_receiver")
		    || idTileEntity.contains("beam receiver") )
		  && nbtTileEntity.hasKey("FacingSide") ) {
			final int facingSide = nbtTileEntity.getInteger("FacingSide");
			switch (rotationSteps) {
			case 1:
				nbtTileEntity.setInteger("FacingSide", rotFacing[facingSide]);
				break;
			case 2:
				nbtTileEntity.setInteger("FacingSide", rotFacing[rotFacing[facingSide]]);
				break;
			case 3:
				nbtTileEntity.setInteger("FacingSide", rotFacing[rotFacing[rotFacing[facingSide]]]);
				break;
			default:
				break;
			}
		}
		
		// panel lighting ('gc_panel_lighting' as of Galacticraft-Legacy 4.0.x)
		if ( ( idTileEntity.contains("panel_lighting")
		    || idTileEntity.contains("panel lighting") )
		  && nbtTileEntity.hasKey("meta") ) {
			final int meta = nbtTileEntity.getInteger("meta");
			
			if ( metadata == 0
			  || metadata == 1 ) {
				switch (rotationSteps) {
				case 1:
					nbtTileEntity.setInteger("meta", rotFacing[meta]);
					break;
				case 2:
					nbtTileEntity.setInteger("meta", rotFacing[rotFacing[meta]]);
					break;
				case 3:
					nbtTileEntity.setInteger("meta", rotFacing[rotFacing[rotFacing[meta]]]);
					break;
				default:
					break;
				}
				
			} else if ( metadata == 2
			         || metadata == 3 ) {
				switch (rotationSteps) {
				case 1:
					nbtTileEntity.setInteger("meta", rotLighting23[meta]);
					break;
				case 2:
					nbtTileEntity.setInteger("meta", rotLighting23[rotLighting23[meta]]);
					break;
				case 3:
					nbtTileEntity.setInteger("meta", rotLighting23[rotLighting23[rotLighting23[meta]]]);
					break;
				default:
					break;
				}
				
			} else if (metadata == 4) {
				switch (rotationSteps) {
				case 1:
					nbtTileEntity.setInteger("meta", rotLighting4[meta]);
					break;
				case 2:
					nbtTileEntity.setInteger("meta", rotLighting4[rotLighting4[meta]]);
					break;
				case 3:
					nbtTileEntity.setInteger("meta", rotLighting4[rotLighting4[rotLighting4[meta]]]);
					break;
				default:
					break;
				}
				
			} else {
				WarpDrive.logger.error(String.format("Unsupported Galacticraft lighting panel %s:%d with nbt %s",
				                                     block, metadata, nbtTileEntity));
			}
		}
		
		// specific rotation for Concealed detector blocks
		if (classBlockConcealedDetector.isInstance(block)) {
			switch (rotationSteps) {
			case 1:
				return rotDetector[metadata];
			case 2:
				return rotDetector[rotDetector[metadata]];
			case 3:
				return rotDetector[rotDetector[rotDetector[metadata]]];
			default:
				return metadata;
			}
		}
		
		// apply default transformer
		return IBlockTransformer.rotateFirstEnumFacingProperty(block, metadata, rotationSteps);
	}
	
	@Override
	public void restoreExternals(final World world, final BlockPos blockPos,
	                             final IBlockState blockState, final TileEntity tileEntity,
	                             final ITransformation transformation, final NBTBase nbtBase) {
		// nothing to do
	}
}
