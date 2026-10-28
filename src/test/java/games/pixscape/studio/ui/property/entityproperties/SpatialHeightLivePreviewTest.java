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
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.render.TiledMapRenderState;
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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;

public class SpatialHeightLivePreviewTest {
    @BeforeClass public static void skin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unload() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void rectangleRowsUseAltitudeColumnsAtDifferentPanelWidths() throws Exception {
        try (Harness h = new Harness()) {
            Field altitude = SpatialPhysicsPanel.class.getDeclaredField("altitudeField"); altitude.setAccessible(true);
            FloatField altitudeField = (FloatField) altitude.get(h.panel);
            com.badlogic.gdx.scenes.scene2d.ui.Table table = (com.badlogic.gdx.scenes.scene2d.ui.Table) h.field.getParent();
            Assert.assertSame(table, altitudeField.getParent());
            for (float width : new float[]{220, 360, 600}) {
                table.setSize(width, table.getPrefHeight()); table.validate();
                Assert.assertEquals(altitudeField.getX(), h.field.getX(), .001f);
                for (int i=0;i<6;i++) {
                    FloatField rectangleField=table.findActor("actor-spatial-rectangle-"+i);
                    Assert.assertSame(table,rectangleField.getParent());
                    Assert.assertEquals(altitudeField.getX(),rectangleField.getX(),.001f);
                    Assert.assertEquals(altitudeField.getWidth(),rectangleField.getWidth(),.001f);
                }
                float labelX=Float.NaN;
                for (com.badlogic.gdx.scenes.scene2d.Actor child:table.getChildren()) {
                    if (!(child instanceof com.kotcrab.vis.ui.widget.VisLabel label) || !label.getText().toString().endsWith(":")) continue;
                    if (Float.isNaN(labelX)) labelX=child.getX();
                    Assert.assertEquals(labelX,child.getX(),.001f);
                }
            }
        }
    }

    @Test public void rectangleHeightEditUpdatesEnvelopeAndIsUndoableWithoutChangingWidthOrAltitude() throws Exception {
        try(Harness h=new Harness()) {
            h.assertRectangle(80);
            h.type("150"); h.key(Input.Keys.ENTER);h.world.process();h.assertRectangle(150);
            Assert.assertEquals(40,h.height.rectangles.staticRectangle.width,0);
            Assert.assertEquals(160,h.height.altitude,0);
            h.history.undo();h.world.process();h.assertRectangle(80);
            Assert.assertFalse(h.history.canUndo());
            h.history.redo();h.world.process();h.assertRectangle(150);
        }
    }
    @Test public void cancelledAndInvalidRectangleEditsPreserveSorting() throws Exception {
        try(Harness h=new Harness()) {
            h.type("250");h.key(Input.Keys.ESCAPE);h.world.process();h.assertRectangle(80);
            h.type("-5");h.key(Input.Keys.ENTER);h.world.process();h.assertRectangle(80);
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
            height.altitude = 160;
            height.rectangles=new games.pixscape.runtime.spatial.ActorSpatialRectangles();
            height.rectangles.staticRectangle=new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
            height.rectangles.staticRectangle.width=40;height.rectangles.staticRectangle.height=80;
            height.rectangles.staticRectangle.x=-20;
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
            field = panel.findActor("actor-spatial-rectangle-1");
        }
        void type(String text) {
            field.setText(text);
            field.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
        }
        void key(int code) { InputEvent event = new InputEvent(); event.setType(InputEvent.Type.keyDown); event.setKeyCode(code); field.fire(event); }
        void assertRectangle(float height) {
            float[] quad=new float[8];Assert.assertTrue(spatial.writeActorInfluenceQuad(actor,quad));
            Assert.assertArrayEquals(new float[]{-10,200,30,200,30,200+height,-10,200+height},quad,.001f);
        }
        @Override public void close() { identities.bind(null, null); world.dispose(); }
    }
}
