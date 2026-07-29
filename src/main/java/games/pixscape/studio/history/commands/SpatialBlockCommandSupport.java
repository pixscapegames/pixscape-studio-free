package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.spatial.SpatialStructureTopology;
import games.pixscape.studio.service.spatial.SpatialStructureCompilation;

public final class SpatialBlockCommandSupport {
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

    static int indexOfLinkedPhysicsShape(
            PhysicsShapesComponent shapes, int spatialBlockId) {
        if (shapes == null || shapes.shapes == null || spatialBlockId <= 0) return -1;
        for (int i = 0; i < shapes.shapes.size; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape != null && shape.spatialBlockId == spatialBlockId) return i;
        }
        return -1;
    }

    static int countLinkedPhysicsShapes(
            PhysicsShapesComponent shapes, int spatialBlockId) {
        if (shapes == null || shapes.shapes == null || spatialBlockId <= 0) return 0;
        int count = 0;
        for (int i = 0; i < shapes.shapes.size; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape != null && shape.spatialBlockId == spatialBlockId) count++;
        }
        return count;
    }

    static Array<PhysicsShapeData> copyPhysicsShapes(PhysicsShapesComponent shapes) {
        Array<PhysicsShapeData> copy = new Array<>(
                true,
                shapes != null && shapes.shapes != null ? shapes.shapes.size : 0,
                PhysicsShapeData.class);
        if (shapes != null && shapes.shapes != null) {
            for (int i = 0; i < shapes.shapes.size; i++) {
                PhysicsShapeData shape = shapes.shapes.get(i);
                copy.add(shape != null ? shape.copy() : null);
            }
        }
        return copy;
    }

    static CommandOutcome validateBlocks(
            World world, int layerEntityId, Array<SpatialBlockData> snapshot) {
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class)
                .getSafe(layerEntityId, null);
        SpatialStructureCompilation.Result compilation = SpatialStructureCompilation.tryCompile(
                snapshot, tiled != null ? tiled.data : null);
        if (compilation.success()) return CommandOutcome.APPLIED;
        if (Gdx.app != null) {
            Gdx.app.error("SpatialBlockCommand",
                    "Rejected atomic wall snapshot for layer " + layerEntityId + ": "
                            + compilation.diagnostic());
        }
        return CommandOutcome.REJECTED;
    }

    static PreparedPhysicsBodyCandidate preparePhysicsCandidateAgainstBlocks(
            World world,
            int layerEntityId,
            Array<SpatialBlockData> candidateBlocks,
            Array<PhysicsShapeData> candidateShapes) {
        if (candidateShapes == null || candidateShapes.size == 0) return null;
        if (!FixtureCommandSupport.containsLinkedShape(candidateShapes)) {
            return PhysicsService.prepareBodyCandidate(candidateShapes);
        }
        SpatialBlocksComponent component = get(world, layerEntityId);
        if (component == null) {
            throw new IllegalArgumentException(
                    "SpatialBlocksComponent is required for linked physics compilation.");
        }
        Array<SpatialBlockData> originalBlocks = component.blocks;
        component.blocks = candidateBlocks;
        try {
            return PhysicsService.prepareBodyCandidate(
                    world,
                    layerEntityId,
                    candidateShapes,
                    FixtureCommandSupport.requireCurrentPixelsPerMeter());
        } finally {
            component.blocks = originalBlocks;
        }
    }

    static void publishStaticTiledPhysicsCandidate(
            World world,
            int layerEntityId,
            Array<PhysicsShapeData> candidateShapes,
            PreparedPhysicsBodyCandidate prepared) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (candidateShapes == null || candidateShapes.size == 0) {
            world.getMapper(PhysicsRuntimeBodyComponent.class).remove(layerEntityId);
            world.getMapper(PhysicsCompiledFixturesComponent.class).remove(layerEntityId);
            world.getMapper(PhysicsShapesComponent.class).remove(layerEntityId);
            world.getMapper(PhysicsBodyComponent.class).remove(layerEntityId);
        } else {
            PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class)
                    .getSafe(layerEntityId, null);
            if (body == null) {
                body = world.getMapper(PhysicsBodyComponent.class).create(layerEntityId);
                PhysicsService.initDefaultBody(body);
            }
            body.type = PhysicsBodyComponent.STATIC;
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class)
                    .getSafe(layerEntityId, null);
            if (shapes == null) {
                shapes = world.getMapper(PhysicsShapesComponent.class).create(layerEntityId);
            }
            PhysicsCompiledFixturesComponent compiled =
                    world.getMapper(PhysicsCompiledFixturesComponent.class)
                            .getSafe(layerEntityId, null);
            if (compiled == null) {
                compiled = world.getMapper(PhysicsCompiledFixturesComponent.class)
                        .create(layerEntityId);
            }
            PhysicsService.publishPreparedCandidate(shapes, compiled, prepared);
        }
        if (dirty != null) dirty.physics(layerEntityId, PhysicsDirtyBits.ALL);
    }

    static CommandOutcome replaceAllValidated(World world,
                                              int layerEntityId,
                                              Array<SpatialBlockData> snapshot) {
        if (validateBlocks(world, layerEntityId, snapshot) != CommandOutcome.APPLIED) {
            return CommandOutcome.REJECTED;
        }
        Array<SpatialBlockData> replacement = new Array<>(SpatialBlockData[]::new);
        if (snapshot != null) {
            for (int i = 0; i < snapshot.size; i++) replacement.add(snapshot.get(i).copy());
        }
        SpatialBlocksComponent component = getOrCreate(world, layerEntityId);
        PhysicsShapesComponent shapes = world.getMapper(
                PhysicsShapesComponent.class).getSafe(layerEntityId, null);
        if (!hasLinkedShape(shapes)) {
            component.blocks = replacement;
            component.revision++;
            return CommandOutcome.APPLIED;
        }

        PhysicsCompiledFixturesComponent compiled = world.getMapper(
                PhysicsCompiledFixturesComponent.class).getSafe(layerEntityId, null);
        if (compiled == null || !compiled.valid) {
            logPhysicsRejection(layerEntityId,
                    "linked body has no valid PhysicsCompiledFixturesComponent");
            return CommandOutcome.REJECTED;
        }

        PreparedPhysicsBodyCandidate prepared;
        Array<SpatialBlockData> original = component.blocks;
        component.blocks = replacement;
        try {
            prepared = PhysicsService.prepareBodyCandidate(
                    world,
                    layerEntityId,
                    shapes.shapes,
                    FixtureCommandSupport.requireCurrentPixelsPerMeter());
        } catch (RuntimeException failure) {
            logPhysicsRejection(layerEntityId, failure.getMessage());
            return CommandOutcome.REJECTED;
        } finally {
            component.blocks = original;
        }

        component.blocks = replacement;
        component.revision++;
        publishStaticTiledPhysicsCandidate(
                world, layerEntityId, shapes.shapes, prepared);
        return CommandOutcome.APPLIED;
    }

    private static boolean hasLinkedShape(PhysicsShapesComponent shapes) {
        if (shapes == null || shapes.shapes == null) return false;
        for (int i = 0; i < shapes.shapes.size; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape != null && shape.spatialBlockId > 0) return true;
        }
        return false;
    }

    private static void logPhysicsRejection(int layerEntityId, String diagnostic) {
        if (Gdx.app != null) {
            Gdx.app.error(
                    "SpatialBlockCommand",
                    "Rejected linked physics compilation for layer "
                            + layerEntityId + ": " + diagnostic);
        }
    }
}
