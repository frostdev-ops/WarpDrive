package cr0s.warpdrive.compat;

import cr0s.warpdrive.WarpDrive;
import cr0s.warpdrive.api.IBlockTransformer;
import cr0s.warpdrive.api.ITransformation;
import cr0s.warpdrive.api.WarpDriveText;
import cr0s.warpdrive.config.WarpDriveConfig;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

/**
 * More Planets is a Galacticraft addon with its own block hierarchy (BlockBaseMP and
 * derived), reusing Galacticraft's 'mainBlockPosition' multiblock convention through
 * its TileEntityDummy. Its Space Warp Pads teleport to absolute coordinates stored in
 * a Space Warper Core item sitting in the pad: those are rewritten during a jump when
 * the destination pad moves along.
 */
public class CompatMorePlanets implements IBlockTransformer {

	private static Class<?> classBlockAdvancedMP;      // covers BlockTileMP & BlockAdvancedTileMP (machines, dark energy multiblocks)
	private static Class<?> classBlockContainerMP;     // covers chests, dummy blocks, warp pads
	private static Class<?> classBlockBaseMP;          // simple blocks, only relevant when they hold a tile entity
	private static Class<?> classTileEntitySpaceWarpPadFull;

	private static final String ITEM_ID_SPACE_WARPER_CORE = "moreplanets:space_warper_core";

	public static void register() {
		try {
			classBlockAdvancedMP = Class.forName("com.stevekung.moreplanets.utils.blocks.BlockAdvancedMP");
			classBlockContainerMP = Class.forName("com.stevekung.moreplanets.utils.blocks.BlockContainerMP");
			classBlockBaseMP = Class.forName("com.stevekung.moreplanets.utils.blocks.BlockBaseMP");
			classTileEntitySpaceWarpPadFull = Class.forName("com.stevekung.moreplanets.tileentity.TileEntitySpaceWarpPadFull");

			WarpDriveConfig.registerBlockTransformer("MorePlanets", new CompatMorePlanets());
		} catch(final ClassNotFoundException exception) {
			exception.printStackTrace();
		}
	}

	@Override
	public boolean isApplicable(final Block block, final int metadata, final TileEntity tileEntity) {
		return classBlockAdvancedMP.isInstance(block)
		    || classBlockContainerMP.isInstance(block)
		    || (classBlockBaseMP.isInstance(block) && tileEntity != null);
	}

	@Override
	public boolean isJumpReady(final Block block, final int metadata, final TileEntity tileEntity, final WarpDriveText reason) {
		return true;
	}

	@Override
	public NBTBase saveExternals(final World world, final int x, final int y, final int z,
	                             final Block block, final int blockMeta, final TileEntity tileEntity) {
		// remember the source dimension for warp pads, so warper core coordinates can be
		// retargeted after a cross dimension jump
		if (classTileEntitySpaceWarpPadFull.isInstance(tileEntity)) {
			final NBTTagCompound tagCompound = new NBTTagCompound();
			tagCompound.setInteger("dimensionSource", world.provider.getDimension());
			return tagCompound;
		}
		return null;
	}

	@Override
	public void removeExternals(final World world, final int x, final int y, final int z,
	                            final Block block, final int blockMeta, final TileEntity tileEntity) {
		// nothing to do
	}

	@Override
	public int rotate(final Block block, final int metadata, final NBTTagCompound nbtTileEntity, final ITransformation transformation) {
		// same multiblock/target conventions as Galacticraft (TileEntityDummy.mainBlockPosition)
		CompatGalacticraft.rotateGalacticraftFamilyNBT(nbtTileEntity, transformation);

		// apply default transformer
		return IBlockTransformer.rotateFirstEnumFacingProperty(block, metadata, transformation.getRotationSteps());
	}

	@Override
	public void restoreExternals(final World world, final BlockPos blockPos,
	                             final IBlockState blockState, final TileEntity tileEntity,
	                             final ITransformation transformation, final NBTBase nbtBase) {
		if ( !(nbtBase instanceof NBTTagCompound)
		  || !((NBTTagCompound) nbtBase).hasKey("dimensionSource")
		  || tileEntity == null
		  || !classTileEntitySpaceWarpPadFull.isInstance(tileEntity) ) {
			return;
		}
		final int dimensionSource = ((NBTTagCompound) nbtBase).getInteger("dimensionSource");
		final int dimensionTarget = world.provider.getDimension();

		// rewrite the warper core(s) sitting in the pad when they pointed to a pad that moved with the ship
		final NBTTagCompound tagCompoundTileEntity = tileEntity.writeToNBT(new NBTTagCompound());
		final NBTTagList tagListItems = tagCompoundTileEntity.getTagList("Items", NBT.TAG_COMPOUND);
		boolean isUpdated = false;
		for (int indexSlot = 0; indexSlot < tagListItems.tagCount(); indexSlot++) {
			final NBTTagCompound tagCompoundItem = tagListItems.getCompoundTagAt(indexSlot);
			if (!ITEM_ID_SPACE_WARPER_CORE.equals(tagCompoundItem.getString("id"))) {
				continue;
			}
			final NBTTagCompound tagCompoundCore = tagCompoundItem.getCompoundTag("tag");
			if ( !tagCompoundCore.hasKey("X")
			  || !tagCompoundCore.hasKey("Y")
			  || !tagCompoundCore.hasKey("Z")
			  || tagCompoundCore.getInteger("DimensionID") != dimensionSource ) {
				continue;
			}
			final int x = tagCompoundCore.getInteger("X");
			final int y = tagCompoundCore.getInteger("Y");
			final int z = tagCompoundCore.getInteger("Z");
			if (transformation.isInside(x, y, z)) {
				final BlockPos blockPosTarget = transformation.apply(x, y, z);
				tagCompoundCore.setInteger("X", blockPosTarget.getX());
				tagCompoundCore.setInteger("Y", blockPosTarget.getY());
				tagCompoundCore.setInteger("Z", blockPosTarget.getZ());
				tagCompoundCore.setInteger("DimensionID", dimensionTarget);
				isUpdated = true;
			} else {
				WarpDrive.logger.info(String.format("More Planets warper core in pad %s points outside the ship (%d %d %d in dimension %d), left unchanged",
				                                    blockPos, x, y, z, dimensionSource ));
			}
		}
		if (isUpdated) {
			tileEntity.readFromNBT(tagCompoundTileEntity);
			tileEntity.markDirty();
		}
	}
}
