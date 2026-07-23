package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.CompiledSpatialStructure;
import games.pixscape.runtime.spatial.SpatialStructureCompiler;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Studio-owned transactional cache for compiled Spatial V3 structure envelopes. */
public final class SpatialStructureGeometryCache {
    private static final SynchronizeResult UNCHANGED = new SynchronizeResult(true, false, null);

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

    private Array<Entry> entries = new Array<>(Entry[]::new);
    private int[] structureIds = new int[0];
    private int structureIdCount;
    private int compilationCount;
    private int publishedRevision;
    private final SourceMarker publishedSource = new SourceMarker();
    private int failureCount;
    private String lastDiagnostic;
    private final SourceMarker failedSource = new SourceMarker();
    private SynchronizeResult lastFailureResult;

    public SynchronizeResult synchronize(int requestedLayerEntityId,
                                         SpatialBlocksComponent component,
                                         TiledMapLayerData map) {
        if (isPublishedSnapshotCurrent(requestedLayerEntityId, component, map)) {
            return UNCHANGED;
        }
        int sourceRevision = component != null ? component.revision : 0;
        if (isFailedSourceCurrent(requestedLayerEntityId, component, map, sourceRevision)) {
            return lastFailureResult;
        }
        collectStructureIds(component);
        SpatialStructureTopology.Plan validation = SpatialStructureTopology.validate(component, map);
        if (!validation.valid) return failure(requestedLayerEntityId, component, map,
                sourceRevision, validation.error);

        Array<Entry> staging = new Array<>(Entry[]::new);
        int stagedCompilations = 0;
        boolean changed = requestedLayerEntityId != publishedSource.layerEntityId
                || component != publishedSource.component
                || map != publishedSource.map
                || entries.size != structureIdCount;
        try {
            for (int i = 0; i < structureIdCount; i++) {
                int structureId = structureIds[i];
                Entry published = requestedLayerEntityId == publishedSource.layerEntityId
                        ? find(entries, structureId) : null;
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
            return failure(requestedLayerEntityId, component, map, sourceRevision,
                    "Spatial structure compilation failed: " + safeMessage(compileFailure));
        }

        if (!changed) {
            capturePublishedSource(requestedLayerEntityId, component, map, sourceRevision);
            clearFailedSource();
            lastDiagnostic = null;
            return UNCHANGED;
        }
        entries = staging;
        capturePublishedSource(requestedLayerEntityId, component, map, sourceRevision);
        compilationCount += stagedCompilations;
        publishedRevision++;
        lastDiagnostic = null;
        clearFailedSource();
        return new SynchronizeResult(true, true, null);
    }

    public void clear() {
        entries.clear();
        clearPublishedSource();
        clearFailedSource();
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
                                               SpatialBlocksComponent component,
                                               TiledMapLayerData map) {
        return isPublishedSourceCurrent(requestedLayerEntityId, component, map,
                component != null ? component.revision : 0);
    }

    private SynchronizeResult failure(int requestedLayerEntityId,
                                      SpatialBlocksComponent component,
                                      TiledMapLayerData map,
                                      int requestedSourceRevision,
                                      String diagnostic) {
        String message = diagnostic != null && !diagnostic.isEmpty()
                ? diagnostic : "Invalid committed Spatial V3 authored snapshot.";
        invalidatePublishedGeometry();
        failureCount++;
        captureFailedSource(requestedLayerEntityId, component, map, requestedSourceRevision);
        if (!message.equals(lastDiagnostic)) {
            lastDiagnostic = message;
            if (Gdx.app != null) {
                Gdx.app.error("SpatialStructureGeometryCache",
                        "Layer " + requestedLayerEntityId + " has no compiled overlay geometry: " + message);
            }
        }
        lastFailureResult = new SynchronizeResult(false, false, message);
        return lastFailureResult;
    }

    private boolean isPublishedSourceCurrent(int requestedLayerEntityId,
                                             SpatialBlocksComponent component,
                                             TiledMapLayerData map,
                                             int sourceRevision) {
        return publishedSource.matches(requestedLayerEntityId, component, map, sourceRevision);
    }

    private boolean isFailedSourceCurrent(int requestedLayerEntityId,
                                          SpatialBlocksComponent component,
                                          TiledMapLayerData map,
                                          int sourceRevision) {
        return lastFailureResult != null
                && failedSource.matches(requestedLayerEntityId, component, map, sourceRevision);
    }

    private void capturePublishedSource(int requestedLayerEntityId,
                                        SpatialBlocksComponent component,
                                        TiledMapLayerData map,
                                        int sourceRevision) {
        publishedSource.capture(requestedLayerEntityId, component, map, sourceRevision);
    }

    private void captureFailedSource(int requestedLayerEntityId,
                                     SpatialBlocksComponent component,
                                     TiledMapLayerData map,
                                     int sourceRevision) {
        failedSource.capture(requestedLayerEntityId, component, map, sourceRevision);
    }

    private void invalidatePublishedGeometry() {
        entries.clear();
        clearPublishedSource();
        publishedRevision++;
    }

    private void clearPublishedSource() {
        publishedSource.clear();
    }

    private void clearFailedSource() {
        failedSource.clear();
        lastFailureResult = null;
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

    private static final class SourceMarker {
        int layerEntityId = -1;
        int sourceRevision = -1;
        SpatialBlocksComponent component;
        TiledMapLayerData map;
        int mapContentRevision = -1;

        boolean matches(int requestedLayerEntityId,
                        SpatialBlocksComponent requestedComponent,
                        TiledMapLayerData requestedMap,
                        int requestedSourceRevision) {
            if (requestedLayerEntityId != layerEntityId
                    || requestedComponent != component
                    || requestedSourceRevision != sourceRevision
                    || requestedMap != map) return false;
            return requestedMap == null || requestedMap.contentRevision() == mapContentRevision;
        }

        void capture(int requestedLayerEntityId,
                     SpatialBlocksComponent requestedComponent,
                     TiledMapLayerData requestedMap,
                     int requestedSourceRevision) {
            layerEntityId = requestedLayerEntityId;
            component = requestedComponent;
            sourceRevision = requestedSourceRevision;
            map = requestedMap;
            mapContentRevision = requestedMap != null ? requestedMap.contentRevision() : -1;
        }

        void clear() {
            layerEntityId = -1;
            sourceRevision = -1;
            component = null;
            map = null;
            mapContentRevision = -1;
        }
    }

    private static final class Entry {
        final int structureId;
        final Array<SpatialBlockData> snapshots = new Array<>(SpatialBlockData[]::new);
        CompiledSpatialStructure compiled;

        Entry(int structureId) { this.structureId = structureId; }

        boolean matches(SpatialBlocksComponent component) {
            int polygonVertexCount = count(component, structureId);
            if (polygonVertexCount != snapshots.size) return false;
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
                    || first.actorOccluder != second.actorOccluder) return false;
            return true;
        }
    }
}
