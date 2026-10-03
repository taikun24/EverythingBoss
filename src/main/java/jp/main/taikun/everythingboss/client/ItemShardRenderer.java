package jp.main.taikun.everythingboss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.main.taikun.everythingboss.entity.ItemShardEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

/** 飛翔方向に先端を向け、進行軸まわりに錐揉み回転するアイテム */
public class ItemShardRenderer extends EntityRenderer<ItemShardEntity> {
    private final ItemRenderer itemRenderer;

    public ItemShardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ItemShardEntity shard, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (shard.tickCount < 2 && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(shard) < 12.25) return;
        pose.pushPose();
        pose.translate(0, shard.getBbHeight() / 2, 0);
        // 矢と同じ規約: ここで +X が進行方向になる
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, shard.yRotO, shard.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, shard.xRotO, shard.getXRot())));
        pose.mulPose(Axis.XP.rotationDegrees((shard.tickCount + partialTick) * 30.0F));
        // アイテム画像の右上 (剣なら切っ先) を +X へ
        pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        float s = shard.getScale();
        pose.scale(s, s, s * 1.5F);
        this.itemRenderer.renderStatic(shard.getItem(), ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                pose, buffers, shard.level(), shard.getId());
        pose.popPose();
        super.render(shard, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ItemShardEntity shard) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
