package jp.main.taikun.everythingboss.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import jp.main.taikun.everythingboss.ModEntities;
import jp.main.taikun.everythingboss.entity.ItemBossEntity;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import jp.main.taikun.everythingboss.entity.BossAttack;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * /itemboss summon &lt;item&gt; [pos]  — NBT 付きで指定可 (例: diamond_sword{Enchantments:[...]})
 * /itemboss hand [pos]            — 実行者のメインハンドのアイテム
 * /itemboss attack &lt;boss&gt; &lt;name&gt;  — 技を強制発動 (確認・マップ制作用)
 */
public final class ItemBossCommand {
    private static final SimpleCommandExceptionType UNKNOWN_ATTACK =
            new SimpleCommandExceptionType(Component.translatable("commands.everythingboss.unknown_attack"));
    private static final SimpleCommandExceptionType EMPTY_HAND =
            new SimpleCommandExceptionType(Component.translatable("commands.everythingboss.empty_hand"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("itemboss")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(c -> summon(c.getSource(), ItemArgument.getItem(c, "item").createItemStack(1, false), null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(c -> summon(c.getSource(), ItemArgument.getItem(c, "item").createItemStack(1, false),
                                                Vec3Argument.getVec3(c, "pos"))))))
                .then(Commands.literal("attack")
                        .then(Commands.argument("boss", EntityArgument.entities())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(BossAttack.values()).filter(a -> a != BossAttack.NONE)
                                                        .map(a -> a.name().toLowerCase(Locale.ROOT)), b))
                                        .executes(c -> forceAttack(c.getSource(), EntityArgument.getEntities(c, "boss"),
                                                StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("hand")
                        .executes(c -> summon(c.getSource(), handItem(c.getSource()), null))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(c -> summon(c.getSource(), handItem(c.getSource()), Vec3Argument.getVec3(c, "pos"))))));
    }

    private static int forceAttack(CommandSourceStack source, java.util.Collection<? extends Entity> entities, String name)
            throws CommandSyntaxException {
        BossAttack attack;
        try {
            attack = BossAttack.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_ATTACK.create();
        }
        if (attack == BossAttack.NONE) throw UNKNOWN_ATTACK.create();
        int count = 0;
        for (Entity e : entities) {
            if (e instanceof ItemBossEntity boss && boss.forceAttack(attack)) count++;
        }
        int n = count;
        source.sendSuccess(() -> Component.translatable("commands.everythingboss.attack", attack.name().toLowerCase(Locale.ROOT), n), true);
        return count;
    }

    private static ItemStack handItem(CommandSourceStack source) throws CommandSyntaxException {
        ItemStack stack = source.getPlayerOrException().getMainHandItem();
        if (stack.isEmpty()) throw EMPTY_HAND.create();
        return stack.copy();
    }

    private static int summon(CommandSourceStack source, ItemStack stack, @Nullable Vec3 pos) {
        ItemBossEntity boss = ModEntities.ITEM_BOSS.get().create(source.getLevel());
        if (boss == null) return 0;
        Entity executor = source.getEntity();
        if (pos == null) {
            // 実行者に重ならないよう、向いている方向の少し先に出す
            pos = source.getPosition();
            if (executor != null) {
                Vec3 look = executor.getLookAngle().multiply(1, 0, 1).normalize();
                pos = pos.add(look.scale(6.0)).add(0, 1.0, 0);
            }
        }
        boss.moveTo(pos.x, pos.y, pos.z, executor != null ? executor.getYRot() + 180.0F : 0.0F, 0.0F);
        boss.setBossItem(stack);
        if (executor instanceof LivingEntity living && !(executor instanceof net.minecraft.world.entity.player.Player p && p.isCreative())) {
            boss.setTarget(living);
        }
        source.getLevel().addFreshEntity(boss);
        source.sendSuccess(() -> Component.translatable("commands.everythingboss.summoned", stack.getDisplayName()).append(" ").append(boss.statsLine()), true);
        return 1;
    }

    private ItemBossCommand() {}
}
