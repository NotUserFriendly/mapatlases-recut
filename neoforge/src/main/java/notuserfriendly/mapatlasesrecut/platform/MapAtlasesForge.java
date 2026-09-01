package notuserfriendly.mapatlasesrecut.platform;

import com.mojang.blaze3d.platform.InputConstants;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.RegHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import notuserfriendly.mapatlasesrecut.MapAtlasesMod;
import notuserfriendly.mapatlasesrecut.client.MapAtlasesClient;
import notuserfriendly.mapatlasesrecut.client.platform.MapAtlasesClientImpl;
import notuserfriendly.mapatlasesrecut.lifecycle.MapAtlasesClientEvents;
import notuserfriendly.mapatlasesrecut.lifecycle.MapAtlasesServerEvents;

@Mod(MapAtlasesMod.MOD_ID)
public class MapAtlasesForge {

    public MapAtlasesForge(IEventBus bus) {
        RegHelper.startRegisteringFor(bus);
        MapAtlasesMod.init();
        NeoForge.EVENT_BUS.register(this);

        if (PlatHelper.getPhysicalSide().isClient()) {
            MapAtlasesClientImpl.init(bus);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDimensionUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel)
            MapAtlasesServerEvents.onDimensionUnload();
    }

    // PROBE -- remove with ChunkGenProbe
    @SubscribeEvent
    public void mapAtlasesChunkGenProbe(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (notuserfriendly.mapatlasesrecut.config.MapAtlasesConfig.measureChunkGen.get()) {
            notuserfriendly.mapatlasesrecut.utils.ChunkGenProbe.runOnce(
                    event.getServer().overworld());
        }
    }

    @SubscribeEvent
    public void mapAtlasesPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            MapAtlasesClient.cachePlayerState(event.getEntity());
        } else if (player instanceof ServerPlayer sp) {
            MapAtlasesServerEvents.onPlayerTick(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MapAtlasesServerEvents.onPlayerJoin(sp);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            MapAtlasesServerEvents.onPlayerLogout(sp);
        }
    }

    @SubscribeEvent
    public void onKeyPress(InputEvent.Key event) {
        if (event.getAction() == InputConstants.PRESS) {
            MapAtlasesClientEvents.onKeyPressed(event.getKey(), event.getScanCode());
        }
    }
}
