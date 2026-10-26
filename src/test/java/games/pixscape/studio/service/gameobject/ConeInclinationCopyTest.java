package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConeInclinationCopyTest {
    @Test public void genericHistoryCopiesAndGameObjectInitializersKeepInclination() {
        World world=new World();
        try {
            int e=world.create();world.getMapper(TransformComponent.class).create(e);
            world.getMapper(DimensionsComponent.class).create(e);
            world.getMapper(PixscapeIdentityComponent.class).create(e).stableId=1;
            world.getMapper(EntityIndexComponent.class).create(e);
            world.getMapper(ConeLightComponent.class).create(e).inclinationDeg=-31.25f;world.process();
            GenericEntityInitializer generic=new GenericEntityInitializer(world);generic.syncFrom(e);
            int copy=world.create();generic.duplicate().init(copy);
            assertEquals(-31.25f,world.getMapper(ConeLightComponent.class).get(copy).inclinationDeg,0);
            GameObjectAsset.GameObjectEntityData data=new GameObjectEntityDataMapper().fromGraphEntry(world,new EntityGraphEntry(e,generic),new com.badlogic.gdx.utils.IntMap<>());
            assertEquals(-31.25f,data.coneLight.inclinationDeg,0);
            var initializer=new GameObjectAssetEntityInitializer(world,generic,data,0,"",null);
            int restored=world.create();initializer.init(restored);
            assertEquals(-31.25f,world.getMapper(ConeLightComponent.class).get(restored).inclinationDeg,0);
            world.getMapper(ConeLightComponent.class).get(restored).inclinationDeg=22.125f;
            initializer.syncFrom(restored);initializer.init(restored);
            assertEquals(22.125f,world.getMapper(ConeLightComponent.class).get(restored).inclinationDeg,0);
        } finally {world.dispose();}
    }
}
