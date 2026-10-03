package jp.main.taikun.everythingboss.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * アイテムのテクスチャ (パーティクルアイコン) から「テーマ色」を抜き出す。
 * 枠やビームの色に使う。彩度の高い画素を重く見て平均し、暗すぎ・くすみすぎを持ち上げる。
 * <p>
 * キーは ItemStack のインスタンス (equals を持たないので同一性で比較される)。
 * ボスの同期データのスタックは差し替わるまで同じオブジェクトなので、これで十分キャッシュが効く。
 */
public final class ItemColorCache {
    private static final int FALLBACK = 0xB070FF;
    private static final Map<ItemStack, Integer> CACHE = new WeakHashMap<>();

    public static int get(ItemStack stack) {
        Integer cached = CACHE.get(stack);
        if (cached != null) return cached;
        int color = compute(stack);
        CACHE.put(stack, color);
        return color;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static int compute(ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getInstance();
            BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
            SpriteContents contents = model.getParticleIcon(ModelData.EMPTY).contents();
            NativeImage image = contents.getOriginalImage();
            int w = contents.width();
            int h = contents.height();
            double sr = 0, sg = 0, sb = 0, sw = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = image.getPixelRGBA(x, y); // 0xAABBGGRR
                    if ((p >>> 24) < 128) continue;
                    float r = (p & 0xFF) / 255.0F;
                    float g = (p >> 8 & 0xFF) / 255.0F;
                    float b = (p >> 16 & 0xFF) / 255.0F;
                    float max = Math.max(r, Math.max(g, b));
                    float min = Math.min(r, Math.min(g, b));
                    float sat = max <= 0 ? 0 : (max - min) / max;
                    double weight = 0.15 + sat * max * max * 3.0;
                    sr += r * weight;
                    sg += g * weight;
                    sb += b * weight;
                    sw += weight;
                }
            }
            if (sw <= 0) return FALLBACK;
            float r = (float) (sr / sw);
            float g = (float) (sg / sw);
            float b = (float) (sb / sw);

            int tint = mc.getItemColors().getColor(stack, 0);
            if (tint != -1) {
                r *= (tint >> 16 & 0xFF) / 255.0F;
                g *= (tint >> 8 & 0xFF) / 255.0F;
                b *= (tint & 0xFF) / 255.0F;
            }

            float max = Math.max(r, Math.max(g, b));
            float min = Math.min(r, Math.min(g, b));
            float value = max;
            float sat = max <= 0 ? 0 : (max - min) / max;
            float hue = hue(r, g, b, max, min);
            if (sat > 0.12F) sat = Math.max(sat, 0.6F);
            value = Math.max(value, 0.9F);
            return Mth.hsvToRgb(hue, sat, value) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return FALLBACK;
        }
    }

    private static float hue(float r, float g, float b, float max, float min) {
        float d = max - min;
        if (d <= 0) return 0;
        float h;
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h /= 6.0F;
        return h < 0 ? h + 1 : h;
    }

    private ItemColorCache() {}
}
