package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class TiledBrushSessionTest {
    @Test
    public void dragLeavesLiveMapAndRevisionUnchanged() {
        TiledLayerComponent tiled = tiled();
        TiledBrushSession session = new TiledBrushSession(7);
        int revision = tiled.data.contentRevision();

        session.apply(tiled, 1, 1, 10);
        session.apply(tiled, 2, 1, 10);
        session.apply(tiled, 3, 1, 10);

        Assert.assertEquals(0, tiled.data.getTile(1, 1));
        Assert.assertEquals(0, tiled.data.getTile(2, 1));
        Assert.assertEquals(0, tiled.data.getTile(3, 1));
        Assert.assertEquals(revision, tiled.data.contentRevision());
    }

    @Test
    public void repeatedCellIsCanonicalAndLastRequestedValueWins() {
        TiledLayerComponent tiled = tiled();
        TiledBrushSession session = new TiledBrushSession(7);
        session.apply(tiled, 2, 3, 10);
        session.apply(tiled, 2, 3, 11, TileTransformFlags.FLIP_H);

        TiledMutationPlan plan = session.toPlan();

        Assert.assertEquals(1, plan.size());
        Assert.assertEquals(11, plan.assetId(0, true));
        Assert.assertEquals(TileTransformFlags.FLIP_H, plan.flags(0, true));
    }

    @Test
    public void finalNoopIsRemoved() {
        TiledLayerComponent tiled = tiled();
        tiled.data.setTile(2, 3, 9, TileTransformFlags.FLIP_V);
        TiledBrushSession session = new TiledBrushSession(7);
        session.apply(tiled, 2, 3, 0);
        session.apply(tiled, 2, 3, 9, TileTransformFlags.FLIP_V);

        Assert.assertTrue(session.toPlan().isEmpty());
    }

    @Test
    public void cancelDiscardsPendingCellsWithoutMapMutation() {
        TiledLayerComponent tiled = tiled();
        TiledBrushSession session = new TiledBrushSession(7);
        session.apply(tiled, 1, 1, 10);

        session.cancel();

        Assert.assertTrue(session.isEmpty());
        Assert.assertEquals(0, tiled.data.getTile(1, 1));
    }

    private static TiledLayerComponent tiled() {
        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4, SceneMetaRuntime.TiledProjection.ISO);
        return tiled;
    }
}
