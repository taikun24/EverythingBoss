package jp.main.taikun.everythingboss.entity;

import jp.main.taikun.everythingboss.EverythingBoss;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Locale;

/**
 * アイテムから決まるボスの性質。
 * アイテムタグ {@code everythingboss:traits/<名前>} に入っているか、
 * 同名のエンチャントタグに入っているエンチャントが付いていれば持つ。データパックで増やせる。
 */
public enum BossTrait {
    /** 当てた相手を燃やす */
    FIRE,
    /** 当てた相手を凍えさせる (鈍化と凍結) */
    FROST,
    /** 当てた相手をウィザー状態にする */
    WITHER,
    /** 弾が着弾で爆ぜる。地形もアイテムも壊さない */
    EXPLOSIVE,
    /** 待機中にときどき相手の近くへ瞬間移動する */
    ENDER,
    /** 吹き飛ばしが強い */
    KNOCKBACK,
    /** 近接攻撃を受けると相手にダメージを返す */
    THORNS;

    public final TagKey<Item> itemTag;
    public final TagKey<Enchantment> enchantmentTag;

    BossTrait() {
        String path = "traits/" + name().toLowerCase(Locale.ROOT);
        this.itemTag = TagKey.create(Registries.ITEM, EverythingBoss.id(path));
        this.enchantmentTag = TagKey.create(Registries.ENCHANTMENT, EverythingBoss.id(path));
    }
}
