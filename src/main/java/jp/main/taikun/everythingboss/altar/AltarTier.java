package jp.main.taikun.everythingboss.altar;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** 祭壇の種類 = ボスの難易度。ドロップはボスのアイテム (NBT 込み) の複製 */
public enum AltarTier {
    //         HP   攻撃  技の間隔 ドロップ 経験値
    STONE(0.6, 0.6, 1.4, 2, 0.5, ChatFormatting.GRAY, BossEvent.BossBarColor.WHITE),
    GOLDEN(1.0, 1.0, 1.0, 4, 1.0, ChatFormatting.YELLOW, BossEvent.BossBarColor.YELLOW),
    DIAMOND(1.8, 1.4, 0.8, 16, 2.0, ChatFormatting.AQUA, BossEvent.BossBarColor.BLUE),
    NETHERITE(3.0, 2.0, 0.6, 64, 4.0, ChatFormatting.DARK_RED, BossEvent.BossBarColor.RED);

    public final double healthMultiplier;
    public final double damageMultiplier;
    /** 技と技の間の待ち時間に掛ける。小さいほど攻撃が激しい */
    public final double cooldownMultiplier;
    public final int drops;
    public final double experienceMultiplier;
    public final ChatFormatting color;
    public final BossEvent.BossBarColor barColor;

    AltarTier(double health, double damage, double cooldown, int drops, double experience, ChatFormatting color, BossEvent.BossBarColor barColor) {
        this.healthMultiplier = health;
        this.damageMultiplier = damage;
        this.cooldownMultiplier = cooldown;
        this.drops = drops;
        this.experienceMultiplier = experience;
        this.color = color;
        this.barColor = barColor;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 難易度名 (かんたん / ふつう / ...) */
    public Component displayName() {
        return Component.translatable("altar.everythingboss.tier." + id()).withStyle(this.color);
    }

    @Nullable
    public static AltarTier byId(String id) {
        for (AltarTier tier : values()) {
            if (tier.id().equals(id)) return tier;
        }
        return null;
    }
}
