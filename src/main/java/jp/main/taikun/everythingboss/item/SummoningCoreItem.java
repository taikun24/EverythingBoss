package jp.main.taikun.everythingboss.item;

import jp.main.taikun.everythingboss.ModEntities;
import jp.main.taikun.everythingboss.entity.ItemBossEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** もう片方の手に持ったアイテム (NBT 込み) をボスとして召喚する */
public class SummoningCoreItem extends Item {
    public SummoningCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack core = player.getItemInHand(hand);
        ItemStack target = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (target.isEmpty() || target.is(this)) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.everythingboss.need_item").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(core);
        }
        if (!level.isClientSide) {
            ItemBossEntity boss = ModEntities.ITEM_BOSS.get().create(level);
            if (boss == null) return InteractionResultHolder.fail(core);
            Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 pos = player.position().add(look.scale(6.0)).add(0, 1.0, 0);
            boss.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180.0F, 0.0F);
            boss.setBossItem(target);
            boss.setTarget(player);
            level.addFreshEntity(boss);
            player.displayClientMessage(boss.statsLine(), true);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.2F);
            if (!player.getAbilities().instabuild) core.shrink(1);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(core, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.everythingboss.summoning_core.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
