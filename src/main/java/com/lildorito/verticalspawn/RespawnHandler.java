package com.lildorito.verticalspawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = VerticalSpawn.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RespawnHandler
{
    private static final int OWN_SPAWN_RADIUS = 3;

    private RespawnHandler() {}

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.isEndConquered()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel overworld = player.server.overworld();
        if (player.level() != overworld) return;

        VerticalSpawnData data = VerticalSpawnData.get(player.server);
        if (!data.isEnabled()) return;
        if (respawnedAtOwnSpawn(player)) return;

        if (!teleportToRandomSpot(player, overworld, data))
        {
            VerticalSpawn.LOGGER.warn("Vertical Spawn: no safe spot found for {}, keeping vanilla respawn",
                    player.getGameProfile().getName());
        }
    }

    public static boolean teleportToRandomSpot(ServerPlayer player, ServerLevel level, VerticalSpawnData data)
    {
        Optional<Vec3> spot = SpawnFinder.find(level, data);
        if (spot.isEmpty()) return false;

        teleportEntity(player, level, spot.get());
        return true;
    }

    public static void teleportEntity(Entity entity, ServerLevel level, Vec3 spot)
    {
        float yaw = level.getRandom().nextFloat() * 360.0F - 180.0F;
        entity.stopRiding();
        entity.teleportTo(level, spot.x, spot.y, spot.z, EnumSet.noneOf(RelativeMovement.class), yaw, 0.0F);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
    }

    private static boolean respawnedAtOwnSpawn(ServerPlayer player)
    {
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null) return false;
        if (!player.getRespawnDimension().equals(player.level().dimension())) return false;

        BlockPos at = player.blockPosition();
        return Math.abs(at.getX() - spawn.getX()) <= OWN_SPAWN_RADIUS
                && Math.abs(at.getY() - spawn.getY()) <= OWN_SPAWN_RADIUS
                && Math.abs(at.getZ() - spawn.getZ()) <= OWN_SPAWN_RADIUS;
    }
}
