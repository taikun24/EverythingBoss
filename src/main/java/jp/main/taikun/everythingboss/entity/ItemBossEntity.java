package jp.main.taikun.everythingboss.entity;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import jp.main.taikun.everythingboss.Config;
import jp.main.taikun.everythingboss.EverythingBoss;
import jp.main.taikun.everythingboss.altar.AltarTier;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 任意のアイテム (NBT 込み) を本体とするボス。
 * <p>
 * 攻撃の状態は {@link #DATA_ATTACK} だけを同期し、経過 tick ({@link #attackTick}) は
 * 両側で独立に数える (値が変わった瞬間に両側で 0 に戻す)。描画はこの 2 つから組み立てる。
 */
public class ItemBossEntity extends Monster {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(ItemBossEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_ATTACK =
            SynchedEntityData.defineId(ItemBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED =
            SynchedEntityData.defineId(ItemBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_BEAM_YAW =
            SynchedEntityData.defineId(ItemBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_PITCH =
            SynchedEntityData.defineId(ItemBossEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_LENGTH =
            SynchedEntityData.defineId(ItemBossEntity.class, EntityDataSerializers.FLOAT);
    /** ビームは技の種類から独立して同期する。どの技からでも撃てるようにするため */
    private static final EntityDataAccessor<Integer> DATA_BEAM_STATE =
            SynchedEntityData.defineId(ItemBossEntity.class, EntityDataSerializers.INT);

    public static final int BEAM_OFF = 0;
    /** 予告線 (狙いを合わせている) */
    public static final int BEAM_AIM = 1;
    /** 予告線 (照準固定、点滅)。避ける猶予 */
    public static final int BEAM_LOCKED = 2;
    public static final int BEAM_FIRE = 3;

    public static final ResourceKey<DamageType> BEAM_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, EverythingBoss.id("item_beam"));

    public static final int BEAM_CHARGE = 40;
    public static final int BEAM_LOCK = 8;
    public static final float BEAM_RANGE = 40.0F;
    public static final int SWING_STRIKE = 14;
    public static final int DASH_START = 16;
    public static final int DASH_END = 36;
    public static final int SLAM_HANG = 18;
    public static final int SLAM_FALL = 27;
    public static final int DEATH_TICKS = 60;
    public static final int SPAWN_BURST = 80;
    public static final int ENRAGE_BURST = 45;
    public static final int COMBO_DASH_SEGMENT = 22;
    public static final int COMBO_DASH_WINDUP = 8;
    public static final int COMBO_DASH_END = 20;
    public static final int COMBO_SWING_START = COMBO_DASH_SEGMENT * 3;

    public static final byte EVENT_SLAM = 70;
    public static final byte EVENT_ENRAGE = 71;
    public static final byte EVENT_DEATH_BURST = 72;

    /** MAX_HEALTH 属性はバニラで 1024 が上限。超えた分は被ダメージを割って表現する */
    private static final double HEALTH_CAP = 1024.0;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private int attackTick;
    private int cooldown = 40;
    private BossAttack lastAttack = BossAttack.NONE;
    private float damageDivisor = 1.0F;
    private boolean statsApplied;
    private boolean introDone;
    /** 祭壇から呼ばれたときの難易度。コマンドやコアで呼んだボスは null (倍率なし) */
    @Nullable
    private AltarTier tier;
    private int lastBlockedSoundTick;
    private float strafeAngle;
    private int strafeDir = 1;

    private Vec3 dashDir = Vec3.ZERO;
    private final IntSet dashHit = new IntOpenHashSet();
    private int slamImpactTick = -1;
    private int volley;
    private int spinDir = 1;
    private Vec3 beamEnd = Vec3.ZERO;

    // クライアント側の補間用
    public float beamYawO;
    public float beamPitchO;
    /** 照射の見た目の強さ 0..1。照射が止まってもすぐには消さず、数 tick かけて細くする */
    private float beamVisual;
    private float beamVisualO;
    private int beamFireTicks;

    public ItemBossEntity(EntityType<? extends ItemBossEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        this.xpReward = 0;
        this.strafeAngle = this.random.nextFloat() * Mth.TWO_PI;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ITEM, ItemStack.EMPTY);
        this.entityData.define(DATA_ATTACK, 0);
        this.entityData.define(DATA_ENRAGED, false);
        this.entityData.define(DATA_BEAM_YAW, 0.0F);
        this.entityData.define(DATA_BEAM_PITCH, 0.0F);
        this.entityData.define(DATA_BEAM_LENGTH, 0.0F);
        this.entityData.define(DATA_BEAM_STATE, BEAM_OFF);
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ItemBossEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    // ---------------------------------------------------------------- item & stats

    public ItemStack getBossItem() {
        return this.entityData.get(DATA_ITEM);
    }

    /** setBossItem より前に呼ぶこと (ステータスの計算に使う) */
    public void setTier(@Nullable AltarTier tier) {
        this.tier = tier;
    }

    @Nullable
    public AltarTier getTier() {
        return this.tier;
    }

    public void setBossItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM, stack.copyWithCount(1));
        if (!level().isClientSide) {
            applyItemStats();
        }
        updateBossBar();
    }

    /** アイテムの性質からステータスを決める。レア度・エンチャント・攻撃力・防御力 */
    private void applyItemStats() {
        ItemStack stack = getBossItem();
        double health = Config.BASE_HEALTH.get();
        health *= switch (stack.getRarity()) {
            case COMMON -> 1.0;
            case UNCOMMON -> 1.3;
            case RARE -> 1.6;
            case EPIC -> 2.0;
        };
        int enchantLevels = EnchantmentHelper.getEnchantments(stack).values().stream().mapToInt(Integer::intValue).sum();
        health *= 1.0 + 0.1 * Math.min(enchantLevels, 50);

        // 武器・防具はその性能から。それ以外はアイテム ID のハッシュを種にした片側正規分布 |N(0,1)| から決める。
        // 大半は控えめだが、ごくまれに飛び抜けて強いアイテムが出る (2σ 超が約 5%、3σ 超が約 0.3%)
        double weaponAttack = sumModifiers(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE);
        double attack = weaponAttack > 0
                ? 7.0 + 0.8 * weaponAttack
                : 6.0 + 4.0 * halfNormal(stack, 1);    // 中央値 ≈ 8.7、2σ で 14、3σ で 18
        double itemArmor = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            itemArmor += sumModifiers(stack, slot, Attributes.ARMOR);
        }
        double armor = itemArmor > 0
                ? 4.0 + 2.0 * itemArmor
                : 2.0 + 4.0 * halfNormal(stack, 2);    // 中央値 ≈ 4.7 (上限は下の 30 で切る)

        if (this.tier != null) {
            health *= this.tier.healthMultiplier;
            attack *= this.tier.damageMultiplier;
        }

        this.damageDivisor = (float) Math.max(1.0, health / HEALTH_CAP);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(health, HEALTH_CAP));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Math.max(1.0, attack));
        getAttribute(Attributes.ARMOR).setBaseValue(Math.min(armor, 30.0));
        setHealth(getMaxHealth());
        this.statsApplied = true;
    }

    /** アイテム ID だけから決まる片側正規分布 |N(0,1)| の値 (Box-Muller) */
    private static double halfNormal(ItemStack stack, int salt) {
        double u1 = Math.max(hashRoll(stack, salt), 1.0E-12);
        double u2 = hashRoll(stack, salt + 1000);
        return Math.abs(Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2));
    }

    /**
     * アイテム ID だけから決まる [0, 1) の値。salt を変えると別の値になる。
     * NBT は使わない (名前を変えても強さは変わらない)。String#hashCode は実行をまたいで安定している。
     */
    private static double hashRoll(ItemStack stack, int salt) {
        long h = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().hashCode();
        h += salt * 0x9E3779B97F4A7C15L;
        // splitmix64 の仕上げ。近い入力でも値がばらけるようにする
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** 召喚時に表示するステータス行 (HP / 攻撃力 / 防御力) */
    public Component statsLine() {
        return Component.translatable("message.everythingboss.stats",
                String.format("%.0f", getEffectiveMaxHealth()),
                String.format("%.1f", getAttributeValue(Attributes.ATTACK_DAMAGE)),
                String.format("%.0f", getAttributeValue(Attributes.ARMOR)));
    }

    /** 被ダメージの割引を含めた実質の最大 HP */
    public float getEffectiveMaxHealth() {
        return getMaxHealth() * this.damageDivisor;
    }

    private static double sumModifiers(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
        double sum = 0;
        for (AttributeModifier modifier : modifiers.get(attribute)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                sum += modifier.getAmount();
            }
        }
        return sum;
    }

    private static ItemStack randomItem(net.minecraft.util.RandomSource random) {
        for (int i = 0; i < 20; i++) {
            ItemStack stack = BuiltInRegistries.ITEM.getRandom(random).map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) return stack;
        }
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    @Override
    protected Component getTypeName() {
        ItemStack stack = getBossItem();
        if (stack.isEmpty()) return super.getTypeName();
        Component name = Component.translatable("entity.everythingboss.item_boss.named", stack.getHoverName());
        return this.tier == null ? name : Component.translatable("entity.everythingboss.item_boss.tiered", name, this.tier.displayName());
    }

    private void updateBossBar() {
        this.bossEvent.setName(getDisplayName());
        this.bossEvent.setColor(this.tier != null ? this.tier.barColor : switch (getBossItem().getRarity()) {
            case COMMON -> BossEvent.BossBarColor.WHITE;
            case UNCOMMON -> BossEvent.BossBarColor.YELLOW;
            case RARE -> BossEvent.BossBarColor.BLUE;
            case EPIC -> BossEvent.BossBarColor.PURPLE;
        });
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        updateBossBar();
    }

    // ---------------------------------------------------------------- sync accessors

    public BossAttack getAttack() {
        return BossAttack.byId(this.entityData.get(DATA_ATTACK));
    }

    public int getAttackTick() {
        return this.attackTick;
    }

    public boolean isEnraged() {
        return this.entityData.get(DATA_ENRAGED);
    }

    public float getBeamYaw() {
        return this.entityData.get(DATA_BEAM_YAW);
    }

    public float getBeamPitch() {
        return this.entityData.get(DATA_BEAM_PITCH);
    }

    public float getBeamLength() {
        return this.entityData.get(DATA_BEAM_LENGTH);
    }

    public int getBeamState() {
        return this.entityData.get(DATA_BEAM_STATE);
    }

    public float getBeamVisual(float partialTick) {
        return Mth.lerp(partialTick, this.beamVisualO, this.beamVisual);
    }

    /** 照射開始からの tick (クライアント側の演出用) */
    public int getBeamFireTicks() {
        return this.beamFireTicks;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ATTACK.equals(key)) {
            this.attackTick = 0;
        }
    }

    public Vec3 center() {
        return position().add(0, getBbHeight() / 2.0, 0);
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        if (level().isClientSide) {
            this.beamYawO = getBeamYaw();
            this.beamPitchO = getBeamPitch();
            this.beamVisualO = this.beamVisual;
            if (getBeamState() == BEAM_FIRE) {
                this.beamVisual = Math.min(1.0F, this.beamVisual + 0.25F);
                this.beamFireTicks++;
            } else {
                this.beamVisual = Math.max(0.0F, this.beamVisual - 0.2F);
                if (this.beamVisual <= 0) this.beamFireTicks = 0;
            }
        } else if (getBossItem().isEmpty()) {
            // /summon で item 無しに呼ばれた場合はランダムなアイテムになる
            setBossItem(randomItem(this.random));
        }
        super.tick();
        if (getAttack() != BossAttack.NONE) {
            this.attackTick++;
        }
        if (level().isClientSide && isAlive()) {
            clientParticles();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || target instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            setTarget(null);
            target = null;
        }

        if (!this.introDone) {
            this.introDone = true;
            startAttack(BossAttack.SPAWN);
        }

        boolean enraged = getHealth() <= getMaxHealth() * 0.5F;
        if (enraged && !isEnraged()) {
            startEnrage();
        }

        BossAttack attack = getAttack();
        if (attack != BossAttack.NONE) {
            boolean needsTarget = attack != BossAttack.BEAM && attack != BossAttack.SLAM && attack != BossAttack.SLAM_BURST
                    && !attack.invulnerable;
            if (target == null && needsTarget) {
                endAttack();
            } else {
                tickAttack(attack, target);
            }
        } else {
            moveIdle(target);
            if (target != null && --this.cooldown <= 0) {
                startAttack(chooseAttack(target));
            }
        }

        if (this.horizontalCollision && getAttack() != BossAttack.SLAM) {
            setDeltaMovement(getDeltaMovement().add(0, 0.15, 0));
        }
        float progress = getHealth() / getMaxHealth();
        if (getAttack() == BossAttack.SPAWN) {
            // ウィザーと同じく、登場中はボスバーが少しずつ満ちていく
            progress *= Mth.clamp(this.attackTick / (float) SPAWN_BURST, 0.0F, 1.0F);
        }
        this.bossEvent.setProgress(progress);
    }

    private void startEnrage() {
        this.entityData.set(DATA_ENRAGED, true);
        this.bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
        this.bossEvent.setDarkenScreen(true);
        playSound(SoundEvents.BEACON_DEACTIVATE, 3.0F, 0.5F);
        startAttack(BossAttack.ENRAGE);
    }

    /** 演出 (SPAWN / ENRAGE) 中はその場に留まり、決まった tick で衝撃波を出す */
    private void tickPhase(BossAttack attack, int t, @Nullable LivingEntity target) {
        setDeltaMovement(getDeltaMovement().scale(0.5));
        if (target != null) faceTowards(target.position(), 4.0F);
        if (attack == BossAttack.SPAWN) {
            if (t == 1) playSound(SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
            if (t == 40) playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.7F);
            if (t == SPAWN_BURST) {
                playSound(SoundEvents.WITHER_SPAWN, 2.0F, 1.2F);
                shockwave(5.0, 1.0);
            }
        } else {
            if (t % 10 == 1) playSound(SoundEvents.ANVIL_LAND, 1.5F, 0.5F + t / 60.0F);
            if (t == ENRAGE_BURST) {
                playSound(SoundEvents.ENDER_DRAGON_GROWL, 3.0F, 1.2F);
                playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.7F);
                shockwave(7.0, 1.5);
            }
        }
    }

    private void shockwave(double radius, double strength) {
        level().broadcastEntityEvent(this, EVENT_ENRAGE);
        for (LivingEntity e : victims(getBoundingBox().inflate(radius))) {
            Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
            launch(e, away.scale(strength).add(0, 0.6, 0));
        }
    }

    private void moveIdle(@Nullable LivingEntity target) {
        Vec3 desired;
        if (target != null) {
            this.strafeAngle += 0.012F * this.strafeDir;
            if (this.random.nextInt(160) == 0) this.strafeDir = -this.strafeDir;
            double dist = 9.0;
            desired = target.position().add(Mth.cos(this.strafeAngle) * dist, 2.5 + Mth.sin(this.tickCount * 0.05F) * 0.8, Mth.sin(this.strafeAngle) * dist);
            faceTowards(target.position(), 8.0F);
        } else {
            desired = position().add(0, Mth.sin(this.tickCount * 0.05F) * 0.3, 0);
        }
        float max = isEnraged() ? 0.45F : 0.32F;
        steerTowards(desired, 0.08, max);
    }

    private void steerTowards(Vec3 desired, double gain, double maxSpeed) {
        Vec3 wanted = desired.subtract(position()).scale(gain);
        if (wanted.length() > maxSpeed) wanted = wanted.normalize().scale(maxSpeed);
        setDeltaMovement(getDeltaMovement().scale(0.8).add(wanted.scale(0.2 / 0.91)));
    }

    private void faceTowards(Vec3 pos, float maxStep) {
        float yaw = yawOf(pos.subtract(position()));
        setYRot(Mth.approachDegrees(getYRot(), yaw, maxStep));
        this.yBodyRot = this.yHeadRot = getYRot();
    }

    private static float yawOf(Vec3 v) {
        return (float) (Mth.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0F;
    }

    private static float pitchOf(Vec3 v) {
        return (float) -(Mth.atan2(v.y, v.horizontalDistance()) * (180.0 / Math.PI));
    }

    // ---------------------------------------------------------------- attacks

    private BossAttack chooseAttack(LivingEntity target) {
        double dist = distanceTo(target);
        boolean enraged = isEnraged();
        List<BossAttack> pool = new ArrayList<>();
        if (dist < 7.5) {
            add(pool, BossAttack.SWING, 4);
            add(pool, BossAttack.DASH, 1);
            add(pool, BossAttack.BEAM, 1);
            add(pool, BossAttack.BARRAGE, 1);
            add(pool, BossAttack.DASH_COMBO, 2);
            if (enraged) {
                add(pool, BossAttack.SLAM, 2);
                add(pool, BossAttack.SLAM_BURST, 3);
                add(pool, BossAttack.SPIN_BEAM, 2);
                add(pool, BossAttack.RAIN, 1);
            }
        } else {
            add(pool, BossAttack.BEAM, 3);
            add(pool, BossAttack.BARRAGE, 3);
            add(pool, BossAttack.DASH, 2);
            add(pool, BossAttack.BEAM_DIVE, 2);
            if (enraged) {
                add(pool, BossAttack.RAIN, 3);
                add(pool, BossAttack.SLAM_BURST, 1);
                add(pool, BossAttack.SPIN_BEAM, 2);
                add(pool, BossAttack.BEAM_DIVE, 2);
                add(pool, BossAttack.DASH_COMBO, 2);
            }
        }
        // 同じ技の連発は避ける
        List<BossAttack> filtered = pool.stream().filter(a -> a != this.lastAttack).toList();
        List<BossAttack> from = filtered.isEmpty() ? pool : filtered;
        return from.get(this.random.nextInt(from.size()));
    }

    private static void add(List<BossAttack> pool, BossAttack attack, int weight) {
        for (int i = 0; i < weight; i++) pool.add(attack);
    }

    private void startAttack(BossAttack attack) {
        this.dashHit.clear();
        this.slamImpactTick = -1;
        this.volley = 0;
        this.lastAttack = attack;
        this.entityData.set(DATA_ATTACK, attack.ordinal());
        this.attackTick = 0;
        setBeamState(BEAM_OFF);
        LivingEntity target = getTarget();
        if (target != null) {
            Vec3 to = aimPoint(target).subtract(center());
            this.entityData.set(DATA_BEAM_YAW, yawOf(to));
            this.entityData.set(DATA_BEAM_PITCH, pitchOf(to));
            this.entityData.set(DATA_BEAM_LENGTH, 0.0F);
        }
    }

    /** コマンドからの強制発動。生きていて対象が居るときだけ (演出系は対象なしでも可) */
    public boolean forceAttack(BossAttack attack) {
        if (!isAlive() || getAttack().invulnerable) return false;
        boolean needsTarget = attack != BossAttack.BEAM && attack != BossAttack.SLAM && attack != BossAttack.SLAM_BURST
                && !attack.invulnerable;
        if (needsTarget && getTarget() == null) return false;
        if (attack == BossAttack.ENRAGE) {
            if (isEnraged()) return false;
            startEnrage();
        } else {
            startAttack(attack);
        }
        return true;
    }

    private void endAttack() {
        setBeamState(BEAM_OFF);
        this.entityData.set(DATA_ATTACK, BossAttack.NONE.ordinal());
        int cooldown = (isEnraged() ? 14 : 28) + this.random.nextInt(20);
        this.cooldown = this.tier == null ? cooldown : (int) Math.round(cooldown * this.tier.cooldownMultiplier);
    }

    private void tickAttack(BossAttack attack, @Nullable LivingEntity target) {
        int t = this.attackTick;
        switch (attack) {
            case SWING -> tickSwing(t, target);
            case DASH -> tickDash(t, target);
            case BEAM -> tickBeam(t, target);
            case BARRAGE -> tickBarrage(t, target);
            case RAIN -> tickRain(t, target);
            case SLAM -> tickSlam(t, target);
            case SPAWN, ENRAGE -> tickPhase(attack, t, target);
            case BEAM_DIVE -> tickBeamDive(t, target);
            case SPIN_BEAM -> tickSpinBeam(t, target);
            case DASH_COMBO -> tickDashCombo(t, target);
            case SLAM_BURST -> tickSlamBurst(t, target);
            default -> {}
        }
        if (getAttack() == attack && t >= attack.duration) {
            endAttack();
        }
    }

    private float attackDamage(float factor) {
        return (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * factor * Config.DAMAGE_MULTIPLIER.get());
    }

    private Vec3 aimPoint(LivingEntity target) {
        return target.position().add(0, target.getBbHeight() * 0.6, 0);
    }

    private void tickSwing(int t, LivingEntity target) {
        if (t < SWING_STRIKE) {
            faceTowards(target.position(), 15.0F);
            Vec3 to = target.position().add(0, -0.5, 0);
            steerTowards(to, 0.12, 0.5);
            if (t == 2) playSound(SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0F, 0.6F);
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.6));
        }
        if (t == SWING_STRIKE) {
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.6F);
            playSound(SoundEvents.ITEM_BREAK, 2.0F, 0.5F);
            Vec3 c = center();
            double radius = isEnraged() ? 7.0 : 6.0;
            for (LivingEntity e : victims(getBoundingBox().inflate(radius, 2.0, radius))) {
                if (e.position().subtract(c).horizontalDistance() > radius + e.getBbWidth() / 2) continue;
                if (e.hurt(damageSources().mobAttack(this), attackDamage(1.0F))) {
                    e.knockback(1.6, getX() - e.getX(), getZ() - e.getZ());
                }
            }
            if (level() instanceof ServerLevel server) {
                for (int i = 0; i < 16; i++) {
                    float a = i / 16.0F * Mth.TWO_PI;
                    server.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Mth.cos(a) * radius * 0.7, getY() + 0.8, c.z + Mth.sin(a) * radius * 0.7, 1, 0, 0, 0, 0);
                }
                server.sendParticles(itemParticle(), c.x, c.y, c.z, 30, radius * 0.4, 0.4, radius * 0.4, 0.2);
            }
        }
    }

    private void tickDash(int t, LivingEntity target) {
        if (t < DASH_START) {
            faceTowards(target.position(), 20.0F);
            setDeltaMovement(getDeltaMovement().scale(0.7));
            if (t == 1) playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.7F);
            if (t == DASH_START - 1) {
                this.dashDir = aimPoint(target).subtract(center()).normalize();
                playSound(SoundEvents.TRIDENT_RIPTIDE_3, 3.0F, 0.8F);
            }
        } else if (t < DASH_END) {
            double speed = isEnraged() ? 1.6 : 1.3;
            setDeltaMovement(this.dashDir.scale(speed / 0.91));
            setYRot(yawOf(this.dashDir));
            this.yBodyRot = this.yHeadRot = getYRot();
            contactHit(this.dashDir, 1.2F);
            if (this.horizontalCollision || this.verticalCollision && this.dashDir.y < -0.3) {
                playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 1.3F);
                if (level() instanceof ServerLevel server) {
                    Vec3 c = center();
                    server.sendParticles(itemParticle(), c.x, c.y, c.z, 24, 1.0, 1.0, 1.0, 0.25);
                }
                setDeltaMovement(this.dashDir.scale(-0.3));
                this.attackTick = DASH_END;
            }
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.75));
        }
    }

    private void tickBeam(int t, @Nullable LivingEntity target) {
        setDeltaMovement(getDeltaMovement().scale(0.7));
        if (t <= BEAM_CHARGE - BEAM_LOCK) {
            setBeamState(BEAM_AIM);
            aimBeamAt(target, 30.0F);                                // 溜め中は正確に狙う (予告線)
        } else if (t <= BEAM_CHARGE) {
            setBeamState(BEAM_LOCKED);
            aimBeamAt((Vec3) null, 0.0F);                            // 発射直前は固定 → 避ける猶予
        } else if (t < BossAttack.BEAM.duration - 4) {
            setBeamState(BEAM_FIRE);
            aimBeamAt(target, isEnraged() ? 1.7F : 1.1F);            // 照射中はゆっくり追尾
        } else {
            setBeamState(BEAM_OFF);
        }
        faceBeam();
        if (t <= BEAM_CHARGE && t % 8 == 1) playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F + t / (float) BEAM_CHARGE);
        beamDamageTick(t);
    }

    // ---------------------------------------------------------------- beam parts

    private void setBeamState(int state) {
        if (getBeamState() == state) return;
        this.entityData.set(DATA_BEAM_STATE, state);
        if (state == BEAM_FIRE) {
            playSound(SoundEvents.BEACON_ACTIVATE, 4.0F, 1.6F);
            playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 1.8F);
        }
    }

    /** 対象へ最大 step 度だけ照準を寄せる (target が null か step が 0 なら向きはそのまま、長さだけ測り直す) */
    private void aimBeamAt(@Nullable LivingEntity target, float step) {
        aimBeamAt(target == null ? null : aimPoint(target), step);
    }

    private void aimBeamAt(@Nullable Vec3 point, float step) {
        float yaw = getBeamYaw();
        float pitch = getBeamPitch();
        if (point != null && step > 0) {
            Vec3 to = point.subtract(center());
            yaw = Mth.approachDegrees(yaw, yawOf(to), step);
            pitch = Mth.approachDegrees(pitch, pitchOf(to), step);
        }
        setBeamDirection(yaw, pitch);
    }

    private void setBeamDirection(float yaw, float pitch) {
        Vec3 start = center();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        this.beamEnd = level().clip(new ClipContext(start, start.add(dir.scale(BEAM_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation();
        this.entityData.set(DATA_BEAM_YAW, Mth.wrapDegrees(yaw));
        this.entityData.set(DATA_BEAM_PITCH, pitch);
        this.entityData.set(DATA_BEAM_LENGTH, (float) this.beamEnd.distanceTo(start));
    }

    private void faceBeam() {
        setYRot(getBeamYaw());
        this.yBodyRot = this.yHeadRot = getYRot();
    }

    /** 照射中なら 5 tick ごとにビーム上の相手へダメージ */
    private void beamDamageTick(int t) {
        if (getBeamState() != BEAM_FIRE) return;
        Vec3 start = center();
        Vec3 end = this.beamEnd;
        if (t % 12 == 0) playSound(SoundEvents.BEACON_AMBIENT, 4.0F, 1.4F);
        if (t % 5 == 1) {
            DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BEAM_DAMAGE), this);
            for (LivingEntity e : victims(new AABB(start, end).inflate(1.0))) {
                AABB box = e.getBoundingBox().inflate(0.5);
                if (box.contains(start) || box.clip(start, end).isPresent()) {
                    e.hurt(source, attackDamage(0.5F));
                }
            }
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(itemParticle(), end.x, end.y, end.z, 3, 0.3, 0.3, 0.3, 0.15);
            if (t % 3 == 0) server.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 1, 0.2, 0.2, 0.2, 0);
        }
    }

    /** 体当たり。1 回の突進の中で同じ相手には 1 度だけ当たる */
    private void contactHit(Vec3 dir, float factor) {
        for (LivingEntity e : victims(getBoundingBox().inflate(0.6))) {
            if (this.dashHit.add(e.getId()) && e.hurt(damageSources().mobAttack(this), attackDamage(factor))) {
                Vec3 push = dir.multiply(1, 0, 1);
                push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize();
                launch(e, push.scale(1.4).add(0, 0.5, 0));
            }
        }
    }

    /** 中心から全方位 (n 方向) へ弾を撃つ */
    private void radialShards(int n, float angleOffset, float pitch, float speed, float damageFactor) {
        Vec3 c = center();
        for (int i = 0; i < n; i++) {
            Vec3 d = Vec3.directionFromRotation(pitch, angleOffset + i * 360.0F / n);
            ItemShardEntity shard = new ItemShardEntity(level(), this, getBossItem(), attackDamage(damageFactor), false, 0.9F);
            Vec3 p = c.add(d.scale(2.2));
            shard.setPos(p.x, p.y - shard.getBbHeight() / 2, p.z);
            shard.shoot(d.x, d.y, d.z, speed, 0.0F);
            level().addFreshEntity(shard);
        }
        playSound(SoundEvents.DISPENSER_LAUNCH, 2.0F, 0.5F);
    }

    // ---------------------------------------------------------------- combo attacks

    /** 上空へ昇り、ビームで狙い続けながら対象の頭上を突き抜ける */
    private void tickBeamDive(int t, LivingEntity target) {
        if (t == 1) {
            Vec3 d = target.position().subtract(position()).multiply(1, 0, 1);
            this.dashDir = d.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : d.normalize();
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.6F);
        }
        if (t <= 20) {
            // 対象の手前上空へ
            steerTowards(target.position().subtract(this.dashDir.scale(12.0)).add(0, 9.0, 0), 0.15, 0.9);
            aimBeamAt(target, 30.0F);
        } else if (t <= 34) {
            setDeltaMovement(getDeltaMovement().scale(0.6));
            boolean locked = t > 28;
            setBeamState(locked ? BEAM_LOCKED : BEAM_AIM);
            aimBeamAt(locked ? null : target, 30.0F);
            if (t % 6 == 0) playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 1.0F + (t - 20) / 14.0F);
        } else if (t <= 80) {
            if (t == 35) playSound(SoundEvents.TRIDENT_RIPTIDE_3, 3.0F, 0.7F);
            setBeamState(BEAM_FIRE);
            setDeltaMovement(this.dashDir.scale((isEnraged() ? 0.75 : 0.6) / 0.91).add(0, -0.05, 0));
            aimBeamAt(target, isEnraged() ? 5.0F : 3.5F);
            contactHit(this.dashDir, 1.0F);
        } else {
            setBeamState(BEAM_OFF);
            setDeltaMovement(getDeltaMovement().scale(0.8));
        }
        faceBeam();
        beamDamageTick(t);
    }

    /** 斜め下にビームを撃ったまま一回転し、地面に輪を描く。同時に全方位へ弾 */
    private void tickSpinBeam(int t, LivingEntity target) {
        if (t == 1) {
            this.spinDir = this.random.nextBoolean() ? 1 : -1;
            Vec3 d = position().subtract(target.position()).multiply(1, 0, 1);
            this.dashDir = d.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : d.normalize();
            playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 3.0F, 0.7F);
        }
        if (t <= 25) {
            // 対象から 7 ブロック離れた少し上へ。ビームが地面に描く輪の半径がちょうど対象の位置になる
            steerTowards(target.position().add(this.dashDir.scale(7.0)).add(0, 4.0, 0), 0.12, 0.6);
            setBeamState(BEAM_AIM);
            aimBeamAt(target.position(), 30.0F);
        } else if (t <= 34) {
            setDeltaMovement(Vec3.ZERO);
            setBeamState(BEAM_LOCKED);
            aimBeamAt((Vec3) null, 0.0F);
        } else if (t < 100) {
            setDeltaMovement(Vec3.ZERO);
            setBeamState(BEAM_FIRE);
            float speed = isEnraged() ? 6.0F : 5.0F;
            setBeamDirection(getBeamYaw() + speed * this.spinDir, getBeamPitch());
            if ((t - 35) % 14 == 0) {
                radialShards(isEnraged() ? 12 : 8, t * 7.0F, -5.0F, 0.9F, 0.4F);
            }
        } else {
            setBeamState(BEAM_OFF);
        }
        faceBeam();
        beamDamageTick(t);
    }

    /** 突進 3 連 → 薙ぎ払い */
    private void tickDashCombo(int t, LivingEntity target) {
        if (t >= COMBO_SWING_START) {
            tickSwing(t - COMBO_SWING_START, target);
            return;
        }
        int segment = t / COMBO_DASH_SEGMENT;
        int local = t % COMBO_DASH_SEGMENT;
        if (local < COMBO_DASH_WINDUP) {
            faceTowards(target.position(), 25.0F);
            setDeltaMovement(getDeltaMovement().scale(0.6));
            if (local == 1) playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.8F + segment * 0.15F);
            if (local == COMBO_DASH_WINDUP - 1) {
                this.dashDir = aimPoint(target).subtract(center()).normalize();
                this.dashHit.clear();
                playSound(SoundEvents.TRIDENT_RIPTIDE_2, 3.0F, 0.9F + segment * 0.15F);
            }
        } else if (local < COMBO_DASH_END) {
            setDeltaMovement(this.dashDir.scale((isEnraged() ? 1.8 : 1.5) / 0.91));
            setYRot(yawOf(this.dashDir));
            this.yBodyRot = this.yHeadRot = getYRot();
            contactHit(this.dashDir, 1.0F);
            if (this.horizontalCollision) {
                playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 1.4F);
                setDeltaMovement(this.dashDir.scale(-0.3));
                this.attackTick = segment * COMBO_DASH_SEGMENT + COMBO_DASH_END;
            }
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.6));
        }
    }

    /** 真下へ予告ビームで着地点を示してから叩きつけ、着地でアイテムを放射状に 2 波 */
    private void tickSlamBurst(int t, @Nullable LivingEntity target) {
        if (t > 8 && t < SLAM_FALL) {
            setBeamState(t <= SLAM_HANG ? BEAM_AIM : BEAM_LOCKED);
            setBeamDirection(getYRot(), 90.0F);
        } else {
            setBeamState(BEAM_OFF);
        }
        tickSlam(t, target);
        if (this.slamImpactTick >= 0 && t == this.slamImpactTick + 8) {
            radialShards(16, 360.0F / 32, -8.0F, 1.0F, 0.45F);
        }
    }

    private void tickBarrage(int t, LivingEntity target) {
        faceTowards(target.position(), 10.0F);
        moveIdle(target);
        int volleys = isEnraged() ? 4 : 3;
        if (t >= 8 && (t - 8) % 10 == 0 && this.volley < volleys) {
            Vec3 c = center();
            Vec3 to = aimPoint(target).subtract(c);
            float baseYaw = yawOf(to);
            float basePitch = pitchOf(to);
            int n = isEnraged() ? 7 : 5;
            float spread = 44.0F;
            float stepAngle = spread / (n - 1);
            float offset = (this.volley % 2 == 1) ? stepAngle / 2 : 0;
            for (int i = 0; i < n; i++) {
                float yawOff = (i - (n - 1) / 2.0F) * stepAngle + offset;
                Vec3 d = Vec3.directionFromRotation(basePitch, baseYaw + yawOff);
                ItemShardEntity shard = new ItemShardEntity(level(), this, getBossItem(), attackDamage(0.45F), false, 0.9F);
                Vec3 p = c.add(d.scale(2.2));
                shard.setPos(p.x, p.y - shard.getBbHeight() / 2, p.z);
                shard.shoot(d.x, d.y, d.z, isEnraged() ? 1.3F : 1.05F, 0.0F);
                level().addFreshEntity(shard);
            }
            playSound(SoundEvents.DISPENSER_LAUNCH, 2.0F, 0.6F);
            playSound(SoundEvents.ITEM_PICKUP, 2.0F, 0.5F);
            this.volley++;
        }
    }

    private void tickRain(int t, LivingEntity target) {
        Vec3 above = target.position().add(0, 5.0, 0);
        steerTowards(above.add(position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(6)), 0.08, 0.3);
        faceTowards(target.position(), 10.0F);
        if (t == 2) playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 3.0F, 0.8F);
        if (t >= 12 && t <= 44 && t % 2 == 0) {
            int count = isEnraged() ? 2 : 1;
            for (int i = 0; i < count; i++) {
                float a = this.random.nextFloat() * Mth.TWO_PI;
                float r = this.random.nextFloat() * 5.5F;
                Vec3 ground = target.position().add(Mth.cos(a) * r, 0.5, Mth.sin(a) * r);
                // 天井がある場所 (洞窟) では天井の少し下から降らせる
                Vec3 top = level().clip(new ClipContext(ground, ground.add(0, 13 + this.random.nextFloat() * 3, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation().add(0, -1.0, 0);
                ItemShardEntity shard = new ItemShardEntity(level(), this, getBossItem(), attackDamage(0.6F), true, 1.4F);
                shard.setPos(top.x, top.y, top.z);
                shard.setDeltaMovement(0, -0.4, 0);
                level().addFreshEntity(shard);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.END_ROD, ground.x, ground.y - 0.4, ground.z, 4, 0.3, 0.0, 0.3, 0.01);
                }
            }
        }
    }

    private void tickSlam(int t, @Nullable LivingEntity target) {
        if (t <= SLAM_HANG) {
            if (target != null) {
                steerTowards(target.position().add(0, 7.0, 0), 0.2, 0.8);
                faceTowards(target.position(), 10.0F);
            }
            if (t == 1) playSound(SoundEvents.ENDER_DRAGON_FLAP, 3.0F, 0.5F);
        } else if (t < SLAM_FALL) {
            setDeltaMovement(Vec3.ZERO);
            if (t == SLAM_FALL - 2) playSound(SoundEvents.TRIDENT_THUNDER, 1.5F, 1.5F);
        } else if (this.slamImpactTick < 0) {
            setDeltaMovement(0, -1.8 / 0.98, 0);
            if (onGround() || this.verticalCollision || t >= 64) {
                slamImpact(t);
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
            if (t >= this.slamImpactTick + 16) endAttack();
        }
    }

    private void slamImpact(int t) {
        this.slamImpactTick = t;
        double radius = isEnraged() ? 9.0 : 7.0;
        playSound(SoundEvents.GENERIC_EXPLODE, 4.0F, 0.6F);
        playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
        level().broadcastEntityEvent(this, EVENT_SLAM);
        if (getAttack() == BossAttack.SLAM_BURST) {
            radialShards(16, 0.0F, -8.0F, 1.0F, 0.45F);
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 8, radius / 3, 0.3, radius / 3, 0);
        }
        Vec3 c = position();
        for (LivingEntity e : victims(getBoundingBox().inflate(radius, 3.0, radius))) {
            double d = e.position().subtract(c).horizontalDistance();
            if (d > radius) continue;
            if (e.hurt(damageSources().mobAttack(this), attackDamage((float) (1.4 * (1.0 - 0.5 * d / radius))))) {
                Vec3 away = e.position().subtract(c).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
                launch(e, away.scale(1.3).add(0, 0.8, 0));
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private List<LivingEntity> victims(AABB area) {
        return level().getEntitiesOfClass(LivingEntity.class, area, e -> e != this && e.isAlive() && e.isAttackable()
                && !(e instanceof ItemBossEntity) && !e.isSpectator()
                && !(e instanceof Player p && p.isCreative()));
    }

    /** 吹き飛ばし。本人のクライアントにも確実に届くよう速度パケットを直接送る */
    private static void launch(LivingEntity e, Vec3 velocity) {
        double resist = e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        Vec3 v = velocity.scale(1.0 - Mth.clamp(resist, 0.0, 1.0));
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hurtMarked = true;
        if (e instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    private ParticleOptions itemParticle() {
        ItemStack stack = getBossItem();
        return stack.isEmpty() ? ParticleTypes.CRIT : new ItemParticleOption(ParticleTypes.ITEM, stack);
    }

    private void clientParticles() {
        if (getBossItem().isEmpty()) return;
        Vec3 c = center();
        if (this.tickCount % 4 == 0) {
            level().addParticle(itemParticle(), c.x + (this.random.nextDouble() - 0.5) * 3, c.y + (this.random.nextDouble() - 0.5) * 3,
                    c.z + (this.random.nextDouble() - 0.5) * 3, 0, 0.05, 0);
        }
        boolean gathering = getBeamState() == BEAM_AIM || getBeamState() == BEAM_LOCKED
                || getAttack() == BossAttack.SPAWN && this.attackTick < SPAWN_BURST
                || getAttack() == BossAttack.ENRAGE && this.attackTick < ENRAGE_BURST;
        if (gathering) {
            // 溜め: 光が中心に吸い込まれる
            for (int i = 0; i < 2; i++) {
                Vec3 off = new Vec3(this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5).normalize().scale(4.0);
                level().addParticle(ParticleTypes.END_ROD, c.x + off.x, c.y + off.y, c.z + off.z, -off.x * 0.08, -off.y * 0.08, -off.z * 0.08);
            }
        }
        if (isEnraged() && this.tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, c.x + (this.random.nextDouble() - 0.5) * 4, c.y + (this.random.nextDouble() - 0.5) * 4,
                    c.z + (this.random.nextDouble() - 0.5) * 4, 0, 0.02, 0);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_SLAM || id == EVENT_ENRAGE || id == EVENT_DEATH_BURST) {
            if (getBossItem().isEmpty()) return;
            ParticleOptions particle = itemParticle();
            Vec3 origin = id == EVENT_SLAM ? position().add(0, 0.3, 0) : center();
            int n = id == EVENT_DEATH_BURST ? 120 : 64;
            double speed = id == EVENT_SLAM ? 0.9 : 0.6;
            for (int i = 0; i < n; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double vy = id == EVENT_SLAM ? 0.15 + this.random.nextDouble() * 0.2 : (this.random.nextDouble() - 0.5) * speed;
                level().addParticle(particle, origin.x, origin.y, origin.z, Math.cos(a) * speed, vy, Math.sin(a) * speed);
            }
            if (id != EVENT_SLAM) {
                level().addParticle(ParticleTypes.EXPLOSION_EMITTER, origin.x, origin.y, origin.z, 0, 0, 0);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (getBeamState() != BEAM_OFF || this.beamVisual > 0) {
            return getBoundingBox().inflate(getBeamLength() + 2.0);
        }
        return getBoundingBox().inflate(3.0);
    }

    // ---------------------------------------------------------------- damage & death

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof ItemBossEntity || source.getDirectEntity() instanceof ItemShardEntity) return false;
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypeTags.IS_FALL)) return false;
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // !introDone: 召喚されてから最初の tick (登場演出の開始) までの隙も無敵にする
            if (getAttack().invulnerable || !this.introDone) {
                // 演出中は無敵。弾かれたことが分かるよう音だけ鳴らす
                if (this.tickCount - this.lastBlockedSoundTick > 8) {
                    this.lastBlockedSoundTick = this.tickCount;
                    playSound(SoundEvents.SHIELD_BLOCK, 1.5F, 0.6F);
                }
                return false;
            }
            amount /= this.damageDivisor;
            // 1 発で削れる量に上限を付け、強すぎる武器での瞬殺を防ぐ
            amount = Math.min(amount, (float) (getMaxHealth() * Config.MAX_DAMAGE_PER_HIT.get()));
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && level() instanceof ServerLevel server) {
            Vec3 c = center();
            server.sendParticles(itemParticle(), c.x, c.y, c.z, 10, 0.8, 0.8, 0.8, 0.15);
        }
        return hurt;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.entityData.set(DATA_ATTACK, BossAttack.NONE.ordinal());
        setBeamState(BEAM_OFF);
        playSound(SoundEvents.BEACON_DEACTIVATE, 4.0F, 0.5F);
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            if (!getBossItem().isEmpty()) {
                Vec3 c = center();
                for (int i = 0; i < 3; i++) {
                    level().addParticle(itemParticle(), c.x, c.y, c.z,
                            (this.random.nextDouble() - 0.5) * 0.8, this.random.nextDouble() * 0.5, (this.random.nextDouble() - 0.5) * 0.8);
                }
            }
        } else if (this.deathTime >= DEATH_TICKS && !isRemoved()) {
            level().broadcastEntityEvent(this, EVENT_DEATH_BURST);
            playSound(SoundEvents.GENERIC_EXPLODE, 4.0F, 0.8F);
            playSound(SoundEvents.ITEM_BREAK, 4.0F, 0.5F);
            dropExperience();
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public int getExperienceReward() {
        return this.tier == null ? Config.EXPERIENCE.get() : (int) Math.round(Config.EXPERIENCE.get() * this.tier.experienceMultiplier);
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        ItemStack item = getBossItem();
        if (item.isEmpty()) return;
        // 祭壇のボスは難易度に応じた数だけ、NBT ごと複製して落とす (増殖は仕様)
        int count = this.tier != null ? this.tier.drops : Config.DROP_ITEM_ON_DEATH.get() ? 1 : 0;
        int perStack = Math.max(1, item.getMaxStackSize());
        while (count > 0) {
            int n = Math.min(count, perStack);
            spawnAtLocation(item.copyWithCount(n), 1.0F);
            count -= n;
        }
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_RESONATE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ITEM_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 2.0F;
    }

    // ---------------------------------------------------------------- boss bar & save

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!getBossItem().isEmpty()) {
            tag.put("BossItem", getBossItem().save(new CompoundTag()));
        }
        tag.putFloat("DamageDivisor", this.damageDivisor);
        tag.putBoolean("StatsApplied", this.statsApplied);
        tag.putBoolean("Enraged", isEnraged());
        tag.putBoolean("IntroDone", this.introDone);
        if (this.tier != null) tag.putString("Tier", this.tier.id());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("DamageDivisor")) this.damageDivisor = Math.max(1.0F, tag.getFloat("DamageDivisor"));
        this.tier = tag.contains("Tier") ? AltarTier.byId(tag.getString("Tier")) : null;
        this.statsApplied = tag.getBoolean("StatsApplied");
        this.introDone = tag.getBoolean("IntroDone");
        if (tag.contains("BossItem")) {
            ItemStack stack = ItemStack.of(tag.getCompound("BossItem"));
            if (this.statsApplied) {
                this.entityData.set(DATA_ITEM, stack);
                updateBossBar();
            } else {
                // /summon ... {BossItem:{...}} で直接指定された場合はここで初めてステータスを決める
                setBossItem(stack);
            }
        }
        if (tag.getBoolean("Enraged")) {
            this.entityData.set(DATA_ENRAGED, true);
            this.bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_10);
            this.bossEvent.setDarkenScreen(true);
        }
        this.bossEvent.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        return effect.getEffect() != net.minecraft.world.effect.MobEffects.LEVITATION && super.canBeAffected(effect);
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void doPush(Entity entity) {
    }
}
