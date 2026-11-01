package jp.main.taikun.everythingboss;

import jp.main.taikun.everythingboss.altar.AltarBlock;
import jp.main.taikun.everythingboss.command.ItemBossCommand;
import jp.main.taikun.everythingboss.entity.ItemBossEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(EverythingBoss.MODID)
public class EverythingBoss {
    public static final String MODID = "everythingboss";

    public EverythingBoss(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::onAttributes);
        modBus.addListener(this::onCreativeTab);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(AltarBlock::onRightClickBlock);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ITEM_BOSS.get(), ItemBossEntity.createAttributes().build());
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            ModItems.ALTARS.values().forEach(item -> event.accept(item.get()));
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ItemBossCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
