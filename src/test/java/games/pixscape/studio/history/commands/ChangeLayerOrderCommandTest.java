package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ChangeLayerOrderCommandTest {

    @Test
    public void moveLayerUpUndoRedoRestoresExactOrder() {
        Fixture fx = new Fixture(3);
        int active = fx.layerEntityAt(1);

        executeIfMeaningful(fx.history, new ChangeLayerOrderCommand(fx.layerService, active, 2));
        Assert.assertEquals(ids(fx, 0, 2, 1), fx.currentOrderHistoryIds());

        fx.history.undo();
        Assert.assertEquals(ids(fx, 0, 1, 2), fx.currentOrderHistoryIds());

        fx.history.redo();
        Assert.assertEquals(ids(fx, 0, 2, 1), fx.currentOrderHistoryIds());
    }

    @Test
    public void moveLayerDownUndoRedoRestoresExactOrder() {
        Fixture fx = new Fixture(3);
        int active = fx.layerEntityAt(2);

        executeIfMeaningful(fx.history, new ChangeLayerOrderCommand(fx.layerService, active, 1));
        Assert.assertEquals(ids(fx, 0, 2, 1), fx.currentOrderHistoryIds());

        fx.history.undo();
        Assert.assertEquals(ids(fx, 0, 1, 2), fx.currentOrderHistoryIds());

        fx.history.redo();
        Assert.assertEquals(ids(fx, 0, 2, 1), fx.currentOrderHistoryIds());
    }

    @Test
    public void reorderAcrossMultipleLayersUndoRedoRestoresExactOrder() {
        Fixture fx = new Fixture(4);
        int moved = fx.layerEntityAt(0);

        executeIfMeaningful(fx.history, new ChangeLayerOrderCommand(fx.layerService, moved, 3));
        Assert.assertEquals(ids(fx, 1, 2, 3, 0), fx.currentOrderHistoryIds());

        fx.history.undo();
        Assert.assertEquals(ids(fx, 0, 1, 2, 3), fx.currentOrderHistoryIds());

        fx.history.redo();
        Assert.assertEquals(ids(fx, 1, 2, 3, 0), fx.currentOrderHistoryIds());
    }

    @Test
    public void noopReorderDoesNotCreateHistoryMutation() {
        Fixture fx = new Fixture(3);
        int beforeCursor = fx.history.getCursor();

        executeIfMeaningful(fx.history, new ChangeLayerOrderCommand(fx.layerService, fx.layerEntityAt(1), 1));

        Assert.assertEquals(beforeCursor, fx.history.getCursor());
        Assert.assertEquals(ids(fx, 0, 1, 2), fx.currentOrderHistoryIds());
    }

    @Test
    public void mixedReorderChainUndoRedoStepByStep() {
        Fixture fx = new Fixture(5);

        executeIfMeaningful(fx.history, new ChangeLayerOrderCommand(fx.layerService, fx.layerEntityAt(1), 3));
        Assert.assertEquals(ids(fx, 0, 2, 3, 1, 4), fx.currentOrderHistoryIds());

        executeIfMeaningful(fx.history, new ChangeLayerOrderCommand(fx.layerService, fx.layerEntityAt(4), 0));
        Assert.assertEquals(ids(fx, 4, 0, 2, 3, 1), fx.currentOrderHistoryIds());

        executeIfMeaningful(fx.history, new ChangeLayerOrderCommand(fx.layerService, fx.layerEntityAt(2), 4));
        Assert.assertEquals(ids(fx, 4, 0, 3, 1, 2), fx.currentOrderHistoryIds());

        fx.history.undo();
        Assert.assertEquals(ids(fx, 4, 0, 2, 3, 1), fx.currentOrderHistoryIds());

        fx.history.undo();
        Assert.assertEquals(ids(fx, 0, 2, 3, 1, 4), fx.currentOrderHistoryIds());

        fx.history.undo();
        Assert.assertEquals(ids(fx, 0, 1, 2, 3, 4), fx.currentOrderHistoryIds());

        fx.history.redo();
        Assert.assertEquals(ids(fx, 0, 2, 3, 1, 4), fx.currentOrderHistoryIds());

        fx.history.redo();
        Assert.assertEquals(ids(fx, 4, 0, 2, 3, 1), fx.currentOrderHistoryIds());

        fx.history.redo();
        Assert.assertEquals(ids(fx, 4, 0, 3, 1, 2), fx.currentOrderHistoryIds());
    }

    private static void executeIfMeaningful(HistoryManager history, Command command) {
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        history.execute(command);
    }

    private static List<Long> ids(Fixture fx, int... logicalOrder) {
        List<Long> ids = new ArrayList<>(logicalOrder.length);
        for (int position : logicalOrder) {
            ids.add(fx.initialLayerHistoryIds.get(position));
        }
        return ids;
    }

    private static final class Fixture {
        private final LayerService layerService;
        private final HistoryManager history = new HistoryManager(32);
        private final List<Integer> initialLayerEntities = new ArrayList<>();
        private final List<Long> initialLayerHistoryIds = new ArrayList<>();

        private Fixture(int layerCount) {
            World world = new World(new WorldConfiguration());
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, new SceneMetaRuntime());
            layerService = new LayerService(world, null, history.historyIds(), identities);
            for (int i = 0; i < layerCount; i++) {
                int index = layerService.addLayerTop("Layer " + i);
                int layerEntity = layerService.getLayerEntity(index);
                initialLayerEntities.add(layerEntity);
                initialLayerHistoryIds.add(layerService.historyIds().ensureForEntity(layerEntity));
            }
        }

        private int layerEntityAt(int initialPosition) {
            return initialLayerEntities.get(initialPosition);
        }

        private List<Long> currentOrderHistoryIds() {
            int count = layerService.count();
            List<Long> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int layerEntity = layerService.getLayerEntity(i);
                result.add(layerService.historyIds().ensureForEntity(layerEntity));
            }
            Assert.assertEquals(count, result.size());
            Assert.assertEquals(count, result.stream().distinct().count());
            return result;
        }
    }
}
