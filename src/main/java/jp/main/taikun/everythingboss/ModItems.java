package jp.main.taikun.everythingboss;

import jp.main.taikun.everythingboss.altar.AltarTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, EverythingBoss.MODID);

    public static final Map<AltarTier, DeferredHolder<Item, Item>> ALTARS = new EnumMap<>(AltarTier.class);

    static {
        ModBlocks.ALTARS.forEach((tier, block) -> ALTARS.put(tier,
                ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()))));
    }

    private ModItems() {}
}
