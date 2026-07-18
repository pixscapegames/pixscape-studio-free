package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.spatial.SpatialStructureTopology;
import games.pixscape.studio.service.spatial.SpatialStructureCompilation;

final class SpatialBlockCommandSupport {
    private SpatialBlockCommandSupport() {
    }

    static SpatialBlocksComponent getOrCreate(World world, int layerEntityId) {
        ComponentMapper<SpatialBlocksComponent> mapper = world.getMapper(SpatialBlocksComponent.class);
        return mapper.has(layerEntityId) ? mapper.get(layerEntityId) : mapper.create(layerEntityId);
    }

    static SpatialBlocksComponent get(World world, int layerEntityId) {
        return world.getMapper(SpatialBlocksComponent.class).getSafe(layerEntityId, null);
    }

    static int indexOf(SpatialBlocksComponent component, int blockId) {
        if (component == null || component.blocks == null || blockId <= 0) return -1;
        for (int i = 0; i < component.blocks.size; i++) {
            SpatialBlockData block = component.blocks.get(i);
            if (block != null && block.id == blockId) return i;
        }
        return -1;
    }

    static SpatialBlockData find(SpatialBlocksComponent component, int blockId) {
        int index = indexOf(component, blockId);
        return index >= 0 ? component.blocks.get(index) : null;
    }

    static int allocateId(SpatialBlocksComponent component) {
        int max = 0;
        if (component != null && component.blocks != null) {
            for (int i = 0; i < component.blocks.size; i++) {
                SpatialBlockData block = component.blocks.get(i);
                if (block != null) max = Math.max(max, block.id);
            }
        }
        return max + 1;
    }

    static void apply(SpatialBlockData target, SpatialBlockData source) {
        if (target == null || source == null) return;
        target.id = source.id;
        target.structureId = source.structureId;
        target.name = source.name;
        target.x = source.x;
        target.y = source.y;
        target.width = source.width;
        target.depth = source.depth;
        target.altitude = source.altitude;
        target.height = source.height;
        target.actorOccluder = source.actorOccluder;
        target.physicsCollision = source.physicsCollision;
        target.lightOccluder = source.lightOccluder;
        target.shadowCaster = source.shadowCaster;
        target.particleOccluder = source.particleOccluder;
        target.linkedTileRefsAuthored = source.linkedTileRefsAuthored;
        target.copyLinkedTileRefsFrom(source);
    }

    static boolean same(SpatialBlockData a, SpatialBlockData b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.id == b.id
                && a.structureId == b.structureId
                && java.util.Objects.equals(a.name, b.name)
                && Float.compare(a.x, b.x) == 0
                && Float.compare(a.y, b.y) == 0
                && Float.compare(a.width, b.width) == 0
                && Float.compare(a.depth, b.depth) == 0
                && Float.compare(a.altitude, b.altitude) == 0
                && Float.compare(a.height, b.height) == 0
                && a.actorOccluder == b.actorOccluder
                && a.physicsCollision == b.physicsCollision
                && a.lightOccluder == b.lightOccluder
                && a.shadowCaster == b.shadowCaster
                && a.particleOccluder == b.particleOccluder
                && sameLinkedTileRefs(a, b);
    }

    private static boolean sameLinkedTileRefs(SpatialBlockData a, SpatialBlockData b) {
        if (a.linkedTileRefsAuthored != b.linkedTileRefsAuthored) return false;
        int aSize = a.linkedTileRefs != null ? a.linkedTileRefs.size : 0;
        int bSize = b.linkedTileRefs != null ? b.linkedTileRefs.size : 0;
        if (aSize != bSize) return false;
        for (int i = 0; i < aSize; i++) {
            SpatialBlockData.LinkedTileRef ar = a.linkedTileRefs.get(i);
            SpatialBlockData.LinkedTileRef br = b.linkedTileRefs.get(i);
            if (ar == br) continue;
            if (ar == null || br == null) return false;
            if (ar.gx != br.gx || ar.gy != br.gy || ar.tileAssetId != br.tileAssetId) return false;
        }
        return true;
    }

    static void markChanged(World world, int layerEntityId, Object source) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.layer(layerEntityId);
            dirty.order(layerEntityId);
        }
        EventFlow.i().publish(new EventFlow.SpatialBlocksChanged(layerEntityId, EventFlow.tag(source)));
    }

    static com.badlogic.gdx.utils.Array<SpatialBlockData> snapshot(SpatialBlocksComponent component) {
        return SpatialStructureTopology.copyWalls(component);
    }

    static CommandOutcome replaceAllValidated(World world,
                                              int layerEntityId,
                                              Array<SpatialBlockData> snapshot) {
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        SpatialStructureCompilation.Result compilation = SpatialStructureCompilation.tryCompile(
                snapshot, tiled != null ? tiled.data : null);
        if (!compilation.success()) {
            if (Gdx.app != null) {
                Gdx.app.error("SpatialBlockCommand",
                        "Rejected atomic wall snapshot for layer " + layerEntityId + ": "
                                + compilation.diagnostic());
            }
            return CommandOutcome.REJECTED;
        }
        Array<SpatialBlockData> replacement = new Array<>(SpatialBlockData[]::new);
        if (snapshot != null) {
            for (int i = 0; i < snapshot.size; i++) replacement.add(snapshot.get(i).copy());
        }
        SpatialBlocksComponent component = getOrCreate(world, layerEntityId);
        component.blocks = replacement;
        component.revision++;
        return CommandOutcome.APPLIED;
    }
}
