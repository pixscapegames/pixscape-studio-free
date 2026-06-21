package games.pixscape.studio.service;

import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AtlasStudioServiceStateTest {

    @Test
    public void requestAsyncPack_tracksQueuedScene_andScopedQueueVisibility() {
        AtlasStudioService service = new AtlasStudioService(null);

        service.requestAsyncPack("main");

        assertTrue(service.isPackRequested());
        assertFalse(service.isPackInProgress());
        assertTrue(service.hasAsyncPackQueuedOrRunningFor("main"));
        assertFalse(service.hasAsyncPackQueuedOrRunningFor("other"));
        assertTrue(service.hasAsyncPackQueuedOrRunningFor(null));
    }

    @Test
    public void markDirty_delegatesToAsyncPackRequest() {
        AtlasStudioService service = new AtlasStudioService(null);

        service.markDirty("scene-a");

        assertTrue(service.isPackRequested());
        assertTrue(service.hasAsyncPackQueuedOrRunningFor("scene-a"));
    }

    @Test
    public void isPacked_usesCachedIds_andGuardsInvalidInputs() throws Exception {
        AtlasStudioService service = new AtlasStudioService(null);

        ObjectMap<String, IntSet> map = new ObjectMap<>();
        IntSet ids = new IntSet();
        ids.add(4);
        ids.add(9);
        map.put("main", ids);
        setField(service, "packedIdsBySceneTag", map);

        assertTrue(service.isPacked(4, "main"));
        assertFalse(service.isPacked(5, "main"));
        assertFalse(service.isPacked(-1, "main"));
        assertFalse(service.isPacked(4, " "));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
