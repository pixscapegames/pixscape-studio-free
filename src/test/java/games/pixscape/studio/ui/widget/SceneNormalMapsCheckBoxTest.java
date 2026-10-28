package games.pixscape.studio.ui.widget;

import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class SceneNormalMapsCheckBoxTest {
    @BeforeClass public static void skin(){VisUiTestBootstrap.loadSkin();}
    @AfterClass public static void dispose(){VisUiTestBootstrap.unloadSkin();}
    @Test public void pointAndConeControlsShareSceneValueThroughUndoAndRedo(){
        SceneMeta a=new SceneMeta(),b=new SceneMeta();HistoryManager history=new HistoryManager(8);
        SceneNormalMapsCheckBox point=new SceneNormalMapsCheckBox(a,history,value->{},null);
        SceneNormalMapsCheckBox cone=new SceneNormalMapsCheckBox(a,history,value->{},null);
        SceneNormalMapsCheckBox other=new SceneNormalMapsCheckBox(b,new HistoryManager(8),value->{},null);
        point.setChecked(false);refresh(point,cone,other);
        assertFalse(point.isChecked());assertFalse(cone.isChecked());assertTrue(other.isChecked());
        history.undo();refresh(point,cone,other);assertTrue(point.isChecked());assertTrue(cone.isChecked());
        history.redo();refresh(point,cone,other);assertFalse(point.isChecked());assertFalse(cone.isChecked());
        cone.setChecked(true);refresh(point,cone,other);assertTrue(point.isChecked());assertTrue(a.useNormalMaps);assertTrue(b.useNormalMaps);
        assertEquals(2,history.getCursor());
    }
    private static void refresh(SceneNormalMapsCheckBox... boxes){EventFlow.i().flush();for(var box:boxes)box.act(0);}
}
