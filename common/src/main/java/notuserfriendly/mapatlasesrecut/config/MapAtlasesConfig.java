package notuserfriendly.mapatlasesrecut.config;

import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigBuilder;
import net.mehvahdjukaar.moonlight.api.platform.configs.ConfigType;
import net.mehvahdjukaar.moonlight.api.platform.configs.ModConfigHolder;
import notuserfriendly.mapatlasesrecut.MapAtlasesMod;
import notuserfriendly.mapatlasesrecut.utils.ActivationLocation;

import java.util.function.Supplier;

public class MapAtlasesConfig {

    static {
        ConfigBuilder builder = ConfigBuilder.create(MapAtlasesMod.MOD_ID, ConfigType.COMMON_SYNCED);


        builder.icon("atlas").push("general");


        maxMapCount = builder
                .comment("The maximum number of Maps (Filled & Empty combined) allowed to be inside an Atlas.")
                .define("max_map_count", 512, 0, 1000000);

        acceptPaperForEmptyMaps = builder
                .comment("If enabled, you can increase the Empty Map count by inserting Paper")
                .define("accept_paper_for_empty_maps", false);

        requireEmptyMapsToExpand = builder
                .comment("If true, the Atlas is required to have spare Empty Maps stored to expand the Filled Map size")
                .define("require_empty_maps_to_expand", true);

        pityActivationMapCount = builder
                .comment("Controls how many free Empty Maps you get for 'activating' an Inactive Atlas")
                .define("pity_activation_map_count", 0, 0, 64);


        requireSliceMaps = !PlatHelper.isModLoaded("supplementaries") ? () -> false :
                builder.comment("If active, when Supplementaries is installed, the atlas will need to be filled with slice maps to be able to create new sliced maps")
                        .define("requires_slice_maps", false);

        enableEmptyMapEntryAndFill = builder
                .comment("If 'true', Atlases will be able to store Empty Maps and auto-fill them as you explore.")
                .define("enable_empty_map_entry_and_fill", true);

        activationLocation = builder
                .comment("Locations of where an atlas will be scanned for. By default only hotbar will be scanned")
                .define("activation_locations", ActivationLocation.HOTBAR_AND_HANDS);

        creativeTeleport = builder
                .comment("Allows players in creative to teleport using the atlas. Hold shift and press anywhere")
                .define("creative_teleport", true);

        pinMarkerId = builder.comment("Marker id associated with the red pin button on the atlas screen. Set to empty string to disable")
                .define("pin_marked_id", "map_atlases_recut:pin");

        lightMap = builder.comment("Shows light color on maps")
                .define("light_map", false);

        entityRadar = builder.icon("minecraft:spider_eye")
                .comment("Show nearby mobs on minimap. Needs matching client config also set")
                .feature("mob_radar", false);

        builder.pop();
        builder.icon("minecraft:clock").push("update_logic");
        updateFashion = builder.comment("Update maps in simple round robin fashion instead of prioritizing the ones closer")
                .define("update_priority", UpdateFashion.SMART);
        mapUpdatePerTick = builder.comment("Max of maps to update each tick. Increase to make maps update faster")
                .define("map_updates_per_tick", 1, 0, 100);
        mapUpdateMultithreaded = builder.comment("Makes map update on different threads, speeding up the process. Disable if it causes issues. Especially on servers. Try turning on for a big performance improvement regarding map atlas update")
                .define("multithreaded_update", UpdateType.SINGLE_PLAYER_ONLY);
        debugUpdate = builder.comment("Visually shows map updates. Makes the minimap flash on every scan")
                .define("debug_map_updates", false);
        markersUpdatePeriod = builder.comment("Every how many ticks should markers be updated")
                .define("markers_update_period", 10, 1, 200);

        builder.pop();
        builder.icon("map_atlases_recut:atlas").push("recut");
        skipUnchangedMaps = builder.comment("Skip rescanning a map whose picture is already complete when no block near the player has changed since it was last scanned. Large saving when standing still or crossing explored ground")
                .define("skip_unchanged_maps", true);

        // which scales an atlas fills in as you travel. Default is finest and coarsest, but a
        // player wanting scale 2 detail over a scale 3 base can say so.
        @SuppressWarnings("unchecked")
        Supplier<Boolean>[] maintain = new Supplier[5];
        for (int sc = 0; sc <= 4; sc++) {
            maintain[sc] = builder.comment("Fill in the scale " + sc + " layer as you travel (cells "
                            + (128 << sc) + " blocks across)")
                    .define("map_layer_scale_" + sc, sc == 0 || sc == 4);
        }
        maintainLayer = maintain;
        limitToTwoLayers = builder.comment("Keep at most two layers, the finest and coarsest of those enabled above, so an atlas stays a detail layer over a base. Turn off to maintain every enabled layer")
                .define("limit_to_two_layers", true);
        paintedRefreshTicks = builder.comment("How often a map whose picture is already complete is refreshed anyway, in ticks. Changes within 48 blocks always update immediately regardless; this only bounds how long a distant change can go unnoticed. Higher saves more work")
                .define("painted_map_refresh_ticks", 200, 20, 2400);
        mapRange = builder.comment("Range multiplier of the map update. Logic affects all maps, atlas or not. Change to make the range smaller or bigger")
                .define("map_range_multiplier", 1, 0.0001, 10);

        logScanStats = builder.comment("Log how many map scans were performed vs skipped, every 10 seconds. Text only, no visual effect")
                .define("log_scan_stats", false);
        builder.pop();


        ModConfigHolder spec = builder.build();
        spec.forceLoad();
        SPEC = spec;
    }

    public static final Supplier<Boolean> debugUpdate;
    public static final Supplier<Boolean> logScanStats;
    public static final Supplier<Integer> markersUpdatePeriod;
    public static final Supplier<UpdateType> mapUpdateMultithreaded;
    public static final Supplier<Integer> maxMapCount;
    public static final Supplier<Integer> pityActivationMapCount;
    public static final Supplier<Boolean> requireSliceMaps;
    public static final Supplier<Boolean> requireEmptyMapsToExpand;
    public static final Supplier<Boolean> acceptPaperForEmptyMaps;
    public static final Supplier<Boolean> enableEmptyMapEntryAndFill;
    public static final Supplier<Boolean> creativeTeleport;
    public static final Supplier<UpdateFashion> updateFashion;
    public static final Supplier<Boolean> lightMap;
    public static final Supplier<Boolean> entityRadar;
    public static final Supplier<String> pinMarkerId;
    public static final Supplier<Integer> mapUpdatePerTick;
    public static final Supplier<Boolean> skipUnchangedMaps;
    public static final Supplier<Integer> paintedRefreshTicks;
    public static final Supplier<Boolean>[] maintainLayer;
    public static final Supplier<Boolean> limitToTwoLayers;
    public static final Supplier<Double> mapRange;
    public static final Supplier<ActivationLocation> activationLocation;

    public static final ModConfigHolder SPEC;

    public static void init() {

    }
}
