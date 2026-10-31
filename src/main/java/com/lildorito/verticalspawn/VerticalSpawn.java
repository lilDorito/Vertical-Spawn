package com.lildorito.verticalspawn;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Vertical Spawn: replaces the vanilla "no spawn point" respawn with a random safe spot
 * inside a configurable box, on any floor between a minimum and maximum Y.
 *
 * Everything runs on the logical server. Commands and the respawn hook are registered
 * through {@link VerticalSpawnCommand} and {@link RespawnHandler} on the Forge event bus.
 */
@Mod(VerticalSpawn.MODID)
public class VerticalSpawn
{
    public static final String MODID = "verticalspawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VerticalSpawn(FMLJavaModLoadingContext context)
    {
        context.getModEventBus().addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("Vertical Spawn loaded");
    }
}
