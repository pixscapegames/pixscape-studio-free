package games.pixscape.studio.service;

import com.badlogic.gdx.utils.Array;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SceneSaveCompletionTest {
    @Test public void headlessSaveCompletesBeforeClosing() {
        List<String> calls=new ArrayList<>();
        SceneService.runSaveSteps(null,new Array<>(),()->calls.add("save"),
                ()->calls.add("close"),failure->fail(failure.toString()));
        assertEquals(List.of("save","close"),calls);
    }
    @Test public void failedSaveReportsOriginalFailureAndDoesNotClose() {
        RuntimeException expected=new RuntimeException("disk full");
        List<Throwable> errors=new ArrayList<>();
        SceneService.runSaveSteps(null,new Array<>(),()->{throw expected;},
                ()->fail("Must not close after a failed save"),errors::add);
        assertEquals(List.of(expected),errors);
    }
}
