package jp.main.taikun.everythingboss;

import jp.main.taikun.everythingboss.altar.AltarBlock;
import jp.main.taikun.everythingboss.altar.AltarBlockEntity;
import jp.main.taikun.everythingboss.altar.AltarTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, EverythingBoss.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EverythingBoss.MODID);

    public static final Map<AltarTier, DeferredHolder<Block, AltarBlock>> ALTARS = new EnumMap<>(AltarTier.class);

    static {
        altar(AltarTier.STONE, MapColor.STONE, SoundType.STONE, 3.5F, 6.0F);
        altar(AltarTier.GOLDEN, MapColor.GOLD, SoundType.METAL, 4.0F, 6.0F);
        altar(AltarTier.DIAMOND, MapColor.DIAMOND, SoundType.METAL, 5.0F, 6.0F);
        altar(AltarTier.NETHERITE, MapColor.COLOR_BLACK, SoundType.NETHERITE_BLOCK, 50.0F, 1200.0F);
    }

    /** 4 種類の祭壇で 1 つのブロックエンティティ型を共有する */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AltarBlockEntity>> ALTAR_BLOCK_ENTITY = BLOCK_ENTITIES.register("altar",
            () -> BlockEntityType.Builder.of(AltarBlockEntity::new,
                    ALTARS.values().stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));

    private static void altar(AltarTier tier, MapColor color, SoundType sound, float hardness, float resistance) {
        ALTARS.put(tier, BLOCKS.register(tier.id() + "_altar", () -> new AltarBlock(tier, BlockBehaviour.Properties.of()
                .mapColor(color).sound(sound).strength(hardness, resistance)
                .requiresCorrectToolForDrops().noOcclusion())));
    }

    private ModBlocks() {}
}
