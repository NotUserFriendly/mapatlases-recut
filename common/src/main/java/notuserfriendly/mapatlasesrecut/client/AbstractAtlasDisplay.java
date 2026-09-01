package notuserfriendly.mapatlasesrecut.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import notuserfriendly.mapatlasesrecut.utils.MapDataHolder;
import notuserfriendly.mapatlasesrecut.utils.MapType;

import notuserfriendly.mapatlasesrecut.map_collection.MapCollection;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public abstract class AbstractAtlasDisplay {

    public static final int MAP_DIMENSION = 128;

    //internally controls how many maps are displayed
    protected final int atlasesCount;
    protected int mapBlocksSize;
    protected MapDataHolder mapWherePlayerIs;

    protected boolean followingPlayer = true;
    protected double currentXCenter;
    protected double currentZCenter;
    protected float zoomLevel = 3;

    protected boolean rotatesWithPlayer = false;
    protected boolean drawBigPlayerMarker = true;

    protected AbstractAtlasDisplay(int atlasesCount) {
        this.atlasesCount = atlasesCount;
    }

    protected void initialize(MapDataHolder newCenter) {
        if (mapWherePlayerIs == null || !mapWherePlayerIs.slice.isSameGroup(newCenter.slice)) {
            this.zoomLevel = atlasesCount * newCenter.type.getDefaultZoomFactor();
        }
        this.mapWherePlayerIs = newCenter;
        this.mapBlocksSize = (1 << mapWherePlayerIs.data.scale) * MAP_DIMENSION;

        this.currentXCenter = mapWherePlayerIs.data.centerX;
        this.currentZCenter = mapWherePlayerIs.data.centerZ;
    }

    public void drawAtlas(GuiGraphics graphics, int x, int y, int width, int height,
                          Player player, float zoomLevelDim, boolean showBorders, MapType type, int light,
                          @Nullable MapItemSavedData selectedKey) {

        MapAtlasesClient.setIsDrawingAtlas(true);

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        float widgetScale = width / (float) (atlasesCount * MAP_DIMENSION);
        float zoomScale = atlasesCount / zoomLevelDim;

        int intXCenter = (int) (currentXCenter);
        int intZCenter = (int) (currentZCenter);
        int scaleIndex = mapBlocksSize / MAP_DIMENSION;

        ColumnPos c = type.getCenter(intXCenter, intZCenter, mapBlocksSize);
        int centerMapX = c.x();
        int centerMapZ = c.z();

        //translate to center
        poseStack.translate(x + width / 2f, y + height / 2f, 0);
        //widget scale + zoom

        poseStack.scale(widgetScale * zoomScale, widgetScale * zoomScale, -1);

        // Draw maps, putting active map in middle of grid

        MultiBufferSource.BufferSource vcp = graphics.bufferSource();

        Pair<List<Matrix4f>, List<Matrix4f>> outlineHack = Pair.of(new ArrayList<>(), new ArrayList<>());

        applyScissors(graphics, x, y, (x + width), (y + height));

        double mapCenterOffsetX = currentXCenter - centerMapX;
        double mapCenterOffsetZ = currentZCenter - centerMapZ;

        //zoom leve is essentially maps on screen
        //dont ask me why all this stuff is like that

        if (rotatesWithPlayer) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(180 - player.getYRot()));
        }

        // ---- layered draw -------------------------------------------------------------
        // The pose is in reference map-pixel units: one unit is (1 << refScale) blocks. A
        // layer at scale s therefore draws its 128 unit quad scaled by 2^(s - ref), and is
        // positioned from its world centre rather than from a grid index, so every layer
        // lands on the same world coordinates regardless of cell size.
        byte ref = refScale();
        double refBlocksPerPixel = 1 << ref;

        int layerIndex = 0;
        for (byte layer : layersCoarsestFirst()) {
            float factor = layer >= ref ? (1 << (layer - ref)) : 1f / (1 << (ref - layer));
            int layerBlocks = MAP_DIMENSION << layer;
            ColumnPos lc = type.getCenter(intXCenter, intZCenter, layerBlocks);

            // coarse cells cover more ground, so fewer are needed to fill the same widget
            int span = Mth.ceil(zoomLevelDim / factor) + 1;
            double halfSpanBlocks = (zoomLevelDim * 0.5 + 1) * (MAP_DIMENSION << ref);

            for (int i = span; i >= -span; i--) {
                for (int j = span; j >= -span; j--) {
                    int cx = lc.x() + j * layerBlocks;
                    int cz = lc.z() + i * layerBlocks;

                    // cull by world distance so the test is layer independent
                    double halfCell = layerBlocks * 0.5;
                    if (Math.abs(cx - currentXCenter) - halfCell > halfSpanBlocks) continue;
                    if (Math.abs(cz - currentZCenter) - halfCell > halfSpanBlocks) continue;

                    MapDataHolder state = getMapAtLayer(cx, cz, layer);
                    if (state == null) continue;

                    boolean drawPlayerIcons = !this.drawBigPlayerMarker
                            && state.data.dimension.equals(player.level().dimension());
                    double px = (cx - currentXCenter) / refBlocksPerPixel;
                    double pz = (cz - currentZCenter) / refBlocksPerPixel;
                    drawMapAt(player, poseStack, vcp, outlineHack, px, pz, factor, layerIndex,
                            state, drawPlayerIcons, light, selectedKey);
                }
            }
            layerIndex++;
        }

        vcp.endBatch();

        if (showBorders) {
            VertexConsumer outlineVC = MapAtlasesClient.MAP_BORDER_TEXTURE.buffer(vcp, RenderType::text); //its already on block atlas
            //using this so we use mipmap. cant use blit sprite
            for (var matrix4f : outlineHack.getFirst()) {
                drawOutline(matrix4f, outlineVC);
            }
            if (showMapBackground()) {
                VertexConsumer backVC = MapAtlasesClient.MAP_BACKGROUND_TEXTURE.buffer(vcp, RenderType::text); //its already on block atlas
                //using this so we use mipmap. cant use blit sprite
                for (var matrix4f : outlineHack.getFirst()) {
                    drawOutline(matrix4f.translate(0, 0, 1), backVC);
                }
            }
            VertexConsumer outlineVC2 = MapAtlasesClient.MAP_HOVERED_TEXTURE.buffer(vcp, RenderType::text); //its already on block atlas
            for (var matrix4f : outlineHack.getSecond()) {
                drawOutline(matrix4f, outlineVC2);
            }
            vcp.endBatch();
        }

        poseStack.popPose();
        graphics.disableScissor();

        MapAtlasesClient.setIsDrawingAtlas(false);
    }

    protected abstract boolean showMapBackground();

    private static void drawOutline(Matrix4f matrix4f, VertexConsumer outlineVC) {
        //cause of vertex consumer chaining bug...
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float zOffset = -1;
        outlineVC.addVertex(matrix4f, 0.0F, 128.0F, zOffset).setColor(255, 255, 255, 255);
        outlineVC.setUv(0.0F, 1.0F)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        outlineVC.addVertex(matrix4f, 128.0F, 128.0F, zOffset).setColor(255, 255, 255, 255);
        outlineVC.setUv(1.0F, 1.0F)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        outlineVC.addVertex(matrix4f, 128.0F, 0.0F, zOffset).setColor(255, 255, 255, 255);
        outlineVC.setUv(1.0F, 0.0F)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        outlineVC.addVertex(matrix4f, 0.0F, 0.0F, zOffset).setColor(255, 255, 255, 255);
        outlineVC.setUv(0.0F, 0.0F)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    protected void applyScissors(GuiGraphics graphics, int x, int y, int x1, int y1) {
        graphics.enableScissor(x, y, x1, y1);
    }

    /** The map covering this world centre at exactly this layer, or null. */
    @Nullable
    public abstract MapDataHolder getMapAtLayer(int centerX, int centerZ, byte scale);

    /** Layers to composite, coarsest first, so finer detail draws over the base. */
    public abstract Iterable<Byte> layersCoarsestFirst();

    /** getScales() is ascending, and layers must draw coarsest first so detail lands on top. */
    protected static Iterable<Byte> coarsestFirst(MapCollection maps) {
        List<Byte> ordered = new ArrayList<>(maps.getScales());
        Collections.reverse(ordered);
        return ordered;
    }

    /** Scale the transform is calibrated to: the layer whose pixels map 1:1 to pose units. */
    protected byte refScale() {
        return mapWherePlayerIs == null ? 0 : mapWherePlayerIs.data.scale;
    }

    public void setFollowingPlayer(boolean followingPlayer) {
        this.followingPlayer = followingPlayer;
    }

    private void drawMapAt(
            Player player,
            PoseStack poseStack,
            MultiBufferSource.BufferSource vcp,
            Pair<List<Matrix4f>, List<Matrix4f>> outlineHack,
            double px, double pz, float factor, int layerIndex,
            MapDataHolder state,
            boolean drawPlayerIcons,
            int light,
            @Nullable MapItemSavedData selectedData
    ) {
        poseStack.pushPose();
        // half a quad back to the corner, then scale the 128 unit quad to this layer's size.
        // The small z step keeps coarse layers behind finer ones: map textures differ per
        // map, so they batch separately and draw order alone cannot be relied on.
        poseStack.translate(px - MAP_DIMENSION * factor / 2.0,
                pz - MAP_DIMENSION * factor / 2.0,
                layerIndex * 0.5);
        poseStack.scale(factor, factor, 1);

        // Remove the off-map player icons temporarily during render
        MapItemSavedData data = state.data;
        List<Map.Entry<String, MapDecoration>> removed = new ArrayList<>();
        List<Map.Entry<String, MapDecoration>> added = new ArrayList<>();
        // Only remove the off-map icon if it's not the active map, or it's not the active dimension
        for (var e : data.decorations.entrySet()) {
            MapDecoration dec = e.getValue();
            var type = dec.type();
            if (type.is(MapDecorationTypes.PLAYER_OFF_MAP) || type.is(MapDecorationTypes.PLAYER_OFF_LIMITS)) {
                if (data == mapWherePlayerIs.data && drawPlayerIcons) {
                    removed.add(e);
                    added.add(new AbstractMap.SimpleEntry<>(e.getKey(), new MapDecoration(MapDecorationTypes.PLAYER,
                            dec.x(), dec.y(), getPlayerMarkerRot(player), dec.name())));
                } else removed.add(e);

            } else if (type.is(MapDecorationTypes.PLAYER)) {
                if (!drawPlayerIcons || data != mapWherePlayerIs.data) {
                    removed.add(e);
                } else {
                    int i = 1 << data.scale;
                    float f = (float) (player.getX() - data.centerX) / i;
                    float f1 = (float) (player.getZ() - data.centerZ) / i;
                    byte b0 = (byte) ((int) ((f * 2.0F) + 0.5D));
                    byte b1 = (byte) ((int) ((f1 * 2.0F) + 0.5D));
                    added.add(new AbstractMap.SimpleEntry<>(e.getKey(), new MapDecoration(MapDecorationTypes.PLAYER,
                            b0, b1, getPlayerMarkerRot(player), dec.name())));
                    //add accurate player
                }
            }
        }

        removed.forEach(d -> data.decorations.remove(d.getKey()));
        added.forEach(d -> data.decorations.put(d.getKey(), d.getValue()));

        light = MapAtlasesClient.debugIsMapUpdated(light, state.id, state.type);

        Minecraft.getInstance().gameRenderer.getMapRenderer()
                .render(
                        poseStack,
                        vcp,
                        state.id,
                        data,
                        false,//(1+ix+iy)*50
                        light //
                );

        if (state.data == selectedData) {
            outlineHack.getSecond().add(new Matrix4f(poseStack.last().pose()));
        } else {
            outlineHack.getFirst().add(new Matrix4f(poseStack.last().pose()));
        }

        poseStack.popPose();
        // Re-add the off-map player icons after render
        for (Map.Entry<String, MapDecoration> e : removed) {
            data.decorations.put(e.getKey(), e.getValue());
        }
    }

    private static byte getPlayerMarkerRot(Player p) {
        float pRotation = p.getYRot();
        pRotation += pRotation < 0.0D ? -8.0D : 8.0D;
        return (byte) ((int) (pRotation * 16.0D / 360.0D));
    }

    public static int round(int num, int mod) {
        //return Math.round((float) num / mod) * mod
        int t = num % mod;
        if (t < (int) Math.floor(mod / 2.0))
            return num - t;
        else
            return num + mod - t;
    }
}
