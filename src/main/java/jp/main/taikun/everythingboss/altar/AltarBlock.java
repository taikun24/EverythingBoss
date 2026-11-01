package jp.main.taikun.everythingboss.altar;

import jp.main.taikun.everythingboss.EverythingBoss;
import jp.main.taikun.everythingboss.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 祭壇。アイテムを持って右クリックで捧げ、素手で右クリックで儀式を始める。
 * スニーク + 素手で捧げ物を取り戻せる。
 */
public class AltarBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            box(0, 0, 0, 16, 4, 16),
            box(4, 4, 4, 12, 10, 12),
            box(1, 10, 1, 15, 12, 15));

    /** 捧げられないアイテム。既定では空で、Mod パックやサーバーがデータパックで足す (中身ごと増える収納アイテムなど) */
    public static final TagKey<Item> CANNOT_OFFER = TagKey.create(Registries.ITEM, EverythingBoss.id("cannot_offer"));

    private final AltarTier tier;

    public AltarBlock(AltarTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public AltarTier getTier() {
        return this.tier;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlocks.ALTAR_BLOCK_ENTITY.get(),
                level.isClientSide ? AltarBlockEntity::clientTick : AltarBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !(level.getBlockEntity(pos) instanceof AltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        if (altar.isRitualRunning()) return InteractionResult.CONSUME;
        ItemStack held = player.getItemInHand(hand);
        ItemStack offering = altar.getOffering();

        if (offering.isEmpty()) {
            if (!level.isClientSide) {
                if (held.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.everythingboss.altar.empty"), true);
                } else if (held.is(CANNOT_OFFER)) {
                    player.displayClientMessage(Component.translatable("message.everythingboss.altar.refused", held.getHoverName()), true);
                } else {
                    altar.setOffering(held.copyWithCount(1));
                    if (!player.getAbilities().instabuild) held.shrink(1);
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
                    player.displayClientMessage(Component.translatable("message.everythingboss.altar.offered",
                            altar.getOffering().getHoverName()), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!held.isEmpty()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.everythingboss.altar.occupied", offering.getHoverName()), true);
            }
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                player.setItemInHand(hand, offering.copy());
                altar.setOffering(ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
            } else {
                altar.startRitual(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * スニーク中にどちらかの手に物があると、バニラはブロックを使う処理を飛ばす。
     * そのままだと、盾などをオフハンドに持っていると捧げ物を取り戻せないので、
     * 右手が素手なら祭壇だけはブロックを使わせる
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity().getMainHandItem().isEmpty()
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof AltarBlock) {
            event.setUseBlock(Event.Result.ALLOW);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AltarBlockEntity altar && !altar.getOffering().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, altar.getOffering());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.everythingboss.altar.tooltip", this.tier.displayName(), this.tier.drops)
                .withStyle(ChatFormatting.GRAY));
    }
}
