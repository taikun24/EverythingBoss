package jp.main.taikun.everythingboss;

import jp.main.taikun.everythingboss.entity.ItemBossEntity;
import jp.main.taikun.everythingboss.entity.ItemShardEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EverythingBoss.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ItemBossEntity>> ITEM_BOSS = ENTITIES.register("item_boss",
            () -> EntityType.Builder.of(ItemBossEntity::new, MobCategory.MONSTER)
                    .sized(3.5F, 3.5F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("item_boss"));

    public static final DeferredHolder<EntityType<?>, EntityType<ItemShardEntity>> ITEM_SHARD = ENTITIES.register("item_shard",
            () -> EntityType.Builder.<ItemShardEntity>of(ItemShardEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build("item_shard"));

    private ModEntities() {}
}
