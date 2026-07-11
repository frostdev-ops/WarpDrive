package cr0s.warpdrive.api;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Extension point for other mods' life support systems to be honored by WarpDrive's breathing logic.
 * Implementations are registered through BreathingManager.registerBreathingProvider() and are only
 * consulted after WarpDrive's own checks failed, so a provider can't degrade vanilla behavior.
 */
public interface IBreathingProvider {

	// Cheap per-block test, called inside the block scan around an entity.
	// Return true if this block provides breathable air (e.g. Galacticraft's sealed room air).
	boolean isBreathableAirBlock(final Block block);

	// Heavier area test (e.g. oxygen distributor bubbles), called only when the block scan failed.
	// Runs every tick per entity in vacuum: implementations shall throttle/cache internally.
	boolean isEntityInBreathableZone(final EntityLivingBase entityLivingBase);

	// Return true when the entity is wearing a valid breathing equipment according to that mod.
	// Like WarpDrive's own setup check, this validates equipment shape, not remaining air supply.
	boolean hasValidSetup(final EntityLivingBase entityLivingBase);

	// Consume one unit of air supply from that mod's equipment.
	// Return the number of air ticks granted, 0 when there's nothing left to consume.
	int consumeAir(final EntityPlayerMP entityPlayerMP);

	// Notification for internal cache cleanup.
	void onEntityLivingDeath(final EntityLivingBase entityLivingBase);
}
