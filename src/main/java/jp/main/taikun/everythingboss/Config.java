package jp.main.taikun.everythingboss;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue BASE_HEALTH;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue DROP_ITEM_ON_DEATH;
    public static final ForgeConfigSpec.IntValue EXPERIENCE;
    public static final ForgeConfigSpec.DoubleValue MAX_DAMAGE_PER_HIT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("boss");
        BASE_HEALTH = b.comment("Base max health before item-derived multipliers (rarity, enchantments).")
                .defineInRange("baseHealth", 300.0, 1.0, 1_000_000.0);
        DAMAGE_MULTIPLIER = b.comment("Multiplier applied to every attack of the boss.")
                .defineInRange("damageMultiplier", 1.0, 0.0, 1000.0);
        // NBT ごと複製されるので、中身入りシュルカーボックス等をボスにすると増殖できてしまう
        DROP_ITEM_ON_DEATH = b.comment("Drop a copy of the boss item (NBT included) on death.",
                        "Off by default: summoning a boss from e.g. a filled shulker box would duplicate its contents.")
                .define("dropItemOnDeath", false);
        EXPERIENCE = b.comment("Experience dropped on death.")
                .defineInRange("experience", 200, 0, 100000);
        MAX_DAMAGE_PER_HIT = b.comment("Largest fraction of the boss's max health a single hit can remove (1.0 = no cap).",
                        "Keeps overpowered weapons from one-shotting the boss.")
                .defineInRange("maxDamagePerHit", 0.5, 0.001, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}
