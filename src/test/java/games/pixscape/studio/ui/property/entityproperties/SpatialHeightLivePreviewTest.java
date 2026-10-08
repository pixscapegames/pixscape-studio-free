package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.SpatialRenderOrderSystem;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.*;
import java.lang.reflect.Field;

public class SpatialHeightLivePreviewTest {
    @BeforeClass public static void skin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unload() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void typingUpdatesRuntimeSquareAndEnterCreatesOneUndoableEdit() throws Exception {
        try (Harness h = new Harness()) {
            h.type("150"); h.world.process(); h.assertSquare(150);
            Assert.assertFalse(h.history.canUndo());
            h.panel.refreshFromModel(h.actor); h.panel.setEntityId(h.actor);
            Assert.assertEquals("150", h.field.getText());
            h.type("250"); h.world.process(); h.assertSquare(250);
            h.key(Input.Keys.ENTER);
            Assert.assertEquals(250, h.height.height, 0);
            h.history.undo(); h.world.process(); h.assertSquare(80);
            Assert.assertFalse(h.history.canUndo());
            h.history.redo(); h.world.process(); h.assertSquare(250);
            Assert.assertEquals(160, h.height.altitude, 0);
        }
    }

    @Test public void escapeAndInvalidTextRestoreAuthoredHeightWithoutHistory() throws Exception {
        try (Harness h = new Harness()) {
            h.type("250"); h.world.process(); h.assertSquare(250);
            h.key(Input.Keys.ESCAPE); h.world.process(); h.assertSquare(80);
            h.type("150"); h.type(""); h.world.process(); h.assertSquare(80);
            Assert.assertFalse(h.history.canUndo());
            h.type("150"); h.panel.setEntityId(-1); h.world.process(); h.assertSquare(80);
            Assert.assertEquals(-1, h.field.getEntityId());
            h.key(Input.Keys.ENTER); Assert.assertFalse(h.history.canUndo());
        }
    }

    @Test public void nativePastePreviewsAndFocusLossCommitsWhileProgrammaticRefreshDoesNotPreview() throws Exception {
        try (Harness h = new Harness()) {
            h.field.setText("150"); h.world.process(); h.assertSquare(80);
            h.field.selectAll();
            java.lang.reflect.Method paste = com.kotcrab.vis.ui.widget.VisTextField.class.getDeclaredMethod("paste", String.class, boolean.class);
            paste.setAccessible(true); paste.invoke(h.field, "250", true);
            h.world.process(); h.assertSquare(250); Assert.assertFalse(h.history.canUndo());
            com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent event = new com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent();
            event.setType(com.badlogic.gdx.scenes.scene2d.utils.FocusListener.FocusEvent.Type.keyboard); event.setFocused(false);
            h.field.fire(event);
            h.history.undo(); h.world.process(); h.assertSquare(80);
            Assert.assertFalse(h.history.canUndo());
        }
    }

    private static final class Harness implements AutoCloseable {
        final DynamicEntityRenderState ecs = new DynamicEntityRenderState(1);
        final DrawList draw = new DrawList(4);
        final SpatialRenderOrderSystem spatial = new SpatialRenderOrderSystem(ecs, new TiledMapRenderState(1), draw, null, null);
        final World world;
        final HistoryManager history = new HistoryManager(8);
        final IdentityRegistry identities = new IdentityRegistry();
        final int actor, slot;
        final SpatialHeightComponent height;
        final SpatialPhysicsPanel panel;
        final FloatField field;
        Harness() throws Exception {
            world = new World(new WorldConfigurationBuilder().with(new BaseSystem() {
                @Override protected void processSystem() { draw.clearEntries(); draw.addEcsSlot(slot); }
            }, spatial).build());
            int layer = world.create();
            LayerComponent layerComponent = world.getMapper(LayerComponent.class).create(layer);
            layerComponent.layerIndex = 0; layerComponent.spatialEnabled = true;
            actor = world.create();
            world.getMapper(EntityIndexComponent.class).create(actor).layerIndex = 0;
            TransformComponent transform = world.getMapper(TransformComponent.class).create(actor);
            transform.x = 10; transform.y = 200;
            height = world.getMapper(SpatialHeightComponent.class).create(actor);
            height.altitude = 160; height.height = 80;
            SpatialPhysicsFootprintComponent footprint = world.getMapper(SpatialPhysicsFootprintComponent.class).create(actor);
            footprint.valid = true; footprint.radiusPx = 9;
            slot = ecs.acquireSlotForEntity(actor); ecs.enabled[slot] = ecs.visible[slot] = true;
            ecs.kind[slot] = RenderKind.SPRITE; ecs.textureHandle[slot] = 1; ecs.layerIndex[slot] = 0;
            ecs.x1[slot] = ecs.x4[slot] = 0; ecs.x2[slot] = ecs.x3[slot] = 20;
            ecs.y1[slot] = ecs.y2[slot] = 200; ecs.y3[slot] = ecs.y4[slot] = 300;
            SceneMeta meta = new SceneMeta(); identities.bind(world, meta);
            LayerService layers = new LayerService(world, null, history.historyIds(), identities);
            AssetMetaDatabase assets = new AssetMetaDatabase();
            panel = new SpatialPhysicsPanel(new EntityPropertiesContext(world, history,
                    new PhysicsSelectionService(), new PhysicsService(world, null, meta), layers,
                    new AtlasStudioService(null), new SelectionService(world, layers), identities,
                    new IconResolver(world), () -> {}, assets::findById, ignored -> {}, Array::new,
                    new AnimationAssetAuthoringService(() -> assets, () -> null, ignored -> {}), 0, meta));
            world.process(); panel.setEntityId(actor);
            Field declared = SpatialPhysicsPanel.class.getDeclaredField("heightField"); declared.setAccessible(true);
            field = (FloatField) declared.get(panel);
        }
        void type(String text) {
            field.setText(text);
            field.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
        }
        void key(int code) { InputEvent event = new InputEvent(); event.setType(InputEvent.Type.keyDown); event.setKeyCode(code); field.fire(event); }
        void assertSquare(float size) {
            float[] quad = new float[8]; Assert.assertTrue(spatial.writeActorInfluenceQuad(actor, quad));
            Assert.assertArrayEquals(new float[]{10-size*.5f,200,10+size*.5f,200,10+size*.5f,200+size,10-size*.5f,200+size}, quad, .001f);
        }
        @Override public void close() { identities.bind(null, null); world.dispose(); }
    }
}
