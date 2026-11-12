package games.pixscape.studio.io;

import com.badlogic.gdx.utils.JsonValue;

import java.util.function.IntConsumer;

/**
 * Logical cell IDs in an authored Scene, before tile animation IDs are expanded to frames.
 */
public final class TiledLayerTileIds {
    private TiledLayerTileIds() {
    }

    public static void forEach(JsonValue scene, IntConsumer consumer) {
        if (scene == null || !scene.isObject()) throw new IllegalArgumentException("Expected a Scene object.");
        JsonValue entities = scene.get("entities");
        if (entities == null) return;
        if (!entities.isObject()) throw new IllegalArgumentException("Expected a Scene entities object.");
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            if (!entity.isObject()) throw new IllegalArgumentException("Invalid Scene entity.");
            JsonValue components = entity.get("components");
            if (components == null) continue;
            if (!components.isObject()) throw new IllegalArgumentException("Expected a Scene components object.");
            JsonValue tiled = components.get("TiledLayerComponent");
            if (tiled == null) continue;
            if (!tiled.isObject()) throw new IllegalArgumentException("Invalid TiledLayerComponent.");
            readIds(tiled.get("tileAssetIds"), consumer);
            JsonValue data = tiled.get("data");
            if (data == null || data.isNull()) continue;
            if (!data.isObject()) throw new IllegalArgumentException("Invalid TiledLayerComponent data.");
            readIds(data.get("tileAssetIds"), consumer);
        }
    }

    private static void readIds(JsonValue bag, IntConsumer consumer) {
        if (bag == null) return;
        JsonValue items = bag.isArray() ? bag : bag.get("items");
        if (items == null || !items.isArray()) throw new IllegalArgumentException("Expected tileAssetIds items.");
        int size = bag.isArray() ? items.size : bag.getInt("size", items.size);
        if (size < 0 || size > items.size) throw new IllegalArgumentException("Invalid tileAssetIds size.");
        int index = 0;
        for (JsonValue item = items.child; index < size; item = item.next, index++) {
            int id = item.asInt();
            if (id > 0) consumer.accept(id);
        }
    }
}
