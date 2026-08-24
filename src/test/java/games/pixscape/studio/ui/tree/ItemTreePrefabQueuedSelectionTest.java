package games.pixscape.studio.ui.tree;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ReorderLogicalLayerCommand;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ItemTreePrefabQueuedSelectionTest {
    @BeforeClass
    public static void loadVisUi() { VisUiTestBootstrap.loadSkin(); }

    @AfterClass
    public static void unloadVisUi() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void atomicPrefabSelectionSurvivesFlushAndThenMovesDownAndUp() {
        EventFlow.i().flush();
        World world = new World(new WorldConfiguration()
                .setSystem(new DirtyTrackerSystem(32)));
        HistoryManager history = new HistoryManager(8);
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 0;
        world.getMapper(LayerMetaComponent.class).create(layer).name = "Layer";
        int a = member(world, 5, 41);
        int b = member(world, 4, 41);
        int c = member(world, 3, 41);
        int d = member(world, 2, 41);
        int x = entity(world, 1);
        int y = entity(world, 0);
        world.process();

        IntArray members = new IntArray(new int[]{a, b, c, d});
        EntityNode prefabNode = EntityNode.prefabInstance(
                "Prefab A", null, 41, members, true);
        IdVisTree tree = new IdVisTree();
        tree.add(prefabNode);
        tree.registerPrefabInstanceNode(prefabNode);
        tree.getSelection().add(prefabNode);

        SelectionService selection = new SelectionService(world, null);
        AtomicInteger explicitPrefab = new AtomicInteger(41);
        EventFlow.Listener<EventFlow.SelectionChanged> listener = event -> {
            boolean matches = ItemTreePanel.selectionExactlyMatchesPrefab(
                    world,
                    world.getMapper(PrefabInstanceComponent.class),
                    event.ids(),
                    prefabNode);
            tree.getSelection().setProgrammaticChangeEvents(false);
            tree.getSelection().clear();
            if (matches) {
                tree.getSelection().add(prefabNode);
            } else {
                explicitPrefab.set(-1);
            }
            tree.getSelection().setProgrammaticChangeEvents(true);
        };
        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, listener);
        try {
            selection.replaceSelection(members, SelectionService.SelectionSource.TREE);
            assertEquals(4, selection.getSelectionSet().size);
            assertEquals(41, explicitPrefab.get());

            EventFlow.i().flush();
            assertEquals(41, explicitPrefab.get());
            assertEquals(1, tree.getSelection().size());
            assertSame(prefabNode, tree.getSelection().first());

            LayerLogicalOrderService order = new LayerLogicalOrderService(world);
            history.execute(new ReorderLogicalLayerCommand(
                    world, history.historyIds(), 0,
                    order.derive(0).movePrefab(explicitPrefab.get(), 1)));
            assertEquals(new IntArray(new int[]{x, a, b, c, d, y}),
                    order.derive(0).flattenedTopToBottom());

            history.execute(new ReorderLogicalLayerCommand(
                    world, history.historyIds(), 0,
                    order.derive(0).movePrefab(explicitPrefab.get(), -1)));
            assertEquals(new IntArray(new int[]{a, b, c, d, x, y}),
                    order.derive(0).flattenedTopToBottom());
        } finally {
            EventFlow.i().unsubscribe(EventFlow.SelectionChanged.class, listener);
            EventFlow.i().flush();
            world.dispose();
        }
    }

    @Test
    public void prefabHandlerUsesOneAtomicSelectionReplacement() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java"));
        int methodStart = source.indexOf("private void handlePrefabInstanceNodeSelection");
        int methodEnd = source.indexOf("private EntityNode findFirstPrefabInstanceNode", methodStart);
        String method = source.substring(methodStart, methodEnd);

        org.junit.Assert.assertTrue(method.contains("selectionService.replaceSelection("));
        org.junit.Assert.assertFalse(method.contains("selectionService.clearSelection("));
        org.junit.Assert.assertFalse(method.contains("selectionService.selectFromTree("));
    }

    @Test
    public void droppedPrefabSelectionWaitsForRebuildThenSelectsOnlyPrefabAndMoves() {
        EventFlow.i().flush();
        World world = new World(new WorldConfiguration()
                .setSystem(new DirtyTrackerSystem(32)));
        HistoryManager history = new HistoryManager(8);
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 0;
        world.getMapper(LayerMetaComponent.class).create(layer).name = "Layer";
        int a = member(world, 5, 51);
        int b = member(world, 4, 51);
        int c = member(world, 3, 51);
        int d = member(world, 2, 51);
        int x = entity(world, 1);
        int y = entity(world, 0);
        world.process();

        IntArray members = new IntArray(new int[]{a, b, c, d});
        SelectionService selection = new SelectionService(world, null);
        IdVisTree tree = new IdVisTree();
        AtomicInteger explicitPrefab = new AtomicInteger(51);
        EventFlow.Listener<EventFlow.SelectionChanged> listener = event -> {
            tree.getSelection().setProgrammaticChangeEvents(false);
            tree.getSelection().clear();
            ItemTreePanel.ExplicitPrefabSyncResult result =
                    ItemTreePanel.syncExplicitPrefabSelection(
                            world,
                            world.getMapper(PrefabInstanceComponent.class),
                            tree,
                            explicitPrefab.get(),
                            event.ids());
            if (result == ItemTreePanel.ExplicitPrefabSyncResult.INVALID) {
                explicitPrefab.set(-1);
            }
            tree.getSelection().setProgrammaticChangeEvents(true);
        };
        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, listener);
        try {
            selection.replaceSelection(members, SelectionService.SelectionSource.TREE);
            EventFlow.i().flush();

            assertContainsExactly(selection.getSelectionSnapshot(), members);
            assertEquals(51, explicitPrefab.get());
            assertEquals(0, tree.getSelection().size());

            EntityNode prefabNode = EntityNode.prefabInstance(
                    "Dropped", null, 51, members, true);
            EntityNode childA = child(a);
            EntityNode childB = child(b);
            EntityNode childC = child(c);
            EntityNode childD = child(d);
            prefabNode.add(childA);
            prefabNode.add(childB);
            prefabNode.add(childC);
            prefabNode.add(childD);
            tree.add(prefabNode);
            tree.registerPrefabInstanceNode(prefabNode);
            tree.registerNode(childA, a);
            tree.registerNode(childB, b);
            tree.registerNode(childC, c);
            tree.registerNode(childD, d);

            tree.getSelection().clear();
            assertEquals(ItemTreePanel.ExplicitPrefabSyncResult.SELECTED,
                    ItemTreePanel.syncExplicitPrefabSelection(
                            world,
                            world.getMapper(PrefabInstanceComponent.class),
                            tree,
                            explicitPrefab.get(),
                            selection.getSelectionSnapshot()));
            assertEquals(1, tree.getSelection().size());
            assertSame(prefabNode, tree.getSelection().first());
            assertFalse(tree.getSelection().toArray().contains(childA, true));
            assertFalse(tree.getSelection().toArray().contains(childB, true));
            assertFalse(tree.getSelection().toArray().contains(childC, true));
            assertFalse(tree.getSelection().toArray().contains(childD, true));

            LayerLogicalOrderService order = new LayerLogicalOrderService(world);
            history.execute(new ReorderLogicalLayerCommand(
                    world, history.historyIds(), 0,
                    order.derive(0).movePrefab(explicitPrefab.get(), 1)));
            assertEquals(new IntArray(new int[]{x, a, b, c, d, y}),
                    order.derive(0).flattenedTopToBottom());
        } finally {
            EventFlow.i().unsubscribe(EventFlow.SelectionChanged.class, listener);
            EventFlow.i().flush();
            world.dispose();
        }
    }

    @Test
    public void singleMemberAndRepeatedDropsSelectOnlyNewestPrefabNode() {
        EventFlow.i().flush();
        World world = new World();
        EventFlow.Listener<EventFlow.SelectionChanged> listener = null;
        try {
            int first = member(world, 2, 61);
            int secondA = member(world, 1, 62);
            int secondB = member(world, 0, 62);
            world.process();
            IntArray firstMembers = new IntArray(new int[]{first});
            IntArray secondMembers = new IntArray(new int[]{secondA, secondB});
            EntityNode firstPrefab = EntityNode.prefabInstance(
                    "First", null, 61, firstMembers, true);
            EntityNode secondPrefab = EntityNode.prefabInstance(
                    "Second", null, 62, secondMembers, true);
            IdVisTree tree = new IdVisTree();
            tree.add(firstPrefab);
            tree.add(secondPrefab);
            tree.registerPrefabInstanceNode(firstPrefab);
            tree.registerPrefabInstanceNode(secondPrefab);
            SelectionService selection = new SelectionService(world, null);
            AtomicInteger explicitPrefab = new AtomicInteger();
            listener = event -> {
                tree.getSelection().setProgrammaticChangeEvents(false);
                tree.getSelection().clear();
                ItemTreePanel.syncExplicitPrefabSelection(
                        world, world.getMapper(PrefabInstanceComponent.class),
                        tree, explicitPrefab.get(), selection.getSelectionSnapshot());
                tree.getSelection().setProgrammaticChangeEvents(true);
            };
            EventFlow.i().subscribe(EventFlow.SelectionChanged.class, listener);

            explicitPrefab.set(61);
            selection.replaceSelection(firstMembers, SelectionService.SelectionSource.TREE);
            tree.getSelection().clear();
            assertEquals(ItemTreePanel.ExplicitPrefabSyncResult.SELECTED,
                    ItemTreePanel.syncExplicitPrefabSelection(
                            world, world.getMapper(PrefabInstanceComponent.class),
                            tree, 61, selection.getSelectionSnapshot()));
            assertEquals(1, tree.getSelection().size());
            assertSame(firstPrefab, tree.getSelection().first());

            explicitPrefab.set(62);
            selection.replaceSelection(secondMembers, SelectionService.SelectionSource.TREE);
            EventFlow.i().flush();

            assertEquals(62, explicitPrefab.get());
            assertContainsExactly(selection.getSelectionSnapshot(), secondMembers);
            assertEquals(1, tree.getSelection().size());
            assertSame(secondPrefab, tree.getSelection().first());
            assertFalse(tree.getSelection().toArray().contains(firstPrefab, true));
        } finally {
            if (listener != null) {
                EventFlow.i().unsubscribe(EventFlow.SelectionChanged.class, listener);
            }
            EventFlow.i().flush();
            world.dispose();
        }
    }

    @Test
    public void worldCanvasPrefabDropDelegatesToSharedTreeSelectionApi() throws Exception {
        String canvas = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java"));
        int methodStart = canvas.indexOf("public void handlePrefabDrop");
        int methodEnd = canvas.indexOf("private boolean prefabContainsPhysics", methodStart);
        String method = canvas.substring(methodStart, methodEnd);
        assertTrue(method.contains("itemTreePanel.selectPrefabInstance("));
        assertFalse(method.contains("selectionService.clearSelection("));
        assertFalse(method.contains("selectionService.selectOnly("));
        assertFalse(method.contains("selectionService.selectAdd("));

        String tree = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java"));
        int clickStart = tree.indexOf("private void handlePrefabInstanceNodeSelection");
        int clickEnd = tree.indexOf("public void selectPrefabInstance", clickStart);
        assertTrue(tree.substring(clickStart, clickEnd)
                .contains("selectPrefabInstance(prefabNode.getPrefabInstanceId()"));
    }

    private static int member(World world, int z, int instanceId) {
        int entity = entity(world, z);
        PrefabInstanceComponent prefab =
                world.getMapper(PrefabInstanceComponent.class).create(entity);
        prefab.instanceId = instanceId;
        prefab.prefabId = "Prefab A";
        return entity;
    }

    private static int entity(World world, int z) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = 0;
        index.zIndex = z;
        world.getMapper(PixscapeIdentityComponent.class).create(entity).name = "E" + entity;
        return entity;
    }

    private static EntityNode child(int entityId) {
        return new EntityNode("E" + entityId, null, entityId, true);
    }

    private static void assertContainsExactly(IntArray actual, IntArray expected) {
        assertEquals(expected.size, actual.size);
        for (int i = 0; i < expected.size; i++) {
            assertTrue(actual.contains(expected.get(i)));
        }
    }
}
