package games.pixscape.studio.asset;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class TiledProjectionCoherenceTest {

    @Test
    public void tilesetProfilesAndTmxImportUseSharedRuntimeTiledProjection() {
        assertSame(SceneMetaRuntime.TiledProjection.ORTHO, new TilesetAssetMeta().projection);
        assertFalse(Files.exists(Path.of("src/main/java/games/pixscape/studio/asset/TilesetProjection.java")));
        assertFalse(Files.exists(Path.of("src/main/java/games/pixscape/studio/importer/tmx/TmxTiledProjectionPlan.java")));
    }
}
