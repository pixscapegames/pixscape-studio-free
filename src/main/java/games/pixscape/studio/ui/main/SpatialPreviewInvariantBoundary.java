package games.pixscape.studio.ui.main;

import games.pixscape.runtime.spatial.SpatialConstraintInvariantException;
import games.pixscape.runtime.spatial.SpatialTileOrderInvariantException;
import games.pixscape.runtime.spatial.SpatialTileSyncInvariantException;

/** Keeps a Spatial V3 invariant failure inside the scene preview while leaving the Studio shell alive. */
final class SpatialPreviewInvariantBoundary {
    interface FrameProcessor {
        void processFrame();
    }

    interface FailureListener {
        void onSpatialInvariantFailure(RuntimeException failure);
    }

    private boolean blocked;
    private String blockedScene;
    private int editRevision,resourceRevision;

    /** Retry only after an authoring operation or successful resource publication. */
    void prepare(String scene,int edits,int resources){
        prepare(scene);
        if(edits!=editRevision||resources!=resourceRevision){blocked=false;blockedScene=null;}
        editRevision=edits;resourceRevision=resources;
    }

    void prepare(String scene) {
        if (blocked && !same(blockedScene, scene)) {
            blocked = false;
            blockedScene = null;
        }
    }

    boolean process(String scene, FrameProcessor processor, FailureListener listener) {
        prepare(scene);
        if (blocked) return false;
        try {
            processor.processFrame();
            return true;
        } catch (SpatialConstraintInvariantException | SpatialTileOrderInvariantException
                 | SpatialTileSyncInvariantException | games.pixscape.runtime.render.lighting.LightingAdmissionException failure) {
            blocked = true;
            blockedScene = scene;
            listener.onSpatialInvariantFailure(failure);
            return false;
        }
    }

    boolean isBlocked() {
        return blocked;
    }

    private static boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }
}
