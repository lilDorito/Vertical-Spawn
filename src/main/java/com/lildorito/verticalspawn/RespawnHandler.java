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
    /** How far vanilla can place a player from their bed/anchor/spawnpoint block when it is used. */
    private static final int OWN_SPAWN_RADIUS = 3;

    private RespawnHandler() {}

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        // Leaving the End through the exit portal is also a "respawn" - leave that alone.
        if (event.isEndConquered()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel overworld = player.server.overworld();
        // Players without a spawn point always respawn in the overworld.
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

    /** Teleports the player to a random safe spot. Returns false if none was found. */
    public static boolean teleportToRandomSpot(ServerPlayer player, ServerLevel level, VerticalSpawnData data)
    {
        Optional<Vec3> spot = SpawnFinder.find(level, data);
        if (spot.isEmpty()) return false;

        teleportEntity(player, level, spot.get());
        return true;
    }

    /** Teleports any entity (player or not) to the spot with a random facing direction. */
    public static void teleportEntity(Entity entity, ServerLevel level, Vec3 spot)
    {
        float yaw = level.getRandom().nextFloat() * 360.0F - 180.0F;
        entity.stopRiding();
        entity.teleportTo(level, spot.x, spot.y, spot.z, EnumSet.noneOf(RelativeMovement.class), yaw, 0.0F);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
    }

    /**
     * Vanilla keeps the stored respawn position even when the bed is gone or blocked, so instead of
     * checking it alone we check whether vanilla actually put the player next to it.
     */
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
