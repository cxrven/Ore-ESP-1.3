package de.example.oreesp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.joml.Matrix4fc;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class OreOverlay {
    private static int radius = 16;
    private static boolean[] selected = {true,true,true,true,true,true,true,true,true,true,false};
    private static final String[] NAMES = {"Diamant", "Smaragd", "Gold", "Eisen", "Kohle", "Kupfer", "Redstone", "Lapislazuli", "Quarz", "Antiker Schrott", "Lava"};
    private static final int MAX_MARKERS = 700;
    private static final int MAX_LAVA = 80; // Lava never displaces ore markings.
    private static final int MAX_PER_GROUP = 700;
    private static final int CHECKS_PER_TICK = 12000;
    
    private static final float THICKNESS = 0.035f;
    private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(net.minecraft.resources.Identifier.fromNamespaceAndPath("oreesp", "pipeline/ore_edges"))
            .withDepthStencilState(Optional.empty()).build());
    private static final RenderType ORE_RENDER_TYPE = RenderType.create(
        "oreesp:through_walls", RenderSetup.builder(THROUGH_WALLS).createRenderSetup());
    public static int radius() { return radius; }
    public static int optionsCount() { return NAMES.length; }
    public static String optionName(int i) { return NAMES[i]; }
    public static boolean isSelected(int i) { return selected[i]; }
    public static void setRadius(int r) {
        radius = Math.max(8, Math.min(64, r));
        resetScan();
    }
    public static void toggleType(int i) {
        selected[i] = !selected[i];
        resetScan();
    }
    private static void resetScan() {
        scanOrigin = null;
        cursor = 0;
        clearPending();
        // Keep previous markers visible until a full refresh is finished.
    }
    private static volatile List<Marker> visible = List.of();
    private static PriorityQueue<ScoredMarker> rarePending = newQueue();
    private static PriorityQueue<ScoredMarker> commonPending = newQueue();
    private static PriorityQueue<ScoredMarker> lavaPending = newQueue();
    private static int knownDiamonds = 0;
    private static int knownRare = 0;
    private static int knownCommon = 0;
    private static int knownLava = 0;
    private static int skippedUnloaded = 0;
    private static volatile String lastDiagnosis = "Noch kein vollständiger Scan.";
    private static int cursor = 0;
    private static BlockPos scanOrigin;
    private static Object previousLevel;
    private static List<Marker> frameMarkers = List.of();

    private record Marker(int x, int y, int z, float r, float g, float b) {}
    private record ScoredMarker(Marker marker, int distanceSquared) {}

    // Farthest retained result at the head, so every direction gets a fair chance.
    private static PriorityQueue<ScoredMarker> newQueue() {
        return new PriorityQueue<>(Comparator.comparingInt(ScoredMarker::distanceSquared).reversed());
    }
    private static void clearPending() {
        rarePending = newQueue();
        commonPending = newQueue();
        lavaPending = newQueue();
        knownDiamonds = knownRare = knownCommon = knownLava = skippedUnloaded = 0;
    }
    private static void retain(PriorityQueue<ScoredMarker> target, Marker marker, int distanceSquared, int limit) {
        if (target.size() < limit) target.add(new ScoredMarker(marker, distanceSquared));
        else if (target.peek() != null && distanceSquared < target.peek().distanceSquared()) {
            target.poll();
            target.add(new ScoredMarker(marker, distanceSquared));
        }
    }
    private static boolean rare(Block b) {
        return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE
            || b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE
            || b == Blocks.ANCIENT_DEBRIS;
    }
    private static boolean diamond(Block b) {
        return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE;
    }
    private static void remember(Block block, Marker marker, int distanceSquared) {
        if (block == Blocks.LAVA) {
            knownLava++;
            retain(lavaPending, marker, distanceSquared, MAX_LAVA);
        } else if (rare(block)) {
            knownRare++;
            if (diamond(block)) knownDiamonds++;
            retain(rarePending, marker, distanceSquared, MAX_PER_GROUP);
        } else {
            knownCommon++;
            retain(commonPending, marker, distanceSquared, MAX_PER_GROUP);
        }
    }
    private static List<Marker> snapshot() {
        List<Marker> result = new ArrayList<>(MAX_MARKERS);
        // Rare ores take precedence; common ores use remaining slots.
        for (ScoredMarker m : rarePending) if (result.size() < MAX_MARKERS) result.add(m.marker());
        for (ScoredMarker m : commonPending) if (result.size() < MAX_MARKERS - MAX_LAVA) result.add(m.marker());
        // Allow common ores to use unused lava slots when lava is disabled / absent.
        if (lavaPending.isEmpty()) {
            for (ScoredMarker m : commonPending) {
                if (result.size() >= MAX_MARKERS) break;
                if (!result.contains(m.marker())) result.add(m.marker());
            }
        }
        for (ScoredMarker m : lavaPending) if (result.size() < MAX_MARKERS) result.add(m.marker());
        return List.copyOf(result);
    }
    public static String diagnosis() { return lastDiagnosis; }

    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(ctx -> frameMarkers = visible);
        LevelRenderEvents.COLLECT_SUBMITS.register(OreOverlay::submit);
    }

    public static void tick(Minecraft client) {
        if (!OreEspClient.enabled || client.level == null || client.player == null) {
            visible = List.of(); clearPending(); cursor = 0; scanOrigin = null; previousLevel = client.level;
            lastDiagnosis = "ESP ausgeschaltet oder keine Welt geladen.";
            return;
        }
        BlockPos center = client.player.blockPosition();
        int side = radius * 2 + 1;
        int volume = side * side * side;
        // Scan from scratch when the player has moved a few blocks or changed dimension.
        if (scanOrigin == null || scanOrigin.distSqr(center) > 64 || previousLevel != client.level) {
            // Prevent old-world ghost markers when switching dimensions.
            if (previousLevel != client.level) visible = List.of();
            scanOrigin = center.immutable(); previousLevel = client.level;
            cursor = 0; clearPending();
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int checked = 0;
        while (cursor < volume && checked++ < CHECKS_PER_TICK) {
            // Coprime step covers all positions but interleaves different directions.
            int stride = side * side + 2; // gcd(stride, side^3) = 1 for odd side
            int n = (int)(((long)cursor++ * stride) % volume);
            int dx = n % side - radius;
            int dy = n / side % side - radius;
            int dz = n / (side * side) - radius;
            if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
            pos.set(scanOrigin.getX() + dx, scanOrigin.getY() + dy, scanOrigin.getZ() + dz);
            if (pos.getY() < client.level.getMinY() || pos.getY() >= client.level.getMaxY()) continue;
            // No world generation, server requests or reading beyond currently loaded chunks.
            if (!client.level.hasChunkAt(pos)) { skippedUnloaded++; continue; }
            Block block = client.level.getBlockState(pos).getBlock();
            float[] c = color(block);
            if (c != null) remember(block, new Marker(pos.getX(), pos.getY(), pos.getZ(), c[0], c[1], c[2]), dx*dx + dy*dy + dz*dz);
        }
        // Never publish partial scan results: otherwise highlights blink/disappear
        // every time a new scan restarts. Swap in a complete snapshot atomically.
        if (cursor >= volume) {
            visible = snapshot();
            lastDiagnosis = "Client-bekannt (aktivierte Typen): Diamant " + knownDiamonds
                + ", seltene Erze " + knownRare + ", andere Erze " + knownCommon
                + ", Lava " + knownLava + ". Markiert: " + visible.size()
                + ". Ungeladene Blockpositionen: " + skippedUnloaded
                + ". Keine Server-Erzstatistik.";
            // Periodically refresh so newly broken/placed blocks are reflected.
            cursor = 0; clearPending();
        }
    }

    private static float[] color(Block b) {
        if (selected[0] && (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE)) return new float[]{0.15f, 0.95f, 1f};
        if (selected[1] && (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE)) return new float[]{0.1f, 1f, 0.2f};
        if (selected[2] && (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE)) return new float[]{1f, 0.75f, 0.1f};
        if (selected[3] && (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE)) return new float[]{0.95f, 0.65f, 0.45f};
        if (selected[4] && (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE)) return new float[]{0.6f, 0.6f, 0.6f};
        if (selected[5] && (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE)) return new float[]{1f, 0.4f, 0.13f};
        if (selected[6] && (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE)) return new float[]{1f, 0.1f, 0.15f};
        if (selected[7] && (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE)) return new float[]{0.22f, 0.42f, 1f};
        if (selected[8] && (b == Blocks.NETHER_QUARTZ_ORE)) return new float[]{0.95f, 0.9f, 0.85f};
        if (selected[9] && (b == Blocks.ANCIENT_DEBRIS)) return new float[]{0.7f, 0.3f, 0.75f};
        if (selected[10] && (b == Blocks.LAVA)) return new float[]{1f, 0.3f, 0.02f};
        return null;
    }

    private static void submit(LevelRenderContext context) {
        if (!OreEspClient.enabled || frameMarkers.isEmpty()) return;
        PoseStack poses = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        List<Marker> markers = frameMarkers;
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        // Submit geometry to Minecraft. It will allocate/upload buffers and draw
        // during its own render pass. Never open another render pass here.
        context.submitNodeCollector().submitCustomGeometry(poses, ORE_RENDER_TYPE,
            (pose, output) -> {
                Matrix4fc matrix = pose.pose();
                for (Marker marker : markers) edges(matrix, output, marker);
            });
        poses.popPose();
    }

    // Twelve narrow rectangular beams form a crisp colored 3D block outline.
    private static void edges(Matrix4fc m, VertexConsumer out, Marker p) {
        float x=p.x, y=p.y, z=p.z, e=THICKNESS;
        for (int i=0;i<2;i++) for (int j=0;j<2;j++) {
            float a=i==0?0:1-e, b=j==0?0:1-e;
            box(m,out,x,y+a,z+b,x+1,y+a+e,z+b+e,p);
            box(m,out,x+a,y,z+b,x+a+e,y+1,z+b+e,p);
            box(m,out,x+a,y+b,z,x+a+e,y+b+e,z+1,p);
        }
    }
    private static void box(Matrix4fc m,VertexConsumer v,float a,float b,float c,float d,float e,float f,Marker p) {
        quad(m,v,a,b,f,d,b,f,d,e,f,a,e,f,p);
        quad(m,v,d,b,c,a,b,c,a,e,c,d,e,c,p);
        quad(m,v,a,b,c,a,b,f,a,e,f,a,e,c,p);
        quad(m,v,d,b,f,d,b,c,d,e,c,d,e,f,p);
        quad(m,v,a,e,f,d,e,f,d,e,c,a,e,c,p);
        quad(m,v,a,b,c,d,b,c,d,b,f,a,b,f,p);
    }
    private static void quad(Matrix4fc m,VertexConsumer v,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float x4,float y4,float z4,Marker p) {
        v.addVertex(m,x1,y1,z1).setColor(p.r,p.g,p.b,0.95f);
        v.addVertex(m,x2,y2,z2).setColor(p.r,p.g,p.b,0.95f);
        v.addVertex(m,x3,y3,z3).setColor(p.r,p.g,p.b,0.95f);
        v.addVertex(m,x4,y4,z4).setColor(p.r,p.g,p.b,0.95f);
    }
}
