package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ChangeLightActivationCommand;
import org.junit.*;
import static org.junit.Assert.*;

public class SceneForwardRenderingCheckBoxTest {
    @BeforeClass public static void skin(){VisUiTestBootstrap.loadSkin();}
    @AfterClass public static void dispose(){VisUiTestBootstrap.unloadSkin();}
    @Test public void pointConeShareChoiceAndEligibilityWithoutChangingOtherScenes(){
        World world=new World(new WorldConfigurationBuilder().build());
        try(EventFlow.SubscriptionScope scope=EventFlow.i().newSubscriptionScope(()->true)){
            SceneMeta scene=new SceneMeta(),other=new SceneMeta();HistoryManager h=new HistoryManager(16);
            h.configureSceneLighting(world,scene,null);
            SceneForwardRenderingCheckBox[] boxes=new SceneForwardRenderingCheckBox[3];
            scope.run(()->{
                boxes[0]=new SceneForwardRenderingCheckBox(world,scene,h);
                boxes[1]=new SceneForwardRenderingCheckBox(world,scene,h);
                boxes[2]=new SceneForwardRenderingCheckBox(world,other,new HistoryManager(8));
            });
            assertFalse(boxes[0].isDisabled());boxes[0].setChecked(true);refresh(boxes);
            assertTrue(boxes[1].isChecked());assertFalse(boxes[2].isChecked());
            h.undo();refresh(boxes);assertFalse(boxes[0].isChecked());assertFalse(boxes[1].isChecked());
            int a=world.create(),b=world.create();world.getMapper(PointLightComponent.class).create(a);
            world.getMapper(ConeLightComponent.class).create(b).enabled=false;
            h.historyIds().ensureForEntity(a);h.historyIds().ensureForEntity(b);world.process();h.refreshSceneLighting();refresh(boxes);
            assertFalse(boxes[0].isDisabled());
            h.execute(new ChangeLightActivationCommand(world,h.historyIds(),b,false,true));refresh(boxes);
            assertTrue(boxes[0].isDisabled());assertTrue(boxes[1].isDisabled());assertFalse(boxes[2].isDisabled());
            assertEquals(SceneForwardRenderingCheckBox.UNAVAILABLE,boxes[0].explanation());
            h.undo();refresh(boxes);assertFalse(boxes[0].isDisabled());
            assertEquals(SceneForwardRenderingCheckBox.AVAILABLE,boxes[0].explanation());
        }finally{world.dispose();EventFlow.i().discardPending();}
    }
    private static void refresh(SceneForwardRenderingCheckBox[] boxes){EventFlow.i().flush();for(var box:boxes)box.act(0);}
}
