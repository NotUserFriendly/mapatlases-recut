package notuserfriendly.mapatlasesrecut.lifecycle;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.network.NetworkHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.jetbrains.annotations.Nullable;
import notuserfriendly.mapatlasesrecut.MapAtlasesMod;
import notuserfriendly.mapatlasesrecut.config.MapAtlasesConfig;
import notuserfriendly.mapatlasesrecut.config.UpdateFashion;
import notuserfriendly.mapatlasesrecut.integration.moonlight.EntityRadar;
import notuserfriendly.mapatlasesrecut.item.MapAtlasItem;
import notuserfriendly.mapatlasesrecut.map_collection.MapCollection;
import notuserfriendly.mapatlasesrecut.map_collection.MapGridKey;
import notuserfriendly.mapatlasesrecut.networking.S2CWorldHashPacket;
import notuserfriendly.mapatlasesrecut.utils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class MapAtlasesServerEvents {

    // Used to prevent Map creation spam consuming all Empty Maps on auto-create
    private static final ReentrantLock MUTEX = new ReentrantLock();

    // keyed by UUID, not by player instance: the map data these values point at lists the players holding it,
    // so a value can reach its own key and weak keys would never be collected
    private static final Map<UUID, UpdateScheduler> SCHEDULERS_PER_PLAYER = new HashMap<>();
    private static final Map<UUID, MapDataHolder> LAST_CENTER_MAP_PER_PLAYER = new HashMap<>();

    private static volatile LevelChunk dummyChunk;

    public static LevelChunk getDummyChunk(Level level) {
        LevelChunk cached = dummyChunk;
        if (cached != null && cached.getLevel() == level) return cached;

        LevelChunk fresh = new EmptyLevelChunk(level, ChunkPos.ZERO,
                level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.FOREST));
        MinecraftServer server = level.getServer();
        if (server != null && server.isRunning()) {
            dummyChunk = fresh;
        }
        return fresh;
    }

    public static void onPlayerTick(ServerPlayer player) {
        ItemStack atlas = MapAtlasesAccessUtils.getAtlasFromPlayerByConfig(player);
        if (atlas.isEmpty()) return;
        if (MapAtlasItem.isLocked(atlas)) return;

        Level level = player.level();
        ResourceKey<Level> dimension = level.dimension();
        if (level.dimensionTypeRegistration().is(MapAtlasesMod.NON_TRACKED_DIMENSIONS)) {
            //don't do anything if player is in a non-tracked dimension
            return;
        }
        Slice slice = MapAtlasItem.getSelectedSlice(atlas, dimension);
        MapCollection maps = MapAtlasItem.getMaps(atlas, level);

        Collection<Byte> layers = layersToMaintain(maps);

        MapsNeighborhood finest = MapsNeighborhood.around(player, layers.iterator().next(), slice);

        boolean createdNewMap = false;
        List<MapDataHolder> mapsInView = new ArrayList<>();
        //create missing maps
        boolean canFillEmpty = MapAtlasesConfig.enableEmptyMapEntryAndFill.get();
        for (byte layer : layers) {
            MapsNeighborhood neighborhood = MapsNeighborhood.around(player, layer, slice);
            for (var m : neighborhood.all()) {
                MapDataHolder info = maps.select(m);
                if (info == null && canFillEmpty) {
                    //can alter map collection
                    info = maybeCreateNewMapEntry(player, atlas, m);
                    if (info != null) {
                        //update maps reference
                        maps = MapAtlasItem.getMaps(atlas, level);
                        createdNewMap = true;
                    }
                }
                if (info != null) mapsInView.add(info);
            }
        }

        //sync the slice below and above so we can update slice automatically
        if ((level.getGameTime() + 13) % 40 == 0) {
            sendSlicesAboveAndBelow(player, atlas, maps, finest.center());
        }

        if (mapsInView.isEmpty()) return;

        // Update Map states & colors
        // updateColors is *easily* the most expensive function in the entire server tick
        // As a result, we will only ever call updateColors twice per tick (same as vanilla's limit)
        UpdateScheduler scheduler = SCHEDULERS_PER_PLAYER.computeIfAbsent(player.getUUID(), p -> {
            if (MapAtlasesConfig.updateFashion.get() == UpdateFashion.ROUND_ROBIN) {
                return new RoundRobinUpdateScheduler();
            } else {
                return new WeightedUpdateScheduler();
            }
        });

        scheduler.performUpdate(player, mapsInView);

        for (MapDataHolder mapHolder : mapsInView) {
            MapAtlasesAccessUtils.tickHoldingPlayerAndSync(mapHolder, player, atlas, TriState.SET_TRUE);
            //if data has changed, a packet will be sent
        }
        // for far away maps so we remove player marker
        MapDataHolder lastData = LAST_CENTER_MAP_PER_PLAYER.get(player.getUUID());
        if (lastData != null && !mapsInView.contains(lastData)) {
            MapAtlasesAccessUtils.tickHoldingPlayerAndSync(lastData, player, atlas, TriState.SET_FALSE);
        }
        LAST_CENTER_MAP_PER_PLAYER.put(player.getUUID(), maps.select(finest.center()));

        if (createdNewMap) {
            // Play the sound
            player.level().playSound(null, player.blockPosition(),
                    MapAtlasesMod.ATLAS_CREATE_MAP_SOUND_EVENT.get(),
                    SoundSource.PLAYERS, 1, 1.0F);
        }
    }

    private static void sendSlicesAboveAndBelow(ServerPlayer player, ItemStack atlas,
                                                MapCollection maps, MapGridKey activeKey) {
        Slice slice = activeKey.slice;
        var dimension = activeKey.slice.dimension();
        var tree = maps.getHeightTree(dimension, slice.type());
        for (Integer hh : tree) {
            if (hh != slice.heightOrTop()) {
                var below = maps.select(activeKey.mapX, activeKey.mapZ, Slice.of(slice.type(), hh, dimension));
                if (below != null)
                    MapAtlasesAccessUtils.tickHoldingPlayerAndSync(below, player, atlas, TriState.SET_TRUE);
            }
        }
    }

    /**
     * Scales this atlas fills in as the player travels.
     * <p>
     * Everything it already holds keeps updating, plus every enabled scale.
     * <p>
     * There is deliberately no "not finer than what it was crafted at" bound. Crafted scale
     * is gone: an atlas is an atlas, and which layers it fills in is the player's choice.
     */
    private static Collection<Byte> layersToMaintain(MapCollection maps) {
        TreeSet<Byte> layers = new TreeSet<>(maps.getScales());
        for (byte sc = 0; sc <= 4; sc++) {
            if (MapAtlasesConfig.maintainLayer[sc].get()) layers.add(sc);
        }
        if (layers.isEmpty()) layers.add((byte) 4);

        // "a detail layer over a base" is the design; more than two is allowed but opt in
        if (MapAtlasesConfig.limitToTwoLayers.get() && layers.size() > 2) {
            TreeSet<Byte> pair = new TreeSet<>();
            pair.add(layers.first());
            pair.add(layers.last());
            return pair;
        }
        return layers;
    }

    //TODO: optimize
    @Nullable
    private static MapDataHolder maybeCreateNewMapEntry(
            ServerPlayer player,
            ItemStack atlas,
            MapGridKey key
    ) {
        MapCollection maps = MapAtlasItem.getMaps(atlas, player.level());
        Level level = player.level();
        if (maps.getCount() == 0) {
            // If the Atlas is "inactive", give it a pity Empty Map count
            MapAtlasItem.getEmptyMaps(atlas).setAndAssign(atlas, MapType.VANILLA, MapAtlasesConfig.pityActivationMapCount.get());
        }

        Slice slice = key.slice;
        int destX = key.mapX;
        int destZ = key.mapZ;
        int emptyCount = MapAtlasItem.getEmptyMaps(atlas).get(slice);
        boolean bypassEmptyMaps = !MapAtlasesConfig.requireEmptyMapsToExpand.get();
        MapDataHolder newMapHolder = null;
        if (!MUTEX.isLocked() && (emptyCount > 0 || player.isCreative() || bypassEmptyMaps)) {
            MUTEX.lock();

            // Make the new map

            //validate height
            var height = slice.height();
            if (height.isPresent() && !maps.getHeightTree(player.level().dimension(), slice.type()).contains(height.get())) {
                MapAtlasesMod.LOGGER.error("Invalid height for slice: {} height: {}", slice, height.get());
            }

            // the key knows which layer it belongs to; the collection's finest is not it
            byte scale = key.scale();

            ItemStack newMap = slice.createNewMap(destX, destZ, scale, player.level(), atlas);
            MapId newMapId = newMap.get(DataComponents.MAP_ID);

            if (newMapId != null) {
                MapDataHolder newData = MapDataHolder.find(newMapId, slice.type(), level);
                // for custom map data to be sent immediately... crappy and hacky. TODO: change custom map data impl
                if (newData != null) {
                    MapAtlasesAccessUtils.tickHoldingPlayerAndSync(newData, player, newMap, TriState.SET_TRUE);
                }
                boolean addedMap = maps.addAndAssigns(atlas, level, slice.type(), newMapId) != maps;


                if (addedMap) {
                    if (!player.isCreative() && !bypassEmptyMaps) {
                        //remove 1 map
                        MapAtlasItem.getEmptyMaps(atlas).addAndAssigns(atlas, slice, -1);
                    }
                    newMapHolder = newData;
                }
            }
            MUTEX.unlock();
        }
        return newMapHolder;
    }


    public static void onPlayerJoin(ServerPlayer player) {
        NetworkHelper.sendToClientPlayer(player, new S2CWorldHashPacket(player));
        ItemStack atlas = MapAtlasesAccessUtils.getAtlasFromPlayerByConfig(player);
        if (atlas.isEmpty()) return;

        Level level = player.level();
        ResourceKey<Level> dimension = level.dimension();
        MapCollection maps = MapAtlasItem.getMaps(atlas, level);

        Slice slice = MapAtlasItem.getSelectedSlice(atlas, dimension);
        // sets new center map
        MapGridKey activeKey = MapGridKey.atEntityPosition(maps.getScale(), slice, player);
        sendSlicesAboveAndBelow(player, atlas, maps, activeKey);

        //TODO: figure out why its not synced automatically
        if (PlatHelper.getPlatform().isFabric()) {
            for (var info : maps.getAllFound()) {
                // update all maps and sends them to player, if needed
                // MapAtlasesAccessUtils.updateMapDataAndSync(info, player, atlas, InteractionResult.PASS);
            }
        }
    }


    public static void onPlayerLogout(ServerPlayer player) {
        SCHEDULERS_PER_PLAYER.remove(player.getUUID());
        LAST_CENTER_MAP_PER_PLAYER.remove(player.getUUID());
    }


    public static void onDimensionUnload() {
        EntityRadar.unloadLevel();
        dummyChunk = null;
    }

}