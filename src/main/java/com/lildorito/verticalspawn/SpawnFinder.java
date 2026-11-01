package com.lildorito.verticalspawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SpawnFinder
{
    private static final int MAX_COLUMNS = 64;
    private static final int ENOUGH_COLUMNS = 8;
    private static final double MAX_LAYER_HEIGHT = 0.125;
    private static final int SPREAD_ATTEMPTS = 4;

    private SpawnFinder() {}

    public record Bounds(String name, int minX, int maxX, int minZ, int maxZ, int minY, int maxY,
                         boolean requireCeiling) {}

    public static List<Bounds> resolveAll(ServerLevel level, VerticalSpawnData data)
    {
        List<Bounds> result = new ArrayList<>();
        if (data.hasZones())
        {
            for (VerticalSpawnData.Zone zone : data.zones())
            {
                Bounds b = clip(level, zone.name(), zone.minX(), zone.maxX(), zone.minZ(), zone.maxZ(),
                        zone.minY(), zone.maxY(), zone.requireCeiling());
                if (b != null) result.add(b);
            }
        }
        else
        {
            Bounds b = resolveArea(level, data);
            if (b != null) result.add(b);
        }
        return result;
    }

    @Nullable
    public static Bounds resolveArea(ServerLevel level, VerticalSpawnData data)
    {
        if (data.useBox())
        {
            return clip(level, "area", data.minX(), data.maxX(), data.minZ(), data.maxZ(),
                    data.minY(), data.maxY(), data.requireCeiling());
        }
        return clip(level, "area", Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE,
                data.minY(), data.maxY(), data.requireCeiling());
    }

    public static List<Bounds> spreadBounds(ServerLevel level, VerticalSpawnData data,
                                            double centerX, double centerZ, double maxRange, @Nullable Integer maxHeight)
    {
        int sqMinX = Mth.ceil(centerX - maxRange - 0.5);
        int sqMaxX = Mth.floor(centerX + maxRange - 0.5);
        int sqMinZ = Mth.ceil(centerZ - maxRange - 0.5);
        int sqMaxZ = Mth.floor(centerZ + maxRange - 0.5);
        int cap = maxHeight == null ? Integer.MAX_VALUE : maxHeight;

        List<Bounds> result = new ArrayList<>();
        if (data.hasZones())
        {
            for (VerticalSpawnData.Zone zone : data.zones())
            {
                Bounds b = clip(level, zone.name(),
                        Math.max(sqMinX, zone.minX()), Math.min(sqMaxX, zone.maxX()),
                        Math.max(sqMinZ, zone.minZ()), Math.min(sqMaxZ, zone.maxZ()),
                        zone.minY(), Math.min(zone.maxY(), cap), zone.requireCeiling());
                if (b != null) result.add(b);
            }
        }
        else
        {
            Bounds b = clip(level, "area", sqMinX, sqMaxX, sqMinZ, sqMaxZ,
                    data.minY(), Math.min(data.maxY(), cap), data.requireCeiling());
            if (b != null) result.add(b);
        }
        return result;
    }

    @Nullable
    private static Bounds clip(ServerLevel level, String name, int minX, int maxX, int minZ, int maxZ,
                               int minY, int maxY, boolean requireCeiling)
    {
        WorldBorder border = level.getWorldBorder();
        minX = Math.max(minX, Mth.ceil(border.getMinX()));
        maxX = Math.min(maxX, Mth.floor(border.getMaxX()) - 1);
        minZ = Math.max(minZ, Mth.ceil(border.getMinZ()));
        maxZ = Math.min(maxZ, Mth.floor(border.getMaxZ()) - 1);

        // Feet Y: need room for the floor block below and the head block above.
        minY = Math.max(minY, level.getMinBuildHeight() + 1);
        maxY = Math.min(maxY, level.getMaxBuildHeight() - 2);

        if (minX > maxX || minZ > maxZ || minY > maxY) return null;
        return new Bounds(name, minX, maxX, minZ, maxZ, minY, maxY, requireCeiling);
    }

    public static Optional<Vec3> find(ServerLevel level, VerticalSpawnData data)
    {
        RandomSource random = level.getRandom();
        List<Bounds> areas = resolveAll(level, data);
        shuffle(areas, random);

        // Random order = equal chance for each zone; the rest are fallbacks.
        for (Bounds b : areas)
        {
            List<Vec3> spots = collectSpots(level, b, ENOUGH_COLUMNS, MAX_COLUMNS);
            if (!spots.isEmpty()) return Optional.of(spots.get(random.nextInt(spots.size())));
        }
        return Optional.empty();
    }

    @Nullable
    public static List<Vec3> spread(ServerLevel level, List<Bounds> areas, int groups, double minDistance)
    {
        if (areas.isEmpty()) return groups == 0 ? new ArrayList<>() : null;

        RandomSource random = level.getRandom();
        int enoughColumns = Mth.clamp(groups * 4, ENOUGH_COLUMNS, 256);
        int maxColumns = enoughColumns * 4;
        double minDistanceSqr = minDistance * minDistance;

        for (int attempt = 0; attempt < SPREAD_ATTEMPTS; attempt++)
        {
            List<List<Vec3>> pools = new ArrayList<>(areas.size());
            for (Bounds b : areas)
            {
                List<Vec3> pool = collectSpots(level, b, enoughColumns, maxColumns);
                shuffle(pool, random);
                pools.add(pool);
            }

            List<Vec3> chosen = new ArrayList<>(groups);
            List<Integer> order = new ArrayList<>(pools.size());
            for (int i = 0; i < pools.size(); i++) order.add(i);

            for (int g = 0; g < groups; g++)
            {
                shuffle(order, random);
                Vec3 pick = null;
                for (int index : order)
                {
                    List<Vec3> pool = pools.get(index);
                    for (int i = 0; i < pool.size(); i++)
                    {
                        Vec3 candidate = pool.get(i);
                        if (isFarEnough(candidate, chosen, minDistanceSqr))
                        {
                            pick = candidate;
                            pool.remove(i);
                            break;
                        }
                    }
                    if (pick != null) break;
                }
                if (pick == null) break;
                chosen.add(pick);
            }
            if (chosen.size() == groups) return chosen;
        }
        return null;
    }

    private static boolean isFarEnough(Vec3 candidate, List<Vec3> chosen, double minDistanceSqr)
    {
        for (Vec3 c : chosen)
        {
            if (c.distanceToSqr(candidate) < minDistanceSqr) return false;
        }
        return true;
    }

    private static <T> void shuffle(List<T> list, RandomSource random)
    {
        for (int i = list.size() - 1; i > 0; i--)
        {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private static List<Vec3> collectSpots(ServerLevel level, Bounds b, int enoughColumns, int maxColumns)
    {
        RandomSource random = level.getRandom();
        boolean requireCeiling = b.requireCeiling();
        List<Vec3> spots = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int productiveColumns = 0;

        for (int i = 0; i < maxColumns && productiveColumns < enoughColumns; i++)
        {
            int x = Mth.nextInt(random, b.minX(), b.maxX());
            int z = Mth.nextInt(random, b.minZ(), b.maxZ());
            int before = spots.size();

            int ceilingTop = requireCeiling ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : 0;

            for (int y = b.minY(); y <= b.maxY(); y++)
            {
                pos.set(x, y, z);
                double offset = standOffset(level, pos);
                if (offset < 0) continue;
                // Heightmap value is the first free Y above the highest motion-blocking block.
                // Anything blocking above the head block (y + 1) means a ceiling.
                if (requireCeiling && ceilingTop <= y + 2) continue;
                spots.add(new Vec3(x + 0.5, y + offset, z + 0.5));
            }

            if (spots.size() > before) productiveColumns++;
        }
        return spots;
    }

    private static double standOffset(ServerLevel level, BlockPos feet)
    {
        BlockState feetState = level.getBlockState(feet);
        double offset = 0.0;

        if (!isPassable(level, feet, feetState))
        {
            // Allow thin floor coverings like carpet or a snow layer, but nothing taller (slabs, chests...).
            if (!feetState.getFluidState().isEmpty() || isHazard(feetState)) return -1;
            VoxelShape shape = feetState.getCollisionShape(level, feet);
            double top = shape.max(Direction.Axis.Y);
            if (top > MAX_LAYER_HEIGHT) return -1;
            offset = top;
        }

        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || isBadFloor(floor)) return -1;

        BlockPos head = feet.above();
        if (!isPassable(level, head, level.getBlockState(head))) return -1;

        return offset;
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos, BlockState state)
    {
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty()
                && !isHazard(state);
    }

    private static boolean isHazard(BlockState state)
    {
        return state.is(BlockTags.FIRE)
                || state.is(BlockTags.PORTALS)
                || state.is(BlockTags.CAMPFIRES)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.COBWEB)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POINTED_DRIPSTONE);
    }

    private static boolean isBadFloor(BlockState state)
    {
        return state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(BlockTags.CAMPFIRES)
                || state.is(BlockTags.LEAVES);
    }
}
