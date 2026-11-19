package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.property.entityproperties.physics.BodyPanel;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class EntityActionsInspectorTest {
    private static com.badlogic.gdx.graphics.GL20 originalGl;

    @Test
    public void hierarchyCommandsRefreshSelectedFieldsWithoutEditingOrReselecting() throws Exception {
        try (Harness h = new Harness()) {
            int parent = h.sprite(), child = h.sprite();
            h.world.getMapper(GameObjectComponent.class).create(parent);
            var p = h.world.getMapper(TransformComponent.class).get(parent);
            p.x = 100; p.y = -40; p.rotationRad = .4f; p.scaleX = p.scaleY = 2;
            var t = h.world.getMapper(TransformComponent.class).get(child);
            t.x = 20; t.y = 30; t.rotationRad = -.2f;
            t.scaleX = -1; t.scaleY = 3; t.originX = 7; t.originY = 9;
            h.select(child);
            games.pixscape.studio.event.EventFlow.i().flush();
            Object panel = field(h.panel, "transformPanel", TransformPanel.class);
            String[] names = {"xField", "yField", "rotationField", "scalexField", "scaleyField", "originxField", "originyField"};
            var fields = new games.pixscape.studio.ui.widget.FloatField[names.length];
            for (int i = 0; i < names.length; i++) {
                fields[i] = field(panel, names[i], games.pixscape.studio.ui.widget.FloatField.class);
                fields[i].setDisplayDecimals(5);
            }
            h.history.execute(new games.pixscape.studio.history.commands.AddEntityToGameObjectCommand(
                    h.world, h.history.historyIds(), h.identities, child, parent, 0));
            assertHierarchyFields(h, child, fields, 1);
            for (int cycle = 0; cycle < 3; cycle++) {
                h.history.undo();
                assertHierarchyFields(h, child, fields, 0);
                h.history.redo();
                assertHierarchyFields(h, child, fields, 1);
            }
            h.history.execute(new games.pixscape.studio.history.commands.RemoveEntityFromGameObjectCommand(
                    h.world, h.history.historyIds(), h.identities, child, 0));
            assertHierarchyFields(h, child, fields, 2);
            for (int cycle = 0; cycle < 3; cycle++) {
                h.history.undo();
                assertHierarchyFields(h, child, fields, 1);
                h.history.redo();
                assertHierarchyFields(h, child, fields, 2);
            }
        }
    }

    @Test
    public void hierarchyRefreshKeepsPhysicalAncestorScaleLocked() throws Exception {
        try (Harness h = new Harness()) {
            int parent = h.sprite(), group = h.sprite(), child = h.sprite();
            h.world.getMapper(GameObjectComponent.class).create(parent);
            h.world.getMapper(GameObjectComponent.class).create(group);
            h.world.getMapper(PhysicsBodyComponent.class).create(child);
            h.world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = h.identities.getStableId(group);
            h.world.getMapper(TransformComponent.class).get(parent).rotationRad = .4f;
            h.select(group);
            var panel = field(h.panel, "transformPanel", TransformPanel.class);
            var scale = field(panel, "uniformScaleField", games.pixscape.studio.ui.widget.FloatField.class);
            assertTrue(scale.isDisabled());
            h.history.execute(new games.pixscape.studio.history.commands.AddEntityToGameObjectCommand(
                    h.world, h.history.historyIds(), h.identities, group, parent, 0));
            for (int step = 0; step < 6; step++) {
                if (step == 1 || step == 4) {
                    h.history.undo();
                } else if (step == 2 || step == 5) {
                    h.history.redo();
                } else if (step == 3) {
                    h.history.execute(new games.pixscape.studio.history.commands.RemoveEntityFromGameObjectCommand(
                            h.world, h.history.historyIds(), h.identities, group, 0));
                }
                games.pixscape.studio.event.EventFlow.i().flush();
                assertTrue(scale.isDisabled());
                assertEquals(group, h.selection.getFirstSelectedEntityId());
                assertEquals(new int[]{1, 0, 1, 2, 1, 2}[step], h.history.getCursor());
            }
        }
    }

    private static void assertHierarchyFields(Harness h, int child,
            games.pixscape.studio.ui.widget.FloatField[] fields, int cursor) {
        var t = h.world.getMapper(TransformComponent.class).get(child);
        float[] before = {t.x, t.y, t.rotationRad, t.scaleX, t.scaleY, t.originX, t.originY};
        games.pixscape.studio.event.EventFlow.i().flush();
        // Sprite position fields use the existing origin-relative display convention.
        float[] displayed = {t.x - t.originX, t.y - t.originY, games.pixscape.studio.helper.GeometryHelper.rotationRadToEditorDeg(t.rotationRad),
                t.scaleX, t.scaleY, t.originX, t.originY};
        for (int i = 0; i < fields.length; i++) {
            assertEquals("field " + i, displayed[i], Float.parseFloat(fields[i].getText()), .0001f);
        }
        assertArrayEquals(before, new float[]{t.x, t.y, t.rotationRad, t.scaleX, t.scaleY, t.originX, t.originY}, 0);
        assertEquals(cursor, h.history.getCursor());
        assertEquals(child, h.selection.getFirstSelectedEntityId());
    }

    @Test
    public void addComponentOffersSpatialOnlyInTheEffectiveSpatialLayer() throws Exception {
        try (Harness h = new Harness()) {
            var batch = (com.badlogic.gdx.graphics.g2d.Batch) java.lang.reflect.Proxy.newProxyInstance(
                    com.badlogic.gdx.graphics.g2d.Batch.class.getClassLoader(),
                    new Class[]{com.badlogic.gdx.graphics.g2d.Batch.class}, (proxy, method, args) -> null);
            var stage = new com.badlogic.gdx.scenes.scene2d.Stage(new com.badlogic.gdx.utils.viewport.ScreenViewport(), batch);
            try {
                stage.addActor(h.panel);
                h.meta.physicsEnabled = false;
                int parent = h.sprite(), child = h.sprite();
                h.world.getMapper(GameObjectComponent.class).create(parent);
                h.world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = h.identities.getStableId(parent);
                h.world.getMapper(EntityIndexComponent.class).get(child).layerIndex = 7;
                var otherLayer = h.world.getMapper(LayerComponent.class).create(h.world.create());
                otherLayer.layerIndex = 7;
                otherLayer.spatialEnabled = true;
                h.select(child);
                var menu = openComponentMenu(h);
                assertFalse(menuLabels(menu).stream().anyMatch(s -> s.startsWith("Spatial") || s.startsWith("Footprint")));
                assertFalse(menuLabels(menu).stream().anyMatch(s -> s.startsWith("Physics")));
                assertFalse(h.history.canUndo());

                otherLayer.spatialEnabled = false;
                h.world.getMapper(LayerComponent.class).get(h.layer).spatialEnabled = true;
                h.select(child);
                menu = openComponentMenu(h);
                assertTrue(menuLabels(menu).contains("Spatial"));
                assertFalse(menuLabels(menu).stream().anyMatch(s -> s.startsWith("Footprint")));
                assertFalse(h.world.getMapper(SpatialFootprintComponent.class).has(child));
                assertFalse(h.history.canUndo());
                var spatial = field(h.panel, "spatialPanel", SpatialActorPanel.class);
                spatial.addFootprint();
                h.select(child);
                menu = openComponentMenu(h);
                assertFalse(menuLabels(menu).contains("Spatial"));
                var section = field(h.panel, "spatialSection", ToggleSection.class);
                var title = field(section, "button", VisTextButton.class);
                title.fire(new ChangeListener.ChangeEvent());
                assertTrue(spatial.isCollapsed());
                var cross = section.<com.badlogic.gdx.scenes.scene2d.ui.Button>findActor("remove-spatial");
                assertSame(com.kotcrab.vis.ui.VisUI.getSkin().get("delete", com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle.class), cross.getStyle());
                int removalCursor = h.history.getCursor();
                for (int cancellation = 0; cancellation < 2; cancellation++) {
                    cross.fire(new ChangeListener.ChangeEvent());
                    var cancelled = (games.pixscape.studio.ui.modal.StudioDialog) stage.getRoot().findActor("remove-spatial-dialog");
                    assertNotNull(cancelled);
                    assertEquals(2, cancelled.getButtonsTable().getChildren().size);
                    assertEquals("Cancel", ((VisTextButton) cancelled.getButtonsTable().getChildren().get(0)).getText().toString());
                    assertEquals("OK", ((VisTextButton) cancelled.getButtonsTable().getChildren().get(1)).getText().toString());
                    if (cancellation == 0) {
                        cancelled.getButtonsTable().getChildren().get(0).fire(new ChangeListener.ChangeEvent());
                    } else {
                        stage.keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                    }
                    stage.act(com.kotcrab.vis.ui.widget.VisDialog.FADE_TIME);
                    stage.act(0);
                    stage.act(0);
                    assertNull(cancelled.getStage());
                    assertTrue(h.world.getMapper(SpatialFootprintComponent.class).has(child));
                    assertEquals(removalCursor, h.history.getCursor());
                }
                cross.fire(new ChangeListener.ChangeEvent());
                assertTrue("Opening removal must not click the title", spatial.isCollapsed());
                var dialog = (games.pixscape.studio.ui.modal.StudioDialog) stage.getRoot().findActor("remove-spatial-dialog");
                assertNotNull(dialog);
                var confirmationText = new java.util.ArrayList<String>();
                collectLabels(dialog, confirmationText);
                assertTrue(confirmationText.contains("Remove Spatial from this object and all its descendants?\nUse Undo to restore all."));
                assertFalse(confirmationText.stream().anyMatch(text -> text.contains("reactivation")));
                assertEquals(2, dialog.getButtonsTable().getChildren().size);
                dialog.getButtonsTable().getChildren().get(1).fire(new ChangeListener.ChangeEvent());
                stage.act(com.kotcrab.vis.ui.widget.VisDialog.FADE_TIME);
                stage.act(0);
                stage.act(0);
                h.select(child);
                menu = openComponentMenu(h);
                assertTrue(menuLabels(menu).contains("Spatial"));
                h.history.undo();
                h.select(child);
                assertFalse(menuLabels(openComponentMenu(h)).contains("Spatial"));
                h.history.redo();
                h.select(child);
                menu = openComponentMenu(h);
                assertTrue(menuLabels(menu).contains("Spatial"));
                for (String required : new String[]{"transformSection", "textureSection", "materialSection"}) {
                    assertNull(field(field(h.panel, required, ToggleSection.class), "removeButton", com.badlogic.gdx.scenes.scene2d.ui.Button.class));
                }

                // An already open menu must not survive a change of layer eligibility.
                h.world.getMapper(LayerComponent.class).get(h.layer).spatialEnabled = false;
                menu.act(0);
                assertNull(menu.getStage());
                menu = openComponentMenu(h);
                assertFalse(menuLabels(menu).stream().anyMatch(s -> s.startsWith("Spatial")));
            } finally {
                stage.dispose();
            }
        }
    }

    @Test
    public void spatialGroupsUseIndentedFieldsAndFifteenUnitGaps() throws Exception {
        try (Harness h = new Harness()) {
            int entity = h.sprite();
            h.world.getMapper(SpatialFootprintComponent.class).create(entity);
            h.representation(entity);
            h.select(entity);
            var spatial = field(h.panel, "spatialPanel", SpatialActorPanel.class);
            var details = field(spatial, "detailsBlock", games.pixscape.studio.ui.widget.CollapsibleVisTable.class).content();
            var ownFields = field(spatial, "ownFields", games.pixscape.studio.ui.widget.CollapsibleVisTable.class);
            assertEquals(15, details.getCell(ownFields).getPadBottom(), 0);
            assertEquals(games.pixscape.studio.ui.config.CommonLayout.PAD_LEFT_SUBMENU,
                    ownFields.content().getCells().first().getPadLeft(), 0);
            var lastRectangle = spatial.findActor("actor-spatial-rectangle-3");
            assertEquals(15, ((com.badlogic.gdx.scenes.scene2d.ui.Table) lastRectangle.getParent()).getCell(lastRectangle).getPadBottom(), 0);
            var labels = new java.util.ArrayList<String>();
            collectLabels(details, labels);
            assertFalse(labels.contains("Footprint owned by this object"));
            assertFalse(labels.contains("Owner pose / local offsets (px)"));
            assertFalse(labels.contains("Selected image / local pixels"));
            var titles = labels.stream().filter(t -> t.equals("Footprint") || t.equals("Occlusion rectangle") || t.equals("Shadow pivot")).toList();
            assertEquals(java.util.List.of("Footprint", "Occlusion rectangle", "Shadow pivot"), titles);
            spatial.setSize(400, spatial.getPrefHeight());
            spatial.validate();
            var representation = field(spatial, "representationFields", games.pixscape.studio.ui.widget.CollapsibleVisTable.class);
            Actor footprintTitle = details.getCells().first().getActor();
            Actor occlusionTitle = representation.content().getCells().first().getActor();
            Actor altitude = field(spatial, "altitudeField", games.pixscape.studio.ui.widget.FloatField.class);
            Actor width = spatial.findActor("actor-spatial-rectangle-0");
            float footprintGap = footprintTitle.localToAscendantCoordinates(spatial, new com.badlogic.gdx.math.Vector2()).y
                    - altitude.localToAscendantCoordinates(spatial, new com.badlogic.gdx.math.Vector2(0, altitude.getHeight())).y;
            float occlusionGap = occlusionTitle.localToAscendantCoordinates(spatial, new com.badlogic.gdx.math.Vector2()).y
                    - width.localToAscendantCoordinates(spatial, new com.badlogic.gdx.math.Vector2(0, width.getHeight())).y;
            assertEquals("Title-to-field spacing", occlusionGap, footprintGap, 1f);
        }
    }

    private static com.kotcrab.vis.ui.widget.PopupMenu openComponentMenu(Harness h) throws Exception {
        h.panel.findActor("entity-add-component").fire(new ChangeListener.ChangeEvent());
        return field(h.panel, "componentMenu", com.kotcrab.vis.ui.widget.PopupMenu.class);
    }

    private static java.util.List<String> menuLabels(com.kotcrab.vis.ui.widget.PopupMenu menu) {
        var labels = new java.util.ArrayList<String>();
        for (Actor child : menu.getChildren()) {
            if (child instanceof com.kotcrab.vis.ui.widget.MenuItem item) {
                assertFalse("Unavailable components must not appear in Add component", item.isDisabled());
                labels.add(item.getText().toString());
            }
        }
        return labels;
    }

    @Test
    public void spatialGroupsKeepOwnershipAndRefreshAfterLayerRemovalAndUndo() throws Exception {
        try (Harness h = new Harness()) {
            int parent = h.sprite();
            int child = h.sprite();
            h.world.getMapper(GameObjectComponent.class).create(parent);
            h.world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = h.identities.getStableId(parent);
            h.world.getMapper(SpatialFootprintComponent.class).create(parent).radiusPx = 12;
            h.representation(parent);
            h.representation(child);
            h.world.getMapper(LayerComponent.class).get(h.layer).spatialEnabled = true;
            h.select(child);
            var section = field(h.panel, "spatialSection", ToggleSection.class);
            var header = field(section, "button", VisTextButton.class);
            assertTrue(header.getText().toString().endsWith("Spatial"));
            assertEquals(1, header.getChildren().size); // Text only, no footprint icon.
            var spatial = field(h.panel, "spatialPanel", SpatialActorPanel.class);
            var labels = new java.util.ArrayList<String>();
            collectLabels(spatial, labels);
            assertTrue(labels.contains("Footprint"));
            assertTrue(labels.contains("Occlusion rectangle"));
            assertTrue(labels.contains("Shadow pivot"));
            var inherited = spatial.<VisCheckBox>findActor("spatial-inherited-footprint");
            assertEquals("Inherited from parent", inherited.getText().toString());
            assertTrue(inherited.isChecked());
            assertTrue(field(spatial, "ownFields", games.pixscape.studio.ui.widget.CollapsibleVisTable.class).isCollapsed());
            var remove = section.<com.badlogic.gdx.scenes.scene2d.ui.Button>findActor("remove-spatial");
            assertFalse(remove.isVisible());
            remove.fire(new ChangeListener.ChangeEvent());
            assertTrue(h.world.getMapper(SpatialFootprintComponent.class).has(parent));
            assertFalse(h.history.canUndo());
            var layers = new LayerService(h.world, null, h.history.historyIds(), h.identities);
            h.history.execute(new games.pixscape.studio.history.commands.ToggleSpatialActorLayerCommand(
                    h.world, h.history.historyIds(), layers, h.layer, false));
            for (int cycle = 0; cycle < 3; cycle++) {
                h.world.process();
                games.pixscape.studio.event.EventFlow.i().flush();
                h.panel.act(0);
                assertFalse(section.isVisible());
                assertEquals(-1, games.pixscape.studio.service.SpatialActorShapeSupport.owner(h.world, child));
                h.history.undo();
                h.world.process();
                games.pixscape.studio.event.EventFlow.i().flush();
                h.panel.act(0);
                assertTrue(section.isVisible());
                assertEquals(parent, games.pixscape.studio.service.SpatialActorShapeSupport.owner(h.world, child));
                assertEquals(12, h.world.getMapper(SpatialFootprintComponent.class).get(parent).radiusPx, 0);
                h.history.redo();
            }
        }
    }

    private static void collectLabels(Actor actor, java.util.List<String> labels) {
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Label label) {
            labels.add(label.getText().toString());
        }
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group group) {
            for (Actor child : group.getChildren()) {
                collectLabels(child, labels);
            }
        }
    }

    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
        // This test exercises widget behavior, not shader compilation. Material's color picker
        // needs successful shader status in the headless GL stub; native rendering is checked separately.
        originalGl = com.badlogic.gdx.Gdx.gl;
        com.badlogic.gdx.Gdx.gl = com.badlogic.gdx.Gdx.gl20 = (com.badlogic.gdx.graphics.GL20)
                java.lang.reflect.Proxy.newProxyInstance(com.badlogic.gdx.graphics.GL20.class.getClassLoader(),
                        new Class[]{com.badlogic.gdx.graphics.GL20.class}, (proxy, method, args) -> {
                            if (method.getName().equals("glCreateShader") || method.getName().equals("glCreateProgram")) {
                                return 1;
                            }
                            if ((method.getName().equals("glGetShaderiv") || method.getName().equals("glGetProgramiv"))
                                    && ((int) args[1] == com.badlogic.gdx.graphics.GL20.GL_COMPILE_STATUS
                                    || (int) args[1] == com.badlogic.gdx.graphics.GL20.GL_LINK_STATUS)) {
                                ((java.nio.IntBuffer) args[2]).put(0, 1);
                                return null;
                            }
                            return method.invoke(originalGl, args);
                        });
    }

    @AfterClass
    public static void unloadSkin() {
        com.badlogic.gdx.Gdx.gl = com.badlogic.gdx.Gdx.gl20 = originalGl;
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void spriteParentRetainsVisibilityTextureAndTypeWithoutSpuriousProvenance() throws Exception {
        try (Harness h = new Harness()) {
            int entity = h.sprite();
            h.world.getMapper(GameObjectComponent.class).create(entity);
            h.select(entity);
            assertEquals("SPRITE", field(h.panel, "typeLabelValue", VisLabel.class).getText().toString());
            assertTrue(field(h.panel, "visibleCheckBox", VisCheckBox.class).isVisible());
            assertTrue(field(h.panel, "textureSection", ToggleSection.class).isVisible());
            assertTrue(field(h.panel, "materialSection", ToggleSection.class).isVisible());
            assertFalse(field(h.panel, "gameObjectSection", ToggleSection.class).isVisible());
            field(h.panel, "visibleCheckBox", VisCheckBox.class).setChecked(false);
            assertFalse(h.world.getMapper(VisibilityComponent.class).get(entity).visible);

            h.world.getMapper(GameObjectComponent.class).get(entity).sourceAssetId = "asset-source";
            h.select(entity);
            assertTrue(field(h.panel, "gameObjectSection", ToggleSection.class).isVisible());
            assertEquals("SPRITE", field(h.panel, "typeLabelValue", VisLabel.class).getText().toString());
            h.world.getMapper(AnimationComponent.class).create(entity);
            h.world.getMapper(EntityMetaComponent.class).get(entity).kind = EntityKind.ANIMATION;
            h.select(entity);
            assertEquals("ANIMATION", field(h.panel, "typeLabelValue", VisLabel.class).getText().toString());
            assertFalse(field(h.panel, "textureSection", ToggleSection.class).isVisible());
            assertTrue(field(h.panel, "animationSection", ToggleSection.class).isVisible());
        }
    }

    @Test
    public void sceneLightingControlsSectionVisibilityWithoutChangingStoredEntitySettings() throws Exception {
        try (Harness h = new Harness()) {
            int parent = h.sprite(), child = h.sprite();
            h.world.getMapper(GameObjectComponent.class).create(parent);
            h.world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = h.identities.getStableId(parent);
            var settings = h.world.getMapper(SurfaceLightingComponent.class).create(parent);
            settings.receiveLight = false;
            settings.shadowCaster = true;
            settings.alphaThreshold = .27f;
            h.meta.lightingEnabled = false;
            h.select(parent);
            var section = field(h.panel, "lightingSection", ToggleSection.class);
            assertFalse("Lighting disabled in scene must hide the entity section", section.isVisible());
            assertEquals(0, section.getPrefHeight(), 0);
            assertFalse(h.history.canUndo());
            h.history.execute(new games.pixscape.studio.history.commands.SetSceneLightingCommand(
                    h.world, h.meta, true, enabled -> { }, () -> { }));
            for (int cycle = 0; cycle < 3; cycle++) {
                games.pixscape.studio.event.EventFlow.i().flush();
                h.panel.act(0);
                assertTrue("Existing selection refreshes on scene activation", section.isVisible());
                assertFalse(settings.receiveLight);
                assertTrue(settings.shadowCaster);
                assertEquals(.27f, settings.alphaThreshold, 0);
                h.history.undo();
                games.pixscape.studio.event.EventFlow.i().flush();
                h.panel.act(0);
                assertFalse(section.isVisible());
                assertSame(settings, h.world.getMapper(SurfaceLightingComponent.class).get(parent));
                h.select(child);
                assertFalse(section.isVisible());
                assertFalse(h.world.getMapper(SurfaceLightingComponent.class).has(child));
                h.select(parent);
                h.history.redo();
            }
        }
    }

    @Test
    public void parentWithoutSpatialShowsItsOwnPhysicsAndCanRemoveItWithUndo() throws Exception {
        try (Harness h = new Harness()) {
            int parent = h.sprite(), child = h.sprite();
            h.world.getMapper(GameObjectComponent.class).create(parent);
            h.world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = h.identities.getStableId(parent);
            h.select(parent);
            BodyPanel bodyPanel = field(h.panel, "bodyPanel", BodyPanel.class);
            bodyPanel.addBody();
            h.world.getMapper(PhysicsBodyComponent.class).get(parent).gravityScale = 2.5f;
            h.select(parent);
            assertFalse(h.world.getMapper(SpatialFootprintComponent.class).has(parent));
            assertFalse(field(h.panel, "spatialSection", ToggleSection.class).isVisible());
            assertTrue("An own Body remains inspectable without Spatial", field(h.panel, "physicsSection", ToggleSection.class).isVisible());
            var remove = h.panel.<com.badlogic.gdx.scenes.scene2d.ui.Button>findActor("remove-physics");
            assertTrue(remove.isVisible());
            assertFalse(remove.isDisabled());

            h.select(child);
            assertFalse("An ancestor Body is not a child's own component", field(h.panel, "physicsSection", ToggleSection.class).isVisible());
            h.select(parent);
            remove.fire(new ChangeListener.ChangeEvent());
            for (int cycle = 0; cycle < 3; cycle++) {
                h.select(parent);
                assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(parent));
                assertFalse(field(h.panel, "physicsSection", ToggleSection.class).isVisible());
                h.history.undo();
                h.select(parent);
                assertTrue(field(h.panel, "physicsSection", ToggleSection.class).isVisible());
                assertEquals(2.5f, h.world.getMapper(PhysicsBodyComponent.class).get(parent).gravityScale, 0);
                assertEquals(h.identities.getStableId(parent), h.world.getMapper(GameObjectMemberComponent.class).get(child).parentStableId);
                assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(child));
                h.history.redo();
            }
        }
    }

    @Test
    public void physicsAddRemoveUsesHistoryAndPreservesIndependentFootprint() throws Exception {
        try (Harness h = new Harness()) {
            int entity = h.sprite();
            SpatialFootprintComponent footprint = h.world.getMapper(SpatialFootprintComponent.class).create(entity);
            footprint.radiusPx = 17;
            footprint.altitude = 23;
            h.representation(entity);
            h.select(entity);
            assertFalse(field(h.panel, "physicsSection", ToggleSection.class).isVisible());
            BodyPanel body = field(h.panel, "bodyPanel", BodyPanel.class);
            body.addBody();
            h.world.process();
            h.select(entity);
            assertTrue(field(h.panel, "physicsSection", ToggleSection.class).isVisible());
            body.addBody();
            h.history.undo();
            assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(entity));
            assertFalse(h.history.canUndo());
            h.history.redo();
            h.world.process();
            h.select(entity);
            h.panel.findActor("remove-physics").fire(new ChangeListener.ChangeEvent());
            assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(entity));
            for (int cycle = 0; cycle < 3; cycle++) {
                h.history.undo();
                assertTrue(h.world.getMapper(PhysicsBodyComponent.class).has(entity));
                h.history.redo();
                assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(entity));
                assertEquals(17, footprint.radiusPx, 0);
                assertEquals(23, footprint.altitude, 0);
            }
        }
    }

    @Test
    public void repeatSectionTracksActualCapabilityAndKeepsFunctionalAxisControls() throws Exception {
        try (Harness h = new Harness()) {
            int entity = h.sprite();
            h.world.getMapper(TransformComponent.class).get(entity).rotationRad = .5f;
            h.select(entity);
            assertFalse(field(h.panel, "repeatableSection", ToggleSection.class).isVisible());
            RepeatablePanel repeat = field(h.panel, "repeatablePanel", RepeatablePanel.class);
            repeat.addRepeat(true, false);
            h.world.process();
            h.select(entity);
            assertTrue(field(h.panel, "repeatableSection", ToggleSection.class).isVisible());
            assertEquals(0, h.world.getMapper(TransformComponent.class).get(entity).rotationRad, 0);
            repeat.addRepeat(false, true);
            h.history.undo();
            assertFalse(h.history.canUndo());
            assertFalse(h.world.getMapper(RenderRepeatComponent.class).has(entity));
            assertEquals(.5f, h.world.getMapper(TransformComponent.class).get(entity).rotationRad, 0);
            h.history.redo();
            h.select(entity);
            field(repeat, "repeatYBox", VisCheckBox.class).setChecked(true);
            assertTrue(h.world.getMapper(RenderRepeatComponent.class).get(entity).repeatY);
        }
    }

    @Test
    public void tiledStructuresKeepSpecializedActions() throws Exception {
        try (Harness h = new Harness()) {
            int entity = h.sprite();
            h.select(entity);
            assertTrue(h.panel.findActor("entity-add-component").isVisible());
            h.world.getMapper(TiledMapComponent.class).create(entity);
            h.select(entity);
            assertFalse(h.panel.findActor("entity-add-component").isVisible());
        }
    }

    @Test
    public void footprintAdmissionInheritedEditingAndOwnRemovalRemainIndependentOfPhysics() throws Exception {
        try (Harness h = new Harness()) {
            h.meta.physicsEnabled = false;
            int parent = h.sprite();
            h.world.getMapper(GameObjectComponent.class).create(parent);
            int child = h.sprite();
            h.world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = h.identities.getStableId(parent);
            // Child's authored layer cannot bypass the parent's effective layer.
            h.world.getMapper(EntityIndexComponent.class).get(child).layerIndex = 7;
            h.select(parent);
            SpatialActorPanel spatial = field(h.panel, "spatialPanel", SpatialActorPanel.class);
            assertFalse(field(h.panel, "spatialSection", ToggleSection.class).isVisible());
            assertNotNull(spatial.activationRejection());
            spatial.addFootprint();
            assertFalse(h.history.canUndo());
            h.world.getMapper(LayerComponent.class).get(h.layer).spatialEnabled = true;
            h.select(parent);
            assertNull(spatial.activationRejection());
            spatial.addFootprint();
            h.select(child);
            assertTrue(field(h.panel, "spatialSection", ToggleSection.class).isVisible());
            assertEquals(parent, games.pixscape.studio.service.SpatialActorShapeSupport.owner(h.world, child));
            var radius = spatial.<games.pixscape.studio.ui.widget.FloatField>findActor("spatial-footprint-1");
            assertTrue(field(spatial, "ownFields", games.pixscape.studio.ui.widget.CollapsibleVisTable.class).isCollapsed());
            float previousRadius = h.world.getMapper(SpatialFootprintComponent.class).get(parent).radiusPx;
            radius.setText("31.5");
            radius.commit(); // Hidden controls have no bound owner and cannot edit the ancestor.
            assertEquals(previousRadius, h.world.getMapper(SpatialFootprintComponent.class).get(parent).radiusPx, 0);
            h.select(parent);
            radius.setText("31.5");
            radius.commit();
            h.select(child);
            assertEquals(31.5f, h.world.getMapper(SpatialFootprintComponent.class).get(parent).radiusPx, 0);
            var inherited = spatial.<VisCheckBox>findActor("spatial-inherited-footprint");
            assertTrue(inherited.isChecked());
            assertNotNull(spatial.activationRejection());
            inherited.setChecked(false);
            h.select(child);
            assertEquals(child, games.pixscape.studio.service.SpatialActorShapeSupport.owner(h.world, child));
            assertFalse(h.panel.findActor("remove-spatial").isVisible());
            int cursor = h.history.getCursor();
            spatial.addFootprint();
            assertEquals(cursor, h.history.getCursor());
            inherited.setChecked(true);
            for (int i = 0; i < 3; i++) {
                h.select(child);
                assertEquals(parent, games.pixscape.studio.service.SpatialActorShapeSupport.owner(h.world, child));
                assertEquals(31.5f, h.world.getMapper(SpatialFootprintComponent.class).get(parent).radiusPx, 0);
                h.history.undo();
                h.select(child);
                assertEquals(child, games.pixscape.studio.service.SpatialActorShapeSupport.owner(h.world, child));
                h.history.redo();
            }
            assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(parent));
            assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(child));
        }
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static final class Harness implements AutoCloseable {
        final games.pixscape.studio.service.StudioGlTestScope globals;
        final World world = new World(new com.artemis.WorldConfigurationBuilder().with(
                new games.pixscape.runtime.system.DirtyTrackerSystem(32),
                new games.pixscape.runtime.system.GameObjectHierarchySystem(),
                new games.pixscape.runtime.system.GameObjectCompositionSystem(null)).build());
        final HistoryManager history = new HistoryManager(20);
        final IdentityRegistry identities = new IdentityRegistry();
        final EntityProperties panel;
        final SelectionService selection;
        final SceneMeta meta = new SceneMeta();
        int layer;

        Harness() throws Exception {
            globals = new games.pixscape.studio.service.StudioGlTestScope();
            meta.physicsEnabled = true;
            identities.bind(world, meta);
            LayerService layers = new LayerService(world, null, history.historyIds(), identities);
            layer = world.create();
            world.getMapper(LayerComponent.class).create(layer).layerIndex = 0;
            world.getMapper(games.pixscape.studio.component.LayerMetaComponent.class).create(layer).name = "Layer";
            AssetMetaDatabase assets = new AssetMetaDatabase();
            selection = new SelectionService(world, layers);
            EntityPropertiesContext context = new EntityPropertiesContext(world, history,
                    new PhysicsSelectionService(), new PhysicsService(world, null, meta), layers,
                    new AtlasStudioService(null), selection, identities, new IconResolver(world), () -> { },
                    assets::findById, ignored -> { }, Array::new,
                    new AnimationAssetAuthoringService(() -> assets, () -> null, ignored -> { }), 0, meta);
            var resolver = new StudioAssetVisualResolver(new games.pixscape.runtime.service.AtlasRuntimeService(),
                    assets::findById, StudioAssetVisualResolver.projectStandaloneAccess());
            panel = new EntityProperties(context, resolver, Array::new, ignored -> { });
        }

        int sprite() {
            int entity = world.create();
            identities.ensureStableId(entity);
            world.getMapper(EntityMetaComponent.class).create(entity).kind = EntityKind.SPRITE;
            world.getMapper(TransformComponent.class).create(entity);
            var dimensions = world.getMapper(DimensionsComponent.class).create(entity);
            dimensions.width = 32;
            dimensions.height = 24;
            world.getMapper(TextureRegionComponent.class).create(entity);
            world.getMapper(RenderMaterialComponent.class).create(entity);
            world.getMapper(AssetRefComponent.class).create(entity).assetId = 1;
            world.getMapper(VisibilityComponent.class).create(entity).visible = true;
            world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 0;
            return entity;
        }

        void representation(int entity) {
            var settings = new games.pixscape.runtime.spatial.ActorSpatialRectangles();
            settings.put(0, null, new games.pixscape.runtime.spatial.ActorSpatialRectangleData());
            world.getMapper(games.pixscape.runtime.component.spatial.SpatialRepresentationComponent.class).create(entity).rectangles = settings;
        }

        void select(int entity) {
            world.process();
            identities.rebuild();
            selection.selectOnly(entity, SelectionService.SelectionSource.PROGRAMMATIC);
            panel.setEntityId(entity);
        }

        @Override
        public void close() throws Exception {
            panel.dispose();
            identities.bind(null, null);
            world.dispose();
            globals.close();
        }
    }
}
