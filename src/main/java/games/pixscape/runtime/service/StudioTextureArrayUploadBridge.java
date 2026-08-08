package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.utils.Array;

/** Studio-only access to Runtime's package-scoped one-shot texture-array upload. */
public final class StudioTextureArrayUploadBridge {

    private StudioTextureArrayUploadBridge() {
    }

    public static TextureArray uploadBorrowed(Array<Pixmap> layers) {
        return OneShotPixmapTextureArrayData.upload(layers, false);
    }
}
