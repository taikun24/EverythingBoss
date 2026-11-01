package jp.main.taikun.everythingboss;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue BASE_HEALTH;
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue DROP_ITEM_ON_DEATH;
    public static final ModConfigSpec.IntValue EXPERIENCE;
    public static final ModConfigSpec.DoubleValue MAX_DAMAGE_PER_HIT;
    public static final ModConfigSpec.DoubleValue HEALTH_PER_EXTRA_PLAYER;
    public static final ModConfigSpec.BooleanValue ITEM_TRAITS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("boss");
        BASE_HEALTH = b.comment("Base max health before item-derived multipliers (rarity, enchantments).")
                .defineInRange("baseHealth", 300.0, 1.0, 1_000_000.0);
        DAMAGE_MULTIPLIER = b.comment("Multiplier applied to every attack of the boss.")
                .defineInRange("damageMultiplier", 1.0, 0.0, 1000.0);
        // コンポーネントごと複製されるので、中身入りシュルカーボックス等をボスにすると増殖できてしまう
        DROP_ITEM_ON_DEATH = b.comment("Drop a copy of the boss item (components included) on death.",
                        "Off by default: summoning a boss from e.g. a filled shulker box would duplicate its contents.")
                .define("dropItemOnDeath", false);
        EXPERIENCE = b.comment("Experience dropped on death.")
                .defineInRange("experience", 200, 0, 100000);
        MAX_DAMAGE_PER_HIT = b.comment("Largest fraction of the boss's max health a single hit can remove (1.0 = no cap).",
                        "Keeps overpowered weapons from one-shotting the boss.")
                .defineInRange("maxDamagePerHit", 0.5, 0.001, 1.0);
        HEALTH_PER_EXTRA_PLAYER = b.comment("Extra max health for each additional player near the boss when it appears (0.5 = +50% per player).",
                        "Players in creative or spectator mode are not counted.")
                .defineInRange("healthPerExtraPlayer", 0.5, 0.0, 10.0);
        ITEM_TRAITS = b.comment("Let the item shape the fight: fire items burn, potions apply their effects, ender pearls teleport, and so on.",
                        "Which items have which trait is set by the item and enchantment tags everythingboss:traits/<name>.")
                .define("itemTraits", true);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}
