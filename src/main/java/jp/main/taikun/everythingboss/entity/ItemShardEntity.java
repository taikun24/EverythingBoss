package jp.main.taikun.everythingboss.entity;

import jp.main.taikun.everythingboss.ModEntities;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** ボスが撃ち出すアイテムの複製。見た目はボスのアイテムそのもの */
public class ItemShardEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(ItemShardEntity.class, EntityDataSerializers.FLOAT);
    private static final byte EVENT_SHATTER = 3;
    private static final int MAX_LIFE = 200;
    /**
     * 当たり判定の半径 (見た目の大きさに対する比)。
     * バニラの投射物は「足元からの移動線分」と「相手の箱 + 0.3」の交差でしか判定しないため、
     * 大きなアイテムが体をかすめても当たらない。見た目に合わせてここで広げる
     */
    private static final double HIT_RADIUS_PER_SCALE = 0.8;
    /** 爆ぜる弾 (爆発物のアイテム) の爆風の半径 */
    private static final double BLAST_RADIUS = 2.5;

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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SCALE, 1.0F);
    }

    public float getScale() {
        return this.entityData.get(DATA_SCALE);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.STICK;
    }

    @Override
    protected double getDefaultGravity() {
        return this.falling ? 0.05 : 0.0;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && sweepHit()) return;
        super.tick();
        if (!level().isClientSide && this.tickCount > MAX_LIFE) {
            discard();
        }
    }

    /** 弾の中心からこの tick の移動先までを、半径付きで掃いて当たる相手を探す */
    private boolean sweepHit() {
        double radius = HIT_RADIUS_PER_SCALE * getScale();
        Vec3 from = position().add(0, getBbHeight() / 2, 0);
        Vec3 to = from.add(getDeltaMovement());
        // 壁の手前までにする。半径を広げた分、壁の向こうの相手に当たってしまうため
        HitResult wall = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : level().getEntities(this, new AABB(from, to).inflate(radius + 1.0), this::canHitEntity)) {
            AABB box = e.getBoundingBox().inflate(radius);
            double d = box.contains(from) ? 0 : box.clip(from, to).map(from::distanceToSqr).orElse(Double.MAX_VALUE);
            if (d < best && canSee(from, e)) {
                best = d;
                nearest = e;
            }
        }
        if (nearest == null) return false;
        onHit(new EntityHitResult(nearest));
        return isRemoved();
    }

    /** 弾の中心から相手の胴か目が見えるか */
    private boolean canSee(Vec3 from, Entity e) {
        return isClear(from, e.getBoundingBox().getCenter()) || isClear(from, e.getEyePosition());
    }

    private boolean isClear(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof ItemBossEntity) && !(entity instanceof ItemShardEntity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity owner = getOwner();
        Entity target = result.getEntity();
        if (target.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity living ? living : null), this.damage)
                && owner instanceof ItemBossEntity boss && target instanceof LivingEntity victim) {
            boss.afterHit(victim);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            if (getOwner() instanceof ItemBossEntity boss && boss.traits().has(BossTrait.EXPLOSIVE)) {
                blast(boss, result instanceof EntityHitResult hit ? hit.getEntity() : null);
            }
            level().broadcastEntityEvent(this, EVENT_SHATTER);
            discard();
        }
    }

    /**
     * 爆ぜる弾。本物の爆発は地面に落ちているアイテム (倒れたプレイヤーの持ち物など) まで消すので使わず、
     * まわりの生き物にだけダメージと吹き飛ばしを与える。直撃した相手は除く (直撃のダメージが入っている)
     */
    private void blast(ItemBossEntity boss, @Nullable Entity direct) {
        Vec3 c = position().add(0, getBbHeight() / 2, 0);
        double radius = BLAST_RADIUS * Math.max(1.0F, getScale());
        DamageSource source = damageSources().explosion(this, boss);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
                e -> e != direct && canHitEntity(e))) {
            double d = Math.sqrt(e.getBoundingBox().distanceToSqr(c));
            if (d > radius || !isClear(c, e.getBoundingBox().getCenter())) continue;
            float falloff = (float) (1.0 - 0.5 * d / radius);
            if (e.hurt(source, this.damage * 0.8F * falloff)) {
                boss.afterHit(e);
                e.knockback(0.6 * falloff, c.x - e.getX(), c.z - e.getZ());
            }
        }
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.3F + this.random.nextFloat() * 0.3F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
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
