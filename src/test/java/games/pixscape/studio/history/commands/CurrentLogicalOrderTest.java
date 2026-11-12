package games.pixscape.studio.history.commands;

import com.badlogic.gdx.utils.IntArray;
import games.pixscape.studio.service.AlignServiceTest;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import org.junit.Test;
import static org.junit.Assert.*;

public class CurrentLogicalOrderTest {
    @Test public void attachedRootDisappearsBeforePublication() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            int parent = f.root(0, 0), child = f.sprite(10, 0, 10, 10);
            f.process(); new LayerLogicalOrderService(f.world).derive(0);
            f.history.execute(new AddEntityToGameObjectCommand(f.world, f.history.historyIds(),
                    f.context.identityRegistry(), child, parent, 0));
            assertFalse(new LayerLogicalOrderService(f.world).derive(0).flattenedTopToBottom().contains(child));
        }
    }
    @Test public void detachedMemberAppearsBeforePublication() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            int parent = f.root(0, 0), child = f.sprite(10, 0, 10, 10);
            f.attach(child, parent); f.process(); new LayerLogicalOrderService(f.world).derive(0);
            f.history.execute(new RemoveEntityFromGameObjectCommand(f.world, f.history.historyIds(),
                    f.context.identityRegistry(), child));
            assertTrue(new LayerLogicalOrderService(f.world).derive(0).flattenedTopToBottom().contains(child));
        }
    }
    @Test public void restoredEntityAppearsBeforePublication() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            int child = f.sprite(10, 0, 10, 10); f.process(); new LayerLogicalOrderService(f.world).derive(0);
            long id = f.history.historyIds().ensureForEntity(child);
            f.history.execute(DeleteEntitiesCommandFactory.create(f.world, f.history.historyIds(), IntArray.with(child), null));
            f.process(); new LayerLogicalOrderService(f.world).derive(0); f.history.undo();
            assertTrue(new LayerLogicalOrderService(f.world).derive(0).flattenedTopToBottom()
                    .contains(f.history.historyIds().entityOfHistoryId(id)));
        }
    }
}
