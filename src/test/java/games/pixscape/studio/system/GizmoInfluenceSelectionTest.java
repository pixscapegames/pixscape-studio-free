package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.system.SpatialRenderOrderSystem;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class GizmoInfluenceSelectionTest {
    @BeforeClass
    public static void skin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unload() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void normalActorSelectionPaintsAndFollowsHeightWithoutEnteringBlockEditMode() throws Exception {
        try (Harness h = new Harness()) {
            h.select(h.actor);
            h.paint();
            Assert.assertTrue(h.draws > 0);
            Assert.assertEquals(StudioEditingMode.NORMAL, h.modes.getCurrentMode());
            float previousTop = h.top;
            h.height.rectangles.staticRectangle.height = 150f / 300;
            h.world.process();
            h.paint();
            Assert.assertEquals(70, h.top - previousTop, .01f);
            h.world.getMapper(SpatialFootprintComponent.class).remove(h.actor);
            h.world.process();
            h.paint();
            Assert.assertEquals(0, h.draws);
        }
    }

    @Test
    public void physicsFocusHidesInfluenceUntilNormalSelectionResumes() throws Exception {
        try (Harness h = new Harness()) {
            h.physics.focusBody(h.actor);
            h.paint();
            Assert.assertEquals(StudioEditingMode.PHYSICS, h.modes.getCurrentMode());
            Assert.assertEquals(0, h.draws);

            h.select(h.actor);
            h.paint();
            Assert.assertEquals(0, h.draws);

            h.physics.clear();
            h.paint();
            Assert.assertEquals(StudioEditingMode.NORMAL, h.modes.getCurrentMode());
            Assert.assertTrue(h.draws > 0);
        }
    }

    @Test
    public void hudDocumentHidesInfluenceUntilSceneDocumentResumes() throws Exception {
        try (Harness h = new Harness()) {
            h.select(h.actor);
            h.paint();
            Assert.assertTrue(h.draws > 0);

            h.modes.activateHudDocument(0);
            h.paint();
            Assert.assertEquals(0, h.draws);

            h.modes.activateSceneDocument(StudioEditingMode.NORMAL, 0);
            h.paint();
            Assert.assertTrue(h.draws > 0);
        }
    }

    private static final class Harness implements AutoCloseable {
        final StudioEditingModeService modes = new StudioEditingModeService();
        final PhysicsSelectionService physics = new PhysicsSelectionService(modes);
        final DynamicEntityRenderState ecs = new DynamicEntityRenderState(1);
        final DrawList draw = new DrawList(4);
        final GizmoSystem gizmo;
        final World world;
        final int actor, slot;
        final LayerComponent layer;
        final SpatialRepresentationComponent height;
        int draws;
        float top;

        Harness() {
            Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class[]{Batch.class}, (proxy, method, args) -> {
                if (method.getName().equals("getColor")) return Color.WHITE;
                if (method.getName().equals("getPackedColor")) return Color.WHITE.toFloatBits();
                if (method.getName().endsWith("Matrix")) return new Matrix4();
                if (method.getName().equals("draw")) {
                    draws++;
                    if (args.length == 4 && args[1] instanceof float[] vertices)
                        for (int p = (int) args[2]; p < (int) args[2] + (int) args[3]; p += 5)
                            top = Math.max(top, vertices[p + 1]);
                }
                if (method.getReturnType() == boolean.class) return true;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
            OrthographicCamera camera = new OrthographicCamera();
            camera.viewportWidth = camera.viewportHeight = 100;
            ScreenViewport viewport = new ScreenViewport(camera);
            viewport.setScreenBounds(0, 0, 100, 100);
            ShapeDrawer drawer = new ShapeDrawer(batch, VisUI.getSkin().getRegion("white"));
            gizmo = new GizmoSystem(new StudioDrawContext(null, drawer, camera, viewport), null, null, null, physics, null, null, null, null);
            gizmo.setEditingModeService(modes);
            SpatialRenderOrderSystem spatial = new SpatialRenderOrderSystem(ecs, draw);
            world = new World(new WorldConfigurationBuilder().with(new BaseSystem() {
                @Override
                protected void processSystem() {
                    draw.clearEntries();
                    draw.addEcsSlot(slot);
                }
            }, spatial, gizmo).build());
            gizmo.setEnabled(false);
            int eid = world.create();
            layer = world.getMapper(LayerComponent.class).create(eid);
            layer.layerIndex = 0;
            layer.spatialEnabled = true;
            actor = world.create();
            world.getMapper(EntityIndexComponent.class).create(actor).layerIndex = 0;
            TransformComponent transform = world.getMapper(TransformComponent.class).create(actor);
            transform.x = 10;
            transform.y = 200;
            height = world.getMapper(SpatialRepresentationComponent.class).create(actor);
            height.rectangles = new games.pixscape.runtime.spatial.ActorSpatialRectangles();
            height.rectangles.staticRectangle = new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
            height.rectangles.staticRectangle.height = 80f / 300;
            SpatialFootprintComponent footprint = world.getMapper(SpatialFootprintComponent.class).create(actor);

            footprint.radiusPx = 9;
            slot = ecs.acquireSlotForEntity(actor);
            ecs.enabled[slot] = ecs.visible[slot] = true;
            ecs.kind[slot] = RenderKind.SPRITE;
            ecs.textureHandle[slot] = 1;
            ecs.layerIndex[slot] = 0;
            ecs.x1[slot] = ecs.x2[slot] = -10;
            ecs.x3[slot] = ecs.x4[slot] = 30;
            ecs.y1[slot] = ecs.y4[slot] = 200;
            ecs.y2[slot] = ecs.y3[slot] = 500;
            ecs.u2[slot] = ecs.v2[slot] = 1;
            modes.activateSceneDocument(StudioEditingMode.NORMAL, 0);
            world.process();
        }

        void select(int eid) throws Exception {
            Field f = GizmoSystem.class.getDeclaredField("selected");
            f.setAccessible(true);
            f.set(gizmo, new int[]{eid});
        }

        void paint() throws Exception {
            draws = 0;
            top = Float.NEGATIVE_INFINITY;
            Method m = GizmoSystem.class.getDeclaredMethod("drawSelectedActorInfluenceQuads");
            m.setAccessible(true);
            m.invoke(gizmo);
        }

        @Override
        public void close() {
            physics.dispose();
            world.dispose();
        }
    }
}
