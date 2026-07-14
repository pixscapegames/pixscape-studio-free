package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.CompiledSpatialStructure;
import games.pixscape.runtime.spatial.SpatialStructureCompiler;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Studio-owned transactional cache for compiled Spatial V3 structure envelopes. */
public final class SpatialStructureGeometryCache {
    public static final class SynchronizeResult {
        private final boolean success;
        private final boolean published;
        private final String diagnostic;

        private SynchronizeResult(boolean success, boolean published, String diagnostic) {
            this.success = success;
            this.published = published;
            this.diagnostic = diagnostic;
        }

        public boolean success() { return success; }
        public boolean published() { return published; }
        public String diagnostic() { return diagnostic; }
    }

    private int layerEntityId = -1;
    private Array<Entry> entries = new Array<>(Entry[]::new);
    private int[] structureIds = new int[0];
    private int structureIdCount;
    private int compilationCount;
    private int publishedRevision;
    private int failureCount;
    private String lastDiagnostic;

    public SynchronizeResult synchronize(int requestedLayerEntityId,
                                         SpatialBlocksComponent component,
                                         TiledMapLayerData map) {
        collectStructureIds(component);
        if (isPublishedSnapshotCurrent(requestedLayerEntityId, component)) {
            return new SynchronizeResult(true, false, null);
        }
        SpatialStructureTopology.Plan validation = SpatialStructureTopology.validate(component, map);
        if (!validation.valid) return failure(requestedLayerEntityId, validation.error);

        Array<Entry> staging = new Array<>(Entry[]::new);
        int stagedCompilations = 0;
        boolean changed = requestedLayerEntityId != layerEntityId || entries.size != structureIdCount;
        try {
            for (int i = 0; i < structureIdCount; i++) {
                int structureId = structureIds[i];
                Entry published = requestedLayerEntityId == layerEntityId ? find(entries, structureId) : null;
                if (published != null && published.matches(component)) {
                    staging.add(published);
                    continue;
                }
                Entry staged = new Entry(structureId);
                staged.capture(component);
                staged.compiled = SpatialStructureCompiler.compile(component.blocks, structureId);
                staging.add(staged);
                stagedCompilations++;
                changed = true;
            }
        } catch (RuntimeException compileFailure) {
            return failure(requestedLayerEntityId,
                    "Spatial structure compilation failed: " + safeMessage(compileFailure));
        }

        if (!changed) return new SynchronizeResult(true, false, null);
        entries = staging;
        layerEntityId = requestedLayerEntityId;
        compilationCount += stagedCompilations;
        publishedRevision++;
        lastDiagnostic = null;
        return new SynchronizeResult(true, true, null);
    }

    public void clear() {
        entries = new Array<>(Entry[]::new);
        layerEntityId = -1;
        publishedRevision++;
        lastDiagnostic = null;
    }

    public int structureCount() { return entries.size; }
    public CompiledSpatialStructure structure(int index) { return entries.get(index).compiled; }
    public int compilationCount() { return compilationCount; }
    public int publishedRevision() { return publishedRevision; }
    public int failureCount() { return failureCount; }
    public String lastDiagnostic() { return lastDiagnostic; }

    private boolean isPublishedSnapshotCurrent(int requestedLayerEntityId,
                                               SpatialBlocksComponent component) {
        if (requestedLayerEntityId != layerEntityId || entries.size != structureIdCount) return false;
        for (int i = 0; i < entries.size; i++) {
            if (!entries.get(i).matches(component)) return false;
        }
        return true;
    }

    private SynchronizeResult failure(int requestedLayerEntityId, String diagnostic) {
        String message = diagnostic != null && !diagnostic.isEmpty()
                ? diagnostic : "Invalid committed Spatial V3 authored snapshot.";
        failureCount++;
        if (!message.equals(lastDiagnostic)) {
            lastDiagnostic = message;
            if (Gdx.app != null) {
                Gdx.app.error("SpatialStructureGeometryCache",
                        "Layer " + requestedLayerEntityId + " retained its last valid compiled cache: " + message);
            }
        }
        return new SynchronizeResult(false, false, message);
    }

    private static Entry find(Array<Entry> source, int structureId) {
        for (int i = 0; i < source.size; i++) {
            Entry entry = source.get(i);
            if (entry.structureId == structureId) return entry;
        }
        return null;
    }

    private void collectStructureIds(SpatialBlocksComponent component) {
        structureIdCount = 0;
        if (component == null || component.blocks == null) return;
        ensureStructureIdCapacity(component.blocks.size);
        for (int i = 0; i < component.blocks.size; i++) {
            SpatialBlockData wall = component.blocks.get(i);
            if (wall == null || wall.structureId <= 0
                    || indexOf(structureIds, structureIdCount, wall.structureId) >= 0) continue;
            int insert = structureIdCount;
            while (insert > 0 && structureIds[insert - 1] > wall.structureId) {
                structureIds[insert] = structureIds[insert - 1];
                insert--;
            }
            structureIds[insert] = wall.structureId;
            structureIdCount++;
        }
    }

    private void ensureStructureIdCapacity(int required) {
        if (required <= structureIds.length) return;
        structureIds = new int[Math.max(required, structureIds.length * 2 + 4)];
    }

    private static int indexOf(int[] values, int count, int value) {
        for (int i = 0; i < count; i++) if (values[i] == value) return i;
        return -1;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message != null && !message.isEmpty() ? message : failure.getClass().getSimpleName();
    }

    private static final class Entry {
        final int structureId;
        final Array<SpatialBlockData> snapshots = new Array<>(SpatialBlockData[]::new);
        CompiledSpatialStructure compiled;

        Entry(int structureId) { this.structureId = structureId; }

        boolean matches(SpatialBlocksComponent component) {
            int sourceCount = count(component, structureId);
            if (sourceCount != snapshots.size) return false;
            for (int i = 0; i < snapshots.size; i++) {
                SpatialBlockData snapshot = snapshots.get(i);
                SpatialBlockData current = find(component, snapshot.id, structureId);
                if (!sameCompilerInput(snapshot, current)) return false;
            }
            return compiled != null;
        }

        void capture(SpatialBlocksComponent component) {
            snapshots.clear();
            if (component == null || component.blocks == null) return;
            for (int i = 0; i < component.blocks.size; i++) {
                SpatialBlockData wall = component.blocks.get(i);
                if (wall != null && wall.structureId == structureId) snapshots.add(wall.copy());
            }
            for (int i = 1; i < snapshots.size; i++) {
                SpatialBlockData value = snapshots.get(i);
                int j = i - 1;
                while (j >= 0 && snapshots.get(j).id > value.id) {
                    snapshots.set(j + 1, snapshots.get(j));
                    j--;
                }
                snapshots.set(j + 1, value);
            }
        }

        private static int count(SpatialBlocksComponent component, int structureId) {
            if (component == null || component.blocks == null) return 0;
            int count = 0;
            for (int i = 0; i < component.blocks.size; i++) {
                SpatialBlockData wall = component.blocks.get(i);
                if (wall != null && wall.structureId == structureId) count++;
            }
            return count;
        }

        private static SpatialBlockData find(SpatialBlocksComponent component, int id, int structureId) {
            if (component == null || component.blocks == null) return null;
            for (int i = 0; i < component.blocks.size; i++) {
                SpatialBlockData wall = component.blocks.get(i);
                if (wall != null && wall.id == id && wall.structureId == structureId) return wall;
            }
            return null;
        }

        private static boolean sameCompilerInput(SpatialBlockData first, SpatialBlockData second) {
            if (first == null || second == null) return false;
            if (first.id != second.id || first.structureId != second.structureId
                    || Float.compare(first.x, second.x) != 0 || Float.compare(first.y, second.y) != 0
                    || Float.compare(first.width, second.width) != 0 || Float.compare(first.depth, second.depth) != 0
                    || Float.compare(first.altitude, second.altitude) != 0
                    || Float.compare(first.height, second.height) != 0
                    || first.actorOccluder != second.actorOccluder
                    || first.physicsCollision != second.physicsCollision
                    || first.lightOccluder != second.lightOccluder
                    || first.shadowCaster != second.shadowCaster
                    || first.particleOccluder != second.particleOccluder
                    || first.linkedTileRefsAuthored != second.linkedTileRefsAuthored) return false;
            int firstRefs = first.linkedTileRefs != null ? first.linkedTileRefs.size : 0;
            int secondRefs = second.linkedTileRefs != null ? second.linkedTileRefs.size : 0;
            if (firstRefs != secondRefs) return false;
            for (int i = 0; i < firstRefs; i++) {
                SpatialBlockData.LinkedTileRef a = first.linkedTileRefs.get(i);
                SpatialBlockData.LinkedTileRef b = second.linkedTileRefs.get(i);
                if (a == b) continue;
                if (a == null || b == null || a.gx != b.gx || a.gy != b.gy || a.tileAssetId != b.tileAssetId) return false;
            }
            return true;
        }
    }
}
