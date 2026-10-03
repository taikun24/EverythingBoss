package jp.main.taikun.everythingboss.client;

import jp.main.taikun.everythingboss.EverythingBoss;
import jp.main.taikun.everythingboss.ModBlocks;
import jp.main.taikun.everythingboss.ModEntities;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EverythingBoss.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ITEM_BOSS.get(), ItemBossRenderer::new);
        event.registerEntityRenderer(ModEntities.ITEM_SHARD.get(), ItemShardRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.ALTAR_BLOCK_ENTITY.get(), AltarRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> ItemColorCache.clear());
    }

    private ClientSetup() {}
}
