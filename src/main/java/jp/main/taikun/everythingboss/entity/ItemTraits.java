package jp.main.taikun.everythingboss.entity;

import jp.main.taikun.everythingboss.Config;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
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

    public boolean has(BossTrait trait) {
        return this.traits.contains(trait);
    }

    public static ItemTraits of(ItemStack stack) {
        if (stack.isEmpty() || !Config.ITEM_TRAITS.get()) return NONE;

        EnumSet<BossTrait> traits = EnumSet.noneOf(BossTrait.class);
        // エンチャントの本も、保存しているエンチャントで判定する
        Set<Holder<Enchantment>> enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet();
        for (BossTrait trait : BossTrait.values()) {
            if (stack.is(trait.itemTag) || enchantments.stream().anyMatch(e -> e.is(trait.enchantmentTag))) {
                traits.add(trait);
            }
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) potion.getAllEffects().forEach(effects::add);
        SuspiciousStewEffects stew = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stew != null) stew.effects().forEach(e -> effects.add(e.createEffectInstance()));
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            for (FoodProperties.PossibleEffect e : food.effects()) {
                if (e.probability() >= MIN_FOOD_EFFECT_CHANCE) effects.add(e.effect());
            }
        }

        List<MobEffectInstance> onHit = new ArrayList<>();
        List<MobEffectInstance> self = new ArrayList<>();
        for (MobEffectInstance effect : effects) {
            switch (effect.getEffect().value().getCategory()) {
                case HARMFUL -> onHit.add(effect);
                // 即時の回復などは、満タンで登場するボスには意味がないので使わない
                case BENEFICIAL -> {
                    if (!effect.getEffect().value().isInstantenous()) self.add(effect);
                }
                default -> {}
            }
        }
        if (traits.isEmpty() && onHit.isEmpty() && self.isEmpty()) return NONE;
        return new ItemTraits(traits, List.copyOf(onHit), List.copyOf(self));
    }
}
