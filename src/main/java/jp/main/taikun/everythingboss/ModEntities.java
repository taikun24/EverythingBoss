package jp.main.taikun.everythingboss;

import jp.main.taikun.everythingboss.entity.ItemBossEntity;
import jp.main.taikun.everythingboss.entity.ItemShardEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EverythingBoss.MODID);

    public static final RegistryObject<EntityType<ItemBossEntity>> ITEM_BOSS = ENTITIES.register("item_boss",
            () -> EntityType.Builder.of(ItemBossEntity::new, MobCategory.MONSTER)
                    .sized(3.5F, 3.5F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("item_boss"));

    public static final RegistryObject<EntityType<ItemShardEntity>> ITEM_SHARD = ENTITIES.register("item_shard",
            () -> EntityType.Builder.<ItemShardEntity>of(ItemShardEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build("item_shard"));

    private ModEntities() {}
}
