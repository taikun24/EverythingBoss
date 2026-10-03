package jp.main.taikun.everythingboss.altar;

import jp.main.taikun.everythingboss.ModBlocks;
import jp.main.taikun.everythingboss.ModEntities;
import jp.main.taikun.everythingboss.entity.ItemBossEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 捧げ物と儀式の進行を持つ。描画のために中身はクライアントへ同期する */
public class AltarBlockEntity extends BlockEntity {
    public static final int RITUAL_TICKS = 80;

    private ItemStack offering = ItemStack.EMPTY;
    /** 0 = 待機中、1 以上 = 儀式の経過 tick */
    private int ritual;
    @Nullable
    private UUID summoner;
    /** クライアントでの浮遊・回転アニメーション用 */
    public int animationTicks;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ALTAR_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStack getOffering() {
        return this.offering;
    }

    public int getRitual() {
        return this.ritual;
    }

    public boolean isRitualRunning() {
        return this.ritual > 0;
    }

    public AltarTier getTier() {
        return getBlockState().getBlock() instanceof AltarBlock altar ? altar.getTier() : AltarTier.GOLDEN;
    }

    public void setOffering(ItemStack stack) {
        this.offering = stack;
        sync();
    }

    public void startRitual(Player player) {
        if (this.offering.isEmpty() || this.ritual > 0 || this.level == null) return;
        this.ritual = 1;
        this.summoner = player.getUUID();
        this.level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.6F);
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity altar) {
        if (altar.ritual <= 0) return;
        if (altar.offering.isEmpty()) {
            altar.ritual = 0;
            altar.sync();
            return;
        }
        altar.ritual++;
        if (altar.ritual % 20 == 0) {
            level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.6F + altar.ritual / (float) RITUAL_TICKS);
        }
        if (altar.ritual >= RITUAL_TICKS) {
            altar.summon((ServerLevel) level, pos);
        }
    }

    private void summon(ServerLevel level, BlockPos pos) {
        ItemBossEntity boss = ModEntities.ITEM_BOSS.get().create(level);
        if (boss != null) {
            boss.moveTo(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            boss.setTier(getTier());
            boss.setBossItem(this.offering);
            Player player = this.summoner == null ? null : level.getPlayerByUUID(this.summoner);
            if (player != null && !player.isCreative() && !player.isSpectator()) {
                boss.setTarget(player);
            }
            level.addFreshEntity(boss);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        this.offering = ItemStack.EMPTY;
        this.ritual = 0;
        this.summoner = null;
        sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity altar) {
        altar.animationTicks++;
        if (altar.offering.isEmpty()) return;
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        if (altar.ritual > 0) {
            // 儀式の開始だけ同期し、以降の経過はクライアントでも数える
            altar.ritual = Math.min(altar.ritual + 1, RITUAL_TICKS);
            float p = altar.ritual / (float) RITUAL_TICKS;
            double y = pos.getY() + 1.15 + p * 1.2;
            // 文字が渦を巻いて吸い込まれ、光が立ち昇る
            for (int i = 0; i < 3; i++) {
                double a = level.random.nextDouble() * Math.PI * 2;
                double r = 1.5 + level.random.nextDouble();
                level.addParticle(ParticleTypes.ENCHANT, x, y, z, Math.cos(a) * r, level.random.nextDouble() - 0.5, Math.sin(a) * r);
            }
            float a = altar.animationTicks * 0.4F;
            level.addParticle(ParticleTypes.END_ROD, x + Mth.cos(a) * 0.8, pos.getY() + 1.0, z + Mth.sin(a) * 0.8, 0, 0.12 + p * 0.1, 0);
        } else if (level.random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, x, pos.getY() + 1.3, z,
                    (level.random.nextDouble() - 0.5) * 1.5, level.random.nextDouble() * 0.5, (level.random.nextDouble() - 0.5) * 1.5);
        }
    }

    private void sync() {
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.offering.isEmpty()) tag.put("Offering", this.offering.save(new CompoundTag()));
        tag.putInt("Ritual", this.ritual);
        if (this.summoner != null) tag.putUUID("Summoner", this.summoner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.offering = tag.contains("Offering") ? ItemStack.of(tag.getCompound("Offering")) : ItemStack.EMPTY;
        this.ritual = tag.getInt("Ritual");
        this.summoner = tag.hasUUID("Summoner") ? tag.getUUID("Summoner") : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
