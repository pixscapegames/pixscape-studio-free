package games.pixscape.studio.batch;

import com.badlogic.gdx.utils.IntIntMap;

import java.util.Arrays;

final class RegionResolveCache {
    private static final int EMPTY_KEY = Integer.MIN_VALUE;
    private static final int MISSING_LAYER = -1;

    private int lastKey = EMPTY_KEY;
    private int lastLayer = MISSING_LAYER;

    private final int[] keys;
    private final int[] layers;
    private int nextSlot;

    private long hitCount;
    private long missCount;

    RegionResolveCache(int capacity) {
        int safeCapacity = Math.max(1, capacity);
        this.keys = new int[safeCapacity];
        this.layers = new int[safeCapacity];
        clear();
    }

    int resolveLayer(int textureHandle, IntIntMap handle2layer) {
        if (handle2layer == null) {
            return MISSING_LAYER;
        }

        if (textureHandle == lastKey && lastLayer >= 0) {
            hitCount++;
            return lastLayer;
        }

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == textureHandle) {
                int layer = layers[i];
                if (layer >= 0) {
                    lastKey = textureHandle;
                    lastLayer = layer;
                    hitCount++;
                    return layer;
                }
            }
        }

        missCount++;
        int resolvedLayer = handle2layer.get(textureHandle, MISSING_LAYER);
        if (resolvedLayer >= 0) {
            put(textureHandle, resolvedLayer);
        }
        return resolvedLayer;
    }

    private void put(int key, int layer) {
        keys[nextSlot] = key;
        layers[nextSlot] = layer;
        nextSlot = (nextSlot + 1) % keys.length;

        lastKey = key;
        lastLayer = layer;
    }

    void clear() {
        Arrays.fill(keys, EMPTY_KEY);
        Arrays.fill(layers, MISSING_LAYER);
        lastKey = EMPTY_KEY;
        lastLayer = MISSING_LAYER;
        nextSlot = 0;
    }

    long hitCount() {
        return hitCount;
    }

    long missCount() {
        return missCount;
    }

    void clearStats() {
        hitCount = 0L;
        missCount = 0L;
    }
}
