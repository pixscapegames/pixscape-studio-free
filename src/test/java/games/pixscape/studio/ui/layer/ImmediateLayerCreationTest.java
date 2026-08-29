package games.pixscape.studio.ui.layer;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.CreateLayerCommand;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.LayerService;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ImmediateLayerCreationTest {
    @Test
    public void createsAboveActiveLayerAndPreservesSelectionHistoryDefaultsAndContents() {
        try (Fixture fixture = new Fixture()) {
            int bottom = fixture.addLayer("Bottom");
            int top = fixture.addLayer("Top");
            int bottomContent = fixture.addContent(0, 4);
            int topContent = fixture.addContent(1, 9);
            AtomicInteger selected = new AtomicInteger(bottom);
            int insertionIndex = LayersPanel.insertionIndexForNewLayer(fixture.layers, bottom);
            CreateLayerCommand command = new CreateLayerCommand(
                    fixture.layers, insertionIndex, "New Layer", bottom, selected::set);

            fixture.history.execute(command);

            assertEquals(3, fixture.layers.count());
            int created = fixture.layers.getLayerEntity(1);
            assertEquals(created, selected.get());
            assertEquals(bottom, fixture.layers.getLayerEntity(0));
            assertEquals(top, fixture.layers.getLayerEntity(2));
            assertEquals(0, fixture.index(bottomContent).layerIndex);
            assertEquals(2, fixture.index(topContent).layerIndex);
            assertEquals(4, fixture.index(bottomContent).zIndex);
            assertEquals(9, fixture.index(topContent).zIndex);
            fixture.assertNewLayerDefaults(created);
            long createdHistoryId = fixture.history.historyIds().historyIdOfEntity(created);
            int createdStableId = fixture.world.getMapper(PixscapeIdentityComponent.class)
                    .get(created).stableId;

            fixture.history.undo();

            assertEquals(2, fixture.layers.count());
            assertEquals(bottom, selected.get());
            assertEquals(bottom, fixture.layers.getLayerEntity(0));
            assertEquals(top, fixture.layers.getLayerEntity(1));
            assertEquals(0, fixture.index(bottomContent).layerIndex);
            assertEquals(1, fixture.index(topContent).layerIndex);

            fixture.history.redo();

            int restored = fixture.layers.getLayerEntity(1);
            assertEquals(restored, selected.get());
            assertEquals(createdHistoryId,
                    fixture.history.historyIds().historyIdOfEntity(restored));
            assertEquals(createdStableId, fixture.world.getMapper(PixscapeIdentityComponent.class)
                    .get(restored).stableId);
            fixture.assertNewLayerDefaults(restored);
            assertEquals(2, fixture.index(topContent).layerIndex);

            fixture.history.undo();
            assertEquals(bottom, selected.get());
            fixture.history.redo();
            int restoredAgain = fixture.layers.getLayerEntity(1);
            assertEquals(restoredAgain, selected.get());
            assertEquals(createdHistoryId,
                    fixture.history.historyIds().historyIdOfEntity(restoredAgain));
            assertEquals(createdStableId, fixture.world.getMapper(PixscapeIdentityComponent.class)
                    .get(restoredAgain).stableId);
        }
    }

    @Test
    public void noActiveLayerFallsBackToTopAndExistingNamingPolicyAllowsDuplicates() {
        try (Fixture fixture = new Fixture()) {
            fixture.addLayer("Existing");
            AtomicInteger selected = new AtomicInteger(-1);

            int firstIndex = LayersPanel.insertionIndexForNewLayer(fixture.layers, -1);
            fixture.history.execute(new CreateLayerCommand(
                    fixture.layers, firstIndex, "New Layer", -1, selected::set));
            int first = selected.get();
            int secondIndex = LayersPanel.insertionIndexForNewLayer(fixture.layers, first);
            fixture.history.execute(new CreateLayerCommand(
                    fixture.layers, secondIndex, "New Layer", first, selected::set));
            int second = selected.get();

            assertEquals(1, firstIndex);
            assertEquals(2, secondIndex);
            assertEquals(first, fixture.layers.getLayerEntity(1));
            assertEquals(second, fixture.layers.getLayerEntity(2));
            assertEquals("New Layer", fixture.meta(first).name);
            assertEquals("New Layer", fixture.meta(second).name);
        }
    }

    @Test
    public void productionUiHasOneImmediateCreationPathAndNoDialogOrRequest() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        String layersPanel = Files.readString(sourceRoot.resolve(
                "games/pixscape/studio/ui/layer/LayersPanel.java"), StandardCharsets.UTF_8);

        assertTrue(layersPanel.contains("createLayerImmediately();"));
        assertFalse(layersPanel.contains("NewLayerDialog"));
        assertFalse(Files.exists(sourceRoot.resolve(
                "games/pixscape/studio/ui/layer/NewLayerDialog.java")));
        assertFalse(Files.exists(sourceRoot.resolve(
                "games/pixscape/studio/ui/layer/NewLayerRequest.java")));

        try (var paths = Files.walk(sourceRoot)) {
            long commandCallers = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(ImmediateLayerCreationTest::readUnchecked)
                    .filter(source -> source.contains("new CreateLayerCommand("))
                    .count();
            assertEquals(1L, commandCallers);
        }
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final World world = new World(new WorldConfiguration());
        private final HistoryManager history = new HistoryManager(16);
        private final LayerService layers;

        private Fixture() {
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, new SceneMetaRuntime());
            layers = new LayerService(world, null, history.historyIds(), identities);
        }

        private int addLayer(String name) {
            int index = layers.addLayerTop(name);
            return layers.getLayerEntity(index);
        }

        private int addContent(int layerIndex, int zIndex) {
            int entity = world.create();
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = layerIndex;
            index.zIndex = zIndex;
            world.process();
            return entity;
        }

        private EntityIndexComponent index(int entity) {
            return world.getMapper(EntityIndexComponent.class).get(entity);
        }

        private LayerMetaComponent meta(int entity) {
            return world.getMapper(LayerMetaComponent.class).get(entity);
        }

        private void assertNewLayerDefaults(int entity) {
            LayerComponent layer = world.getMapper(LayerComponent.class).get(entity);
            LayerMetaComponent meta = meta(entity);
            VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).get(entity);
            EntityMetaComponent entityMeta = world.getMapper(EntityMetaComponent.class).get(entity);

            assertFalse(layer.spatialEnabled);
            assertEquals("New Layer", meta.name);
            assertEquals("", meta.description);
            assertFalse(meta.locked);
            assertTrue(visibility.visible);
            assertFalse(world.getMapper(LayerParallaxComponent.class).has(entity));
            assertEquals(EntityKind.LAYER, entityMeta.kind);
        }

        @Override
        public void close() {
            world.dispose();
        }
    }
}
