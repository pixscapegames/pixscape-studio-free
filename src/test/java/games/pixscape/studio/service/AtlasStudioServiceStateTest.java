package games.pixscape.studio.service;

import games.pixscape.studio.service.atlas.AtlasStudioService;
import org.junit.Test;

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
}
