package cr0s.warpdrive.client.gui;

import java.util.Arrays;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Vanilla-map style terrain snapshot around the ship for the navigation surface chart.
 * Samples map colors from already loaded chunks only, on a strict per-tick budget, into a
 * {@link DynamicTexture}. Unsampled or unloaded areas stay transparent so the chart's
 * "unscanned" backdrop shows through.
 */
public class SurfaceTerrainCache {

	private static final int GRID_SIZE = 256;
	private static final int BLOCKS_PER_SAMPLE = 2;
	private static final int WINDOW_BLOCKS = GRID_SIZE * BLOCKS_PER_SAMPLE;
	private static final int ORIGIN_SNAP_BLOCKS = 64;
	private static final int RECENTER_DISTANCE_BLOCKS = 128;
	private static final int COLUMNS_PER_TICK = 2048;
	private static final int RESWEEP_INTERVAL_TICKS = 100;

	private final int[] colors = new int[GRID_SIZE * GRID_SIZE];
	private DynamicTexture texture;
	private boolean isTextureDirty;
	private int originX = Integer.MIN_VALUE;
	private int originZ = Integer.MIN_VALUE;
	private int sweepIndex;
	private int ticksSinceSweepStart;
	private final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

	public int getOriginX() {
		return originX;
	}

	public int getOriginZ() {
		return originZ;
	}

	public int getWindowBlocks() {
		return WINDOW_BLOCKS;
	}

	public void invalidate() {
		Arrays.fill(colors, 0);
		sweepIndex = 0;
		ticksSinceSweepStart = 0;
		isTextureDirty = true;
	}

	// call once per client tick while the surface chart is visible
	public void update(final World world, final int shipX, final int shipZ) {
		if (world == null) {
			return;
		}
		if ( originX == Integer.MIN_VALUE
		  || Math.abs(shipX - (originX + WINDOW_BLOCKS / 2)) > RECENTER_DISTANCE_BLOCKS
		  || Math.abs(shipZ - (originZ + WINDOW_BLOCKS / 2)) > RECENTER_DISTANCE_BLOCKS ) {
			// snap the window origin so small ship movements don't thrash the cache
			originX = Math.floorDiv(shipX - WINDOW_BLOCKS / 2, ORIGIN_SNAP_BLOCKS) * ORIGIN_SNAP_BLOCKS;
			originZ = Math.floorDiv(shipZ - WINDOW_BLOCKS / 2, ORIGIN_SNAP_BLOCKS) * ORIGIN_SNAP_BLOCKS;
			invalidate();
		}
		ticksSinceSweepStart++;
		if (sweepIndex >= colors.length) {
			if (ticksSinceSweepStart < RESWEEP_INTERVAL_TICKS) {
				return;
			}
			sweepIndex = 0;
			ticksSinceSweepStart = 0;
		}
		final int indexLast = Math.min(colors.length, sweepIndex + COLUMNS_PER_TICK);
		boolean changed = false;
		for (; sweepIndex < indexLast; sweepIndex++) {
			final int color = sampleColumn(world,
			                               originX + (sweepIndex % GRID_SIZE) * BLOCKS_PER_SAMPLE,
			                               originZ + (sweepIndex / GRID_SIZE) * BLOCKS_PER_SAMPLE);
			if (colors[sweepIndex] != color) {
				colors[sweepIndex] = color;
				changed = true;
			}
		}
		isTextureDirty |= changed;
	}

	private int sampleColumn(final World world, final int x, final int z) {
		final Chunk chunk = world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
		if (chunk == null) {
			return 0;
		}
		int y = chunk.getHeightValue(x & 15, z & 15);
		IBlockState blockState = null;
		while (y > 0) {
			blockState = chunk.getBlockState(x & 15, y - 1, z & 15);
			if (blockState.getMaterial() != Material.AIR) {
				break;
			}
			y--;
		}
		if (y <= 0 || blockState == null) {
			return 0xFF000000;
		}
		mutableBlockPos.setPos(x, y - 1, z);
		int color = blockState.getMapColor(world, mutableBlockPos).colorValue;
		if (color == 0) {
			color = 0x202020;
		}
		if (blockState.getMaterial() == Material.WATER) {
			// darken with depth, like vanilla maps
			int depth = 0;
			while (depth < 6 && y - 2 - depth > 0
			    && chunk.getBlockState(x & 15, y - 2 - depth, z & 15).getMaterial() == Material.WATER) {
				depth++;
			}
			color = scaleColor(color, 1.0F - depth * 0.06F);
		} else {
			// relief shading against the northern neighbor column
			final int neighborY = heightAt(world, chunk, x, z - 1);
			if (neighborY > 0) {
				if (y > neighborY) {
					color = scaleColor(color, 1.18F);
				} else if (y < neighborY) {
					color = scaleColor(color, 0.82F);
				}
			}
		}
		return 0xFF000000 | color;
	}

	private static int heightAt(final World world, final Chunk chunkHint, final int x, final int z) {
		final Chunk chunk = (x >> 4) == chunkHint.x && (z >> 4) == chunkHint.z
		                  ? chunkHint : world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
		return chunk == null ? -1 : chunk.getHeightValue(x & 15, z & 15);
	}

	private static int scaleColor(final int color, final float factor) {
		final int red   = Math.min(255, Math.max(0, (int) (((color >> 16) & 0xFF) * factor)));
		final int green = Math.min(255, Math.max(0, (int) (((color >> 8) & 0xFF) * factor)));
		final int blue  = Math.min(255, Math.max(0, (int) ((color & 0xFF) * factor)));
		return red << 16 | green << 8 | blue;
	}

	// draws the sampled window inside the given screen rectangle; caller handles scissor and overlays
	public void draw(final int screenLeft, final int screenTop, final int screenRight, final int screenBottom,
	                 final double centerX, final double centerZ, final double zoom) {
		if (originX == Integer.MIN_VALUE) {
			return;
		}
		if (texture == null) {
			texture = new DynamicTexture(GRID_SIZE, GRID_SIZE);
			isTextureDirty = true;
		}
		if (isTextureDirty) {
			System.arraycopy(colors, 0, texture.getTextureData(), 0, colors.length);
			texture.updateDynamicTexture();
			isTextureDirty = false;
		}
		// intersect the visible world rect with the sampled window, then map back to screen + UV
		final double screenCenterX = (screenLeft + screenRight) * 0.5D;
		final double screenCenterY = (screenTop + screenBottom) * 0.5D;
		final double worldLeft   = Math.max(originX, centerX + (screenLeft - screenCenterX) / zoom);
		final double worldRight  = Math.min(originX + WINDOW_BLOCKS, centerX + (screenRight - screenCenterX) / zoom);
		final double worldTop    = Math.max(originZ, centerZ + (screenTop - screenCenterY) / zoom);
		final double worldBottom = Math.min(originZ + WINDOW_BLOCKS, centerZ + (screenBottom - screenCenterY) / zoom);
		if (worldLeft >= worldRight || worldTop >= worldBottom) {
			return;
		}
		final double drawLeft   = screenCenterX + (worldLeft - centerX) * zoom;
		final double drawRight  = screenCenterX + (worldRight - centerX) * zoom;
		final double drawTop    = screenCenterY + (worldTop - centerZ) * zoom;
		final double drawBottom = screenCenterY + (worldBottom - centerZ) * zoom;
		final double uLeft   = (worldLeft - originX) / WINDOW_BLOCKS;
		final double uRight  = (worldRight - originX) / WINDOW_BLOCKS;
		final double vTop    = (worldTop - originZ) / WINDOW_BLOCKS;
		final double vBottom = (worldBottom - originZ) / WINDOW_BLOCKS;

		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
		                                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.bindTexture(texture.getGlTextureId());
		final Tessellator tessellator = Tessellator.getInstance();
		final BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
		buffer.pos(drawLeft , drawBottom, 0.0D).tex(uLeft , vBottom).endVertex();
		buffer.pos(drawRight, drawBottom, 0.0D).tex(uRight, vBottom).endVertex();
		buffer.pos(drawRight, drawTop   , 0.0D).tex(uRight, vTop   ).endVertex();
		buffer.pos(drawLeft , drawTop   , 0.0D).tex(uLeft , vTop   ).endVertex();
		tessellator.draw();
		GlStateManager.disableBlend();
	}

	public void release() {
		if (texture != null) {
			texture.deleteGlTexture();
			texture = null;
		}
	}
}
