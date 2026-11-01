package com.lildorito.verticalspawn;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class VerticalSpawnData extends SavedData
{
    private static final String DATA_NAME = VerticalSpawn.MODID;

    private boolean enabled = false;
    private boolean useBox = false;
    private int minX = 0;
    private int minZ = 0;
    private int maxX = 0;
    private int maxZ = 0;
    private int minY = -64;
    private int maxY = 319;
    private boolean requireCeiling = false;
    private final Map<String, Zone> zones = new LinkedHashMap<>();

    public record Zone(String name, int minX, int maxX, int minZ, int maxZ, int minY, int maxY, boolean requireCeiling)
    {
        public static Zone of(String name, int x1, int z1, int x2, int z2, int y1, int y2, boolean requireCeiling)
        {
            return new Zone(name, Math.min(x1, x2), Math.max(x1, x2), Math.min(z1, z2), Math.max(z1, z2),
                    Math.min(y1, y2), Math.max(y1, y2), requireCeiling);
        }

        CompoundTag save()
        {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putInt("minX", minX);
            tag.putInt("maxX", maxX);
            tag.putInt("minZ", minZ);
            tag.putInt("maxZ", maxZ);
            tag.putInt("minY", minY);
            tag.putInt("maxY", maxY);
            tag.putBoolean("requireCeiling", requireCeiling);
            return tag;
        }

        static Zone load(CompoundTag tag)
        {
            return new Zone(tag.getString("name"), tag.getInt("minX"), tag.getInt("maxX"),
                    tag.getInt("minZ"), tag.getInt("maxZ"), tag.getInt("minY"), tag.getInt("maxY"),
                    tag.getBoolean("requireCeiling"));
        }

        public String describe()
        {
            return name + ": X " + minX + " to " + maxX + ", Z " + minZ + " to " + maxZ
                    + ", Y " + minY + " to " + maxY + (requireCeiling ? ", ceiling required" : "");
        }
    }

    public static VerticalSpawnData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage()
                .computeIfAbsent(VerticalSpawnData::load, VerticalSpawnData::new, DATA_NAME);
    }

    public static VerticalSpawnData load(CompoundTag tag)
    {
        VerticalSpawnData data = new VerticalSpawnData();
        data.enabled = tag.getBoolean("enabled");
        data.useBox = tag.getBoolean("useBox");
        data.minX = tag.getInt("minX");
        data.minZ = tag.getInt("minZ");
        data.maxX = tag.getInt("maxX");
        data.maxZ = tag.getInt("maxZ");
        if (tag.contains("minY")) data.minY = tag.getInt("minY");
        if (tag.contains("maxY")) data.maxY = tag.getInt("maxY");
        data.requireCeiling = tag.getBoolean("requireCeiling");
        ListTag zoneList = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < zoneList.size(); i++)
        {
            Zone zone = Zone.load(zoneList.getCompound(i));
            data.zones.put(zone.name(), zone);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("useBox", useBox);
        tag.putInt("minX", minX);
        tag.putInt("minZ", minZ);
        tag.putInt("maxX", maxX);
        tag.putInt("maxZ", maxZ);
        tag.putInt("minY", minY);
        tag.putInt("maxY", maxY);
        tag.putBoolean("requireCeiling", requireCeiling);
        ListTag zoneList = new ListTag();
        for (Zone zone : zones.values()) zoneList.add(zone.save());
        tag.put("zones", zoneList);
        return tag;
    }

    public boolean putZone(Zone zone)
    {
        boolean existed = zones.put(zone.name(), zone) != null;
        setDirty();
        return existed;
    }

    public boolean removeZone(String name)
    {
        boolean removed = zones.remove(name) != null;
        if (removed) setDirty();
        return removed;
    }

    public int clearZones()
    {
        int count = zones.size();
        zones.clear();
        setDirty();
        return count;
    }

    @Nullable
    public Zone getZone(String name) { return zones.get(name); }
    public Collection<Zone> zones() { return Collections.unmodifiableCollection(zones.values()); }
    public boolean hasZones() { return !zones.isEmpty(); }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        setDirty();
    }

    public void setBorderArea(int minY, int maxY)
    {
        this.useBox = false;
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        setDirty();
    }

    public void setBoxArea(int x1, int z1, int x2, int z2, int minY, int maxY)
    {
        this.useBox = true;
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        setDirty();
    }

    public void setRequireCeiling(boolean requireCeiling)
    {
        this.requireCeiling = requireCeiling;
        setDirty();
    }

    public boolean isEnabled() { return enabled; }
    public boolean useBox() { return useBox; }
    public int minX() { return minX; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxZ() { return maxZ; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public boolean requireCeiling() { return requireCeiling; }
}
