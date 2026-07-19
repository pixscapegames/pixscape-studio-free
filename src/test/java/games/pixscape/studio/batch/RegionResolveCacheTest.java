package games.pixscape.studio.batch;

import com.badlogic.gdx.utils.IntIntMap;
import org.junit.Assert;
import org.junit.Test;

public class RegionResolveCacheTest {

    @Test
    public void repeatedHandleMissesOnceThenHitsLastResolvedFastPath() {
        IntIntMap layers = new IntIntMap();
        layers.put(10, 3);

        RegionResolveCache cache = new RegionResolveCache(4);

        Assert.assertEquals(3, cache.resolveLayer(10, layers));
        Assert.assertEquals(3, cache.resolveLayer(10, layers));
        Assert.assertEquals(3, cache.resolveLayer(10, layers));

        Assert.assertEquals(1L, cache.missCount());
        Assert.assertEquals(2L, cache.hitCount());
    }

    @Test
    public void differentHandlesAreCachedInFixedArray() {
        IntIntMap layers = new IntIntMap();
        layers.put(10, 3);
        layers.put(20, 7);

        RegionResolveCache cache = new RegionResolveCache(4);

        Assert.assertEquals(3, cache.resolveLayer(10, layers));
        Assert.assertEquals(7, cache.resolveLayer(20, layers));
        Assert.assertEquals(3, cache.resolveLayer(10, layers));

        Assert.assertEquals(2L, cache.missCount());
        Assert.assertEquals(1L, cache.hitCount());
    }

    @Test
    public void clearInvalidatesCachedLayersButKeepsStats() {
        IntIntMap layers = new IntIntMap();
        layers.put(10, 3);

        RegionResolveCache cache = new RegionResolveCache(4);

        Assert.assertEquals(3, cache.resolveLayer(10, layers));
        cache.clear();
        Assert.assertEquals(3, cache.resolveLayer(10, layers));

        Assert.assertEquals(2L, cache.missCount());
        Assert.assertEquals(0L, cache.hitCount());
    }

    @Test
    public void missingHandleIsNotCached() {
        IntIntMap layers = new IntIntMap();
        RegionResolveCache cache = new RegionResolveCache(4);

        Assert.assertEquals(-1, cache.resolveLayer(99, layers));

        layers.put(99, 5);

        Assert.assertEquals(5, cache.resolveLayer(99, layers));
        Assert.assertEquals(2L, cache.missCount());
        Assert.assertEquals(0L, cache.hitCount());
    }

    @Test
    public void fixedCapacityEvictsOldestSlot() {
        IntIntMap layers = new IntIntMap();
        layers.put(1, 10);
        layers.put(2, 20);
        layers.put(3, 30);

        RegionResolveCache cache = new RegionResolveCache(2);

        Assert.assertEquals(10, cache.resolveLayer(1, layers));
        Assert.assertEquals(20, cache.resolveLayer(2, layers));
        Assert.assertEquals(30, cache.resolveLayer(3, layers));
        Assert.assertEquals(10, cache.resolveLayer(1, layers));

        Assert.assertEquals(4L, cache.missCount());
        Assert.assertEquals(0L, cache.hitCount());
    }
}
