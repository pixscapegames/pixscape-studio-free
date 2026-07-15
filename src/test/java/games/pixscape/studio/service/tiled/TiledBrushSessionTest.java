package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class TiledBrushSessionTest {
    @Test
    public void dragPublishesOneMapContentRevisionAtCommit() {
        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4, SceneMetaRuntime.TiledProjection.ISO);
        TiledBrushSession session = new TiledBrushSession(7);

        session.apply(tiled, 1, 1, 10);
        session.apply(tiled, 2, 1, 10);
        session.apply(tiled, 3, 1, 10);
        Assert.assertEquals(0, tiled.data.contentRevision());

        session.commit();
        Assert.assertEquals(1, tiled.data.contentRevision());
    }
}
