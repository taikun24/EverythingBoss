package jp.main.taikun.everythingboss.entity;

import com.mojang.datafixers.util.Pair;
import jp.main.taikun.everythingboss.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SuspiciousStewItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * ボスのアイテムから読み取った性質。
 * <p>
 * {@link BossTrait} のほかに、アイテム自体が持つ効果 (ポーション・怪しいシチュー・食べ物) も使う。
 * 有害な効果は攻撃を当てた相手に、有益な効果は登場時にボス自身に掛かる。
 */
public record ItemTraits(Set<BossTrait> traits, List<MobEffectInstance> onHit, List<MobEffectInstance> self) {
    public static final ItemTraits NONE = new ItemTraits(EnumSet.noneOf(BossTrait.class), List.of(), List.of());

    /** 食べ物の効果は、この確率以上で付くものだけ使う (生の鶏肉の空腹 30% などは除く) */
    private static final float MIN_FOOD_EFFECT_CHANCE = 0.5F;
    /** 怪しいシチューの効果時間が書かれていないときの長さ (バニラの既定と同じ) */
    private static final int DEFAULT_STEW_DURATION = 160;

    public boolean has(BossTrait trait) {
        return this.traits.contains(trait);
    }

    public static ItemTraits of(ItemStack stack) {
        if (stack.isEmpty() || !Config.ITEM_TRAITS.get()) return NONE;

        EnumSet<BossTrait> traits = EnumSet.noneOf(BossTrait.class);
        // エンチャントの本も、保存しているエンチャントで判定する (getEnchantments は本なら StoredEnchantments を読む)
        Set<Enchantment> enchantments = EnchantmentHelper.getEnchantments(stack).keySet();
        for (BossTrait trait : BossTrait.values()) {
            if (stack.is(trait.itemTag) || enchantments.stream()
                    .anyMatch(e -> BuiltInRegistries.ENCHANTMENT.wrapAsHolder(e).is(trait.enchantmentTag))) {
                traits.add(trait);
            }
        }

        // ポーション・効果付きの矢 (Potion と CustomPotionEffects を両方読む)
        List<MobEffectInstance> effects = new ArrayList<>(PotionUtils.getMobEffects(stack));
        readStewEffects(stack, effects);
        FoodProperties food = stack.getItem().getFoodProperties();
        if (food != null) {
            for (Pair<MobEffectInstance, Float> e : food.getEffects()) {
                if (e.getSecond() >= MIN_FOOD_EFFECT_CHANCE) effects.add(new MobEffectInstance(e.getFirst()));
            }
        }

        List<MobEffectInstance> onHit = new ArrayList<>();
        List<MobEffectInstance> self = new ArrayList<>();
        for (MobEffectInstance effect : effects) {
            switch (effect.getEffect().getCategory()) {
                case HARMFUL -> onHit.add(effect);
                // 即時の回復などは、満タンで登場するボスには意味がないので使わない
                case BENEFICIAL -> {
                    if (!effect.getEffect().isInstantenous()) self.add(effect);
                }
                default -> {}
            }
        }
        if (traits.isEmpty() && onHit.isEmpty() && self.isEmpty()) return NONE;
        return new ItemTraits(traits, List.copyOf(onHit), List.copyOf(self));
    }

    /** 怪しいシチューの効果。読み取り用のメソッドが private なので、NBT を直接読む */
    private static void readStewEffects(ItemStack stack, List<MobEffectInstance> out) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(SuspiciousStewItem.EFFECTS_TAG, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(SuspiciousStewItem.EFFECTS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            MobEffect effect = MobEffect.byId(entry.getInt(SuspiciousStewItem.EFFECT_ID_TAG));
            if (effect == null) continue;
            int duration = entry.contains(SuspiciousStewItem.EFFECT_DURATION_TAG, Tag.TAG_ANY_NUMERIC)
                    ? entry.getInt(SuspiciousStewItem.EFFECT_DURATION_TAG) : DEFAULT_STEW_DURATION;
            out.add(new MobEffectInstance(effect, duration));
        }
    }
}
