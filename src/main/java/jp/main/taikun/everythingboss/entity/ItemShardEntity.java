package jp.main.taikun.everythingboss.entity;

import jp.main.taikun.everythingboss.ModEntities;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** ボスが撃ち出すアイテムの複製。見た目はボスのアイテムそのもの */
public class ItemShardEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(ItemShardEntity.class, EntityDataSerializers.FLOAT);
    private static final byte EVENT_SHATTER = 3;
    private static final int MAX_LIFE = 200;

    private float damage = 4.0F;
    private boolean falling;

    public ItemShardEntity(EntityType<? extends ItemShardEntity> type, Level level) {
        super(type, level);
    }

    public ItemShardEntity(Level level, LivingEntity owner, ItemStack stack, float damage, boolean falling, float scale) {
        super(ModEntities.ITEM_SHARD.get(), owner, level);
        setItem(stack.copyWithCount(1));
        this.damage = damage;
        this.falling = falling;
        this.entityData.set(DATA_SCALE, scale);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SCALE, 1.0F);
    }

    public float getScale() {
        return this.entityData.get(DATA_SCALE);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.STICK;
    }

    @Override
    protected float getGravity() {
        return this.falling ? 0.05F : 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && this.tickCount > MAX_LIFE) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof ItemBossEntity) && !(entity instanceof ItemShardEntity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity owner = getOwner();
        result.getEntity().hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity living ? living : null), this.damage);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, EVENT_SHATTER);
            discard();
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SHATTER) {
            ItemStack stack = getItem();
            if (stack.isEmpty()) return;
            ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, stack);
            for (int i = 0; i < 10; i++) {
                level().addParticle(particle, getX(), getY(), getZ(),
                        (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.3);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ShardDamage", this.damage);
        tag.putBoolean("Falling", this.falling);
        tag.putFloat("Scale", getScale());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("ShardDamage");
        this.falling = tag.getBoolean("Falling");
        if (tag.contains("Scale")) this.entityData.set(DATA_SCALE, tag.getFloat("Scale"));
    }
}
