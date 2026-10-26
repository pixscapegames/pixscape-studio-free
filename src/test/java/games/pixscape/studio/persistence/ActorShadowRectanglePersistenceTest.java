package games.pixscape.studio.persistence;

import com.artemis.*;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.loading.*;
import games.pixscape.runtime.spatial.*;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.SceneService;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActorShadowRectanglePersistenceTest {
    @Test public void authoredVolumeSurvivesSceneAndClipboardThenDisappearsWhenSpatialComponentIsRemoved() throws Exception {
        FileHandle file=new FileHandle(Files.createTempFile("actor-volume-", ".json").toFile());
        World a=new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        World b=new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        try {
            int e=a.create();a.getMapper(PixscapeIdentityComponent.class).create(e).stableId=1;
            SpatialHeightComponent height=a.getMapper(SpatialHeightComponent.class).create(e);
            height.rectangles=new ActorSpatialRectangles();height.rectangles.staticRectangle=new ActorSpatialRectangleData();
            height.rectangles.staticRectangle.orientation=37.25f;
            a.process();
            GenericEntityInitializer initializer=new GenericEntityInitializer(a);initializer.syncFrom(e);
            assertEquals(37.25f,initializer.duplicate().toSnapshotData(e).actorSpatialRectangles.staticRectangle.orientation,0);
            var detached=initializer.duplicate().normalizeClipboardSpatial(false).toSnapshotData(e);
            assertFalse(detached.hasSpatialHeight);assertNull(detached.actorSpatialRectangles);
            SceneService.saveScene(a,file,false);
            SceneMetaRuntime meta=new SceneMetaRuntime();meta.nextEntityStableId=2;
            SceneLoader.loadScene(b,file,true,meta);b.process();
            int restored=b.getAspectSubscriptionManager().get(Aspect.all(SpatialHeightComponent.class)).getEntities().get(0);
            assertEquals(37.25f,b.getMapper(SpatialHeightComponent.class).get(restored).rectangles.staticRectangle.orientation,0);
            a.getMapper(SpatialHeightComponent.class).remove(e);a.process();SceneService.saveScene(a,file,false);
            assertFalse(file.readString().contains("ActorSpatialRectangles"));assertFalse(file.readString().contains("SpatialHeightComponent"));
            SceneLoader.loadScene(b,file,true,meta);
        }finally{a.dispose();b.dispose();file.delete();}
    }
}
