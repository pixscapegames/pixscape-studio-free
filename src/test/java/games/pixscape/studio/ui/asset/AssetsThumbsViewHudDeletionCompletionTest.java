package games.pixscape.studio.ui.asset;

import games.pixscape.studio.service.hud.HudScreenDeletionService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AssetsThumbsViewHudDeletionCompletionTest {
    @Test public void acquiredDeletionClosesRefreshesThenWarnsWithoutTreatingCleanupAsFailure() {
        HudScreenDeletionService.DeletionResult deletion = new HudScreenDeletionService.DeletionResult(
                "hud/status", true, true, List.of("C:/project/hud/status.json.delete-leftover"));
        ArrayList<String> events = new ArrayList<>();

        AssetsThumbsView.completeHudScreenDeletion(deletion,
                () -> events.add("close"),
                () -> events.add("refresh"),
                ignored -> events.add("warning"));

        assertEquals(List.of("close", "refresh", "warning"), events);
    }
}
