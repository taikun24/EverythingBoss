package jp.main.taikun.everythingboss;

import jp.main.taikun.everythingboss.item.SummoningCoreItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EverythingBoss.MODID);

    public static final RegistryObject<Item> SUMMONING_CORE = ITEMS.register("summoning_core",
            () -> new SummoningCoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    private ModItems() {}
}
