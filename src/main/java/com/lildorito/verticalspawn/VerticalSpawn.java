package com.lildorito.verticalspawn;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

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
