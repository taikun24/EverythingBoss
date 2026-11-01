package jp.main.taikun.everythingboss.client;

import jp.main.taikun.everythingboss.EverythingBoss;
import jp.main.taikun.everythingboss.ModBlocks;
import jp.main.taikun.everythingboss.ModEntities;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = EverythingBoss.MODID, value = Dist.CLIENT)
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
