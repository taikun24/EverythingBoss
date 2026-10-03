package jp.main.taikun.everythingboss.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.main.taikun.everythingboss.EverythingBoss;
import jp.main.taikun.everythingboss.entity.BossAttack;
import jp.main.taikun.everythingboss.entity.ItemBossEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * ボス本体: 回転する立方体の枠 + その中に 3 ブロック幅に引き伸ばしたアイテム。
 * ビームや溜めの演出もすべてボスのアイテムの描画 (ItemRenderer) を使う。
 */
public class ItemBossRenderer extends EntityRenderer<ItemBossEntity> {
    private static final ResourceLocation FRAME_TEXTURE = EverythingBoss.id("textures/entity/frame.png");
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    /** アイテム本体の大きさ (ブロック) */
    private static final float ITEM_SIZE = 3.0F;
    /** 奥行き方向の倍率。厚みを持たせて「塊」に見せる */
    private static final float ITEM_DEPTH = 2.0F;

    private final ItemRenderer itemRenderer;

    public ItemBossRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 1.4F;
    }

    @Override
    public void render(ItemBossEntity boss, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = boss.getBossItem();
        if (stack.isEmpty()) return;

        float time = boss.tickCount + partialTick;
        int rgb = ItemColorCache.get(stack);
        float r = (rgb >> 16 & 0xFF) / 255.0F;
        float g = (rgb >> 8 & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float death = boss.deathTime > 0 ? Math.min(1.0F, (boss.deathTime + partialTick) / ItemBossEntity.DEATH_TICKS) : 0.0F;
        boolean enraged = boss.isEnraged();
        BossAttack attack = boss.getAttack();
        float t = boss.getAttackTick() + partialTick;
        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(boss.hurtTime > 0 || boss.deathTime > 0));

        pose.pushPose();
        pose.translate(0, boss.getBbHeight() / 2.0F + Mth.sin(time * 0.08F) * 0.15F, 0);

        // ---- 枠
        float spin = time * (enraged ? 4.0F : 2.0F) * (1.0F + death * 8.0F);
        float half = 2.1F * (1.0F - death * 0.7F);
        half *= 1.0F + 0.15F * boss.getBeamVisual(partialTick);
        if (attack == BossAttack.BEAM && t <= ItemBossEntity.BEAM_CHARGE) {
            half *= 1.0F - 0.25F * smooth(t / ItemBossEntity.BEAM_CHARGE);
        } else if (attack == BossAttack.SWING && t >= ItemBossEntity.SWING_STRIKE) {
            half *= 1.0F + 0.3F * Math.max(0, 1.0F - (t - ItemBossEntity.SWING_STRIKE) / 8.0F);
        } else if (attack == BossAttack.SPAWN) {
            // 登場: 枠が点から組み上がり、高速回転から落ち着く
            half *= easeOutBack(Mth.clamp((t - 5) / 40.0F, 0, 1));
            spin += (1.0F - smooth(t / ItemBossEntity.SPAWN_BURST)) * 720.0F;
        } else if (attack == BossAttack.ENRAGE && t < ItemBossEntity.ENRAGE_BURST) {
            // 変身: 枠が赤く明滅しながら縮む
            half *= 1.0F - 0.2F * smooth(t / ItemBossEntity.ENRAGE_BURST);
            if (Mth.sin(t * 1.4F) > 0) {
                r = 1.0F;
                g = 0.2F;
                b = 0.15F;
            }
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7F + 35.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.45F + 20.0F));
        renderFrame(pose, buffers, half, 0.09F, r, g, b);
        pose.popPose();
        if (enraged && death <= 0) {
            // 発狂: 逆回転する赤い外枠
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-spin * 1.3F));
            pose.mulPose(Axis.XP.rotationDegrees(-spin * 0.5F + 10.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.8F));
            float pulse = 1.0F + Mth.sin(time * 0.3F) * 0.05F;
            renderFrame(pose, buffers, half * 1.35F * pulse, 0.06F, 1.0F, 0.2F, 0.15F);
            pose.popPose();
        }

        // ---- アイテム本体
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-boss.getViewYRot(partialTick)));
        float scale = ITEM_SIZE * (1.0F - death * 0.85F);
        if (death > 0) {
            pose.mulPose(Axis.YP.rotationDegrees(time * 40.0F * death));
        } else {
            scale *= applyAttackPose(attack, t, time, pose);
        }
        // 平らなアイテムだけ厚くする。ブロック等の立体モデルを伸ばすと細長い直方体になってしまう
        boolean flat = !this.itemRenderer.getModel(stack, boss.level(), null, boss.getId()).isGui3d();
        pose.scale(scale, scale, flat ? scale * ITEM_DEPTH : scale);
        renderItem(stack, pose, buffers, overlay, boss);
        pose.popPose();

        // ---- 無敵中のシールド
        if (attack.invulnerable && death <= 0) {
            boolean red = attack == BossAttack.ENRAGE;
            float alpha = 0.07F + 0.05F * Mth.sin(time * 0.6F);
            float s = 2.1F * 1.3F;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * 1.5F));
            pose.mulPose(Axis.XP.rotationDegrees(time * 0.8F));
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            glowBox(glow, pose, -s, -s, -s, s, s, s, red ? 1.0F : 0.85F, red ? 0.3F : 0.9F, red ? 0.25F : 1.0F, alpha);
            float s2 = s * 0.9F;
            glowBox(glow, pose, -s2, -s2, -s2, s2, s2, s2, 1.0F, 1.0F, 1.0F, alpha * 0.6F);
            pose.popPose();
        }

        // ---- 攻撃演出
        if (death <= 0) {
            if (attack == BossAttack.BEAM && t <= ItemBossEntity.BEAM_CHARGE) {
                renderChargeOrbit(boss, stack, t, time, pose, buffers);
            } else if (attack == BossAttack.RAIN) {
                renderRainHalo(boss, stack, t, time, pose, buffers);
            }
            renderBeam(boss, stack, time, partialTick, pose, buffers, r, g, b);
        }

        pose.popPose();
        super.render(boss, entityYaw, partialTick, pose, buffers, light);
    }

    /** 攻撃ごとのアイテムの姿勢。戻り値は大きさの倍率。ポーズはエンティティの向き (前方 +Z) 基準 */
    private static float applyAttackPose(BossAttack attack, float t, float time, PoseStack pose) {
        // 待機中のゆらぎ
        pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(time * 0.05F) * 18.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.07F) * 8.0F));
        switch (attack) {
            case SPAWN -> {
                // 点から膨らみ (少し行き過ぎてから戻る)、回転しながら正面を向く
                float p = easeOutBack(Mth.clamp((t - 20) / 45.0F, 0, 1));
                pose.mulPose(Axis.YP.rotationDegrees((1.0F - Mth.clamp(p, 0, 1)) * 540.0F));
                return Math.max(0.0F, p);
            }
            case ENRAGE -> {
                int burst = ItemBossEntity.ENRAGE_BURST;
                if (t < burst) {
                    float p = t / burst;
                    pose.translate(Mth.sin(time * 3.7F) * 0.15F * p, Mth.cos(time * 4.3F) * 0.15F * p, Mth.sin(time * 2.9F) * 0.1F * p);
                    return 1.0F - 0.15F * smooth(p) + 0.05F * Mth.sin(time * 1.3F);
                }
                // 衝撃波と同時に一回り大きく弾け、元に戻る
                return 1.0F + 0.3F * (float) Math.exp(-(t - burst) * 0.3F);
            }
            case SWING -> {
                return swingPose(t, pose);
            }
            case DASH -> {
                return dashPose(t, ItemBossEntity.DASH_START, ItemBossEntity.DASH_END, 8.0F, time, pose);
            }
            case DASH_COMBO -> {
                if (t >= ItemBossEntity.COMBO_SWING_START) {
                    return swingPose(t - ItemBossEntity.COMBO_SWING_START, pose);
                }
                return dashPose(t % ItemBossEntity.COMBO_DASH_SEGMENT, ItemBossEntity.COMBO_DASH_WINDUP,
                        ItemBossEntity.COMBO_DASH_END, 2.0F, time, pose);
            }
            case BEAM_DIVE -> {
                // 昇る間は切っ先を上に、照射中は前に倒して反動で震える
                if (t <= 20) {
                    pose.mulPose(Axis.ZP.rotationDegrees(45.0F * smooth(t / 20.0F)));
                    return 1.0F;
                }
                pose.mulPose(Axis.XP.rotationDegrees(-30.0F));
                pose.translate(Mth.sin(time * 3.3F) * 0.05F, Mth.cos(time * 2.9F) * 0.05F, -0.15F);
                return 1.05F + Mth.sin(time * 3.0F) * 0.04F;
            }
            case SPIN_BEAM -> {
                // 独楽のように水平に寝かせて回る
                float p = smooth(Mth.clamp(t / 25.0F, 0, 1)) * (1.0F - Mth.clamp((t - 100) / 8.0F, 0, 1));
                pose.mulPose(Axis.XP.rotationDegrees(-70.0F * p));
                pose.mulPose(Axis.ZP.rotationDegrees(time * 25.0F * p));
                return 1.0F + 0.1F * p;
            }
            case BEAM -> {
                if (t <= ItemBossEntity.BEAM_CHARGE) {
                    float p = t / ItemBossEntity.BEAM_CHARGE;
                    pose.translate(Mth.sin(time * 3.3F) * 0.06F * p, Mth.cos(time * 2.9F) * 0.06F * p, 0);
                    pose.mulPose(Axis.ZP.rotationDegrees(p * p * 360.0F));
                    return 1.0F - 0.15F * p;
                }
                pose.translate(0, 0, -0.15F + Mth.sin(time * 4.0F) * 0.05F); // 反動
                return 1.05F + Mth.sin(time * 3.0F) * 0.04F;
            }
            case BARRAGE -> {
                pose.mulPose(Axis.ZP.rotationDegrees(t * 14.0F)); // 風車のように回しながら撃つ
                if (t < 8) return 1.0F;
                float k = (t - 8) % 10;
                return 1.0F + 0.3F * (float) Math.exp(-k * 0.6F);
            }
            case RAIN -> {
                float p = smooth(Mth.clamp(t / 10.0F, 0, 1)) * (1.0F - Mth.clamp((t - 48) / 10.0F, 0, 1));
                pose.translate(0, 0.8F * p, 0);
                pose.mulPose(Axis.YP.rotationDegrees(time * 20.0F * p));
                pose.mulPose(Axis.ZP.rotationDegrees(45.0F * p)); // 切っ先を上へ
                return 1.0F;
            }
            case SLAM, SLAM_BURST -> {
                pose.mulPose(Axis.YP.rotationDegrees(time * 12.0F));
                if (t < ItemBossEntity.SLAM_FALL) {
                    float p = smooth(Mth.clamp(t / ItemBossEntity.SLAM_HANG, 0, 1));
                    if (t > ItemBossEntity.SLAM_HANG) {
                        pose.translate(Mth.sin(time * 3.0F) * 0.08F, 0, Mth.cos(time * 3.4F) * 0.08F);
                    }
                    pose.mulPose(Axis.ZP.rotationDegrees(45.0F * p));
                    return 1.0F + 0.2F * p;
                }
                pose.mulPose(Axis.ZP.rotationDegrees(-135.0F)); // 切っ先を下へ
                return 1.2F;
            }
            default -> {
                return 1.0F;
            }
        }
    }

    /** 振りかぶり → 寝かせて一回転の薙ぎ払い → 戻る */
    private static float swingPose(float t, PoseStack pose) {
        int strike = ItemBossEntity.SWING_STRIKE;
        float wind = t < strike ? smooth(t / strike) : 1.0F - Mth.clamp((t - strike - 8) / 6.0F, 0, 1);
        float yaw = t < strike ? 50.0F * smooth(t / strike) : 50.0F - 410.0F * easeOut(Mth.clamp((t - strike) / 6.0F, 0, 1));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-80.0F * wind));
        // 柄を中心に寄せ、先端を外へ伸ばす
        pose.translate(0.9F * wind, 0.9F * wind, 0);
        return 1.0F + 0.35F * wind;
    }

    /** 溜め (震え) → 切っ先を前に錐揉み突進 → 戻る */
    private static float dashPose(float t, int start, int end, float recover, float time, PoseStack pose) {
        float p;
        if (t < start) {
            p = smooth(t / start);
            pose.translate(Mth.sin(time * 2.7F) * 0.1F * p, Mth.cos(time * 3.1F) * 0.1F * p, 0);
        } else if (t < end) {
            p = 1.0F;
            pose.mulPose(Axis.ZP.rotationDegrees((t - start) * 45.0F)); // 錐揉み
        } else {
            p = 1.0F - Mth.clamp((t - end) / recover, 0, 1);
        }
        // 画像の右上 (切っ先) を前方へ
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F * p));
        pose.mulPose(Axis.ZP.rotationDegrees(-45.0F * p));
        return 1.0F + 0.15F * p;
    }

    private void renderItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int overlay, ItemBossEntity boss) {
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, FULL_BRIGHT, overlay, pose, buffers, boss.level(), boss.getId());
    }

    // ---------------------------------------------------------------- beam

    /** 溜め中: アイテムの複製が中心へ渦を巻いて吸い込まれる */
    private void renderChargeOrbit(ItemBossEntity boss, ItemStack stack, float t, float time, PoseStack pose, MultiBufferSource buffers) {
        float p = t / ItemBossEntity.BEAM_CHARGE;
        float radius = 4.5F * (1.0F - smooth(p)) + 0.4F;
        for (int i = 0; i < 6; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * (6.0F + 18.0F * p) + i * 60.0F));
            pose.mulPose(Axis.XP.rotationDegrees(25.0F * Mth.sin(i * 1.7F + time * 0.1F)));
            pose.translate(radius, 0, 0);
            pose.mulPose(Axis.YP.rotationDegrees(time * 25.0F));
            float s = 0.9F * (1.0F - 0.6F * p);
            pose.scale(s, s, s);
            renderItem(stack, pose, buffers, OverlayTexture.NO_OVERLAY, boss);
            pose.popPose();
        }
    }

    private void renderBeam(ItemBossEntity boss, ItemStack stack, float time, float partialTick, PoseStack pose,
                            MultiBufferSource buffers, float r, float g, float b) {
        int state = boss.getBeamState();
        boolean telegraph = state == ItemBossEntity.BEAM_AIM || state == ItemBossEntity.BEAM_LOCKED;
        float k = boss.getBeamVisual(partialTick);
        if (!telegraph && k <= 0) return;
        float yaw = Mth.rotLerp(partialTick, boss.beamYawO, boss.getBeamYaw());
        float pitch = Mth.lerp(partialTick, boss.beamPitchO, boss.getBeamPitch());
        float len = boss.getBeamLength();

        if (len <= 0.01F) return;

        pose.pushPose();
        // ここから先は +Z がビームの進行方向
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());

        if (telegraph) {
            // 予告線。発射直前 (照準固定) は太くなって点滅
            boolean locked = state == ItemBossEntity.BEAM_LOCKED;
            float w = locked ? 0.08F : 0.04F;
            float a = locked ? (Mth.sin(time * 2.5F) > 0 ? 0.9F : 0.25F) : 0.45F;
            glowBox(glow, pose, -w, -w, 0, w, w, len, r, g, b, a);
            pose.popPose();
            return;
        }

        float fire = boss.getBeamFireTicks() + partialTick;
        float flicker = 1.0F + 0.12F * Mth.sin(time * 2.3F);

        // 光の芯 (3 層)
        float w1 = 0.95F * k * flicker;
        float w2 = 0.5F * k * flicker;
        float w3 = 0.18F * k;
        glowBox(glow, pose, -w1, -w1, 0, w1, w1, len, r, g, b, 0.1F);
        glowBox(glow, pose, -w2, -w2, 0, w2, w2, len, r, g, b, 0.25F);
        glowBox(glow, pose, -w3, -w3, 0, w3, w3, len, 1, 1, 1, 0.6F);

        // ビーム軸方向に引き伸ばしたアイテムを 3 枚、軸まわりに回しながら重ねる
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(time * 15.0F + i * 60.0F));
            pose.translate(0, 0, len / 2.0F);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F)); // アイテムの X 軸 → ビーム軸
            pose.scale(len, 1.4F * k * flicker, 1.0F);
            renderItem(stack, pose, buffers, OverlayTexture.NO_OVERLAY, boss);
            pose.popPose();
        }

        // ビームに乗って流れていくアイテム
        float spacing = 2.5F;
        int count = Math.max(1, (int) (len / spacing));
        for (int i = 0; i < count; i++) {
            float z = (fire * 1.2F + i * spacing) % (count * spacing);
            if (z > len) continue;
            pose.pushPose();
            pose.translate(0, 0, z);
            pose.mulPose(Axis.ZP.rotationDegrees(time * 25.0F + i * 40.0F));
            float s = 1.3F * k;
            pose.scale(s, s, s);
            renderItem(stack, pose, buffers, OverlayTexture.NO_OVERLAY, boss);
            pose.popPose();
        }

        // 着弾点
        pose.pushPose();
        pose.translate(0, 0, len);
        pose.mulPose(Axis.YP.rotationDegrees(time * 30.0F));
        pose.mulPose(Axis.XP.rotationDegrees(time * 17.0F));
        float s = 2.2F * k * flicker;
        pose.scale(s, s, s);
        renderItem(stack, pose, buffers, OverlayTexture.NO_OVERLAY, boss);
        pose.popPose();

        pose.popPose();
    }

    /** RAIN: 頭上でアイテムの輪が回る */
    private void renderRainHalo(ItemBossEntity boss, ItemStack stack, float t, float time, PoseStack pose, MultiBufferSource buffers) {
        float p = smooth(Mth.clamp(t / 10.0F, 0, 1)) * (1.0F - Mth.clamp((t - 48) / 10.0F, 0, 1));
        if (p <= 0) return;
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.translate(0, 2.8F * p, 0);
            pose.mulPose(Axis.YP.rotationDegrees(time * 10.0F + i * 45.0F));
            pose.translate(2.4F, 0, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-135.0F)); // 切っ先を下へ
            float s = 1.0F * p;
            pose.scale(s, s, s);
            renderItem(stack, pose, buffers, OverlayTexture.NO_OVERLAY, boss);
            pose.popPose();
        }
    }

    // ---------------------------------------------------------------- frame

    /**
     * 立方体の 12 辺 + 角。
     * 不透明パスと発光パスは分けて書くこと: どちらも固定バッファを持たない RenderType なので、
     * 交互に getBuffer すると先に取った方のバッチが閉じられ、その VertexConsumer への書き込みで落ちる。
     */
    private static void renderFrame(PoseStack pose, MultiBufferSource buffers, float h, float th, float r, float g, float b) {
        float[][] edges = new float[12][];
        int e = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < 4; i++) {
                float u = (i & 1) == 0 ? -h : h;
                float v = (i & 2) == 0 ? -h : h;
                float[] box = new float[6];
                int a1 = (axis + 1) % 3;
                int a2 = (axis + 2) % 3;
                box[axis] = -h - th;
                box[axis + 3] = h + th;
                box[a1] = u - th;
                box[a1 + 3] = u + th;
                box[a2] = v - th;
                box[a2 + 3] = v + th;
                edges[e++] = box;
            }
        }

        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(FRAME_TEXTURE));
        for (float[] box : edges) {
            texturedBox(solid, pose, box[0], box[1], box[2], box[3], box[4], box[5], r, g, b);
        }
        // 角の飾り
        float c = th * 2.0F;
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? -h : h;
            float y = (i & 2) == 0 ? -h : h;
            float z = (i & 4) == 0 ? -h : h;
            texturedBox(solid, pose, x - c, y - c, z - c, x + c, y + c, z + c, 1, 1, 1);
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        float gw = th * 1.6F;
        for (float[] box : edges) {
            glowBox(glow, pose, box[0] - gw, box[1] - gw, box[2] - gw, box[3] + gw, box[4] + gw, box[5] + gw, r, g, b, 0.18F);
        }
    }

    private static void texturedBox(VertexConsumer vc, PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1,
                                    float r, float g, float b) {
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f n = last.normal();
        texQuad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r, g, b);
        texQuad(vc, m, n, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, r, g, b);
        texQuad(vc, m, n, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, r, g, b);
        texQuad(vc, m, n, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, r, g, b);
        texQuad(vc, m, n, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, r, g, b);
        texQuad(vc, m, n, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, r, g, b);
    }

    private static void texQuad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                                float ax, float ay, float az, float bx, float by, float bz,
                                float cx, float cy, float cz, float dx, float dy, float dz,
                                float nx, float ny, float nz, float r, float g, float b) {
        texVertex(vc, m, n, ax, ay, az, 0, 0, nx, ny, nz, r, g, b);
        texVertex(vc, m, n, bx, by, bz, 1, 0, nx, ny, nz, r, g, b);
        texVertex(vc, m, n, cx, cy, cz, 1, 1, nx, ny, nz, r, g, b);
        texVertex(vc, m, n, dx, dy, dz, 0, 1, nx, ny, nz, r, g, b);
    }

    private static void texVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                                  float nx, float ny, float nz, float r, float g, float b) {
        vc.vertex(m, x, y, z).color(r, g, b, 1.0F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT)
                .normal(n, nx, ny, nz).endVertex();
    }

    /** 加算合成の光る箱。lightning は面カリングが有効なので両面を張る */
    private static void glowBox(VertexConsumer vc, PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1,
                                float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        glowQuad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        glowQuad(vc, m, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, a);
        glowQuad(vc, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        glowQuad(vc, m, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, r, g, b, a);
        glowQuad(vc, m, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, r, g, b, a);
        glowQuad(vc, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
    }

    private static void glowQuad(VertexConsumer vc, Matrix4f m,
                                 float ax, float ay, float az, float bx, float by, float bz,
                                 float cx, float cy, float cz, float dx, float dy, float dz,
                                 float r, float g, float b, float a) {
        vc.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        vc.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = x - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private static float easeOut(float x) {
        float inv = 1 - x;
        return 1 - inv * inv * inv;
    }

    @Override
    public ResourceLocation getTextureLocation(ItemBossEntity boss) {
        return FRAME_TEXTURE;
    }
}
