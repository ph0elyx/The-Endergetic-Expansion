package endergeticexpansion.common.entities;

import java.util.Collection;
import java.util.Optional;

import javax.annotation.Nullable;

import endergeticexpansion.api.endimator.EndimatedEntity;
import endergeticexpansion.api.endimator.EndimatorEntityModel;
import endergeticexpansion.client.model.puffbug.ModelPuffBugDeflated;
import endergeticexpansion.client.model.puffbug.ModelPuffBugInflated;
import endergeticexpansion.client.model.puffbug.ModelPuffBugInflatedMedium;
import endergeticexpansion.common.tileentities.TileEntityPuffBugHive;
import endergeticexpansion.core.registry.EEEntities;
import endergeticexpansion.core.registry.EEItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class EntityPuffBug extends EndimatedEntity {
	private static final DataParameter<Optional<BlockPos>> HIVE_POS = EntityDataManager.createKey(EntityPuffBug.class, DataSerializers.OPTIONAL_BLOCK_POS);
	private static final DataParameter<Integer> COLOR = EntityDataManager.createKey(EntityPuffBug.class, DataSerializers.VARINT);
	private static final DataParameter<Integer> PUFF_STATE = EntityDataManager.createKey(EntityPuffBug.class, DataSerializers.VARINT);
	private static final DataParameter<Boolean> FROM_BOTTLE = EntityDataManager.createKey(EntityPuffBug.class, DataSerializers.BOOLEAN);
	
	public EntityPuffBug(EntityType<? extends EntityPuffBug> type, World worldIn) {
		super(type, worldIn);
		this.experienceValue = 5;
	}
	
	public EntityPuffBug(World worldIn, BlockPos pos) {
		this(EEEntities.PUFF_BUG.get(), worldIn);
		this.setHivePos(pos);
	}
	
	public EntityPuffBug(World worldIn, double x, double y, double z) {
		this(EEEntities.PUFF_BUG.get(), worldIn);
		this.setPosition(x, y, z);
	}
	
	@Override
	protected void registerAttributes() {
		super.registerAttributes();
		this.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(8.0D);
	}
	
	@Override
	protected void registerData() {
		super.registerData();
		this.getDataManager().register(HIVE_POS, Optional.empty());
		this.getDataManager().register(COLOR, -1);
		this.getDataManager().register(PUFF_STATE, 1);
		this.getDataManager().register(FROM_BOTTLE, false);
	}
	
	@Override
	public void readAdditional(CompoundNBT compound) {
		super.readAdditional(compound);
		
		this.setFromBottle(compound.getBoolean("FromBottle"));
		
		if(compound.contains("PuffState")) {
			this.setPuffState(PuffState.getPuffStateById(compound.getInt("PuffState")));
		}
		
		if(compound.contains("HivePos", 10)) {
			this.setHivePos(NBTUtil.readBlockPos(compound.getCompound("HivePos")));
		}
	}
	
	@Override
	public void writeAdditional(CompoundNBT compound) {
		super.writeAdditional(compound);
		
		compound.putBoolean("FromBottle", this.isFromBottle());
		
		if(compound.contains("PuffState")) {
			compound.putInt("PuffState", this.getPuffStateId());
		}
		
		if(this.getHivePos() != null) {
			compound.put("HivePos", NBTUtil.writeBlockPos(this.getHivePos()));
		}
	}
	
	@Nullable
	public BlockPos getHivePos() {
		return this.getDataManager().get(HIVE_POS).orElse(null);
	}
	
	public void setHivePos(@Nullable BlockPos pos) {
		this.getDataManager().set(HIVE_POS, Optional.ofNullable(pos));
	}
	
	public boolean isHiveAtPos(@Nullable BlockPos pos) {
		if(pos == null) {
			return false;
		}
		return this.world.getTileEntity(pos).getTileEntity() instanceof TileEntityPuffBugHive;
	}
	
	@Nullable
	private TileEntity getHive() {
		BlockPos hivePos = this.getHivePos();
		if(this.isHiveAtPos(hivePos)) {
			return this.world.getTileEntity(hivePos).getTileEntity();
		}
		return null;
	}
	
	public PuffState getPuffState() {
		return PuffState.getPuffStateById(this.getPuffStateId());
	}
	
	public int getPuffStateId() {
		return this.getDataManager().get(PUFF_STATE);
	}
	
	public void setPuffState(PuffState state) {
		this.getDataManager().set(PUFF_STATE, state.getStateId());
	}
	
	public boolean isFromBottle() {
        return this.getDataManager().get(FROM_BOTTLE);
    }

    public void setFromBottle(boolean value) {
        this.getDataManager().set(FROM_BOTTLE, value);
    }
	
	public int getColor() {
		return this.dataManager.get(COLOR);
	}
	
	public void setColor(int color) {
		this.dataManager.set(COLOR, color);
	}
	
	@Override
	public void tick() {
		super.tick();
		
		if(this.getHive() == null && this.getHivePos() != null) {
			this.setHivePos(null);
		}
	}
	
	protected void setBottleData(ItemStack bottle) {
		if(this.hasCustomName()) {
			bottle.setDisplayName(this.getCustomName());
		}
		
		CompoundNBT nbt = bottle.getOrCreateTag();
		
		if(this.getColor() != -1) {
			nbt.putInt("ColorTag", this.getColor());
		}
		
		if(!this.getActivePotionEffects().isEmpty()) {
			ListNBT listnbt = new ListNBT();

			for(EffectInstance effectinstance : this.getActivePotionEffects()) {
				listnbt.add(effectinstance.write(new CompoundNBT()));
			}

			nbt.put("CustomPotionEffects", listnbt);
		}
    }
	
	@Override
	protected void updatePotionMetadata() {
		super.updatePotionMetadata();
		Collection<EffectInstance> effects = this.getActivePotionEffects();
		
		if(!effects.isEmpty()) {
			this.setColor(PotionUtils.getPotionColorFromEffectList(effects));
		} else {
			this.setColor(-1);
		}
	}
	
	@Override
	public ItemStack getPickedResult(RayTraceResult target) {
		return new ItemStack(EEItems.PUFF_BUG_SPAWN_EGG.get());
	}
	
	@Override
	protected boolean processInteract(PlayerEntity player, Hand hand) {
		ItemStack itemstack = player.getHeldItem(hand);
        if(itemstack.getItem() == Items.GLASS_BOTTLE && this.isAlive()) {
        	this.playSound(SoundEvents.ITEM_BOTTLE_FILL_DRAGONBREATH, 1.0F, 1.0F);
        	itemstack.shrink(1);
        	ItemStack bottle = new ItemStack(EEItems.PUFFBUG_BOTTLE.get());
        	this.setBottleData(bottle);
        	
        	if(itemstack.isEmpty()) {
        		player.setHeldItem(hand, bottle);
        	} else if(!player.inventory.addItemStackToInventory(bottle)) {
        		player.dropItem(bottle, false);
        	}

            this.remove();
            return true;
        } else {
			return super.processInteract(player, hand);
		}
	}
	
	@Override
	public ILivingEntityData onInitialSpawn(IWorld worldIn, DifficultyInstance difficultyIn, SpawnReason reason, ILivingEntityData spawnDataIn, CompoundNBT dataTag) {
		if(dataTag != null && dataTag.contains("ColorTag", 3)) {
			this.setColor(dataTag.getInt("ColorTag"));
			
			if(dataTag.contains("CustomPotionEffects")) {
				for(EffectInstance effectinstance : PotionUtils.getFullEffectsFromTag(dataTag)) {
					this.addPotionEffect(effectinstance);
				}
			}
		}
		return super.onInitialSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
	}
	
	@Override
	public boolean canDespawn(double distanceToClosestPlayer) {
		return !this.isFromBottle() && !this.hasCustomName();
	}

    @Override
    public boolean preventDespawn() {
    	return this.isFromBottle();
    }
	
	@Override
	public boolean isOnLadder() {
		return false;
	}
	
	public static enum PuffState {
		DEFLATED(0),
		MEDIUM_INFLATION(1),
		FULL_INFLATION(2);
		
		private final int stateId;
		
		PuffState(int stateId) {
			this.stateId = stateId;
		}
		
		public int getStateId() {
			return this.stateId;
		}
		
		public static PuffState getPuffStateById(int stateId) {
			for(PuffState states : values()) {
				if(states.getStateId() == stateId) {
					return states;
				}
			}
			return PuffState.MEDIUM_INFLATION;
		}
		
		@OnlyIn(Dist.CLIENT)
		public static EndimatorEntityModel<EntityPuffBug> getModel(PuffState state) {
			final ModelPuffBugDeflated<EntityPuffBug> DEFLATED_MODEL = new ModelPuffBugDeflated<>();
			final ModelPuffBugInflatedMedium<EntityPuffBug> MEDIUM_INFLATED_MODEL = new ModelPuffBugInflatedMedium<>();
			final ModelPuffBugInflated<EntityPuffBug> INFLATED_MODEL = new ModelPuffBugInflated<>();
			
			switch(state) {
				default:
				case MEDIUM_INFLATION:
					return MEDIUM_INFLATED_MODEL;
				case DEFLATED:
					return DEFLATED_MODEL;
				case FULL_INFLATION:
					return INFLATED_MODEL;
			}
		}
	}
}