package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.CompiledSpatialStructure;
import games.pixscape.runtime.spatial.SpatialStructureCompiler;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Studio-safe validation and staging boundary around the strict Runtime compiler. */
public final class SpatialStructureCompilation {
    public static final class Result {
        private final boolean success;
        private final String diagnostic;
        private final CompiledSpatialStructure[] structures;

        private Result(boolean success, String diagnostic, CompiledSpatialStructure[] structures) {
            this.success = success;
            this.diagnostic = diagnostic;
            this.structures = structures;
        }

        public boolean success() { return success; }
        public String diagnostic() { return diagnostic; }
        public int structureCount() { return structures.length; }
        public CompiledSpatialStructure structure(int index) { return structures[index]; }
    }

    private SpatialStructureCompilation() {
    }

    public static Result tryCompile(SpatialBlocksComponent component, TiledMapLayerData map) {
        Array<SpatialBlockData> walls = SpatialStructureTopology.copyWalls(component);
        return tryCompile(walls, map);
    }

    public static Result tryCompile(Array<SpatialBlockData> walls, TiledMapLayerData map) {
        SpatialBlocksComponent detached = new SpatialBlocksComponent();
        detached.blocks = new Array<>(SpatialBlockData[]::new);
        if (walls != null) {
            for (int i = 0; i < walls.size; i++) {
                SpatialBlockData wall = walls.get(i);
                if (wall != null) detached.blocks.add(wall.copy());
            }
        }

        SpatialStructureTopology.Plan validation = SpatialStructureTopology.validate(detached, map);
        if (!validation.valid) return failure(validation.error);

        int[] structureIds = collectStructureIds(detached);
        CompiledSpatialStructure[] staged = new CompiledSpatialStructure[structureIds.length];
        try {
            for (int i = 0; i < structureIds.length; i++) {
                staged[i] = SpatialStructureCompiler.compile(detached.blocks, structureIds[i]);
            }
        } catch (RuntimeException failure) {
            return failure("Spatial structure compilation failed: " + safeMessage(failure));
        }
        return new Result(true, null, staged);
    }

    private static int[] collectStructureIds(SpatialBlocksComponent component) {
        int[] ids = new int[component.blocks.size];
        int count = 0;
        for (int i = 0; i < component.blocks.size; i++) {
            int id = component.blocks.get(i).structureId;
            int insert = 0;
            while (insert < count && ids[insert] < id) insert++;
            if (insert < count && ids[insert] == id) continue;
            for (int move = count; move > insert; move--) ids[move] = ids[move - 1];
            ids[insert] = id;
            count++;
        }
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = ids[i];
        return result;
    }

    private static Result failure(String diagnostic) {
        return new Result(false,
                diagnostic != null && !diagnostic.isEmpty() ? diagnostic : "Invalid Spatial V3 authored snapshot.",
                new CompiledSpatialStructure[0]);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message != null && !message.isEmpty() ? message : failure.getClass().getSimpleName();
    }
}
