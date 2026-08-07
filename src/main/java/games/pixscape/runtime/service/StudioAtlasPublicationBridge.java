package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;

/** Studio-only access to Runtime's package-scoped preloaded-atlas publication seam. */
public final class StudioAtlasPublicationBridge {

    private StudioAtlasPublicationBridge() {
    }

    public static void publish(AtlasRuntimeService service, String sceneTag, TextureAtlas atlas) {
        if (service == null) throw new IllegalArgumentException("Atlas service is null.");
        service.load(sceneTag, atlas);
    }
}
