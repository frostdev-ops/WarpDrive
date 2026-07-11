package cr0s.warpdrive;

import cr0s.warpdrive.api.IAirContainerItem;
import cr0s.warpdrive.api.IBreathingHelmet;
import cr0s.warpdrive.api.IBreathingProvider;
import cr0s.warpdrive.config.Dictionary;
import cr0s.warpdrive.config.WarpDriveConfig;
import cr0s.warpdrive.data.EnumTier;
import cr0s.warpdrive.data.StateAir;
import cr0s.warpdrive.data.VectorI;
import cr0s.warpdrive.event.ChunkHandler;
import cr0s.warpdrive.data.EnergyWrapper;
import cr0s.warpdrive.render.EntityCamera;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class BreathingManager {
	
	private static final int AIR_BLOCK_TICKS = 20;
	private static final int AIR_DROWN_TICKS = 20;
	private static final int AIR_FIRST_BREATH_TICKS = 300;
	
	private static final int AIR_IC2_COMPRESSED_AIR_TICKS = 300;
	
	private static final int AIR_ENERGY_FOR_ELECTROLYSE = 2000;
	
	private static final VectorI[] vAirOffsets = {
			new VectorI(0, 0, 0), new VectorI(0, 1, 0),
			new VectorI(0, 1, 1), new VectorI(0, 1, -1), new VectorI(1, 1, 0), new VectorI(1, 1, 0),
			new VectorI(0, 0, 1), new VectorI(0, 0, -1), new VectorI(1, 0, 0), new VectorI(1, 0, 0) };
	
	private static final HashMap<UUID, Integer> entity_airBlock = new HashMap<>();
	private static final HashMap<UUID, Integer> player_airTank = new HashMap<>();

	// compatibility hooks so other mods' life support (i.e. Galacticraft) is honored, see IBreathingProvider
	private static final List<IBreathingProvider> breathingProviders = new CopyOnWriteArrayList<>();

	public static void registerBreathingProvider(@Nonnull final IBreathingProvider breathingProvider) {
		if (!breathingProviders.contains(breathingProvider)) {
			breathingProviders.add(breathingProvider);
		}
	}

	private static boolean isProviderAirBlock(@Nonnull final Block block) {
		for (final IBreathingProvider breathingProvider : breathingProviders) {
			if (breathingProvider.isBreathableAirBlock(block)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isEntityInProviderZone(@Nonnull final EntityLivingBase entityLivingBase) {
		for (final IBreathingProvider breathingProvider : breathingProviders) {
			if (breathingProvider.isEntityInBreathableZone(entityLivingBase)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasAirBlock(final EntityLivingBase entityLivingBase, final int x, final int y, final int z) {
		final MutableBlockPos mutableBlockPos = new MutableBlockPos();
		for (final VectorI vOffset : vAirOffsets) {
			mutableBlockPos.setPos(x + vOffset.x, y + vOffset.y, z + vOffset.z);
			final Block block = entityLivingBase.world.getBlockState(mutableBlockPos).getBlock();
			if (isAirBlock(block)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAirBlock(@Nonnull final Block block) {
		return block == WarpDrive.blockAirSource
		    || block == WarpDrive.blockAirFlow;
	}

	// Returns true when the entity has breathable air around it.
	// Set includeProviders to false to restrict the check to WarpDrive's own air system.
	public static boolean isEntityInBreathableAir(@Nonnull final EntityLivingBase entityLivingBase,
	                                              final int x, final int y, final int z,
	                                              final boolean includeProviders) {
		final MutableBlockPos mutableBlockPos = new MutableBlockPos();
		for (final VectorI vOffset : vAirOffsets) {
			mutableBlockPos.setPos(x + vOffset.x, y + vOffset.y, z + vOffset.z);
			final Block block = entityLivingBase.world.getBlockState(mutableBlockPos).getBlock();
			if (isAirBlock(block)) {
				return true;
			}
			if ( includeProviders
			  && !breathingProviders.isEmpty()
			  && isProviderAirBlock(block) ) {
				return true;
			}
			if (block != Blocks.AIR) {
				final StateAir stateAir = ChunkHandler.getStateAir(entityLivingBase.world, mutableBlockPos.getX(), mutableBlockPos.getY(), mutableBlockPos.getZ());
				if ( stateAir == null
				  || stateAir.concentration > 0 ) {
					return true;
				}
			}
		}
		if ( includeProviders
		  && !breathingProviders.isEmpty() ) {
			return isEntityInProviderZone(entityLivingBase);
		}
		return false;
	}
	
	public static boolean onLivingJoinEvent(final EntityLivingBase entityLivingBase, final int x, final int y, final int z) {
		if ( entityLivingBase instanceof EntityCamera
		  && !entityLivingBase.getEntityWorld().isRemote ) {
			WarpDrive.logger.warn(String.format("EntityCamera is client-side only, deny spawning %s entityId '%s'",
			                                    Commons.format(entityLivingBase.world, x, y, z),
			                                    Dictionary.getId(entityLivingBase) ));
			return false;
		}
		
		// skip living entities who don't need air
		if (Dictionary.isLivingWithoutAir(entityLivingBase)) {
			return true;
		}
		
		if (hasAirBlock(entityLivingBase, x, y, z)) {
			return true;
		}
		if (hasValidSetup(entityLivingBase)) {
			return true;
		}
		// compatibility: other mods' breathable zones (i.e. Galacticraft sealed rooms & oxygen bubbles)
		if (!breathingProviders.isEmpty()) {
			try {
				if (isEntityInProviderZone(entityLivingBase)) {
					return true;
				}
			} catch (final Exception exception) {
				// chunks may be partially loaded during entity join: fail-safe to default behavior
				if (WarpDriveConfig.LOGGING_BREATHING) {
					WarpDrive.logger.error(String.format("Exception from breathing provider during entity join %s: %s",
					                                     Commons.format(entityLivingBase.world, x, y, z),
					                                     exception ));
				}
			}
		}

		if (WarpDriveConfig.LOGGING_BREATHING) {
			WarpDrive.logger.warn(String.format("Entity spawn denied %s entityId '%s'",
			                                    Commons.format(entityLivingBase.world, x, y, z),
			                                    Dictionary.getId(entityLivingBase) ));
		}
		return false;
	}
	
	public static void onLivingUpdateEvent(final EntityLivingBase entityLivingBase, final int x, final int y, final int z) {
		// skip living entities who don't need air
		if (Dictionary.isLivingWithoutAir(entityLivingBase)) {
			return;
		}
		
		// find an air block, including other mods' breathable air
		final UUID uuidEntity = entityLivingBase.getUniqueID();
		final boolean notInVacuum = isEntityInBreathableAir(entityLivingBase, x, y, z, true);
		
		Integer air = entity_airBlock.get(uuidEntity);
		if (notInVacuum) {// no atmosphere with air blocks
			if (air == null) {
				entity_airBlock.put(uuidEntity, AIR_BLOCK_TICKS);
			} else if (air <= 1) {// time elapsed => consume air block
				entity_airBlock.put(uuidEntity, AIR_BLOCK_TICKS);
			} else {
				entity_airBlock.put(uuidEntity, air - 1);
			}
			
		} else {// no atmosphere without air blocks
			// add grace period on first breath
			if (air == null) {
				entity_airBlock.put(uuidEntity, AIR_FIRST_BREATH_TICKS);
				return;
			}
			// players have a grace period when exiting breathable area
			// others just finish air from blocks
			if (air > 0) {
				if (entityLivingBase instanceof EntityPlayerMP) {
					entity_airBlock.put(uuidEntity, 0);
					player_airTank.put(uuidEntity, AIR_FIRST_BREATH_TICKS);
					return;
				} else {
					entity_airBlock.put(uuidEntity, air - 1);
					return;
				} 
			}
			
			// damage entity if in vacuum without protection
			final boolean hasValidSetup = hasValidSetup(entityLivingBase);
			if (entityLivingBase instanceof EntityPlayerMP) {
				final EntityPlayerMP player = (EntityPlayerMP) entityLivingBase;
				air = player_airTank.get(uuidEntity);
				
				boolean hasHelmet = hasValidSetup;
				if (hasValidSetup) {
					if (air == null) {// new player in space => grace period
						player_airTank.put(uuidEntity, AIR_FIRST_BREATH_TICKS);
					} else if (air <= 1) {
						final int ticksAir = consumeAir(player);
						if (ticksAir > 0) {
							player_airTank.put(uuidEntity, ticksAir);
						} else {
							hasHelmet = false;
						}
					} else {
						player_airTank.put(uuidEntity, air - 1);
					}
				}
				
				if (!hasHelmet) {
					if (air == null) {// new player in space => grace period
						player_airTank.put(uuidEntity, AIR_FIRST_BREATH_TICKS);
					} else if (air <= 1) {
						player_airTank.put(uuidEntity, AIR_DROWN_TICKS);
						entityLivingBase.attackEntityFrom(WarpDrive.damageAsphyxia, 2.0F);
					} else {
						player_airTank.put(uuidEntity, air - 1);
					}
				}
				
			} else {// (in space, no air block and not a player)
				if (hasValidSetup) {
					// let it live for now, checking periodically if helmet gets broken in combat
					entity_airBlock.put(uuidEntity, AIR_FIRST_BREATH_TICKS);
				} else {
					entity_airBlock.put(uuidEntity, 0);
					entityLivingBase.attackEntityFrom(WarpDrive.damageAsphyxia, 2.0F);
				}
			}
		}
	}
	
	private static int consumeAir(final EntityLivingBase entityLivingBase) {
		if (WarpDriveConfig.LOGGING_BREATHING) {
			WarpDrive.logger.info("Checking inventory for air reserves...");
		}
		if (!(entityLivingBase instanceof EntityPlayerMP)) {
			return 0;
		}
		
		final EntityPlayerMP entityPlayer = (EntityPlayerMP) entityLivingBase;
		final NonNullList<ItemStack> playerInventory = entityPlayer.inventory.mainInventory;
		int slotAirCanisterFound = -1;
		float fillingRatioAirCanisterFound = 0.0F;
		
		// find most consumed air canister with smallest stack
		for (int slotIndex = 0; slotIndex < playerInventory.size(); slotIndex++) {
			final ItemStack itemStack = playerInventory.get(slotIndex);
			if ( itemStack != ItemStack.EMPTY
			  && itemStack.getCount() > 0
			  && itemStack.getItem() instanceof IAirContainerItem) {
				final IAirContainerItem airContainerItem = (IAirContainerItem) itemStack.getItem();
				final int airAvailable = airContainerItem.getCurrentAirStorage(itemStack);
				if (airAvailable > 0) {
					float fillingRatio = airAvailable / (float) airContainerItem.getMaxAirStorage(itemStack);
					fillingRatio -= itemStack.getCount() / 1000.0F;
					if (fillingRatioAirCanisterFound <= 0.0F || fillingRatio < fillingRatioAirCanisterFound) {
						slotAirCanisterFound = slotIndex;
						fillingRatioAirCanisterFound = fillingRatio;
					}
				}
			}
		}
		// consume air on the selected Air canister
		if (slotAirCanisterFound >= 0) {
			final ItemStack itemStack = playerInventory.get(slotAirCanisterFound);
			if ( !itemStack.isEmpty()
			  && itemStack.getItem() instanceof IAirContainerItem ) {
				final IAirContainerItem airContainerItem = (IAirContainerItem) itemStack.getItem();
				final int airAvailable = airContainerItem.getCurrentAirStorage(itemStack);
				if (airAvailable > 0) {
					if (itemStack.getCount() > 1) {// unstack
						itemStack.shrink(1);
						ItemStack itemStackToAdd = itemStack.copy();
						itemStackToAdd.setCount(1);
						itemStackToAdd = airContainerItem.consumeAir(itemStackToAdd);
						if (!entityPlayer.inventory.addItemStackToInventory(itemStackToAdd)) {
							final EntityItem entityItem = new EntityItem(entityPlayer.world, entityPlayer.posX, entityPlayer.posY, entityPlayer.posZ, itemStackToAdd);
							entityPlayer.world.spawnEntity(entityItem);
						}
						entityPlayer.sendContainerToPlayer(entityPlayer.inventoryContainer);
					} else {
						final ItemStack itemStackNew = airContainerItem.consumeAir(itemStack);
						if (itemStack != itemStackNew) {
							playerInventory.set(slotAirCanisterFound, itemStackNew);
						}
					}
					return airContainerItem.getAirTicksPerConsumption(itemStack);
				}
			}
		}
		
		// (no WarpDrive air canister or all empty)
		// check IC2 compressed air cells
		if (WarpDriveConfig.IC2_compressedAir != null) {
			for (int slotIndex = 0; slotIndex < playerInventory.size(); ++slotIndex) {
				final ItemStack itemStack = playerInventory.get(slotIndex);
				if ( !itemStack.isEmpty()
				  && WarpDriveConfig.isIC2CompressedAir(itemStack) ) {
					itemStack.shrink(1);
					playerInventory.set(slotIndex, itemStack);
					
					if (WarpDriveConfig.IC2_emptyCell != null) {
						final ItemStack emptyCell = new ItemStack(WarpDriveConfig.IC2_emptyCell.getItem(), 1, 0);
						if (!entityPlayer.inventory.addItemStackToInventory(emptyCell)) {
							final World world = entityPlayer.world;
							final EntityItem entityItem = new EntityItem(world, entityPlayer.posX, entityPlayer.posY, entityPlayer.posZ, emptyCell);
							entityPlayer.world.spawnEntity(entityItem);
						}
						entityPlayer.sendContainerToPlayer(entityPlayer.inventoryContainer);
					}
					return AIR_IC2_COMPRESSED_AIR_TICKS;
				}
			}
		}
		
		// all air containers are empty
		final ItemStack itemStackChestplate = entityLivingBase.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
		if (!itemStackChestplate.isEmpty()) {
			final Item itemChestplate = itemStackChestplate.getItem();
			if (itemChestplate == WarpDrive.itemWarpArmor[EnumTier.SUPERIOR.getIndex()][2]) {
				final int ticksAir = electrolyseIceToAir(entityLivingBase);
				if (ticksAir > 0) {
					return ticksAir;
				}
			}
		}

		// compatibility: other mods' air supplies (i.e. Galacticraft oxygen tanks)
		for (final IBreathingProvider breathingProvider : breathingProviders) {
			final int ticksAir = breathingProvider.consumeAir(entityPlayer);
			if (ticksAir > 0) {
				return ticksAir;
			}
		}
		return 0;
	}
	
	public static boolean hasValidSetup(@Nonnull final EntityLivingBase entityLivingBase) {
		if (hasValidSetupWarpDrive(entityLivingBase)) {
			return true;
		}
		// compatibility: other mods' breathing equipment (i.e. Galacticraft oxygen gear)
		for (final IBreathingProvider breathingProvider : breathingProviders) {
			if (breathingProvider.hasValidSetup(entityLivingBase)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasValidSetupWarpDrive(@Nonnull final EntityLivingBase entityLivingBase) {
		final ItemStack itemStackHelmet = entityLivingBase.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
		if (entityLivingBase instanceof EntityPlayer) {
			final ItemStack itemStackChestplate = entityLivingBase.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
			final ItemStack itemStackLeggings = entityLivingBase.getItemStackFromSlot(EntityEquipmentSlot.LEGS);
			final ItemStack itemStackBoots = entityLivingBase.getItemStackFromSlot(EntityEquipmentSlot.FEET);
			// need full armor set to breath
			if ( !itemStackHelmet.isEmpty()
			  && !itemStackChestplate.isEmpty()
			  && !itemStackLeggings.isEmpty()
			  && !itemStackBoots.isEmpty() ) {
				// need a working breathing helmet to breath
				final Item itemHelmet = itemStackHelmet.getItem();
				return (itemHelmet instanceof IBreathingHelmet && ((IBreathingHelmet) itemHelmet).canBreath(entityLivingBase))
				    || Dictionary.ITEMS_BREATHING_HELMET.contains(itemHelmet);
			}

		} else {
			// need just a working breathing helmet to breath
			if (!itemStackHelmet.isEmpty()) {
				final Item itemHelmet = itemStackHelmet.getItem();
				return (itemHelmet instanceof IBreathingHelmet && ((IBreathingHelmet) itemHelmet).canBreath(entityLivingBase))
				    || Dictionary.ITEMS_BREATHING_HELMET.contains(itemHelmet);
			}
		}
		return false;
	}

	// Returns true when WarpDrive's own systems are keeping that entity alive: nearby air blocks,
	// remaining air credits from a previous breath, or a valid WarpDrive setup with air reserves.
	// Other mods' protections are deliberately excluded: this is used to answer "can WarpDrive
	// justify cancelling another mod's suffocation damage" (i.e. Galacticraft's), and that mod
	// already knows about its own protections.
	public static boolean isProtectedByWarpDrive(@Nonnull final EntityLivingBase entityLivingBase) {
		if (Dictionary.isLivingWithoutAir(entityLivingBase)) {
			return true;
		}

		final int x = MathHelper.floor(entityLivingBase.posX);
		final int y = MathHelper.floor(entityLivingBase.posY);
		final int z = MathHelper.floor(entityLivingBase.posZ);
		if (isEntityInBreathableAir(entityLivingBase, x, y, z, false)) {
			return true;
		}

		// remaining air credits grant a grace period, matching our own asphyxia timing
		final UUID uuidEntity = entityLivingBase.getUniqueID();
		final Integer airBlockTicks = entity_airBlock.get(uuidEntity);
		if (airBlockTicks != null && airBlockTicks > 0) {
			return true;
		}

		if (entityLivingBase instanceof EntityPlayer) {
			final Integer airTankTicks = player_airTank.get(uuidEntity);
			if (airTankTicks != null && airTankTicks > 0) {
				return true;
			}
			return hasValidSetupWarpDrive(entityLivingBase)
			    && getAirReserveRatio((EntityPlayer) entityLivingBase) > 0.0F;
		}
		return hasValidSetupWarpDrive(entityLivingBase);
	}
	
	public static float getAirReserveRatio(@Nonnull final EntityPlayer entityPlayer) {
		final NonNullList<ItemStack> playerInventory = entityPlayer.inventory.mainInventory;
		
		// check electrolysing
		boolean canElectrolyse = false;
		final ItemStack itemStackChestplate = entityPlayer.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
		if (!itemStackChestplate.isEmpty()) {
			final Item itemChestplate = itemStackChestplate.getItem();
			if (itemChestplate == WarpDrive.itemWarpArmor[EnumTier.SUPERIOR.getIndex()][2]) {
				canElectrolyse = true;
			}
		}
		
		// check all inventory slots for air containers, etc.
		final Item itemIce = Item.getItemFromBlock(Blocks.ICE);
		int sumAirCapacityTicks = 0;
		int sumAirStoredTicks = 0;
		int countAirContainer = 0;
		int countIce = 0;
		int countEnergy = 0;
		ItemStack itemStackAirContainer = null;
		for (final ItemStack itemStack : playerInventory) {
			if (!itemStack.isEmpty()) {
				if (itemStack.getItem() instanceof IAirContainerItem) {
					countAirContainer++;
					itemStackAirContainer = itemStack;
					final IAirContainerItem airContainerItem = (IAirContainerItem) itemStack.getItem();
					final int airAvailable = airContainerItem.getCurrentAirStorage(itemStack);
					if (airAvailable > 0) {
						sumAirStoredTicks += airAvailable * airContainerItem.getAirTicksPerConsumption(itemStack);
					}
					final int airCapacity = airContainerItem.getMaxAirStorage(itemStack);
					sumAirCapacityTicks += airCapacity * airContainerItem.getAirTicksPerConsumption(itemStack);
					
				} else if ( WarpDriveConfig.IC2_compressedAir != null
				         && WarpDriveConfig.isIC2CompressedAir(itemStack) ) {
					sumAirStoredTicks += AIR_IC2_COMPRESSED_AIR_TICKS * itemStack.getCount();
					sumAirCapacityTicks += AIR_IC2_COMPRESSED_AIR_TICKS * itemStack.getCount();
					
				} else if ( WarpDriveConfig.IC2_emptyCell != null
				         && itemStack.isItemEqual(WarpDriveConfig.IC2_emptyCell) ) {
					sumAirCapacityTicks += AIR_IC2_COMPRESSED_AIR_TICKS * itemStack.getCount();
					
				} else if (canElectrolyse) {
					if (itemStack.getItem() == itemIce) {
						countIce += itemStack.getCount();
					} else if ( EnergyWrapper.isEnergyContainer(itemStack)
					         && EnergyWrapper.canOutput(itemStack)
					         && EnergyWrapper.getEnergyStored(itemStack) >= AIR_ENERGY_FOR_ELECTROLYSE ) {
						countEnergy += EnergyWrapper.getEnergyStored(itemStack) / AIR_ENERGY_FOR_ELECTROLYSE;
					}
				}
			}
		}
		
		// add electrolyse bonus
		if (countAirContainer >= 1 && countIce > 0 && countEnergy > 0 && itemStackAirContainer.getItem() instanceof IAirContainerItem) {
			final IAirContainerItem airContainerItem = (IAirContainerItem) itemStackAirContainer.getItem();
			final int sumElectrolyseTicks =
					  Math.min(2, countAirContainer)       // up to 2 containers refilled
			        * Math.min(countIce, countEnergy)      // requiring both ice and energy
			        * airContainerItem.getMaxAirStorage(itemStackAirContainer)
			        * airContainerItem.getAirTicksPerConsumption(itemStackAirContainer);
			sumAirStoredTicks += sumElectrolyseTicks;
			sumAirCapacityTicks += sumElectrolyseTicks;
		}
		
		return sumAirCapacityTicks > 0 ? sumAirStoredTicks / (float) sumAirCapacityTicks : 0.0F;
	}
	
	private static int electrolyseIceToAir(final Entity entity) {
		if (WarpDriveConfig.LOGGING_BREATHING) {
			WarpDrive.logger.info("Checking inventory for ice electrolysing...");
		}
		if (!(entity instanceof EntityPlayerMP)) {
			return 0;
		}
		final EntityPlayerMP entityPlayer = (EntityPlayerMP) entity;
		final NonNullList<ItemStack> playerInventory = entityPlayer.inventory.mainInventory;
		int slotIceFound = -1;
		int slotFirstEmptyAirContainerFound = -1;
		int slotSecondEmptyAirContainerFound = -1;
		int slotEnergyContainer = -1;
		
		// find 1 ice, 1 energy and up to 2 empty air containers
		final Item itemIce = Item.getItemFromBlock(Blocks.ICE);
		for (int slotIndex = 0; slotIndex < playerInventory.size(); slotIndex++) {
			final ItemStack itemStack = playerInventory.get(slotIndex);
			if (itemStack.isEmpty()) {
				continue;
			}
			
			if (itemStack.getItem() == itemIce) {
				slotIceFound = slotIndex;
				if ( slotSecondEmptyAirContainerFound >= 0
				  && slotEnergyContainer >= 0 ) {
					break;
				}
				
			} else if ( itemStack.getCount() == 1
			         && itemStack.getItem() instanceof IAirContainerItem ) {
				final IAirContainerItem airCanister = (IAirContainerItem) itemStack.getItem();
				if ( airCanister.canContainAir(itemStack)
				  && airCanister.getCurrentAirStorage(itemStack) >= 0 ) {
					if (slotFirstEmptyAirContainerFound < 0) {
						slotFirstEmptyAirContainerFound = slotIndex;
					} else if (slotSecondEmptyAirContainerFound < 0) {
						slotSecondEmptyAirContainerFound = slotIndex;
						if ( slotIceFound >= 0
						  && slotEnergyContainer >= 0 ) {
							break;
						}
					}
				}
			} else if ( slotEnergyContainer < 0
			         && EnergyWrapper.isEnergyContainer(itemStack)
			         && EnergyWrapper.canOutput(itemStack)
			         && EnergyWrapper.getEnergyStored(itemStack) >= AIR_ENERGY_FOR_ELECTROLYSE ) {
				slotEnergyContainer = slotIndex;
				if ( slotIceFound >= 0
				  && slotSecondEmptyAirContainerFound >= 0 ) {
					break;
				}
			}
		}
		
		// skip if we're missing energy, ice or air container
		if ( slotEnergyContainer < 0
		  || slotIceFound < 0
		  || slotFirstEmptyAirContainerFound < 0 ) {
			return 0;
		}
		
		// consume energy
		final ItemStack itemStackEnergyContainer = playerInventory.get(slotEnergyContainer);
		final int energyProvided = EnergyWrapper.consume(itemStackEnergyContainer, AIR_ENERGY_FOR_ELECTROLYSE, false);
		if (energyProvided <= 0) {
			return 0;
		}
		
		// consume ice
		final ItemStack itemStackIce = playerInventory.get(slotIceFound);
		itemStackIce.shrink(1);
		playerInventory.set(slotIceFound, itemStackIce);
		
		// fill air canister(s)
		ItemStack itemStackAirCanister = playerInventory.get(slotFirstEmptyAirContainerFound);
		IAirContainerItem airCanister = (IAirContainerItem) itemStackAirCanister.getItem();
		playerInventory.set(slotFirstEmptyAirContainerFound, airCanister.getFullAirContainer(itemStackAirCanister));
		
		if (slotSecondEmptyAirContainerFound >= 0) {
			itemStackAirCanister = playerInventory.get(slotSecondEmptyAirContainerFound);
			airCanister = (IAirContainerItem) itemStackAirCanister.getItem();
			playerInventory.set(slotSecondEmptyAirContainerFound, airCanister.getFullAirContainer(itemStackAirCanister));
		}
		entityPlayer.sendContainerToPlayer(entityPlayer.inventoryContainer);
		
		// first air breath is free
		return airCanister.getAirTicksPerConsumption(itemStackAirCanister);
	}
	
	public static void onEntityLivingDeath(@Nonnull final EntityLivingBase entityLivingBase) {
		entity_airBlock.remove(entityLivingBase.getUniqueID());
		for (final IBreathingProvider breathingProvider : breathingProviders) {
			breathingProvider.onEntityLivingDeath(entityLivingBase);
		}
	}
}
