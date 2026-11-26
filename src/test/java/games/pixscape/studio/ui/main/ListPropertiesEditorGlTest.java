package games.pixscape.studio.ui.main;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.history.commands.EditCustomPropertiesCommand;
import games.pixscape.studio.importer.tmx.TmxSceneImportRequest;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.ui.property.entityproperties.EditPropertiesDialog;
import org.junit.Test;

import static org.junit.Assert.*;

/** Real GL/Studio rendering with injected Scene2D events, not native mouse/keyboard evidence. */
public class ListPropertiesEditorGlTest {
    @Test
    public void importedListsAndEnumsEditThroughOneCommandAndRenderWithinTheDialog() throws Exception {
        ResourceAssetInspectorGlTest.withStudio(800, (studio, root) -> {
            FileHandle project = ResourceAssetInspectorGlTest.project(studio, root.child("project"));
            ResourceAssetInspectorGlTest.open(studio, project);
            var imported = studio.getSceneService().importTmxAsNewScene(new TmxSceneImportRequest(
                    new FileHandle("src/test/resources/tmx/list-enums/orthogonal.tmx"), "List and enums", true,
                    new FileHandle("src/test/resources/tmx/list-enums/types.tiled-project")));
            assertTrue(String.valueOf(imported.failure()), imported.imported());
            var context = studio.getSceneEditorContext();
            var world = context.world();
            var history = context.historyManager();
            int actor = -1;
            var identities = world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(
                    PixscapeIdentityComponent.class, CustomPropertiesComponent.class)).getEntities();
            for (int i = 0; i < identities.size(); i++) {
                int id = identities.get(i);
                if ("Actor".equals(world.getMapper(PixscapeIdentityComponent.class).get(id).name)) {
                    actor = id;
                }
            }
            assertTrue(actor >= 0);
            final int owner = actor;
            int ownerStableId = world.getMapper(PixscapeIdentityComponent.class).get(owner).stableId;
            context.selectionService().selectOnly(owner);
            PropertySet source = world.getMapper(CustomPropertiesComponent.class).get(owner).properties;
            PropertySet before = source.copy();
            int cursor = history.getCursor();
            var dialog = new EditPropertiesDialog("Actor properties", source, world, values ->
                    history.execute(new EditCustomPropertiesCommand(world, history.historyIds(),
                            history.historyIds().ensureForEntity(owner), true, before, values, 0, null)));
            dialog.show(studio.getUiStage());
            settle(studio);
            ResourceAssetInspectorGlTest.screenshot(studio, "list-enums-main");
            Object itemRow = row(dialog, "items");
            change(field(itemRow, "editListButton", Button.class));
            var list = (EditPropertiesDialog) studio.getUiStage().getActors().peek();
            settle(studio);
            assertTrue(list.getWidth() <= studio.getUiStage().getWidth());
            assertTrue(list.getHeight() < studio.getUiStage().getHeight());
            ResourceAssetInspectorGlTest.screenshot(studio, "list-enums-list");
            // Real row buttons, using injected ChangeEvents.
            change(list.findActor("property-list-down"));
            change(field(rows(list).get(2), "removeButton", Button.class));
            change(field(list, "addButton", Button.class));
            Object newRow = rows(list).peek();
            field(newRow, "typeBox", VisSelectBox.class).setSelected(PropertyType.FLOAT);
            field(newRow, "numberField", VisTextField.class).setText("16777217");
            change(buttons(list, "Apply").first());
            studio.getUiStage().act(1f);
            studio.getUiStage().act(0f);
            assertEquals(cursor, history.getCursor());
            assertTrue(before.contentEquals(source));
            // Exercise the mounted enum controls, including an ordinary selection.
            Array<VisSelectBox> choices = new Array<>();
            collect(valueEditor(dialog, "direction"), VisSelectBox.class, choices);
            choices.first().setSelected("South");
            Array<VisCheckBox> checks = new Array<>();
            collect(valueEditor(dialog, "flags"), VisCheckBox.class, checks);
            checks.get(1).setChecked(true);
            change(field(dialog, "addButton", Button.class));
            Object created = rows(dialog).peek();
            field(created, "nameField", VisTextField.class).setText("createdEmpty");
            field(created, "typeBox", VisSelectBox.class).setSelected(PropertyType.LIST);
            change(field(dialog, "addButton", Button.class));
            Object localEnum = rows(dialog).peek();
            field(localEnum, "nameField", VisTextField.class).setText("localState");
            field(localEnum, "typeBox", VisSelectBox.class).setSelected(PropertyType.ENUM);
            var enumCreator = (VisDialog) studio.getUiStage().getActors().peek();
            assertEquals("Create enum", enumCreator.getTitleLabel().getText().toString());
            field(enumCreator, "nameField", VisTextField.class).setText("Movement");
            field(enumCreator, "valuesField", VisTextArea.class).setText("Idle\nWalking\nRunning");
            settle(studio);
            ResourceAssetInspectorGlTest.screenshot(studio, "local-enum-create");
            assertTrue(enumCreator.getWidth() <= studio.getUiStage().getWidth());
            assertTrue(enumCreator.getHeight() < studio.getUiStage().getHeight());
            change(buttons(enumCreator, "Create").first());
            Array<VisSelectBox> localChoices = new Array<>();
            collect(valueEditor(dialog, "localState"), VisSelectBox.class, localChoices);
            localChoices.first().setSelected("Running");
            change(buttons(dialog, "Apply").first());
            assertEquals(cursor + 1, history.getCursor());
            PropertySet changed = world.getMapper(CustomPropertiesComponent.class).get(owner).properties.copy();
            assertEquals(7, changed.getEnumValue("flags").intValue());
            assertEquals("South", changed.getEnumValue("direction").stringValue());
            assertEquals("Movement", changed.getEnumValue("localState").typeName());
            assertEquals("Running", changed.getEnumValue("localState").stringValue());
            assertEquals(PropertyType.LIST, changed.typeOf("createdEmpty"));
            assertEquals(0, changed.getListValue("createdEmpty").size());
            assertEquals(7, changed.getListValue("items").getInt(0));
            assertEquals("7", changed.getListValue("items").getString(1));
            assertEquals(16777217d, changed.getListValue("items").getDouble(7), 0);
            for (int i = 0; i < 3; i++) {
                history.undo();
                assertTrue(before.contentEquals(world.getMapper(CustomPropertiesComponent.class).get(owner).properties));
                history.redo();
                assertTrue(changed.contentEquals(world.getMapper(CustomPropertiesComponent.class).get(owner).properties));
            }
            var cancelled = new EditPropertiesDialog("Cancel working copy", changed, values -> fail("Cancel published"));
            cancelled.show(studio.getUiStage());
            change(field(cancelled, "addButton", Button.class));
            change(buttons(cancelled, "Cancel").first());
            assertEquals(cursor + 1, history.getCursor());
            var cfg = ProjectConfig.getInstance();
            SceneService.saveScene(world, project.parent().child("scenes").child(imported.sceneFileName()), false);
            cfg.removeSceneMeta("Scene"); // Keep this witness focused on the imported scene.
            ProjectConfig.ProjectIO.saveProject(cfg, project);
            ResourceAssetInspectorGlTest.open(studio, project);
            ResourceAssetInspectorGlTest.screenshot(studio, "list-enums-reopened");
            var reopenedWorld = studio.getSceneEditorContext().world();
            var reopenedIds = reopenedWorld.getAspectSubscriptionManager().get(com.artemis.Aspect.all(
                    PixscapeIdentityComponent.class, CustomPropertiesComponent.class)).getEntities();
            boolean restored = false;
            for (int i = 0; i < reopenedIds.size(); i++) {
                int id = reopenedIds.get(i);
                if (reopenedWorld.getMapper(PixscapeIdentityComponent.class).get(id).stableId == ownerStableId) {
                    assertTrue(changed.contentEquals(reopenedWorld.getMapper(CustomPropertiesComponent.class).get(id).properties));
                    restored = true;
                }
            }
            assertTrue(restored);
            RuntimeExport.exportRuntime(ProjectConfig.getInstance(), project.parent(), root.child("export"));
            FileHandle runtimeDir = root.child("export").child(RuntimeExport.RUNTIME_DIR_NAME);
            var runtimeConfig = games.pixscape.runtime.loading.RuntimeProjectIO.loadProject(runtimeDir);
            var runtime = new com.artemis.World(new com.artemis.WorldConfiguration()
                    .setSystem(new com.artemis.managers.WorldSerializationManager()));
            var engine = new games.pixscape.runtime.engine.PixscapeEngine();
            var worldField = engine.getClass().getDeclaredField("world");
            worldField.setAccessible(true);
            worldField.set(engine, runtime);
            engine.getIdentityRegistry().bind(runtime, runtimeConfig.getCurrentSceneMeta());
            try {
                games.pixscape.runtime.loading.SceneLoader.loadScene(runtime,
                        runtimeDir.child("scenes").child(imported.sceneFileName()), false, runtimeConfig.getCurrentSceneMeta());
                runtime.process();
                var api = engine.api().entities().requireStableId(ownerStableId).properties();
                assertEquals(7, api.getEnumValue("flags").intValue());
                assertEquals("South", api.getEnumValue("direction").stringValue());
                assertEquals("Movement", api.getEnumValue("localState").typeName());
                assertEquals("Running", api.getEnumValue("localState").stringValue());
                assertEquals(3, api.getEnumValue("localState").choiceCount());
                assertEquals(0, api.getListValue("createdEmpty").size());
                assertEquals(16777217d, api.getListValue("items").getDouble(7), 0);
                assertEquals("7", api.getListValue("items").getString(1));
            } finally {
                engine.getIdentityRegistry().bind(null, null);
                runtime.dispose();
            }
            FileHandle retained = new FileHandle("build/local-validation/list-enums-" + System.nanoTime());
            retained.mkdirs();
            project.parent().copyTo(retained);
            root.child("export").copyTo(retained);
            new FileHandle("build/list-enums-gl-fixture-path.txt").writeString(retained.file().getAbsolutePath(), false);
        });
    }

    private static void settle(StudioApplicationAdapter studio) {
        // Advance the dialog's alpha-set/fade/focus action sequence, without wall-clock waits.
        studio.getUiStage().act(0f);
        studio.getUiStage().act(1f);
        studio.getUiStage().act(0f);
    }

    private static Object row(EditPropertiesDialog dialog, String name) throws Exception {
        for (Object row : rows(dialog)) {
            if (name.equals(field(row, "nameField", VisTextField.class).getText())) {
                return row;
            }
        }
        throw new AssertionError("Missing row " + name);
    }

    private static Actor valueEditor(EditPropertiesDialog dialog, String property) throws Exception {
        Object row = row(dialog, property);
        Array<?> rows = rows(dialog);
        int index = -1;
        for (int i = 0; i < rows.size; i++) {
            if (rows.get(i) == row) {
                index = i;
            }
        }
        return field(dialog, "rowsTable", VisTable.class).getChildren().get(index * 4 + 2);
    }

    private static Array<?> rows(EditPropertiesDialog dialog) throws Exception {
        return field(dialog, "rows", Array.class);
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }

    private static Array<VisTextButton> buttons(Actor root, String text) {
        Array<VisTextButton> all = new Array<>();
        collect(root, VisTextButton.class, all);
        Array<VisTextButton> matching = new Array<>();
        for (VisTextButton button : all) {
            if (text.contentEquals(button.getText())) {
                matching.add(button);
            }
        }
        assertFalse("Missing button " + text, matching.isEmpty());
        return matching;
    }

    private static <T> void collect(Actor actor, Class<T> type, Array<T> out) {
        if (type.isInstance(actor)) {
            out.add(type.cast(actor));
        }
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) {
                collect(child, type, out);
            }
        }
    }

    private static void change(Actor button) {
        button.fire(new ChangeListener.ChangeEvent());
    }
}
