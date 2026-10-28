package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.Initializer;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SceneRendererHistoryTest {
    @Test public void secondActivationWaitsForConsentAndHistoryRestoresBothChoices(){
        World world=new World(new WorldConfigurationBuilder().build());
        try {
            SceneMeta scene=new SceneMeta();scene.forwardLighting=true;scene.useNormalMaps=false;
            HistoryManager history=new HistoryManager(16);Runnable[] accept={null};int[] dialogs={0};
            int p=world.create(),c=world.create();world.getMapper(PointLightComponent.class).create(p);
            ConeLightComponent cone=world.getMapper(ConeLightComponent.class).create(c);cone.enabled=false;
            history.historyIds().ensureForEntity(p);history.historyIds().ensureForEntity(c);world.process();
            history.configureSceneLighting(world,scene,action->{dialogs[0]++;accept[0]=action;});
            history.execute(new ChangeLightActivationCommand(world,history.historyIds(),c,false,true));
            assertNotNull(accept[0]);assertFalse(cone.enabled);assertTrue(scene.forwardLighting);assertEquals(0,history.getCursor());
            // Cancel simply drops the continuation; no mutation or history entry exists.
            accept[0]=null;assertFalse(cone.enabled);
            history.execute(new ChangeLightActivationCommand(world,history.historyIds(),c,false,true));accept[0].run();
            assertTrue(cone.enabled);assertFalse(scene.forwardLighting);assertEquals(1,history.getCursor());
            history.undo();assertFalse(cone.enabled);assertTrue(scene.forwardLighting);
            history.redo();assertTrue(cone.enabled);assertFalse(scene.forwardLighting);
            assertEquals(2,dialogs[0]);assertFalse(scene.useNormalMaps);
            history.undo();
            world.getMapper(VisibilityComponent.class).create(c).visible=false;cone.enabled=true;
            history.execute(new ChangeLightActivationCommand(world,history.historyIds(),c,true,true));
            assertFalse(world.getMapper(VisibilityComponent.class).get(c).visible);accept[0].run();
            assertFalse(scene.forwardLighting);assertTrue(world.getMapper(VisibilityComponent.class).get(c).visible);
            history.undo();assertTrue(scene.forwardLighting);assertFalse(world.getMapper(VisibilityComponent.class).get(c).visible);
        }finally{world.dispose();EventFlow.i().discardPending();}
    }

    @Test public void preparedMultiEntityCreationIsGuardedBeforeAnyEntityOrCallback(){
        World world=new World(new WorldConfigurationBuilder().build());
        try {
            SceneMeta scene=new SceneMeta();scene.forwardLighting=true;
            HistoryManager history=new HistoryManager(16);Runnable[] accept={null};int[] created={0};
            history.configureSceneLighting(world,scene,action->accept[0]=action);
            Initializer light=new Initializer(){
                public int activeLightCount(){return 1;}
                public void init(int entity){world.getMapper(PointLightComponent.class).create(entity);}
                public void syncFrom(int entity){}
            };
            CreateEntityCommand a=new CreateEntityCommand(world,history.historyIds(),light,e->created[0]++);
            CreateEntityCommand b=new CreateEntityCommand(world,history.historyIds(),light,e->created[0]++);
            history.execute(new CompositeCommand("Insert lights",Arrays.asList(a,b)));
            assertEquals(0,created[0]);assertEquals(0,history.getCursor());assertNotNull(accept[0]);
            accept[0].run();assertEquals(2,created[0]);assertFalse(scene.forwardLighting);
            history.undo();assertTrue(scene.forwardLighting);assertEquals(0,history.historyIds().activeLightCount(world));
            world.process();history.redo();assertFalse(scene.forwardLighting);assertEquals(2,history.historyIds().activeLightCount(world));
        }finally{world.dispose();EventFlow.i().discardPending();}
    }
}
