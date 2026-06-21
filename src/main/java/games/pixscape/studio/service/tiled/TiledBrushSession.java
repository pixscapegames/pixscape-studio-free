package games.pixscape.studio.service.tiled;

import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.PackedTileValue;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.studio.helper.TiledSparseStorageHelper;

public final class TiledBrushSession {

    private final IntIntMap previousValues = new IntIntMap();
    private final IntIntMap newValues = new IntIntMap();

    private final int layerEntityId;

    public TiledBrushSession(int layerEntityId) {
        this.layerEntityId = layerEntityId;
    }

    public void apply(TiledLayerComponent comp, int gx, int gy, int newAssetId) {
        apply(comp, gx, gy, newAssetId, TileTransformFlags.NONE);
    }

    public void apply(TiledLayerComponent comp, int gx, int gy, int newAssetId, byte newFlags) {

        int key = pack(gx, gy);

        if (newValues.containsKey(key)) return;

        int previousAssetId = comp.data.getTile(gx, gy);
        byte previousFlags = comp.data.getTileTransformFlags(gx, gy);

        int previousPacked = PackedTileValue.pack(previousAssetId, previousFlags);
        int nextPacked = PackedTileValue.pack(newAssetId, newFlags);

        if (previousPacked == nextPacked) return;

        previousValues.put(key, previousPacked);
        newValues.put(key, nextPacked);

        comp.data.setTile(gx, gy, newAssetId, newFlags);
        TiledSparseStorageHelper.setTile(comp, gx, gy, newAssetId, newFlags);
    }

    public boolean isEmpty() {
        return newValues.size == 0;
    }

    public IntIntMap getPreviousValues() {
        return previousValues;
    }

    public IntIntMap getNewValues() {
        return newValues;
    }

    public int getLayerEntityId() {
        return layerEntityId;
    }

    private static int pack(int x, int y) {
        return (x << 16) ^ (y & 0xFFFF);
    }
}