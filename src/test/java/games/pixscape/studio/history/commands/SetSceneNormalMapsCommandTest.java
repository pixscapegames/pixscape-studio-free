package games.pixscape.studio.history.commands;

import com.badlogic.gdx.utils.Json;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Test;

import static org.junit.Assert.*;

public class SetSceneNormalMapsCommandTest {
    @Test public void historyPersistsTheGlobalValueAndNotifiesEveryInspectorOfOnlyItsScene() {
        SceneMeta a=new SceneMeta("A","a.json"),b=new SceneMeta("B","b.json");
        HistoryManager history=new HistoryManager(16);
        boolean[] checkboxes={true,true,true};int[] preparations={0};
        EventFlow.i().subscribe(EventFlow.SceneNormalMapsChanged.class,event->{
            if(event.scene()==a){checkboxes[0]=event.scene().useNormalMaps;checkboxes[1]=event.scene().useNormalMaps;}
            if(event.scene()==b)checkboxes[2]=event.scene().useNormalMaps;
        });
        history.execute(new SetSceneNormalMapsCommand(a,false,value->{
            assertNotEquals("Resources are prepared before authored state is published",a.useNormalMaps,value);
            preparations[0]++;
        },null));
        for(int i=0;i<3;i++){
            EventFlow.i().flush();assertFalse(a.useNormalMaps);assertFalse(checkboxes[0]);assertFalse(checkboxes[1]);assertTrue(checkboxes[2]);
            Json json=new Json();assertFalse(json.fromJson(SceneMeta.class,json.toJson(a)).useNormalMaps);
            history.undo();EventFlow.i().flush();assertTrue(a.useNormalMaps);assertTrue(checkboxes[0]);assertTrue(checkboxes[1]);
            history.redo();
        }
        assertTrue(b.useNormalMaps);assertEquals(7,preparations[0]);
        assertTrue(new Json().fromJson(SceneMeta.class,"{}").useNormalMaps);
    }
    @Test public void failedPreparationLeavesTheAuthoredSettingUnchanged(){
        SceneMeta scene=new SceneMeta();
        SetSceneNormalMapsCommand command=new SetSceneNormalMapsCommand(scene,false,value->{throw new IllegalStateException("upload failed");},null);
        assertThrows(IllegalStateException.class,command::redo);
        assertTrue(scene.useNormalMaps);
    }
}
