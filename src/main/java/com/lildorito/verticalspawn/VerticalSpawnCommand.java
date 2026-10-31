package com.lildorito.verticalspawn;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /verticalspawn enable|disable
 * /verticalspawn area border <minY> <maxY>
 * /verticalspawn area box <from> <to> <minY> <maxY>
 * /verticalspawn ceiling <true|false>
 * /verticalspawn teleport <targets>
 * /verticalspawn spread <center> <spreadDistance> <maxRange> [under <maxHeight>] <respectTeams> <targets>
 * /verticalspawn zone add <name> <from> <to> <minY> <maxY> [ceiling]
 * /verticalspawn zone ceiling <name> <true|false>
 * /verticalspawn zone remove <name> | list | clear
 * /verticalspawn status
 *
 * Permission level 2, so it works from datapack functions, command blocks and ops.
 */
@Mod.EventBusSubscriber(modid = VerticalSpawn.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VerticalSpawnCommand
{
    private VerticalSpawnCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(VerticalSpawn.MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enable").executes(ctx -> setEnabled(ctx, true)))
                .then(Commands.literal("disable").executes(ctx -> setEnabled(ctx, false)))
                .then(Commands.literal("area")
                        .then(Commands.literal("border")
                                .then(Commands.argument("minY", IntegerArgumentType.integer())
                                        .then(Commands.argument("maxY", IntegerArgumentType.integer())
                                                .executes(VerticalSpawnCommand::setBorderArea))))
                        .then(Commands.literal("box")
                                .then(Commands.argument("from", ColumnPosArgument.columnPos())
                                        .then(Commands.argument("to", ColumnPosArgument.columnPos())
                                                .then(Commands.argument("minY", IntegerArgumentType.integer())
                                                        .then(Commands.argument("maxY", IntegerArgumentType.integer())
                                                                .executes(VerticalSpawnCommand::setBoxArea)))))))
                .then(Commands.literal("ceiling")
                        .then(Commands.argument("required", BoolArgumentType.bool())
                                .executes(VerticalSpawnCommand::setCeiling)))
                .then(Commands.literal("teleport")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(VerticalSpawnCommand::teleport)))
                // Same arguments as /spreadplayers, including the optional "under <maxHeight>".
                .then(Commands.literal("spread")
                        .then(Commands.argument("center", Vec2Argument.vec2())
                                .then(Commands.argument("spreadDistance", FloatArgumentType.floatArg(0.0F))
                                        .then(Commands.argument("maxRange", FloatArgumentType.floatArg(1.0F))
                                                .then(Commands.argument("respectTeams", BoolArgumentType.bool())
                                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                                .executes(ctx -> spread(ctx, null))))
                                                .then(Commands.literal("under")
                                                        .then(Commands.argument("maxHeight", IntegerArgumentType.integer())
                                                                .then(Commands.argument("respectTeams", BoolArgumentType.bool())
                                                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                                                .executes(ctx -> spread(ctx,
                                                                                        IntegerArgumentType.getInteger(ctx, "maxHeight")))))))))))
                .then(zoneCommand())
                .then(Commands.literal("status").executes(VerticalSpawnCommand::status)));
    }

    private static int spread(CommandContext<CommandSourceStack> ctx, @Nullable Integer maxHeight) throws CommandSyntaxException
    {
        Vec2 center = Vec2Argument.getVec2(ctx, "center");
        float spreadDistance = FloatArgumentType.getFloat(ctx, "spreadDistance");
        float maxRange = FloatArgumentType.getFloat(ctx, "maxRange");
        boolean respectTeams = BoolArgumentType.getBool(ctx, "respectTeams");
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");

        ServerLevel level = ctx.getSource().getLevel();
        VerticalSpawnData data = data(ctx);
        List<SpawnFinder.Bounds> areas = SpawnFinder.spreadBounds(level, data, center.x, center.y, maxRange, maxHeight);
        if (areas.isEmpty())
        {
            ctx.getSource().sendFailure(Component.literal(data.hasZones()
                    ? "Vertical Spawn: no zone overlaps the spread square (or the world border / maxHeight cuts them off)"
                    : "Vertical Spawn: spread area is outside the world border or the configured Y range"));
            return 0;
        }

        // Like /spreadplayers: with respectTeams, everyone on the same team lands on the same spot.
        Map<String, List<Entity>> groups = new LinkedHashMap<>();
        for (Entity entity : targets)
        {
            Team team = respectTeams ? entity.getTeam() : null;
            String key = team != null ? "team:" + team.getName() : "entity:" + entity.getStringUUID();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
        }

        List<Vec3> spots = SpawnFinder.spread(level, areas, groups.size(), spreadDistance);
        if (spots == null)
        {
            ctx.getSource().sendFailure(Component.literal(
                    "Vertical Spawn: could not fit " + groups.size() + (respectTeams ? " teams" : " entities")
                            + " " + spreadDistance + " blocks apart. Try a smaller spreadDistance or a bigger maxRange"));
            return 0;
        }

        int i = 0;
        for (List<Entity> group : groups.values())
        {
            Vec3 spot = spots.get(i++);
            for (Entity entity : group)
            {
                RespawnHandler.teleportEntity(entity, level, spot);
            }
        }

        int entityCount = targets.size();
        int groupCount = groups.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Vertical Spawn: spread " + entityCount + " entities"
                        + (respectTeams ? " in " + groupCount + " teams" : "")
                        + " around " + center.x + ", " + center.y
                        + (data.hasZones() ? " across " + areas.size() + " zone(s)"
                                : " (Y " + areas.get(0).minY() + " to " + areas.get(0).maxY() + ")")), true);
        return entityCount;
    }

    private static VerticalSpawnData data(CommandContext<CommandSourceStack> ctx)
    {
        return VerticalSpawnData.get(ctx.getSource().getServer());
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled)
    {
        data(ctx).setEnabled(enabled);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Vertical Spawn " + (enabled ? "enabled" : "disabled")), true);
        return 1;
    }

    private static int setBorderArea(CommandContext<CommandSourceStack> ctx)
    {
        int minY = IntegerArgumentType.getInteger(ctx, "minY");
        int maxY = IntegerArgumentType.getInteger(ctx, "maxY");
        VerticalSpawnData data = data(ctx);
        data.setBorderArea(minY, maxY);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Vertical Spawn area: whole world border, Y " + data.minY() + " to " + data.maxY()), true);
        return 1;
    }

    private static int setBoxArea(CommandContext<CommandSourceStack> ctx)
    {
        ColumnPos from = ColumnPosArgument.getColumnPos(ctx, "from");
        ColumnPos to = ColumnPosArgument.getColumnPos(ctx, "to");
        int minY = IntegerArgumentType.getInteger(ctx, "minY");
        int maxY = IntegerArgumentType.getInteger(ctx, "maxY");
        VerticalSpawnData data = data(ctx);
        data.setBoxArea(from.x(), from.z(), to.x(), to.z(), minY, maxY);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Vertical Spawn area: X " + data.minX() + " to " + data.maxX()
                        + ", Z " + data.minZ() + " to " + data.maxZ()
                        + ", Y " + data.minY() + " to " + data.maxY()), true);
        return 1;
    }

    private static int setCeiling(CommandContext<CommandSourceStack> ctx)
    {
        boolean required = BoolArgumentType.getBool(ctx, "required");
        data(ctx).setRequireCeiling(required);
        ctx.getSource().sendSuccess(() -> Component.literal(required
                ? "Vertical Spawn: only spots with a ceiling above will be used"
                : "Vertical Spawn: open-sky spots are allowed"), true);
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        ServerLevel overworld = ctx.getSource().getServer().overworld();
        VerticalSpawnData data = data(ctx);

        int moved = 0;
        for (ServerPlayer player : targets)
        {
            if (RespawnHandler.teleportToRandomSpot(player, overworld, data)) moved++;
        }

        int count = moved;
        int failed = targets.size() - moved;
        if (count == 0)
        {
            ctx.getSource().sendFailure(Component.literal(
                    "Vertical Spawn: no safe spot found in the area. Check /verticalspawn status"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Vertical Spawn: teleported " + count + " player(s)"
                        + (failed > 0 ? ", " + failed + " had no safe spot" : "")), true);
        return count;
    }

    private static int status(CommandContext<CommandSourceStack> ctx)
    {
        VerticalSpawnData data = data(ctx);
        ServerLevel overworld = ctx.getSource().getServer().overworld();
        StringBuilder msg = new StringBuilder("Vertical Spawn: " + (data.isEnabled() ? "ENABLED" : "disabled"));

        if (data.hasZones())
        {
            List<SpawnFinder.Bounds> usable = SpawnFinder.resolveAll(overworld, data);
            msg.append("\n  Mode: zones (").append(data.zones().size()).append(", equal chance each)");
            for (VerticalSpawnData.Zone zone : data.zones())
            {
                boolean ok = usable.stream().anyMatch(b -> b.name().equals(zone.name()));
                msg.append("\n  - ").append(zone.describe()).append(ok ? "" : "  [OUTSIDE BORDER - unused]");
            }
            msg.append("\n  (the single area and global ceiling setting are ignored while zones exist)");
        }
        else
        {
            SpawnFinder.Bounds b = SpawnFinder.resolveArea(overworld, data);
            String area = data.useBox()
                    ? "box X " + data.minX() + " to " + data.maxX() + ", Z " + data.minZ() + " to " + data.maxZ()
                    : "whole world border";
            String effective = b == null
                    ? "EMPTY (area is outside the world border or build height)"
                    : "X " + b.minX() + " to " + b.maxX() + ", Z " + b.minZ() + " to " + b.maxZ()
                            + ", Y " + b.minY() + " to " + b.maxY();
            msg.append("\n  Mode: single area")
                    .append("\n  Area: ").append(area).append(", Y ").append(data.minY()).append(" to ").append(data.maxY())
                    .append("\n  Ceiling required: ").append(data.requireCeiling())
                    .append("\n  Effective (clipped to border): ").append(effective);
        }

        String text = msg.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return data.isEnabled() ? 1 : 0;
    }

    // ---- zones ----

    private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    data(ctx).zones().stream().map(VerticalSpawnData.Zone::name), builder);

    private static LiteralArgumentBuilder<CommandSourceStack> zoneCommand()
    {
        return Commands.literal("zone")
                .then(Commands.literal("add")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("from", ColumnPosArgument.columnPos())
                                        .then(Commands.argument("to", ColumnPosArgument.columnPos())
                                                .then(Commands.argument("minY", IntegerArgumentType.integer())
                                                        .then(Commands.argument("maxY", IntegerArgumentType.integer())
                                                                .executes(ctx -> addZone(ctx, false))
                                                                .then(Commands.argument("ceiling", BoolArgumentType.bool())
                                                                        .executes(ctx -> addZone(ctx,
                                                                                BoolArgumentType.getBool(ctx, "ceiling"))))))))))
                .then(Commands.literal("ceiling")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
                                .then(Commands.argument("required", BoolArgumentType.bool())
                                        .executes(VerticalSpawnCommand::setZoneCeiling))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
                                .executes(VerticalSpawnCommand::removeZone)))
                .then(Commands.literal("list").executes(VerticalSpawnCommand::status))
                .then(Commands.literal("clear").executes(VerticalSpawnCommand::clearZones));
    }

    private static int addZone(CommandContext<CommandSourceStack> ctx, boolean ceiling)
    {
        String name = StringArgumentType.getString(ctx, "name");
        ColumnPos from = ColumnPosArgument.getColumnPos(ctx, "from");
        ColumnPos to = ColumnPosArgument.getColumnPos(ctx, "to");
        int minY = IntegerArgumentType.getInteger(ctx, "minY");
        int maxY = IntegerArgumentType.getInteger(ctx, "maxY");

        VerticalSpawnData.Zone zone = VerticalSpawnData.Zone.of(name, from.x(), from.z(), to.x(), to.z(), minY, maxY, ceiling);
        boolean replaced = data(ctx).putZone(zone);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Vertical Spawn: zone " + (replaced ? "updated " : "added ") + zone.describe()), true);
        return 1;
    }

    private static int setZoneCeiling(CommandContext<CommandSourceStack> ctx)
    {
        String name = StringArgumentType.getString(ctx, "name");
        boolean required = BoolArgumentType.getBool(ctx, "required");
        VerticalSpawnData data = data(ctx);
        VerticalSpawnData.Zone old = data.getZone(name);
        if (old == null)
        {
            ctx.getSource().sendFailure(Component.literal("Vertical Spawn: no zone named " + name));
            return 0;
        }
        VerticalSpawnData.Zone zone = new VerticalSpawnData.Zone(old.name(), old.minX(), old.maxX(),
                old.minZ(), old.maxZ(), old.minY(), old.maxY(), required);
        data.putZone(zone);
        ctx.getSource().sendSuccess(() -> Component.literal("Vertical Spawn: zone updated " + zone.describe()), true);
        return 1;
    }

    private static int removeZone(CommandContext<CommandSourceStack> ctx)
    {
        String name = StringArgumentType.getString(ctx, "name");
        VerticalSpawnData data = data(ctx);
        if (!data.removeZone(name))
        {
            ctx.getSource().sendFailure(Component.literal("Vertical Spawn: no zone named " + name));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Vertical Spawn: zone " + name + " removed"
                + (data.hasZones() ? "" : " - no zones left, using the single area again")), true);
        return 1;
    }

    private static int clearZones(CommandContext<CommandSourceStack> ctx)
    {
        int count = data(ctx).clearZones();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Vertical Spawn: removed " + count + " zone(s), using the single area again"), true);
        return count;
    }
}
