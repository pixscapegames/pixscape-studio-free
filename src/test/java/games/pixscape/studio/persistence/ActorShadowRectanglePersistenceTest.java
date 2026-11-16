package games.pixscape.studio.persistence;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.SceneService;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.*;

public class ActorShadowRectanglePersistenceTest {
    @Test
    public void authoredVolumeSurvivesSceneAndClipboardThenDisappearsWhenSpatialComponentIsRemoved() throws Exception {
        FileHandle file = new FileHandle(Files.createTempFile("actor-volume-", ".json").toFile());
        World a = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        World b = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        try {
            int e = a.create();
            a.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).create(e).radiusPx = 27;
            a.getMapper(PixscapeIdentityComponent.class).create(e).stableId = 1;
            SpatialRepresentationComponent height = a.getMapper(SpatialRepresentationComponent.class).create(e);
            height.rectangles = new ActorSpatialRectangles();
            height.rectangles.staticRectangle = new ActorSpatialRectangleData();
            height.rectangles.staticRectangle.pivotX = 37.25f;
            a.process();
            GenericEntityInitializer initializer = new GenericEntityInitializer(a);
            initializer.syncFrom(e);
            assertEquals(37.25f, initializer.duplicate().toSnapshotData(e).actorSpatialRectangles.staticRectangle.pivotX, 0);
            var detached = initializer.duplicate().normalizeClipboardSpatial(false).toSnapshotData(e);
            assertNull(detached.spatialFootprint);
            assertFalse(detached.hasSpatialRepresentation);
            assertNull(detached.actorSpatialRectangles);
            SceneService.saveScene(a, file, false);
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 2;
            SceneLoader.loadScene(b, file, true, meta);
            b.process();
            int restored = b.getAspectSubscriptionManager().get(Aspect.all(SpatialRepresentationComponent.class)).getEntities().get(0);
            assertEquals(37.25f, b.getMapper(SpatialRepresentationComponent.class).get(restored).rectangles.staticRectangle.pivotX, 0);
            a.getMapper(SpatialRepresentationComponent.class).remove(e);
            a.process();
            SceneService.saveScene(a, file, false);
            assertFalse(file.readString().contains("ActorSpatialRectangles"));
            assertFalse(file.readString().contains("SpatialRepresentationComponent"));
            SceneLoader.loadScene(b, file, true, meta);
        } finally {
            a.dispose();
            b.dispose();
            file.delete();
        }
    }
}
