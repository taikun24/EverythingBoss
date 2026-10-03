package jp.main.taikun.everythingboss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.main.taikun.everythingboss.altar.AltarBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 祭壇の上で捧げ物が浮いて回る。儀式中は昇りながら速く回り、膨らむ */
public class AltarRenderer implements BlockEntityRenderer<AltarBlockEntity> {
    private final ItemRenderer itemRenderer;

    public AltarRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(AltarBlockEntity altar, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = altar.getOffering();
        if (stack.isEmpty()) return;
        float time = altar.animationTicks + partialTick;
        float ritual = altar.isRitualRunning()
                ? Math.min(1.0F, (altar.getRitual() + partialTick) / AltarBlockEntity.RITUAL_TICKS) : 0.0F;
        pose.pushPose();
        pose.translate(0.5, 1.2 + Mth.sin(time * 0.1F) * 0.06 + ritual * ritual * 1.2, 0.5);
        // 儀式が進むほど加速する。角度は時間の 2 乗で積む
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.0F + ritual * ritual * 900.0F));
        float s = 0.6F + ritual * 0.6F;
        pose.scale(s, s, s);
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, ritual > 0 ? LightTexture.FULL_BRIGHT : light,
                OverlayTexture.NO_OVERLAY, pose, buffers, altar.getLevel(), (int) altar.getBlockPos().asLong());
        pose.popPose();
    }
}
