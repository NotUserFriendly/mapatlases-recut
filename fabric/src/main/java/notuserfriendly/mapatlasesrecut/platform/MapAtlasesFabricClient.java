package notuserfriendly.mapatlasesrecut.platform;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import notuserfriendly.mapatlasesrecut.client.MapAtlasesClient;
import notuserfriendly.mapatlasesrecut.client.platform.MapAtlasesClientImpl;
import notuserfriendly.mapatlasesrecut.lifecycle.MapAtlasesClientEvents;

public class MapAtlasesFabricClient {

    public static void clientInit() {

        MapAtlasesClientImpl.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) MapAtlasesClient.cachePlayerState(client.player);
            if (client.level != null) MapAtlasesClientEvents.onClientTick(client, client.level);
        });
    }
}
