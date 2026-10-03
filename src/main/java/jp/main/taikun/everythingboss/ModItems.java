package jp.main.taikun.everythingboss;

import jp.main.taikun.everythingboss.altar.AltarTier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EverythingBoss.MODID);

    public static final Map<AltarTier, RegistryObject<Item>> ALTARS = new EnumMap<>(AltarTier.class);

    static {
        ModBlocks.ALTARS.forEach((tier, block) -> ALTARS.put(tier,
                ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()))));
    }

    private ModItems() {}
}
